package com.bhaavbook.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.csv.AppField
import com.bhaavbook.app.csv.ColumnMapping
import com.bhaavbook.app.csv.DuplicateStrategy
import com.bhaavbook.app.csv.ImportResult
import com.bhaavbook.app.csv.ParsedCsvResult
import com.bhaavbook.app.ui.viewmodel.CsvImportViewModel
import com.bhaavbook.app.ui.viewmodel.ImportStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: CsvImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // SAF launcher to pick a CSV file
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFilePicked(it) }
    }

    // SAF launcher to save error rows CSV
    val errorSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { savedUri ->
            val done = state.step as? ImportStep.Done ?: return@let
            val parsed = done.result
            // We need headers — use the done result's error rows
            viewModel.exportErrors(savedUri, CsvImportViewModel.SAMPLE_HEADERS, done.result.errors)
        }
    }

    // SAF launcher for sample CSV
    val sampleSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { viewModel.saveSampleCsv(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import CSV", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onNavigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (val step = state.step) {
                is ImportStep.PickFile -> PickFileStep(
                    onPickFile = { filePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                    onDownloadSample = { sampleSaver.launch("bhaavbook_sample.csv") }
                )

                is ImportStep.Preview -> PreviewStep(
                    parsed = step.parsed,
                    onConfirm = { viewModel.onPreviewConfirmed(step.parsed) },
                    onCancel = { viewModel.reset() }
                )

                is ImportStep.ColumnMap -> ColumnMapStep(
                    parsed = step.parsed,
                    mapping = step.mapping,
                    duplicateStrategy = state.duplicateStrategy,
                    onMappingChange = { newMapping ->
                        viewModel.onMappingUpdated(step.parsed, newMapping)
                    },
                    onStrategyChange = viewModel::onDuplicateStrategyChange,
                    onImport = { viewModel.startImport(step.parsed, step.mapping) },
                    onCancel = { viewModel.reset() }
                )

                is ImportStep.Importing -> ImportingStep(step.done, step.total)

                is ImportStep.Done -> DoneStep(
                    result = step.result,
                    onExportErrors = {
                        errorSaver.launch("bhaavbook_errors.csv")
                    },
                    onDone = {
                        viewModel.reset()
                        onNavigateUp()
                    }
                )

                is ImportStep.ParseError -> ParseErrorStep(
                    message = step.message,
                    onRetry = { viewModel.reset() }
                )
            }
        }
    }
}

// ============================================================================
// Step composables
// ============================================================================

@Composable
private fun PickFileStep(onPickFile: () -> Unit, onDownloadSample: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Import products from a CSV file",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Supported: comma or semicolon delimiter, UTF-8 encoding, Excel exported CSV",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Text("Pick CSV file")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onDownloadSample, modifier = Modifier.fillMaxWidth()) {
            Text("Download sample CSV")
        }
    }
}

@Composable
private fun PreviewStep(
    parsed: ParsedCsvResult,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Preview — first ${parsed.previewRows.size} of ${parsed.totalRowCount} rows",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Delimiter detected: '${parsed.delimiter}'",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // Scrollable table preview
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                // Header row
                TableRow(cells = parsed.headers, isHeader = true)
                HorizontalDivider()
                // Data rows
                parsed.previewRows.forEachIndexed { i, row ->
                    TableRow(cells = row, isHeader = false)
                    if (i < parsed.previewRows.lastIndex) HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Map columns →") }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, isHeader: Boolean) {
    Row {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .background(
                        if (isHeader) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(8.dp)
            ) {
                Text(
                    cell,
                    style = if (isHeader) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.bodySmall,
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMapStep(
    parsed: ParsedCsvResult,
    mapping: ColumnMapping,
    duplicateStrategy: DuplicateStrategy,
    onMappingChange: (ColumnMapping) -> Unit,
    onStrategyChange: (DuplicateStrategy) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Map columns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tell the app what each CSV column contains.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }

        items(parsed.headers.indices.toList()) { colIdx ->
            val header = parsed.headers[colIdx]
            val currentField = mapping.fieldForIndex(colIdx)
            var expanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "\"$header\"",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Text("→", style = MaterialTheme.typography.bodyMedium)

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.weight(1.5f)
                ) {
                    OutlinedTextField(
                        value = currentField.displayName,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        AppField.entries.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field.displayName) },
                                onClick = {
                                    val newMappings = mapping.mappings.toMutableMap()
                                    newMappings[colIdx] = field
                                    onMappingChange(mapping.copy(mappings = newMappings))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "Duplicate handling",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                DuplicateStrategy.SKIP to "Skip duplicates (keep existing)",
                DuplicateStrategy.UPDATE to "Update existing (match on brand + name)",
                DuplicateStrategy.ADD_ANYWAY to "Add as new (allow duplicates)"
            ).forEach { (strategy, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = duplicateStrategy == strategy,
                        onClick = { onStrategyChange(strategy) }
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text("Import ${parsed.totalRowCount} rows")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportingStep(done: Int, total: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Importing…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$done / $total rows",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DoneStep(
    result: ImportResult,
    onExportErrors: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Import complete ✓",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SummaryRow("Total rows processed", result.totalRows.toString())
                SummaryRow("Products imported", result.importedCount.toString())
                SummaryRow("Products updated", result.updatedCount.toString())
                SummaryRow("Rows skipped (duplicates)", result.skippedCount.toString())
                SummaryRow("Rows with errors", result.errors.size.toString())
            }
        }

        // Error list
        if (result.errors.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Error details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(result.errors) { err ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Row ${err.rowNumber}: ${err.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExportErrors,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export failed rows as CSV")
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

@Composable
private fun ParseErrorStep(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Could not read file",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
