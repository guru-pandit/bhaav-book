package com.bhaavbook.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bhaavbook.app.R
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.repository.ProductFilter
import com.bhaavbook.app.data.repository.SortOrder
import com.bhaavbook.app.data.settings.PriceFontSize
import com.bhaavbook.app.ui.viewmodel.ProductListViewModel
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    // Selected product for the big price bottom sheet
    var selectedProduct by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    // Auto-focus search
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.settings.autoFocusSearch) {
        if (uiState.settings.autoFocusSearch) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Snackbar with undo
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = "UNDO",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        viewModel.clearSnackbar()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onImportCsv) {
                        Icon(
                            Icons.Outlined.FileOpen,
                            contentDescription = stringResource(R.string.import_csv)
                        )
                    }
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.sort_and_filter)
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProduct,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_product),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ─── Pinned search bar ───────────────────────────────────────
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::clearSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            // ─── Filter chips ────────────────────────────────────────────
            if (uiState.categories.isNotEmpty() || uiState.brands.isNotEmpty()) {
                FilterChipRow(
                    categories = uiState.categories,
                    brands = uiState.brands,
                    activeFilter = uiState.activeFilter,
                    onFilterChange = viewModel::onFilterChange
                )
            }

            // ─── Product list or empty state ─────────────────────────────
            if (!uiState.isLoading && uiState.products.isEmpty()) {
                EmptyState(
                    isSearching = uiState.searchQuery.isNotBlank(),
                    onAddProduct = onAddProduct,
                    onImportCsv = onImportCsv
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp) // FAB clearance
                ) {
                    items(
                        items = uiState.products,
                        key = { it.id }
                    ) { product ->
                        ProductRow(
                            product = product,
                            currencySymbol = uiState.settings.currencySymbol,
                            priceFontSize = uiState.settings.priceFontSize,
                            onClick = { selectedProduct = product.id }
                        )
                    }
                }
            }
        }
    }

    // ─── Sort bottom sheet ───────────────────────────────────────────────
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

    // ─── Big price bottom sheet ───────────────────────────────────────────
    selectedProduct?.let { id ->
        val product = uiState.products.firstOrNull { it.id == id }
        if (product != null) {
            PriceSheet(
                product = product,
                currencySymbol = uiState.settings.currencySymbol,
                priceFontSize = uiState.settings.priceFontSize,
                showCostPrice = uiState.settings.showCostPrice,
                onEdit = {
                    selectedProduct = null
                    onEditProduct(product.id)
                },
                onDelete = {
                    selectedProduct = null
                    viewModel.requestDelete(product)
                },
                onDismiss = { selectedProduct = null }
            )
        }
    }
}

// ============================================================================
// Sub-components
// ============================================================================

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50)),
        placeholder = { Text("Search by name, brand, category…") },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = "Search")
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun FilterChipRow(
    categories: List<String>,
    brands: List<String>,
    activeFilter: ProductFilter,
    onFilterChange: (ProductFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "In stock only" chip
        item {
            FilterChip(
                selected = activeFilter is ProductFilter.InStockOnly,
                onClick = {
                    onFilterChange(
                        if (activeFilter is ProductFilter.InStockOnly) ProductFilter.None
                        else ProductFilter.InStockOnly
                    )
                },
                label = { Text("In stock") }
            )
        }

        // Category chips
        items(categories) { cat ->
            FilterChip(
                selected = activeFilter is ProductFilter.ByCategory &&
                    activeFilter.category == cat,
                onClick = {
                    onFilterChange(
                        if (activeFilter is ProductFilter.ByCategory && activeFilter.category == cat)
                            ProductFilter.None
                        else ProductFilter.ByCategory(cat)
                    )
                },
                label = { Text(cat, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }

        // Brand chips
        items(brands) { brand ->
            FilterChip(
                selected = activeFilter is ProductFilter.ByBrand &&
                    activeFilter.brand == brand,
                onClick = {
                    onFilterChange(
                        if (activeFilter is ProductFilter.ByBrand && activeFilter.brand == brand)
                            ProductFilter.None
                        else ProductFilter.ByBrand(brand)
                    )
                },
                label = { Text(brand, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun ProductRow(
    product: Product,
    currencySymbol: String,
    priceFontSize: PriceFontSize,
    onClick: () -> Unit
) {
    val isOutOfStock = !product.inStock
    val textAlpha = if (isOutOfStock) 0.5f else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${product.displayTitle} price ${product.sellingPrice}" },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: name + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val subtitle = buildString {
                        if (product.quantityValue != null) append("${product.quantityValue.toLong()} ")
                        append(product.unit.shortLabel)
                        product.category?.let { append(" · $it") }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
                    )
                    if (isOutOfStock) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Out of stock",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Right: price (large, high contrast)
            val priceTextSize = when (priceFontSize) {
                PriceFontSize.NORMAL -> 18.sp
                PriceFontSize.LARGE -> 22.sp
                PriceFontSize.EXTRA_LARGE -> 28.sp
            }
            Text(
                text = "$currencySymbol${product.sellingPrice.toDisplayPrice()}",
                fontSize = priceTextSize,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha),
                maxLines = 1
            )
        }
    }

    // Thin divider
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    currentSort: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val options = listOf(
                SortOrder.NAME_ASC to "Name A–Z",
                SortOrder.BRAND_ASC to "Brand A–Z",
                SortOrder.PRICE_ASC to "Price: Low → High",
                SortOrder.PRICE_DESC to "Price: High → Low",
                SortOrder.RECENTLY_UPDATED to "Recently updated"
            )
            options.forEach { (order, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortChange(order) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSort == order,
                        onClick = { onSortChange(order) }
                    )
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmptyState(
    isSearching: Boolean,
    onAddProduct: () -> Unit,
    onImportCsv: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Inventory2,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isSearching) "No products found" else "BhaavBook",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isSearching) "Try different keywords"
                   else stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isSearching) {
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onAddProduct) { Text("Add product") }
                TextButton(onClick = onImportCsv) { Text("Import CSV") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Price formatter
// ---------------------------------------------------------------------------
private fun Double.toDisplayPrice(): String =
    if (this == kotlin.math.floor(this) && this < 1_000_000) this.toLong().toString()
    else "%.2f".format(this)
