package com.bhaavbook.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bhaavbook.app.ui.screens.CsvImportScreen
import com.bhaavbook.app.ui.screens.ManageBrandsScreen
import com.bhaavbook.app.ui.screens.ProductEditScreen
import com.bhaavbook.app.ui.screens.ProductListScreen
import com.bhaavbook.app.ui.screens.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.ProductList.route
    ) {
        composable(Screen.ProductList.route) {
            ProductListScreen(
                onAddProduct = { navController.navigate(Screen.AddProduct.route) },
                onEditProduct = { productId ->
                    navController.navigate(Screen.EditProduct.createRoute(productId))
                },
                onImportCsv = { navController.navigate(Screen.CsvImport.route) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.AddProduct.route) {
            ProductEditScreen(onNavigateUp = { navController.popBackStack() })
        }

        composable(
            route = Screen.EditProduct.route,
            arguments = listOf(navArgument(Screen.EditProduct.ARG_PRODUCT_ID) {
                type = NavType.LongType
            })
        ) {
            ProductEditScreen(onNavigateUp = { navController.popBackStack() })
        }

        composable(Screen.CsvImport.route) {
            CsvImportScreen(
                onNavigateUp = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateUp = { navController.popBackStack() },
                onManageBrands = { navController.navigate(Screen.ManageBrands.route) }
            )
        }

        composable(Screen.ManageBrands.route) {
            ManageBrandsScreen(
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}
