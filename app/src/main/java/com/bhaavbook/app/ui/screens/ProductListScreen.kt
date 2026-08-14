package com.bhaavbook.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.R
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductWithVariants
import com.bhaavbook.app.data.repository.ProductFilter
import com.bhaavbook.app.data.repository.SortOrder
import com.bhaavbook.app.data.settings.PriceFontSize
import com.bhaavbook.app.format.toPriceString
import com.bhaavbook.app.ui.theme.TabularFigures
import com.bhaavbook.app.ui.viewmodel.ProductListUiState
import com.bhaavbook.app.ui.viewmodel.ProductListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onAddProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
    onImportCsv: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ProductListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let(viewModel::exportToCsvUri) }

    // The tapped product is held by value, not by id: a debounced search result
    // arriving while the sheet is open must not yank it out from under the user.
    var openProduct by remember { mutableStateOf<ProductWithVariants?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.settings.autoFocusSearch) {
        if (uiState.settings.autoFocusSearch) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Scrolling the results means the shopkeeper is reading, not typing.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) focusManager.clearFocus() }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.undoLabel,
                withDismissAction = message.undoLabel == null,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed && message.undoLabel != null) {
                viewModel.undoDelete()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StoreTopBar(
                totalCount = uiState.totalCount,
                onSort = { showSortSheet = true },
                menuExpanded = showMenu,
                onMenuToggle = { showMenu = it },
                onShare = { viewModel.shareCsv(context) },
                onExport = { exportCsvLauncher.launch(EXPORT_FILE_NAME) },
                onImport = onImportCsv,
                onSettings = onSettings
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProduct,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_product),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::clearSearch,
                onSubmit = { focusManager.clearFocus() },
                modifier = Modifier.focusRequester(focusRequester)
            )

            FilterBar(
                categories = uiState.categories,
                brands = uiState.brands,
                activeFilter = uiState.activeFilter,
                onFilterChange = viewModel::onFilterChange
            )

            ResultSummary(state = uiState, onClearFilter = viewModel::clearFilters)

            when {
                uiState.isLoading -> LoadingRows()

                uiState.isEmptyResult -> EmptyState(
                    state = uiState,
                    onAddProduct = onAddProduct,
                    onImportCsv = onImportCsv,
                    onClearFilter = viewModel::clearFilters
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        // Room for the FAB to hover over without covering the last row.
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.products, key = { it.product.id }) { item ->
                        ProductRow(
                            pwv = item,
                            currencySymbol = uiState.settings.currencySymbol,
                            priceFontSize = uiState.settings.priceFontSize,
                            onClick = {
                                focusManager.clearFocus()
                                openProduct = item
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortSheet(
            currentSort = uiState.sortOrder,
            onSortChange = {
                viewModel.onSortOrderChange(it)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }

    openProduct?.let { pwv ->
        PriceSheet(
            pwv = pwv,
            currencySymbol = uiState.settings.currencySymbol,
            priceFontSize = uiState.settings.priceFontSize,
            showCostPrice = uiState.settings.showCostPrice,
            showWholesalePrice = uiState.settings.showWholesalePrice,
            onEdit = {
                openProduct = null
                onEditProduct(pwv.product.id)
            },
            onDelete = {
                openProduct = null
                viewModel.requestDelete(pwv)
            },
            onDismiss = { openProduct = null }
        )
    }
}

private const val EXPORT_FILE_NAME = "chaitanya_stores_prices.csv"

// ============================================================================
// Top bar
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreTopBar(
    totalCount: Int,
    onSort: () -> Unit,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit
) {
    val onBar = MaterialTheme.colorScheme.onSecondary

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo_master),
                    contentDescription = stringResource(R.string.logo_content_description),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = onBar,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (totalCount) {
                            0 -> stringResource(R.string.price_list_empty)
                            1 -> stringResource(R.string.one_item_in_price_list)
                            else -> stringResource(R.string.items_in_price_list, totalCount)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        // Quiet against the bar without needing a second brand colour.
                        color = onBar.copy(alpha = 0.72f),
                        maxLines = 1
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSort) {
                Icon(
                    Icons.Outlined.SwapVert,
                    contentDescription = stringResource(R.string.sort_and_filter),
                    tint = onBar
                )
            }
            IconButton(onClick = { onMenuToggle(true) }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = onBar
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuToggle(false) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                MenuAction(R.string.share_price_list, Icons.Outlined.Share) {
                    onMenuToggle(false); onShare()
                }
                MenuAction(R.string.export_to_storage, Icons.Outlined.FileDownload) {
                    onMenuToggle(false); onExport()
                }
                MenuAction(R.string.import_csv, Icons.Outlined.FileUpload) {
                    onMenuToggle(false); onImport()
                }
                MenuAction(R.string.settings, Icons.Filled.Settings) {
                    onMenuToggle(false); onSettings()
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            titleContentColor = onBar,
            actionIconContentColor = onBar
        )
    )
}

@Composable
private fun MenuAction(
    @StringRes labelRes: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge) },
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    )
}

