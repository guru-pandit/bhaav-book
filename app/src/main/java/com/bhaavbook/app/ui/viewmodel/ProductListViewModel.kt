package com.bhaavbook.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.repository.ProductFilter
import com.bhaavbook.app.data.repository.ProductRepository
import com.bhaavbook.app.data.repository.SortOrder
import com.bhaavbook.app.data.settings.AppSettings
import com.bhaavbook.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
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
    val settings: AppSettings = AppSettings(),
    val snackbarMessage: String? = null
)

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ProductListViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Persisted across process death
    private val _searchQuery = MutableStateFlow(
        savedStateHandle["search_query"] ?: ""
    )
    private val _sortOrder = MutableStateFlow(
        SortOrder.valueOf(savedStateHandle["sort_order"] ?: SortOrder.NAME_ASC.name)
    )
    private val _activeFilter = MutableStateFlow<ProductFilter>(ProductFilter.None)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _pendingDelete = MutableStateFlow<Product?>(null)

    val searchQuery: StateFlow<String> = _searchQuery
    val sortOrder: StateFlow<SortOrder> = _sortOrder
    val activeFilter: StateFlow<ProductFilter> = _activeFilter

    // Debounce search by 200 ms
    private val debouncedQuery = _searchQuery.debounce(200L)

    private val products = combine(
        debouncedQuery, _sortOrder, _activeFilter
    ) { q, s, f -> Triple(q, s, f) }
        .flatMapLatest { (q, s, f) -> repository.getProducts(q, s, f) }

    val uiState: StateFlow<ProductListUiState> = combine(
        products,
        _searchQuery,
        _sortOrder,
        _activeFilter,
        repository.getAllCategories(),
        repository.getAllBrands(),
        settingsRepository.settings,
        _snackbarMessage
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        ProductListUiState(
            products = args[0] as List<Product>,
            isLoading = false,
            searchQuery = args[1] as String,
            sortOrder = args[2] as SortOrder,
            activeFilter = args[3] as ProductFilter,
            categories = args[4] as List<String>,
            brands = args[5] as List<String>,
            settings = args[6] as AppSettings,
            snackbarMessage = args[7] as String?
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProductListUiState()
    )

    // -----------------------------------------------------------------------
    // User actions
    // -----------------------------------------------------------------------

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun onSortOrderChange(order: SortOrder) { _sortOrder.value = order }
    fun onFilterChange(filter: ProductFilter) { _activeFilter.value = filter }
    fun clearSearch() { _searchQuery.value = "" }

    /**
     * Soft-delete: mark pending for 5 seconds, show undo snackbar.
     * After 5 s, if not undone, hard-delete from DB.
     */
    fun requestDelete(product: Product) {
        _pendingDelete.value = product
        _snackbarMessage.value = "\"${product.name}\" deleted"
        viewModelScope.launch {
            kotlinx.coroutines.delay(5_000)
            val pending = _pendingDelete.value
            if (pending != null && pending.id == product.id) {
                repository.delete(pending)
                _pendingDelete.value = null
            }
        }
    }

    fun undoDelete() {
        _pendingDelete.value = null
        _snackbarMessage.value = null
    }

    fun clearSnackbar() { _snackbarMessage.value = null }

    fun showSnackbar(message: String) { _snackbarMessage.value = message }
}
