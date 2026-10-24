package com.bhaavbook.app.ui.screens

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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.R
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

    if (state.itemToDelete != null) {
        val itemName = when (val item = state.itemToDelete) {
            is Brand -> item.name
            is Category -> item.name
            else -> ""
        }
        val titleRes = if (state.activeTab == ManageTab.BRANDS)
            R.string.delete_brand_title else R.string.delete_category_title

        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(R.string.delete_item_body, itemName)) },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.error,
                        contentColor = colors.onError
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_brands_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openAddForm,
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_product))
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
                    text = { Text(stringResource(R.string.tab_brands, state.brands.size)) }
                )
                Tab(
                    selected = state.activeTab == ManageTab.CATEGORIES,
                    onClick = { viewModel.selectTab(ManageTab.CATEGORIES) },
                    text = { Text(stringResource(R.string.tab_categories, state.categories.size)) }
                )
            }

            when (state.activeTab) {
                ManageTab.BRANDS -> {
                    if (state.brands.isEmpty()) {
                        EmptyListMessage(stringResource(R.string.no_brands_yet))
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
                        EmptyListMessage(stringResource(R.string.no_categories_yet))
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

        if (state.formState != null) {
            val isAdd = state.formState!!.id == 0L
            val titleRes = when {
                isAdd && state.activeTab == ManageTab.BRANDS -> R.string.add_brand
                isAdd -> R.string.add_category
                state.activeTab == ManageTab.BRANDS -> R.string.edit_brand
                else -> R.string.edit_category
            }
            ItemFormSheet(
                form = state.formState!!,
                title = stringResource(titleRes),
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
            Text(
                stringResource(R.string.slug_label, slug),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.delete),
                        tint = colors.error,
                        modifier = Modifier.size(18.dp)
                    )
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
    if (form.showSlugWarning) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.change_slug_title)) },
            text = {
                Text(stringResource(R.string.change_slug_body, form.originalSlug, form.slug))
            },
            confirmButton = {
                Button(onClick = onSave) { Text(stringResource(R.string.change_slug_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
                label = { Text(stringResource(R.string.field_display_name)) },
                isError = form.nameError != null,
                supportingText = form.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = form.slug,
                onValueChange = onSlugChange,
                label = { Text(stringResource(R.string.field_slug)) },
                readOnly = form.id != 0L && !form.isEditingSlug,
                isError = form.slugError != null,
                supportingText = form.slugError?.let { { Text(it) } },
                trailingIcon = {
                    if (form.id != 0L && !form.isEditingSlug) {
                        TextButton(onClick = onToggleEditSlug) {
                            Text(stringResource(R.string.edit), style = MaterialTheme.typography.labelSmall)
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
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onSave) {
                    Text(
                        if (form.id == 0L) stringResource(R.string.action_add)
                        else stringResource(R.string.action_save)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
