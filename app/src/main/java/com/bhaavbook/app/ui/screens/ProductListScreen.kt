package com.bhaavbook.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
import com.bhaavbook.app.ui.theme.Cream
import com.bhaavbook.app.ui.theme.CreamDark
import com.bhaavbook.app.ui.theme.Gold
import com.bhaavbook.app.ui.theme.Maroon
import com.bhaavbook.app.ui.theme.Terracotta
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

    // SAF launcher for CSV export
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportToCsvUri(it) }
    }

    var selectedProduct by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Auto-focus search
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.settings.autoFocusSearch) {
        if (uiState.settings.autoFocusSearch) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Snackbar listener with undo
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Display Chaitanya Stores Brand Logo
                        Image(
                            painter = painterResource(id = R.drawable.logo_master),
                            contentDescription = "Chaitanya Stores Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Chaitanya Stores",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = Cream
                            )
                            Text(
                                text = "${uiState.products.size} Items in Price List",
                                style = MaterialTheme.typography.labelSmall,
                                color = Gold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.shareCsv(context) }) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share CSV via WhatsApp",
                            tint = Cream
                        )
                    }
                    IconButton(onClick = { showSortSheet = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.sort_and_filter),
                            tint = Cream
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = Cream
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Cream)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share CSV (WhatsApp / Email)") },
                            onClick = {
                                showMenu = false
                                viewModel.shareCsv(context)
                            },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null, tint = Maroon) }
                        )
                        DropdownMenuItem(
                            text = { Text("Export CSV to Storage") },
                            onClick = {
                                showMenu = false
                                exportCsvLauncher.launch("chaitanya_stores_prices.csv")
                            },
                            leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Maroon) }
                        )
                        DropdownMenuItem(
                            text = { Text("Import CSV File") },
                            onClick = {
                                showMenu = false
                                onImportCsv()
                            },
                            leadingIcon = { Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = Maroon) }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                onSettings()
                            },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = Maroon) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Maroon,
                    titleContentColor = Cream,
                    actionIconContentColor = Cream
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProduct,
                shape = CircleShape,
                containerColor = Terracotta,
                contentColor = Cream
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_product),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Cream)
        ) {
            // ─── Warm Styled Search Bar ─────────────────────────────────
            ChaitanyaSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::clearSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            // ─── Filter Chips Row ───────────────────────────────────────
            if (uiState.categories.isNotEmpty() || uiState.brands.isNotEmpty()) {
                ChaitanyaFilterChipRow(
                    categories = uiState.categories,
                    brands = uiState.brands,
                    activeFilter = uiState.activeFilter,
                    onFilterChange = viewModel::onFilterChange
                )
            }

            // ─── Product List or Empty State ─────────────────────────────
            if (!uiState.isLoading && uiState.products.isEmpty()) {
                ChaitanyaEmptyState(
                    isSearching = uiState.searchQuery.isNotBlank(),
                    onAddProduct = onAddProduct,
                    onImportCsv = onImportCsv,
                    onShareCsv = { viewModel.shareCsv(context) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.products,
                        key = { it.id }
                    ) { product ->
                        ChaitanyaProductCard(
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

    // ─── Sort Bottom Sheet ───────────────────────────────────────────────
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

    // ─── Big Price Bottom Sheet ───────────────────────────────────────────
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
// Chaitanya Stores Styled Sub-Components
// ============================================================================

@Composable
private fun ChaitanyaSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, Gold, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = CreamDark
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Search",
                tint = Terracotta
            )
            Spacer(Modifier.width(10.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Search item name, brand, or category…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            AnimatedVisibility(visible = query.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        tint = Terracotta
                    )
                }
            }
        }
    }
}

@Composable
private fun ChaitanyaFilterChipRow(
    categories: List<String>,
    brands: List<String>,
    activeFilter: ProductFilter,
    onFilterChange: (ProductFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = activeFilter is ProductFilter.InStockOnly,
                onClick = {
                    onFilterChange(
                        if (activeFilter is ProductFilter.InStockOnly) ProductFilter.None
                        else ProductFilter.InStockOnly
                    )
                },
                label = { Text("In stock") },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Maroon,
                    selectedLabelColor = Cream,
                    containerColor = CreamDark,
                    labelColor = Maroon
                )
            )
        }

        items(categories) { cat ->
            FilterChip(
                selected = activeFilter is ProductFilter.ByCategory && activeFilter.category == cat,
                onClick = {
                    onFilterChange(
                        if (activeFilter is ProductFilter.ByCategory && activeFilter.category == cat) ProductFilter.None
                        else ProductFilter.ByCategory(cat)
                    )
                },
                label = { Text(cat, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Maroon,
                    selectedLabelColor = Cream,
                    containerColor = CreamDark,
                    labelColor = Maroon
                )
            )
        }

        items(brands) { brand ->
            FilterChip(
                selected = activeFilter is ProductFilter.ByBrand && activeFilter.brand == brand,
                onClick = {
                    onFilterChange(
                        if (activeFilter is ProductFilter.ByBrand && activeFilter.brand == brand) ProductFilter.None
                        else ProductFilter.ByBrand(brand)
                    )
                },
                label = { Text(brand, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Maroon,
                    selectedLabelColor = Cream,
                    containerColor = CreamDark,
                    labelColor = Maroon
                )
            )
        }
    }
}

