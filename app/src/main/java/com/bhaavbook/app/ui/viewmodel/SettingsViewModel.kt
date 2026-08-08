package com.bhaavbook.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhaavbook.app.csv.CsvExporter
import com.bhaavbook.app.data.repository.ProductRepository
import com.bhaavbook.app.data.settings.AppSettings
import com.bhaavbook.app.data.settings.PriceFontSize
import com.bhaavbook.app.data.settings.SettingsRepository
import com.bhaavbook.app.data.settings.ThemeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val productRepository: ProductRepository,
    private val csvExporter: CsvExporter
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    fun updateCurrencySymbol(symbol: String) = viewModelScope.launch {
        repo.updateCurrencySymbol(symbol)
    }

    fun updateDefaultUnit(unit: String) = viewModelScope.launch {
        repo.updateDefaultUnit(unit)
    }

    fun updateTheme(theme: ThemeOption) = viewModelScope.launch {
        repo.updateTheme(theme)
    }

    fun updatePriceFontSize(size: PriceFontSize) = viewModelScope.launch {
        repo.updatePriceFontSize(size)
    }

    fun updateAutoFocusSearch(enabled: Boolean) = viewModelScope.launch {
        repo.updateAutoFocusSearch(enabled)
    }

    fun updateShowCostPrice(show: Boolean) = viewModelScope.launch {
        repo.updateShowCostPrice(show)
    }

    fun shareCsv(context: Context) = viewModelScope.launch {
        try {
            val snapshot = productRepository.getAllSnapshot()
            val shareIntent = csvExporter.createShareCsvIntent(snapshot)
            context.startActivity(shareIntent)
        } catch (_: Exception) {}
    }

    fun exportToCsvUri(uri: Uri) = viewModelScope.launch {
        try {
            val snapshot = productRepository.getAllSnapshot()
            csvExporter.exportToCsv(uri, snapshot)
        } catch (_: Exception) {}
    }
}