// ============================================================================
// Search
// ============================================================================

/**
 * Built on [BasicTextField] rather than a Material `TextField` so the field can
 * be a compact 52dp: the stock component reserves room for a floating label
 * that this screen never shows, which pushes the first result off the fold on
 * a small phone.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .border(1.dp, colors.outline, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() })
                )
            }

            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear_search),
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// Filters
// ============================================================================

@Composable
private fun FilterBar(
    categories: List<String>,
    brands: List<String>,
    activeFilter: ProductFilter,
    onFilterChange: (ProductFilter) -> Unit
) {
    if (categories.isEmpty() && brands.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ChipToggle(
                label = stringResource(R.string.filter_in_stock),
                selected = activeFilter is ProductFilter.InStockOnly,
                onToggle = { on ->
                    onFilterChange(if (on) ProductFilter.InStockOnly else ProductFilter.None)
                }
            )
        }
        items(brands, key = { "brand:$it" }) { brand ->
            ChipToggle(
                label = brand,
                selected = activeFilter is ProductFilter.ByBrand && activeFilter.brand == brand,
                onToggle = { on ->
                    onFilterChange(if (on) ProductFilter.ByBrand(brand) else ProductFilter.None)
                }
            )
        }
        items(categories, key = { "category:$it" }) { category ->
            ChipToggle(
                label = category,
                selected = activeFilter is ProductFilter.ByCategory &&
                    activeFilter.category == category,
                onToggle = { on ->
                    onFilterChange(if (on) ProductFilter.ByCategory(category) else ProductFilter.None)
                }
            )
        }
    }
}

@Composable
private fun ChipToggle(label: String, selected: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(!selected) },
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge
            )
        },
        shape = RoundedCornerShape(10.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.secondary
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
        )
    )
}

/**
 * A quiet one-line status between the filters and the list. It exists so the
 * shopkeeper can tell "no results" apart from "a filter is hiding them", which
 * is the single most common source of confusion in a list this dense.
 */
