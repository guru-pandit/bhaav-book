package com.bhaavbook.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.R
import com.bhaavbook.app.data.model.ProductVariant
import com.bhaavbook.app.format.toPriceString
import com.bhaavbook.app.ui.viewmodel.ProductEditViewModel
import com.bhaavbook.app.ui.viewmodel.ProductEditUiState
import com.bhaavbook.app.ui.viewmodel.VariantFormState
import com.bhaavbook.app.ui.viewmodel.readableMessage

private val QUICK_FILL_LABELS = listOf("1 Dozen", "1/2 Dozen", "100 g", "250 g", "500 g", "1 kg", "500 ml", "1 L", "Single", "Packet", "Box")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    onNavigateUp: () -> Unit,
    viewModel: ProductEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate away on successful save
    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onNavigateUp()
    }

    // Show save errors in snackbar
    LaunchedEffect(state.saveError) {
        state.saveError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveError()
        }
    }

    // Duplicate warning dialog
    if (state.duplicateWarning != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearSaveError() },
            title = { Text("Duplicate product") },
            text = {
                Text(
                    "\"${state.duplicateWarning}\" already exists. " +
                        "Save anyway to create a second entry, or go back to edit."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::save) { Text("Save anyway") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearSaveError() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isEditMode) "Edit product" else "Add product")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.isSaving
                    ) {
                        Text(
                            if (state.isSaving) "Saving…" else "Save",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------------------------------------------------------
            // Product-level fields
            // ---------------------------------------------------------------
            SectionLabel("Product details")

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Product name *") },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(stringResource(it)) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            SearchableSelector(
                label = "Brand",
                selected = state.brand,
                options = state.availableBrands,
                onSelect = viewModel::onBrandChange,
                onClear = { viewModel.onBrandChange("") }
            )

            SearchableSelector(
                label = "Category",
                selected = state.category,
                options = state.availableCategories,
                onSelect = viewModel::onCategoryChange,
                onClear = { viewModel.onCategoryChange("") }
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(Modifier.height(4.dp))

            // ---------------------------------------------------------------
            // Variants section
            // ---------------------------------------------------------------
            SectionLabel("Variants")

            if (state.variants.isEmpty()) {
                Text(
                    "No variants yet. Add at least one size and price.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp
                ) {
                    Column {
                        state.variants.forEachIndexed { index, variant ->
                            VariantRow(
                                variant = variant,
                                showWholesale = state.showWholesalePrice,
                                onEdit = { viewModel.openEditVariantForm(variant) },
                                onDelete = { viewModel.deleteVariant(variant) }
                            )
                            if (index < state.variants.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            TextButton(
                onClick = { viewModel.openAddVariantForm() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add variant")
            }

            Spacer(Modifier.height(16.dp))
        }

        // -------------------------------------------------------------------
        // Variant add/edit bottom sheet
        // -------------------------------------------------------------------
        if (state.variantForm != null) {
            VariantFormSheet(
                form = state.variantForm!!,
                showWholesale = state.showWholesalePrice,
                onLabelChange = viewModel::onVariantLabelChange,
                onSellingPriceChange = viewModel::onVariantSellingPriceChange,
                onSellingMinChange = viewModel::onVariantSellingMinChange,
                onWholesalePriceChange = viewModel::onVariantWholesalePriceChange,
                onWholesaleMinChange = viewModel::onVariantWholesaleMinChange,
                onInStockChange = viewModel::onVariantInStockChange,
                onQuickFill = viewModel::onVariantLabelChange,
                onSave = viewModel::saveVariantForm,
                onDismiss = viewModel::dismissVariantForm
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Searchable brand/category selector
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    // Display field — tapping opens the sheet
    OutlinedTextField(
        value = selected,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            if (selected.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Clear $label")
                }
            } else {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { sheetOpen = true },
        enabled = false,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )

    if (sheetOpen) {
        SelectorSheet(
            title = "Select $label",
            options = options,
            selected = selected,
            onSelect = {
                onSelect(it)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(search, options) {
        if (search.isBlank()) options
        else options.filter { it.contains(search, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true
            )
            LazyColumn {
                items(filtered) { option ->
                    ListItem(
                        headlineContent = { Text(option) },
                        trailingContent = if (option == selected) {
                            { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
                        } else null,
                        modifier = Modifier.clickable { onSelect(option) }
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No matches",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Variant row
// ---------------------------------------------------------------------------

@Composable
private fun VariantRow(
    variant: ProductVariant,
    showWholesale: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                variant.variantLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                com.bhaavbook.app.format.formatPriceRange(variant.sellingMin, variant.sellingPrice, "₹"),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary
            )
            if (showWholesale && variant.wholesalePrice != null) {
                Text(
                    "Wholesale ${com.bhaavbook.app.format.formatPriceRange(variant.wholesaleMin, variant.wholesalePrice, "₹")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (!variant.inStock) {
                Text(
                    "Out of stock",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit variant", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Delete variant",
                tint = colors.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Variant add/edit bottom sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VariantFormSheet(
    form: VariantFormState,
    showWholesale: Boolean,
    onLabelChange: (String) -> Unit,
    onSellingPriceChange: (String) -> Unit,
    onSellingMinChange: (String) -> Unit,
    onWholesalePriceChange: (String) -> Unit,
    onWholesaleMinChange: (String) -> Unit,
    onInStockChange: (Boolean) -> Unit,
    onQuickFill: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (form.id == 0L) "Add variant" else "Edit variant",
                style = MaterialTheme.typography.titleMedium
            )

            // Quick-fill chips
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_FILL_LABELS.forEach { chip ->
                    FilterChip(
                        selected = form.label == chip,
                        onClick = { onQuickFill(chip) },
                        label = { Text(chip) }
                    )
                }
            }

            OutlinedTextField(
                value = form.label,
                onValueChange = onLabelChange,
                label = { Text("Variant label *") },
                placeholder = { Text("e.g. 100 g, Single, Box of 12") },
                isError = form.labelError != null || form.duplicateLabelError != null,
                supportingText = when {
                    form.labelError != null -> { { Text(stringResource(form.labelError)) } }
                    form.duplicateLabelError != null -> { { Text(form.duplicateLabelError) } }
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = form.sellingPrice,
                onValueChange = onSellingPriceChange,
                label = { Text("Selling max price (₹) *") },
                isError = form.sellingPriceError != null,
                supportingText = form.sellingPriceError?.let { { Text(stringResource(it)) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                )
            )

            OutlinedTextField(
                value = form.sellingMin,
                onValueChange = onSellingMinChange,
                label = { Text("Selling min price (₹)") },
                isError = form.sellingMinError != null,
                supportingText = form.sellingMinError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                )
            )

            AnimatedVisibility(visible = showWholesale) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = form.wholesalePrice,
                        onValueChange = onWholesalePriceChange,
                        label = { Text("Wholesale max price (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                    OutlinedTextField(
                        value = form.wholesaleMin,
                        onValueChange = onWholesaleMinChange,
                        label = { Text("Wholesale min price (₹)") },
                        isError = form.wholesaleMinError != null,
                        supportingText = form.wholesaleMinError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("In stock", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = form.inStock, onCheckedChange = onInStockChange)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onSave) { Text(if (form.id == 0L) "Add" else "Update") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
