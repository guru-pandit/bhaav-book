package com.bhaavbook.app

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
import com.bhaavbook.app.data.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Settings toggles that change what the rest of the app shows.
 *
 * [SettingsRepository] is backed by real DataStore, not swapped by
 * `TestAppModule` — it survives across every test in the same instrumentation
 * process (see the `e2e-testing` skill). Every setting this class touches is
 * reset back to its default in [tearDown] so later test classes (e.g.
 * `AddProductFlowTest`, which assumes the default "₹" symbol) don't inherit
 * leftover state.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var productRepository: ProductRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() = runBlocking {
        settingsRepository.updateCurrencySymbol("₹")
        settingsRepository.updateShowCostPrice(false)
        settingsRepository.updateShowWholesalePrice(false)
    }

    private fun seedTataSalt(costPrice: Double? = null) {
        runBlocking {
            val id = productRepository.insert(Product(name = "Tata Salt", brand = "Tata"))
            productRepository.upsertVariant(
                ProductVariant(
                    productId = id,
                    variantLabel = "1 kg",
                    sellingPrice = 100.0,
                    costPrice = costPrice
                )
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Tata — Tata Salt", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun navigateToSettings() {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Settings").performClick()
    }

    @Test
    fun togglingShowCostPrice_revealsItInThePriceSheet() {
        seedTataSalt(costPrice = 18.0)

        navigateToSettings()
        composeRule.onNodeWithText("Show cost price").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithContentDescription("Tata — Tata Salt, ₹100", substring = true)
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Cost ₹18", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun changingCurrencySymbol_reflectsInThePriceList() {
        seedTataSalt()

        navigateToSettings()
        composeRule.onNodeWithText("Currency symbol").performTextClearance()
        composeRule.onNodeWithText("Currency symbol").performTextInput("$")
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Tata — Tata Salt, $100", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