@Composable
private fun ResultSummary(state: ProductListUiState, onClearFilter: () -> Unit) {
    if (state.isLoading || state.totalCount == 0) {
        Spacer(Modifier.height(8.dp))
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (state.isSearching || state.isFiltered) {
                stringResource(R.string.results_count, state.products.size, state.totalCount)
            } else {
                stringResource(R.string.showing_all, state.totalCount)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (state.isFiltered) {
            TextButton(onClick = onClearFilter) {
                Text(
                    stringResource(R.string.clear_filter),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// ============================================================================
// Product row
// ============================================================================

@Composable
private fun ProductRow(
    pwv: ProductWithVariants,
    currencySymbol: String,
    priceFontSize: PriceFontSize,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val priceLabel = pwv.priceLabel(currencySymbol)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${pwv.product.displayTitle}, $priceLabel"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pwv.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                MetaLine(pwv = pwv)
            }

            Spacer(Modifier.width(12.dp))

            PricePill(
                price = priceLabel,
                fontSize = priceFontSize.rowSize,
                muted = !pwv.isAnyInStock
            )
        }
    }
}

/**
 * Brand · variants/pack · category on one muted line.
 */
@Composable
private fun MetaLine(pwv: ProductWithVariants) {
    val colors = MaterialTheme.colorScheme

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!pwv.product.brand.isNullOrBlank()) {
            Text(
                text = pwv.product.brand,
                style = MaterialTheme.typography.labelMedium,
                color = colors.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Dot()
        }
        val variantText = when {
            pwv.variants.size > 1 -> "${pwv.variants.size} variants"
            pwv.variants.size == 1 -> pwv.variants.first().variantLabel
            else -> ""
        }
        Text(
            text = buildString {
                append(variantText)
                pwv.product.category?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (!pwv.isAnyInStock) {
            Dot()
            Text(
                text = stringResource(R.string.out_of_stock),
                style = MaterialTheme.typography.labelSmall,
                color = colors.error,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Dot() {
    Text(
        text = " · ",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Out-of-stock items keep a fully legible price — the customer still asked what
 * it costs — but lose the solid fill, so stock status reads at a glance without
 * making the number harder to see.
 */
@Composable
private fun PricePill(price: String, fontSize: Int, muted: Boolean) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (muted) colors.surfaceContainerHigh else colors.primary,
        border = if (muted) BorderStroke(1.dp, colors.primary) else null,
        modifier = Modifier.clearAndSetSemantics { }
    ) {
        Text(
            text = price,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Black,
            color = if (muted) colors.primary else colors.onPrimary,
            style = TabularFigures,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

private val PriceFontSize.rowSize: Int
    get() = when (this) {
        PriceFontSize.NORMAL -> 19
        PriceFontSize.LARGE -> 23
        PriceFontSize.EXTRA_LARGE -> 27
    }

// ============================================================================
// Sort sheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    currentSort: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.sort_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SORT_OPTIONS.forEach { (order, labelRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortChange(order) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentSort == order, onClick = { onSortChange(order) })
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val SORT_OPTIONS = listOf(
    SortOrder.NAME_ASC to R.string.sort_name_asc,
    SortOrder.BRAND_ASC to R.string.sort_brand_asc,
    SortOrder.PRICE_ASC to R.string.sort_price_asc,
    SortOrder.PRICE_DESC to R.string.sort_price_desc,
    SortOrder.RECENTLY_UPDATED to R.string.sort_recent
)

// ============================================================================
// Loading & empty states
// ============================================================================

/** Placeholder rows, so the first frame after launch is not a blank page. */
@Composable
private fun LoadingRows() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    // Fading down the page reads as "more is coming" rather than
                    // five identical grey bricks.
                    .alpha(0.55f - index * 0.09f)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.shapes.medium
                    )
            )
        }
    }
}

@Composable
private fun EmptyState(
    state: ProductListUiState,
    onAddProduct: () -> Unit,
    onImportCsv: () -> Unit,
    onClearFilter: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val (titleRes, bodyRes) = when {
        state.isSearching -> R.string.no_matches_title to R.string.no_matches_body
        state.isFiltered -> R.string.no_filter_matches_title to R.string.no_filter_matches_body
        else -> R.string.first_run_title to R.string.first_run_body
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceContainer,
            modifier = Modifier
                .size(92.dp)
                .border(1.dp, colors.outline, CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state.isSearching) Icons.Filled.Search else Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = colors.primary
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))
        when {
            state.isFiltered && !state.isSearching -> TextButton(onClick = onClearFilter) {
                Text(stringResource(R.string.clear_filter))
            }
            !state.isSearching -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAddProduct) { Text(stringResource(R.string.add_product)) }
                TextButton(onClick = onImportCsv) { Text(stringResource(R.string.import_csv)) }
            }
        }
    }
}