@Composable
private fun ChaitanyaProductCard(
    product: Product,
    currencySymbol: String,
    priceFontSize: PriceFontSize,
    onClick: () -> Unit
) {
    val isOutOfStock = !product.inStock
    val cardAlpha = if (isOutOfStock) 0.5f else 1.0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${product.displayTitle} price ${product.sellingPrice}" },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = CreamDark
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column
            Column(modifier = Modifier.weight(1f)) {
                if (!product.brand.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Gold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = product.brand.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Maroon,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Maroon.copy(alpha = cardAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val unitStr = buildString {
                        if (product.quantityValue != null) append("${product.quantityValue.toLong()} ")
                        append(product.unit.shortLabel)
                        product.category?.let { append(" · $it") }
                    }
                    Text(
                        text = unitStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = cardAlpha)
                    )

                    if (isOutOfStock) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Out of stock",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Right Column: Terracotta Price Badge
            val priceSp = when (priceFontSize) {
                PriceFontSize.NORMAL -> 20.sp
                PriceFontSize.LARGE -> 24.sp
                PriceFontSize.EXTRA_LARGE -> 28.sp
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Terracotta.copy(alpha = if (isOutOfStock) 0.4f else 1.0f)
            ) {
                Text(
                    text = "$currencySymbol${product.sellingPrice.toDisplayPrice()}",
                    fontSize = priceSp,
                    fontWeight = FontWeight.Black,
                    color = Cream,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Cream
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                "Sort Products",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Maroon,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val options = listOf(
                SortOrder.NAME_ASC to "Name A–Z",
                SortOrder.BRAND_ASC to "Brand A–Z",
                SortOrder.PRICE_ASC to "Price: Low → High",
                SortOrder.PRICE_DESC to "Price: High → Low",
                SortOrder.RECENTLY_UPDATED to "Recently Updated"
            )
            options.forEach { (order, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortChange(order) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSort == order,
                        onClick = { onSortChange(order) },
                        colors = RadioButtonDefaults.colors(selectedColor = Terracotta)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = Maroon)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ChaitanyaEmptyState(
    isSearching: Boolean,
    onAddProduct: () -> Unit,
    onImportCsv: () -> Unit,
    onShareCsv: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = CreamDark,
            modifier = Modifier
                .size(100.dp)
                .border(2.dp, Gold, CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Terracotta
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (isSearching) "No matching items" else "Chaitanya Stores",
            fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Maroon
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isSearching) "Try searching with a different name or brand."
                   else stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        if (!isSearching) {
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onAddProduct) { Text("+ Add Product", color = Terracotta) }
                TextButton(onClick = onImportCsv) { Text("Import CSV", color = Terracotta) }
                TextButton(onClick = onShareCsv) { Text("Share CSV", color = Terracotta) }
            }
        }
    }
}

private fun Double.toDisplayPrice(): String =
    if (this == kotlin.math.floor(this) && this < 1_000_000) this.toLong().toString()
    else "%.2f".format(this)
