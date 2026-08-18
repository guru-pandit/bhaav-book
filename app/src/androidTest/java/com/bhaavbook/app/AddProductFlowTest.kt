package com.bhaavbook.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real app — real navigation, real ViewModels, real Room DAOs —
 * against the in-memory database from [com.bhaavbook.app.di.TestAppModule].
 * See the `e2e-testing` skill for the conventions this follows.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AddProductFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun addingAProduct_showsItInThePriceList() {
        // The in-memory database starts empty, so the list opens on the
        // first-run empty state with its own "Add item" action (R.string.add_product).
        composeRule.onNodeWithText("Add item").performClick()

        composeRule.onNodeWithText("Product name *").performTextInput("Amul Butter")

        composeRule.onNodeWithText("Add variant").performClick()
        composeRule.onNodeWithText("Variant label *").performTextInput("500 g")
        composeRule.onNodeWithText("Selling price (₹) *").performTextInput("285")
        composeRule.onNodeWithText("Add").performClick()

        composeRule.onNodeWithText("Save").performClick()

        // Save navigates back to the list once the product is persisted. The
        // price is only reachable via the row's contentDescription — PricePill
        // clears its own semantics, so onNodeWithText can't see the price text.
        composeRule.onNodeWithContentDescription("Amul Butter, ₹285", substring = true)
            .assertIsDisplayed()
    }
}
