package com.bhaavbook.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNode
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Search, filter-chip and sort-sheet flows against three seeded products —
 * see the `e2e-testing` skill's "Seeding state without driving the UI"
 * section for why these are inserted directly rather than through "Add item".
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProductSearchFilterSortFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var productRepository: ProductRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            seed("Amul Butter", "Amul", "Dairy", "500 g", 285.0, inStock = true)
            seed("Amul Milk", "Amul", "Dairy", "1 L", 60.0, inStock = true)
            seed("Tata Salt", "Tata", "Grocery", "1 kg", 25.0, inStock = false)
        }
        // The list starts collecting before seeding runs, so wait for the
        // reactive Room flow to catch up before driving the UI.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasContentDescription("Amul — Amul Butter", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private suspend fun seed(
        name: String,
        brand: String,
        category: String,
        variantLabel: String,
        price: Double,
        inStock: Boolean
    ) {
        val productId = productRepository.insert(Product(name = name, brand = brand, category = category))
        productRepository.upsertVariant(
            ProductVariant(
                productId = productId,
                variantLabel = variantLabel,
                sellingPrice = price,
                inStock = inStock
            )
        )
    }

    /** Filtering/searching re-runs a Room query — a real async round trip. */
    private fun waitUntilHidden(contentDescriptionSubstring: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(contentDescriptionSubstring, substring = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun searchingByName_showsOnlyTheMatchingItem() {
        composeRule.onNode(hasSetTextAction()).performTextInput("Milk")
        waitUntilHidden("Amul — Amul Butter")

        composeRule.onNodeWithContentDescription("Amul — Amul Milk", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Amul — Amul Butter", substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Tata — Tata Salt", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun filteringByCategory_showsOnlyThatCategory() {
        // "Dairy" is safe to match on exact visible text: the row's own
        // category text is folded into one combined "pack · category" string,
        // not a standalone "Dairy" node — unlike brand names, which do repeat
        // verbatim inside each row and would collide with the chip.
        composeRule.onNodeWithText("Dairy").performClick()
        waitUntilHidden("Tata — Tata Salt")

        composeRule.onNodeWithContentDescription("Amul — Amul Butter", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Amul — Amul Milk", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Tata — Tata Salt", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun filteringInStockOnly_hidesOutOfStockItems() {
        composeRule.onNodeWithText("In stock").performClick()
        waitUntilHidden("Tata — Tata Salt")

        composeRule.onNodeWithContentDescription("Tata — Tata Salt", substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Amul — Amul Butter", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Amul — Amul Milk", substring = true)
            .assertIsDisplayed()
    }

    private fun currentRowDescriptions(): List<String> = composeRule
        .onAllNodes(hasContentDescription(", ₹", substring = true))
        .fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.ContentDescription].first() }

    @Test
    fun sortingByPriceAscending_ordersCheapestFirst() {
        composeRule.onNodeWithContentDescription("Sort & filter").performClick()
        composeRule.onNodeWithText("Price: low to high").performClick()

        // Sorting re-runs the Room query, so wait for the reordered list
        // rather than reading whatever happens to be composed right after the click.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            currentRowDescriptions().firstOrNull()?.contains("₹25") == true
        }

        val rowDescriptions = currentRowDescriptions()

        assertTrue(
            "Expected 3 product rows, got: $rowDescriptions",
            rowDescriptions.size == 3
        )
        assertTrue(
            "Cheapest item (Tata Salt, ₹25) should sort first: $rowDescriptions",
            rowDescriptions.first().contains("₹25")
        )
        assertTrue(
            "Priciest item (Amul Butter, ₹285) should sort last: $rowDescriptions",
            rowDescriptions.last().contains("₹285")
        )
    }
}
