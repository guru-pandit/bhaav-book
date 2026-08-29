package com.bhaavbook.app

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductVariant
import com.bhaavbook.app.data.repository.ProductRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Edit and delete flows, driven from a product seeded directly through
 * [ProductRepository] rather than by tapping through "Add item" — see the
 * `e2e-testing` skill's "Seeding state without driving the UI" section.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProductEditDeleteFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var productRepository: ProductRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun seedTataSalt(): Long {
        val productId = runBlocking {
            val id = productRepository.insert(Product(name = "Tata Salt", brand = "Tata"))
            productRepository.upsertVariant(
                ProductVariant(productId = id, variantLabel = "1 kg", sellingPrice = 25.0)
            )
            id
        }
        // The list is already collecting the (empty) Flow by the time this
        // runs, so wait for the reactive Room query to catch up.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Tata — Tata Salt", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        return productId
    }

    @Test
    fun editingAProduct_updatesThePriceList() {
        seedTataSalt()

        composeRule.onNodeWithContentDescription("Tata — Tata Salt, ₹25", substring = true)
            .performClick()
        composeRule.onNodeWithText("Edit").performClick()

        // The row-level edit control is an icon button — reachable only by its
        // contentDescription, not by onNodeWithText.
        composeRule.onNodeWithContentDescription("Edit variant").performClick()
        composeRule.onNodeWithText("Selling max price (₹) *").performTextClearance()
        composeRule.onNodeWithText("Selling max price (₹) *").performTextInput("30")
        composeRule.onNodeWithText("Update").performClick()

        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithContentDescription("Tata — Tata Salt, ₹30", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun enteringSellingMinPrice_showsRangeInPriceSheet() {
        seedTataSalt()

        composeRule.onNodeWithContentDescription("Tata — Tata Salt, ₹25", substring = true)
            .performClick()
        composeRule.onNodeWithText("Edit").performClick()

        composeRule.onNodeWithContentDescription("Edit variant").performClick()
        composeRule.onNodeWithText("Selling min price (₹)").performTextInput("20")
        composeRule.onNodeWithText("Update").performClick()

        composeRule.onNodeWithText("Save").performClick()

        // The list row and the price sheet both use formatPriceRange, so the
        // same "min - max" text should now appear in both places.
        composeRule.onNodeWithContentDescription("Tata — Tata Salt, ₹20 - ₹25", substring = true)
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("₹20 - ₹25", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun sellingMinGreaterThanMaxPrice_blocksSavingTheVariant() {
        seedTataSalt()

        composeRule.onNodeWithContentDescription("Tata — Tata Salt, ₹25", substring = true)
            .performClick()
        composeRule.onNodeWithText("Edit").performClick()

        composeRule.onNodeWithContentDescription("Edit variant").performClick()
        composeRule.onNodeWithText("Selling min price (₹)").performTextInput("999")
        composeRule.onNodeWithText("Update").performClick()

        // The sheet stays open (ProductEditViewModel.saveVariantForm returns
        // early on a validation error rather than committing) and shows the
        // field-level error instead.
        composeRule.onNodeWithText("Selling min price cannot be greater than max price")
            .assertIsDisplayed()
    }

    @Test
    fun deletingAProduct_removesItFromTheListWithUndo() {
        seedTataSalt()

        composeRule.onNodeWithContentDescription("Tata — Tata Salt, ₹25", substring = true)
            .performClick()
        composeRule.onNodeWithText("Delete").performClick()

        // The delete is optimistic: the row disappears immediately, before the
        // undo window's real DB commit fires (see ProductListViewModel.requestDelete).
        composeRule.onNodeWithText("\"Tata Salt\" deleted").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Tata — Tata Salt", substring = true)
            .assertDoesNotExist()

        composeRule.onNodeWithText("UNDO").performClick()

        composeRule.onNodeWithContentDescription("Tata — Tata Salt, ₹25", substring = true)
            .assertIsDisplayed()
    }
}
