package com.bhaavbook.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeOption { SYSTEM, LIGHT, DARK }
enum class PriceFontSize { NORMAL, LARGE, EXTRA_LARGE }

data class AppSettings(
    val currencySymbol: String = "₹",
    val defaultUnit: String = "PIECE",
    val theme: ThemeOption = ThemeOption.SYSTEM,
    val priceFontSize: PriceFontSize = PriceFontSize.LARGE,
    val autoFocusSearch: Boolean = true,
    /** When true, cost price is shown in the big price bottom sheet. */
    val showCostPrice: Boolean = false,
    /** When true, wholesale price is shown in the big price bottom sheet. */
    val showWholesalePrice: Boolean = false
)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bhaavbook_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val DEFAULT_UNIT = stringPreferencesKey("default_unit")
        val THEME = stringPreferencesKey("theme")
        val PRICE_FONT_SIZE = stringPreferencesKey("price_font_size")
        val AUTO_FOCUS_SEARCH = booleanPreferencesKey("auto_focus_search")
        val SHOW_COST_PRICE = booleanPreferencesKey("show_cost_price")
        val SHOW_WHOLESALE_PRICE = booleanPreferencesKey("show_wholesale_price")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            currencySymbol = prefs[Keys.CURRENCY_SYMBOL] ?: "₹",
            defaultUnit = prefs[Keys.DEFAULT_UNIT] ?: "PIECE",
            theme = ThemeOption.valueOf(prefs[Keys.THEME] ?: ThemeOption.SYSTEM.name),
            priceFontSize = PriceFontSize.valueOf(
                prefs[Keys.PRICE_FONT_SIZE] ?: PriceFontSize.LARGE.name
            ),
            autoFocusSearch = prefs[Keys.AUTO_FOCUS_SEARCH] ?: true,
            showCostPrice = prefs[Keys.SHOW_COST_PRICE] ?: false,
            showWholesalePrice = prefs[Keys.SHOW_WHOLESALE_PRICE] ?: false
        )
    }

    suspend fun updateCurrencySymbol(symbol: String) {
        context.dataStore.edit { it[Keys.CURRENCY_SYMBOL] = symbol }
    }

    suspend fun updateDefaultUnit(unit: String) {
        context.dataStore.edit { it[Keys.DEFAULT_UNIT] = unit }
    }

    suspend fun updateTheme(theme: ThemeOption) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun updatePriceFontSize(size: PriceFontSize) {
        context.dataStore.edit { it[Keys.PRICE_FONT_SIZE] = size.name }
    }

    suspend fun updateAutoFocusSearch(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FOCUS_SEARCH] = enabled }
    }

    suspend fun updateShowCostPrice(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_COST_PRICE] = show }
    }

    suspend fun updateShowWholesalePrice(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_WHOLESALE_PRICE] = show }
    }
}
