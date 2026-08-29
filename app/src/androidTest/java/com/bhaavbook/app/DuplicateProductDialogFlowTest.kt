package com.bhaavbook.app

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Regression guard for **issue.md Pass 2, finding #8 (High)**.
 *
 * The "Duplicate product" `AlertDialog` on `ProductEditScreen` used to wire
 * both its Cancel button and its `onDismissRequest` (back / tap-outside) to
 * `clearSaveError()`, which nulls `saveError` but not `duplicateWarning` — the
 * flag the dialog's visibility is bound to — so the dialog could not be
 * dismissed. The fix added `ProductEditViewModel.dismissDuplicateWarning()`.
 * This test checks Cancel closes the dialog and saves nothing.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DuplicateProductDialogFlowTest {

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

    @Test
    fun cancellingTheDuplicateWarning_closesTheDialogWithoutSaving() {
        // Existing product with no brand, so the new one below collides on
        // name alone (ProductDao.getByBrandAndName matches brand IS NULL).
        runBlocking {
            val id = productRepository.insert(Product(name = "Rice Bag"))
            productRepository.upsertVariant(
                ProductVariant(productId = id, variantLabel = "5 kg", sellingPrice = 50.0)
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Rice Bag", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Add a second "Rice Bag" via the FAB (list is not empty, so the
        // empty-state button isn't present — the FAB carries the same string
        // as a contentDescription).
        composeRule.onNodeWithContentDescription("Add item").performClick()
        composeRule.onNodeWithText("Product name *").performTextInput("Rice Bag")

        composeRule.onNodeWithText("Add variant").performClick()
        composeRule.onNodeWithText("Variant label *").performTextInput("5 kg")
        composeRule.onNodeWithText("Selling max price (₹) *").performTextInput("55")
        composeRule.onNodeWithText("Add").performClick()

        composeRule.onNodeWithText("Save").performClick()

        // The duplicate warning dialog appears (DB round-trip via checkDuplicate).
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Duplicate product").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        // BUG #8: clearSaveError() leaves duplicateWarning set, so the dialog
        // recomposes and stays. These assertions fail until #8 is fixed.
        composeRule.onNodeWithText("Duplicate product").assertDoesNotExist()
        composeRule.onNodeWithText("Save anyway").assertDoesNotExist()

        // Back on the edit form, nothing saved yet.
        composeRule.onNodeWithText("Product name *").assertIsDisplayed()
    }
}
