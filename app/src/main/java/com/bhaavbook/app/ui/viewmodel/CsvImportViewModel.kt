package com.bhaavbook.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhaavbook.app.csv.ColumnMapping
import com.bhaavbook.app.csv.CsvExporter
import com.bhaavbook.app.csv.CsvImporter
import com.bhaavbook.app.csv.CsvParser
import com.bhaavbook.app.csv.DuplicateStrategy
import com.bhaavbook.app.csv.ImportResult
import com.bhaavbook.app.csv.ParsedCsvResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class ImportStep {
    /** Pick a file. */
    data object PickFile : ImportStep()

    /** Show the first rows so the user can confirm the file parsed sensibly. */
    data class Preview(val parsed: ParsedCsvResult) : ImportStep()

    /** Match each CSV column to a product field. */
    data class ColumnMap(val parsed: ParsedCsvResult, val mapping: ColumnMapping) : ImportStep()

    data class Importing(val done: Int, val total: Int) : ImportStep()

    /**
     * Finished. [sourceHeaders] are the headers of the imported file, kept so
     * the failed-rows export lines its columns up with the original.
     */
    data class Done(val result: ImportResult, val sourceHeaders: List<String>) : ImportStep()

    data class Failed(val message: String) : ImportStep()
}

data class CsvImportUiState(
    val step: ImportStep = ImportStep.PickFile,
    val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.SKIP
)

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val parser: CsvParser,
    private val importer: CsvImporter,
    private val exporter: CsvExporter
) : ViewModel() {

    private val _state = MutableStateFlow(CsvImportUiState())
    val state: StateFlow<CsvImportUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(
        capacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            val parsed = runCatching { withContext(Dispatchers.IO) { parser.parse(uri) } }
                .getOrElse { error ->
                    _state.value = _state.value.copy(
                        step = ImportStep.Failed(error.readableMessage())
                    )
                    return@launch
                }

            _state.value = _state.value.copy(
                step = if (parsed.totalRowCount == 0) {
                    ImportStep.Failed("That file has a header row but no items in it.")
                } else {
                    ImportStep.Preview(parsed)
                }
            )
        }
    }

    fun onPreviewConfirmed(parsed: ParsedCsvResult) {
        _state.value = _state.value.copy(
            step = ImportStep.ColumnMap(parsed, ColumnMapping.autoGuess(parsed.headers))
        )
    }

    fun onMappingUpdated(parsed: ParsedCsvResult, newMapping: ColumnMapping) {
        _state.value = _state.value.copy(step = ImportStep.ColumnMap(parsed, newMapping))
    }

    fun onDuplicateStrategyChange(strategy: DuplicateStrategy) {
        _state.value = _state.value.copy(duplicateStrategy = strategy)
    }

    fun startImport(parsed: ParsedCsvResult, mapping: ColumnMapping) {
        viewModelScope.launch {
            _state.value = _state.value.copy(step = ImportStep.Importing(0, parsed.totalRowCount))

            runCatching {
                importer.import(
                    rows = parsed.rows,
                    mapping = mapping,
                    strategy = _state.value.duplicateStrategy,
                    onProgress = { done, total ->
                        _state.value = _state.value.copy(step = ImportStep.Importing(done, total))
                    }
                )
            }.onSuccess { result ->
                _state.value = _state.value.copy(
                    step = ImportStep.Done(result, parsed.headers)
                )
            }.onFailure { error ->
                // Nothing was written: the commit is a single transaction.
                _state.value = _state.value.copy(
                    step = ImportStep.Failed(
                        "Nothing was imported — ${error.readableMessage()}"
                    )
                )
            }
        }
    }

    /** Writes the rows that failed to the SAF document at [uri]. */
    fun exportErrors(uri: Uri) {
        val done = _state.value.step as? ImportStep.Done ?: return
        viewModelScope.launch {
            runCatching { exporter.exportErrorRows(uri, done.sourceHeaders, done.result.errors) }
                .onSuccess {
                    _messages.trySend(UiMessage("Saved ${done.result.errors.size} rows to fix"))
                }
                .onFailure { _messages.trySend(UiMessage("Could not save: ${it.readableMessage()}")) }
        }
    }

    fun saveSampleCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching { exporter.writeSampleCsv(uri) }
                .onSuccess { _messages.trySend(UiMessage("Sample file saved")) }
                .onFailure { _messages.trySend(UiMessage("Could not save: ${it.readableMessage()}")) }
        }
    }

    fun reset() {
        _state.value = CsvImportUiState()
    }
}
