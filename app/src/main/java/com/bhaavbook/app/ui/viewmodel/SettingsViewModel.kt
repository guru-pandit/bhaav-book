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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _messages = Channel<UiMessage>(
        capacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /**
     * Blank input keeps the stored symbol rather than saving an empty string —
     * a price list with no currency mark in front of the numbers is worse than
     * whatever the user was mid-way through typing.
     */
    fun updateCurrencySymbol(symbol: String) {
        val cleaned = symbol.trim().take(3)
        if (cleaned.isEmpty()) return
        viewModelScope.launch { repo.updateCurrencySymbol(cleaned) }
    }

    fun updateDefaultUnit(unit: String) = launchUpdate { repo.updateDefaultUnit(unit) }

    fun updateTheme(theme: ThemeOption) = launchUpdate { repo.updateTheme(theme) }

    fun updatePriceFontSize(size: PriceFontSize) = launchUpdate { repo.updatePriceFontSize(size) }

    fun updateAutoFocusSearch(enabled: Boolean) = launchUpdate { repo.updateAutoFocusSearch(enabled) }

    fun updateShowCostPrice(show: Boolean) = launchUpdate { repo.updateShowCostPrice(show) }

    private fun launchUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun shareCsv(context: Context) {
        viewModelScope.launch {
            val snapshot = productRepository.getAllSnapshot()
            if (snapshot.isEmpty()) {
                _messages.trySend(UiMessage("Nothing to share yet"))
                return@launch
            }
            runCatching { context.startActivity(csvExporter.createShareCsvIntent(snapshot)) }
                .onFailure { _messages.trySend(UiMessage("Could not share: ${it.readableMessage()}")) }
        }
    }

    fun exportToCsvUri(uri: Uri) {
        viewModelScope.launch {
            val snapshot = productRepository.getAllSnapshot()
            if (snapshot.isEmpty()) {
                _messages.trySend(UiMessage("Nothing to export yet"))
                return@launch
            }
            runCatching { csvExporter.exportToCsv(uri, snapshot) }
                .onSuccess { _messages.trySend(UiMessage("Exported ${snapshot.size} items")) }
                .onFailure { _messages.trySend(UiMessage("Export failed: ${it.readableMessage()}")) }
        }
    }
}
