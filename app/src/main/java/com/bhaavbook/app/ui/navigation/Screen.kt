package com.bhaavbook.app.ui.navigation

// ---------------------------------------------------------------------------
// Navigation destinations as a sealed class for type safety
// ---------------------------------------------------------------------------
sealed class Screen(val route: String) {

    data object ProductList : Screen("product_list")

    data object AddProduct : Screen("add_product")

    data object EditProduct : Screen("edit_product/{productId}") {
        fun createRoute(productId: Long) = "edit_product/$productId"
    }

    data object CsvImport : Screen("csv_import")

    data object Settings : Screen("settings")
}
