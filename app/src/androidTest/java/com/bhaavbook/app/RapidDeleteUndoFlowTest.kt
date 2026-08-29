package com.bhaavbook.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Regression guard for **issue.md Pass 2, finding #7 (High)**.
 *
 * The old `ProductListViewModel` kept a single `pendingDelete` slot: deleting
 * a second row immediately committed the first one to the DB, and
 * `commitDelete` then nulled the slot asynchronously — so tapping UNDO on the
 * first row's (still-visible) snackbar did nothing and that product was gone
 * for good.
 *
 * The fix gives every pending delete its own map entry (keyed by product id)
 * and its own undo window; `UiMessage.undoProductId` + `undoDelete(id)` make a
 * snackbar reverse exactly its own delete. This test deletes a second product
 * and then undoes the *first* one from its snackbar.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RapidDeleteUndoFlowTest {

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

    /** Seeds one product with a single variant and waits for the list to show it. */
    private fun seed(name: String, price: Double) {
        runBlocking {
            val id = productRepository.insert(Product(name = name))
            productRepository.upsertVariant(
                ProductVariant(productId = id, variantLabel = "Pack", sellingPrice = price)
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(name, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun deleteFromList(rowContentDescription: String) {
        composeRule.onNodeWithContentDescription(rowContentDescription, substring = true).performClick()
        // PriceSheet's own "Delete" text button (not the row-icon contentDescription).
        composeRule.onNodeWithText("Delete").performClick()
    }

    @Test
    fun deletingASecondProduct_doesNotBreakUndoOfTheFirst() {
        seed("Parle Rusk", 10.0)
        seed("Britannia Cake", 20.0)

        // Delete the first — its snackbar (with UNDO) appears.
        deleteFromList("Parle Rusk, ₹10")
        composeRule.onNodeWithText("\"Parle Rusk\" deleted").assertIsDisplayed()

        // Delete the second right away. Under the old code this synchronously
        // committed Parle Rusk's deletion and nulled the undo slot.
        deleteFromList("Britannia Cake, ₹20")

        // Parle Rusk's snackbar is still on screen — tap its UNDO.
        composeRule.onNodeWithText("UNDO").performClick()

        // Parle Rusk must come back. (Britannia Cake is left to its own window
        // and is expected to stay deleted — not asserted here.)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Parle Rusk", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Parle Rusk, ₹10", substring = true)
            .assertIsDisplayed()
    }
}
