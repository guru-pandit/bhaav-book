package com.bhaavbook.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.R
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
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::onFilePicked) }

    val errorSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let(viewModel::exportErrors) }

    val sampleSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let(viewModel::saveSampleCsv) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.text) }
    }

    val leave = {
        viewModel.reset()
        onNavigateUp()
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.import_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.secondary,
                    titleContentColor = colors.onSecondary,
                    navigationIconContentColor = colors.onSecondary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (val step = state.step) {
                is ImportStep.PickFile -> PickFileStep(
                    onPickFile = { filePicker.launch(CSV_MIME_TYPES) },
                    onDownloadSample = { sampleSaver.launch("chaitanya_stores_sample.csv") }
                )

                is ImportStep.Preview -> PreviewStep(
                    parsed = step.parsed,
                    onConfirm = { viewModel.onPreviewConfirmed(step.parsed) },
                    onCancel = viewModel::reset
                )

                is ImportStep.ColumnMap -> ColumnMapStep(
                    parsed = step.parsed,
                    mapping = step.mapping,
                    duplicateStrategy = state.duplicateStrategy,
                    onMappingChange = { viewModel.onMappingUpdated(step.parsed, it) },
                    onStrategyChange = viewModel::onDuplicateStrategyChange,
                    onImport = { viewModel.startImport(step.parsed, step.mapping) },
                    onCancel = viewModel::reset
                )

                is ImportStep.Importing -> ImportingStep(step.done, step.total)

                is ImportStep.Done -> DoneStep(
                    result = step.result,
                    onExportErrors = { errorSaver.launch("chaitanya_stores_rows_to_fix.csv") },
                    onDone = leave
                )

                is ImportStep.Failed -> FailedStep(
                    message = step.message,
                    onRetry = viewModel::reset
                )
            }
        }
    }
}

// The wildcard type is deliberate: plenty of file managers report a CSV that
// arrived over WhatsApp as application/octet-stream, and a strict filter greys
// the user's own file out so it cannot be picked at all.
private val CSV_MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")

// ============================================================================
// Steps
// ============================================================================

@Composable
private fun PickFileStep(onPickFile: () -> Unit, onDownloadSample: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.import_pick_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_pick_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onPickFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.import_pick_file))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onDownloadSample,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.import_sample))
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
            text = stringResource(
                R.string.import_preview_title,
                parsed.previewRows.size,
                parsed.totalRowCount
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.import_delimiter, describeDelimiter(parsed.delimiter)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TableRow(cells = parsed.headers, isHeader = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                parsed.previewRows.forEach { row ->
                    TableRow(cells = row, isHeader = false)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        StepButtons(
            cancelLabel = stringResource(R.string.cancel),
            confirmLabel = stringResource(R.string.import_map_columns),
            onCancel = onCancel,
            onConfirm = onConfirm
        )
    }
}

private fun describeDelimiter(delimiter: Char): String = when (delimiter) {
    ',' -> "comma"
    ';' -> "semicolon"
    '\t' -> "tab"
    else -> delimiter.toString()
}

@Composable
private fun TableRow(cells: List<String>, isHeader: Boolean) {
    Row {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .background(
                        if (isHeader) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = cell,
                    style = if (isHeader) MaterialTheme.typography.labelMedium
                    else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

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
    // One expanded index for the whole list rather than per-row state: LazyColumn
    // recycles rows, and remembered state inside an item can reappear attached to
    // a different column.
    var expandedColumn by remember { mutableIntStateOf(-1) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    text = stringResource(R.string.import_map_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.import_map_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }

            itemsIndexed(parsed.headers) { columnIndex, header ->
                ColumnMappingRow(
                    header = header,
                    field = mapping.fieldForIndex(columnIndex),
                    expanded = expandedColumn == columnIndex,
                    onExpandedChange = { expandedColumn = if (it) columnIndex else -1 },
                    onFieldPicked = { field ->
                        onMappingChange(
                            mapping.copy(mappings = mapping.mappings + (columnIndex to field))
                        )
                        expandedColumn = -1
                    }
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.import_duplicates_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                DUPLICATE_OPTIONS.forEach { (strategy, labelRes) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = duplicateStrategy == strategy,
                            onClick = { onStrategyChange(strategy) }
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        StepButtons(
            cancelLabel = stringResource(R.string.cancel),
            confirmLabel = stringResource(R.string.import_start, parsed.totalRowCount),
            onCancel = onCancel,
            onConfirm = onImport
        )
    }
}

private val DUPLICATE_OPTIONS = listOf(
    DuplicateStrategy.SKIP to R.string.import_dup_skip,
    DuplicateStrategy.UPDATE to R.string.import_dup_update,
    DuplicateStrategy.ADD_ANYWAY to R.string.import_dup_add
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMappingRow(
    header: String,
    field: AppField,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onFieldPicked: (AppField) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = header,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.weight(1.4f)
        ) {
            OutlinedTextField(
                value = field.displayName,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                AppField.entries.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(candidate.displayName) },
                        onClick = { onFieldPicked(candidate) }
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ImportingStep(done: Int, total: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.import_running),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) done.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_progress, done, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DoneStep(
    result: ImportResult,
    onExportErrors: () -> Unit,
    onDone: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.import_done),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onPrimaryContainer
        )
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SummaryRow(R.string.import_total_rows, result.totalRows)
                SummaryRow(R.string.import_added, result.importedCount)
                SummaryRow(R.string.import_updated, result.updatedCount)
                SummaryRow(R.string.import_skipped, result.skippedCount)
                SummaryRow(R.string.import_errors, result.errors.size)
            }
        }

        if (result.errors.isEmpty()) {
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.import_error_details),
                style = MaterialTheme.typography.titleMedium,
                color = colors.error
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(result.errors) { error ->
                    Text(
                        text = stringResource(
                            R.string.import_error_row,
                            error.rowNumber,
                            error.reason
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExportErrors,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.import_export_errors))
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.done))
        }
    }
}

@Composable
private fun FailedStep(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.import_read_failed),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, shape = MaterialTheme.shapes.medium) {
            Text(stringResource(R.string.import_try_again))
        }
    }
}

@Composable
private fun SummaryRow(@StringRes labelRes: Int, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StepButtons(
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(cancelLabel)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .weight(1.4f)
                .height(48.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(confirmLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
