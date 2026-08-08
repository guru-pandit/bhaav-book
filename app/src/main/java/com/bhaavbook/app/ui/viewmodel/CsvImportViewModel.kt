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
import com.bhaavbook.app.csv.ImportRowError
import com.bhaavbook.app.csv.ParsedCsvResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImportStep {
    /** User picks a CSV file. */
    data object PickFile : ImportStep()
    /** Show first 10 rows for preview. */
    data class Preview(val parsed: ParsedCsvResult) : ImportStep()
    /** User maps columns to app fields. */
    data class ColumnMap(val parsed: ParsedCsvResult, val mapping: ColumnMapping) : ImportStep()
    /** Import in progress. */
    data class Importing(val done: Int, val total: Int) : ImportStep()
    /** Import complete. */
    data class Done(val result: ImportResult) : ImportStep()
    /** Error reading/parsing the file. */
    data class ParseError(val message: String) : ImportStep()
}

data class CsvImportUiState(
    val step: ImportStep = ImportStep.PickFile,
    val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.SKIP,
    val isExportingErrors: Boolean = false,
    val errorExportedUri: Uri? = null
)

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val parser: CsvParser,
    private val importer: CsvImporter,
    private val exporter: CsvExporter
) : ViewModel() {

    private val _state = MutableStateFlow(CsvImportUiState())
    val state: StateFlow<CsvImportUiState> = _state.asStateFlow()

    // -----------------------------------------------------------------------
    // Step navigation
    // -----------------------------------------------------------------------

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = parser.parse(uri)
                if (parsed.totalRowCount == 0) {
                    _state.value = _state.value.copy(
                        step = ImportStep.ParseError("The CSV file has no data rows.")
                    )
                    return@launch
                }
                _state.value = _state.value.copy(step = ImportStep.Preview(parsed))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    step = ImportStep.ParseError(e.message ?: "Failed to read file")
                )
            }
        }
    }

    fun onPreviewConfirmed(parsed: ParsedCsvResult) {
        val mapping = ColumnMapping.autoGuess(parsed.headers)
        _state.value = _state.value.copy(step = ImportStep.ColumnMap(parsed, mapping))
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
            val result = importer.import(
                rows = parsed.rows,
                mapping = mapping,
                strategy = _state.value.duplicateStrategy,
                onProgress = { done, total ->
                    _state.value = _state.value.copy(step = ImportStep.Importing(done, total))
                }
            )
            _state.value = _state.value.copy(step = ImportStep.Done(result))
        }
    }

    fun exportErrors(uri: Uri, headers: List<String>, errors: List<ImportRowError>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isExportingErrors = true)
            try {
                exporter.exportErrorRows(uri, headers, errors)
                _state.value = _state.value.copy(
                    isExportingErrors = false,
                    errorExportedUri = uri
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isExportingErrors = false)
            }
        }
    }

    fun saveSampleCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                exporter.writeSampleCsv(uri)
            } catch (_: Exception) {}
        }
    }

    fun reset() {
        _state.value = CsvImportUiState()
    }
}
