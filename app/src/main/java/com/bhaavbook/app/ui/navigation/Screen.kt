package com.bhaavbook.app.ui.navigation

/** Navigation destinations, as a sealed class so routes are never string literals at call sites. */
sealed class Screen(val route: String) {

    data object ProductList : Screen("product_list")

    data object AddProduct : Screen("add_product")

    data object EditProduct : Screen("edit_product/{productId}") {
        /** Also the `SavedStateHandle` key `ProductEditViewModel` reads. */
        const val ARG_PRODUCT_ID = "productId"

        fun createRoute(productId: Long) = "edit_product/$productId"
    }

    data object CsvImport : Screen("csv_import")

    data object Settings : Screen("settings")

    data object ManageBrands : Screen("manage_brands")
}
