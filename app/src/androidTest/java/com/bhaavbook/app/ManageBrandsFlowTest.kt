package com.bhaavbook.app

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhaavbook.app.data.repository.BrandRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Manage Brands & Categories — only reachable from Settings (there is no
 * direct link from the product list), so every test starts by drilling in
 * through the overflow menu.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ManageBrandsFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var brandRepository: BrandRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun navigateToManageBrands() {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Manage Brands & Categories").performClick()
    }

    @Test
    fun addingABrand_showsItInTheList() {
        navigateToManageBrands()

        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.onNodeWithText("Display name *").performTextInput("Amul")
        composeRule.onNodeWithText("Add").performClick()

        composeRule.onNodeWithText("Amul").assertIsDisplayed()
        composeRule.onNodeWithText("slug: amul").assertIsDisplayed()
    }

    @Test
    fun editingABrandsSlug_warnsAndUpdatesTheList() {
        runBlocking { brandRepository.save("Zed Black", customSlug = "zed-black") }
        navigateToManageBrands()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("slug: zed-black").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Edit").performClick()
        // The slug is read-only until unlocked via its own trailing "Edit" text button.
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText("Canonical Slug (CSV Key)").performTextClearance()
        composeRule.onNodeWithText("Canonical Slug (CSV Key)").performTextInput("zed-black-2")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Change slug?").assertIsDisplayed()
        composeRule.onNodeWithText("Change slug anyway").performClick()

        composeRule.onNodeWithText("slug: zed-black-2").assertIsDisplayed()
        composeRule.onNodeWithText("slug: zed-black").assertDoesNotExist()
    }

    @Test
    fun deletingABrand_removesItFromTheList() {
        runBlocking { brandRepository.save("Mangaldeep", customSlug = "mangaldeep") }
        navigateToManageBrands()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("slug: mangaldeep").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete brand?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("No brands added yet.").assertIsDisplayed()
    }

    @Test
    fun switchingToCategoriesTab_andAddingACategory_showsItInTheList() {
        navigateToManageBrands()

        composeRule.onNodeWithText("Categories (0)").performClick()
        composeRule.onNodeWithText("No categories added yet.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.onNodeWithText("Display name *").performTextInput("Pooja Items")
        composeRule.onNodeWithText("Add").performClick()

        composeRule.onNodeWithText("Pooja Items").assertIsDisplayed()
        composeRule.onNodeWithText("slug: pooja-items").assertIsDisplayed()
    }
}
