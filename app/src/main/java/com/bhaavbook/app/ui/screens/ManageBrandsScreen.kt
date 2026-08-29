package com.bhaavbook.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.data.model.Brand
import com.bhaavbook.app.data.model.Category
import com.bhaavbook.app.ui.viewmodel.ItemFormState
import com.bhaavbook.app.ui.viewmodel.ManageBrandsViewModel
import com.bhaavbook.app.ui.viewmodel.ManageTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageBrandsScreen(
    onNavigateUp: () -> Unit,
    viewModel: ManageBrandsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Delete confirmation dialog
    if (state.itemToDelete != null) {
        val itemName = when (val item = state.itemToDelete) {
            is Brand -> item.name
            is Category -> item.name
            else -> ""
        }
        val itemType = if (state.activeTab == ManageTab.BRANDS) "brand" else "category"

        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = { Text("Delete $itemType?") },
            text = { Text("Are you sure you want to delete \"$itemName\"? Existing products won't be deleted.") },
            confirmButton = {
                Button(onClick = viewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Brands & Categories") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openAddForm,
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = state.activeTab.ordinal) {
                Tab(
                    selected = state.activeTab == ManageTab.BRANDS,
                    onClick = { viewModel.selectTab(ManageTab.BRANDS) },
                    text = { Text("Brands (${state.brands.size})") }
                )
                Tab(
                    selected = state.activeTab == ManageTab.CATEGORIES,
                    onClick = { viewModel.selectTab(ManageTab.CATEGORIES) },
                    text = { Text("Categories (${state.categories.size})") }
                )
            }

            when (state.activeTab) {
                ManageTab.BRANDS -> {
                    if (state.brands.isEmpty()) {
                        EmptyListMessage("No brands added yet.")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.brands, key = { it.id }) { brand ->
                                ItemRow(
                                    name = brand.name,
                                    slug = brand.slug,
                                    onEdit = { viewModel.openEditBrandForm(brand) },
                                    onDelete = { viewModel.requestDelete(brand) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
                ManageTab.CATEGORIES -> {
                    if (state.categories.isEmpty()) {
                        EmptyListMessage("No categories added yet.")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.categories, key = { it.id }) { category ->
                                ItemRow(
                                    name = category.name,
                                    slug = category.slug,
                                    onEdit = { viewModel.openEditCategoryForm(category) },
                                    onDelete = { viewModel.requestDelete(category) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        // Add/Edit sheet
        if (state.formState != null) {
            ItemFormSheet(
                form = state.formState!!,
                title = if (state.formState!!.id == 0L) {
                    if (state.activeTab == ManageTab.BRANDS) "Add Brand" else "Add Category"
                } else {
                    if (state.activeTab == ManageTab.BRANDS) "Edit Brand" else "Edit Category"
                },
                onNameChange = viewModel::onNameChange,
                onSlugChange = viewModel::onSlugChange,
                onToggleEditSlug = viewModel::toggleEditSlug,
                onSave = viewModel::saveForm,
                onDismiss = viewModel::dismissForm
            )
        }
    }
}

@Composable
private fun ItemRow(
    name: String,
    slug: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text("slug: $slug", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = colors.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    )
}

@Composable
private fun EmptyListMessage(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemFormSheet(
    form: ItemFormState,
    title: String,
    onNameChange: (String) -> Unit,
    onSlugChange: (String) -> Unit,
    onToggleEditSlug: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    // Slug warning dialog
    if (form.showSlugWarning) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Change slug?") },
            text = {
                Text(
                    "Changing the slug from \"${form.originalSlug}\" to \"${form.slug}\" " +
                        "means CSV files using the old slug will no longer match automatically."
                )
            },
            confirmButton = {
                Button(onClick = onSave) { Text("Change slug anyway") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }

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
            Text(title, style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text("Display name *") },
                isError = form.nameError != null,
                supportingText = form.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = form.slug,
                onValueChange = onSlugChange,
                label = { Text("Canonical Slug (CSV Key)") },
                readOnly = form.id != 0L && !form.isEditingSlug,
                isError = form.slugError != null,
                supportingText = form.slugError?.let { { Text(it) } },
                trailingIcon = {
                    if (form.id != 0L && !form.isEditingSlug) {
                        TextButton(onClick = onToggleEditSlug) {
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onSave) { Text(if (form.id == 0L) "Add" else "Save") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
