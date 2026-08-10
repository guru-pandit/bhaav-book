package com.bhaavbook.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhaavbook.app.csv.CsvExporter
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.repository.ProductFilter
import com.bhaavbook.app.data.repository.ProductRepository
import com.bhaavbook.app.data.repository.SortOrder
import com.bhaavbook.app.data.settings.AppSettings
import com.bhaavbook.app.data.settings.SettingsRepository
import com.bhaavbook.app.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductListUiState(
    val products: List<Product> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val activeFilter: ProductFilter = ProductFilter.None,
    val categories: List<String> = emptyList(),
    val brands: List<String> = emptyList(),
    val totalCount: Int = 0,
    val settings: AppSettings = AppSettings()
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()
    val isFiltered: Boolean get() = activeFilter != ProductFilter.None
    val isEmptyResult: Boolean get() = !isLoading && products.isEmpty()
}

/**
 * A one-shot message for the snackbar. Delivered over a channel rather than
 * parked in [ProductListUiState] so two identical messages in a row both show,
 * and so a rotation doesn't replay a stale one.
 */
data class UiMessage(
    val text: String,
    /** Set only for reversible actions; drives the snackbar's action button. */
    val undoLabel: String? = null
)

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ProductListViewModel @Inject constructor(
    private val repository: ProductRepository,
    settingsRepository: SettingsRepository,
    private val csvExporter: CsvExporter,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _searchQuery = MutableStateFlow(savedStateHandle[KEY_QUERY] ?: "")
    private val _sortOrder = MutableStateFlow(
        runCatching { SortOrder.valueOf(savedStateHandle[KEY_SORT] ?: "") }
            .getOrDefault(SortOrder.NAME_ASC)
    )
    private val _activeFilter = MutableStateFlow<ProductFilter>(ProductFilter.None)

    /**
     * Ids hidden while their delete is still undoable. The row disappears the
     * moment Delete is tapped — waiting for the commit makes the tap feel dead.
     */
    private val _hiddenIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _messages = Channel<UiMessage>(
        capacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var pendingDelete: Product? = null
    private var pendingDeleteJob: Job? = null

    /**
     * An empty query needs no debounce — clearing the box should snap back to
     * the full list instantly. Typing gets a short window so each keystroke
     * doesn't fire its own query.
     */
    private val debouncedQuery = _searchQuery.debounce { query ->
        if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS
    }

    private val visibleProducts: Flow<List<Product>> =
        combine(debouncedQuery, _sortOrder, _activeFilter) { query, sort, filter ->
            SearchInputs(query, sort, filter)
        }
            .flatMapLatest { (query, sort, filter) -> repository.getProducts(query, sort, filter) }
            .combine(_hiddenIds) { products, hidden ->
                if (hidden.isEmpty()) products else products.filterNot { it.id in hidden }
            }

    private val listState = combine(
        visibleProducts,
        _searchQuery,
        _sortOrder,
        _activeFilter
    ) { products, query, sort, filter -> ListInputs(products, query, sort, filter) }

    private val referenceData = combine(
        repository.getAllCategories(),
        repository.getAllBrands(),
        repository.observeCount(),
        settingsRepository.settings
    ) { categories, brands, count, settings ->
        ReferenceData(categories, brands, count, settings)
    }

    val uiState: StateFlow<ProductListUiState> =
        combine(listState, referenceData) { list, reference ->
            ProductListUiState(
                products = list.products,
                isLoading = false,
                searchQuery = list.query,
                sortOrder = list.sort,
                activeFilter = list.filter,
                categories = reference.categories,
                brands = reference.brands,
                totalCount = reference.count,
                settings = reference.settings
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductListUiState())

    // -----------------------------------------------------------------------
    // Search, sort, filter
    // -----------------------------------------------------------------------

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        savedStateHandle[KEY_QUERY] = query
    }

    fun clearSearch() = onSearchQueryChange("")

    fun onSortOrderChange(order: SortOrder) {
        _sortOrder.value = order
        savedStateHandle[KEY_SORT] = order.name
    }

    fun onFilterChange(filter: ProductFilter) {
        _activeFilter.value = filter
    }

    fun clearFilters() {
        _activeFilter.value = ProductFilter.None
    }

    // -----------------------------------------------------------------------
    // Delete with undo
    // -----------------------------------------------------------------------

    /**
     * Hides [product] at once and commits the delete when the undo window
     * closes. Both the timer and the write live in [applicationScope], so
     * navigating away — or the ViewModel being cleared outright — cannot
     * resurrect a row the shopkeeper already deleted.
     */
    fun requestDelete(product: Product) {
        flushPendingDelete()

        pendingDelete = product
        _hiddenIds.value = _hiddenIds.value + product.id
        _messages.trySend(UiMessage("\"${product.name}\" deleted", undoLabel = "UNDO"))

        pendingDeleteJob = applicationScope.launch {
            delay(UNDO_WINDOW_MS)
            commitDelete(product)
        }
    }

    fun undoDelete() {
        val product = pendingDelete ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        pendingDelete = null
        _hiddenIds.value = _hiddenIds.value - product.id
    }

    /** Commits a still-pending delete immediately instead of letting it queue up. */
    private fun flushPendingDelete() {
        val product = pendingDelete ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        applicationScope.launch { commitDelete(product) }
    }

    private suspend fun commitDelete(product: Product) {
        pendingDelete = null
        runCatching { repository.delete(product) }
            .onFailure { _messages.trySend(UiMessage("Could not delete \"${product.name}\"")) }
        _hiddenIds.value = _hiddenIds.value - product.id
    }

    // -----------------------------------------------------------------------
    // Export & share
    // -----------------------------------------------------------------------

    /** Writes the whole price list to the file the user picked through SAF. */
    fun exportToCsvUri(uri: Uri) {
        viewModelScope.launch {
            val snapshot = repository.getAllSnapshot()
            if (snapshot.isEmpty()) {
                _messages.trySend(UiMessage("Nothing to export yet"))
                return@launch
            }
            runCatching { csvExporter.exportToCsv(uri, snapshot) }
                .onSuccess { _messages.trySend(UiMessage("Exported ${snapshot.size} items")) }
                .onFailure { _messages.trySend(UiMessage("Export failed: ${it.readableMessage()}")) }
        }
    }

    /** Hands the price list to WhatsApp / Email / Drive via the system share sheet. */
    fun shareCsv(context: Context) {
        viewModelScope.launch {
            val snapshot = repository.getAllSnapshot()
            if (snapshot.isEmpty()) {
                _messages.trySend(UiMessage("Nothing to share yet"))
                return@launch
            }
            runCatching { context.startActivity(csvExporter.createShareCsvIntent(snapshot)) }
                .onFailure { _messages.trySend(UiMessage("Could not share: ${it.readableMessage()}")) }
        }
    }

    private data class SearchInputs(
        val query: String,
        val sort: SortOrder,
        val filter: ProductFilter
    )

    private data class ListInputs(
        val products: List<Product>,
        val query: String,
        val sort: SortOrder,
        val filter: ProductFilter
    )

    private data class ReferenceData(
        val categories: List<String>,
        val brands: List<String>,
        val count: Int,
        val settings: AppSettings
    )

    private companion object {
        const val KEY_QUERY = "search_query"
        const val KEY_SORT = "sort_order"
        const val SEARCH_DEBOUNCE_MS = 180L
        const val UNDO_WINDOW_MS = 4_500L
    }
}

internal fun Throwable.readableMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
