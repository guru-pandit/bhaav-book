package com.bhaavbook.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.R
import com.bhaavbook.app.data.model.ProductUnit
import com.bhaavbook.app.ui.viewmodel.ProductEditViewModel

/**
 * The product id is not a parameter: the ViewModel reads it straight out of the
 * navigation arguments via `SavedStateHandle`, so there is only one source of
 * truth for which item is being edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    onNavigateUp: () -> Unit,
    viewModel: ProductEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    // Leave immediately on success. Awaiting the snackbar here used to hold the
    // screen open for the snackbar's full four seconds after a save.
    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onNavigateUp()
    }

    LaunchedEffect(state.saveError) {
        val error = state.saveError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearSaveError()
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (state.isEditMode) R.string.edit_product else R.string.add_product
                        ),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel(R.string.section_required)

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.field_name)) },
                isError = state.nameError != null,
                supportingText = state.nameError?.let {
                    { Text(stringResource(it), color = colors.error) }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = state.sellingPrice,
                onValueChange = viewModel::onSellingPriceChange,
                label = { Text(stringResource(R.string.field_selling_price)) },
                isError = state.sellingPriceError != null,
                supportingText = state.sellingPriceError?.let {
                    { Text(stringResource(it), color = colors.error) }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                )
            )

            HorizontalDivider(color = colors.outline)
            SectionLabel(R.string.section_details)

            SuggestionField(
                value = state.brand,
                onValueChange = viewModel::onBrandChange,
                labelRes = R.string.field_brand,
                suggestions = state.availableBrands
            )

            SuggestionField(
                value = state.category,
                onValueChange = viewModel::onCategoryChange,
                labelRes = R.string.field_category,
                suggestions = state.availableCategories
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UnitDropdown(
                    selected = state.unit,
                    onSelect = viewModel::onUnitChange,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.quantityValue,
                    onValueChange = viewModel::onQuantityValueChange,
                    label = { Text(stringResource(R.string.field_pack_size)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.field_in_stock),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface
                )
                Switch(checked = state.inStock, onCheckedChange = viewModel::onInStockChange)
            }

            HorizontalDivider(color = colors.outline)
            SectionLabel(R.string.section_optional)

            OutlinedTextField(
                value = state.costPrice,
                onValueChange = viewModel::onCostPriceChange,
                label = { Text(stringResource(R.string.field_cost_price)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.field_notes)) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                maxLines = 4
            )

            state.duplicateWarning?.let { clashingTitle ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.duplicate_warning, clashingTitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = !state.isSaving
            ) {
                Text(
                    text = stringResource(
                        if (state.isSaving) R.string.saving else R.string.save_product
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ============================================================================
// Sub-components
// ============================================================================

@Composable
private fun SectionLabel(@StringRes labelRes: Int) {
    Text(
        text = stringResource(labelRes).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/**
 * A free-text field that also offers what the shop already uses. Typing wins
 * over the list — a brand this store has never stocked has to be enterable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes labelRes: Int,
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        if (value.isBlank()) suggestions
        else suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, true) }
    }
    val showMenu = expanded && matches.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = showMenu,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(labelRes)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            trailingIcon = {
                if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu)
                }
            }
        )

        ExposedDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { expanded = false }
        ) {
            matches.take(8).forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    selected: ProductUnit,
    onSelect: (ProductUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_unit)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProductUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text("${unit.displayName} (${unit.shortLabel})") },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}
