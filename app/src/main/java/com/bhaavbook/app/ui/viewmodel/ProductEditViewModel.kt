package com.bhaavbook.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhaavbook.app.data.model.DEFAULT_CATEGORIES
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductUnit
import com.bhaavbook.app.data.repository.ProductRepository
import com.bhaavbook.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductEditUiState(
    // Form fields
    val name: String = "",
    val brand: String = "",
    val category: String = "",
    val sellingPrice: String = "",
    val costPrice: String = "",
    val unit: ProductUnit = ProductUnit.PIECE,
    val quantityValue: String = "",
    val inStock: Boolean = true,
    val notes: String = "",

    // Validation
    val nameError: String? = null,
    val sellingPriceError: String? = null,
    val duplicateWarning: String? = null,

    // Metadata
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,

    // Dropdowns populated from DB
    val availableCategories: List<String> = DEFAULT_CATEGORIES,
    val availableBrands: List<String> = emptyList(),

    val snackbarMessage: String? = null
)

@HiltViewModel
class ProductEditViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** productId = 0 means Add mode; any other value is Edit mode. */
    private val productId: Long = savedStateHandle["productId"] ?: 0L
    private var originalProduct: Product? = null

    private val _state = MutableStateFlow(ProductEditUiState())
    val state: StateFlow<ProductEditUiState> = combine(
        _state,
        repository.getAllCategories(),
        repository.getAllBrands()
    ) { s, dbCategories, dbBrands ->
        // Merge DB categories with defaults, deduplicating and keeping order
        val merged = (DEFAULT_CATEGORIES + dbCategories).distinct()
        s.copy(
            availableCategories = merged,
            availableBrands = dbBrands
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductEditUiState())

    init {
        if (productId != 0L) {
            loadProduct(productId)
        } else {
            // Prefill default unit from settings
            viewModelScope.launch {
                settingsRepository.settings.collect { settings ->
                    _state.value = _state.value.copy(
                        unit = ProductUnit.fromString(settings.defaultUnit),
                        isEditMode = false
                    )
                    return@collect // only need first emission for initial state
                }
            }
        }
    }

    private fun loadProduct(id: Long) {
        viewModelScope.launch {
            val product = repository.getById(id) ?: return@launch
            originalProduct = product
            _state.value = _state.value.copy(
                name = product.name,
                brand = product.brand ?: "",
                category = product.category ?: "",
                sellingPrice = product.sellingPrice.formatForField(),
                costPrice = product.costPrice?.formatForField() ?: "",
                unit = product.unit,
                quantityValue = product.quantityValue?.formatForField() ?: "",
                inStock = product.inStock,
                notes = product.notes ?: "",
                isEditMode = true
            )
        }
    }

    // -----------------------------------------------------------------------
    // Field updates
    // -----------------------------------------------------------------------

    fun onNameChange(v: String) {
        _state.value = _state.value.copy(name = v, nameError = null, duplicateWarning = null)
    }

    fun onBrandChange(v: String) {
        _state.value = _state.value.copy(brand = v, duplicateWarning = null)
    }

    fun onCategoryChange(v: String) { _state.value = _state.value.copy(category = v) }
    fun onSellingPriceChange(v: String) {
        _state.value = _state.value.copy(sellingPrice = v, sellingPriceError = null)
    }
    fun onCostPriceChange(v: String) { _state.value = _state.value.copy(costPrice = v) }
    fun onUnitChange(v: ProductUnit) { _state.value = _state.value.copy(unit = v) }
    fun onQuantityValueChange(v: String) { _state.value = _state.value.copy(quantityValue = v) }
    fun onInStockChange(v: Boolean) { _state.value = _state.value.copy(inStock = v) }
    fun onNotesChange(v: String) { _state.value = _state.value.copy(notes = v) }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    fun save() {
        val s = _state.value
        var hasError = false

        // Validate name
        if (s.name.isBlank()) {
            _state.value = _state.value.copy(nameError = "Name is required")
            hasError = true
        }

        // Validate price
        val price = s.sellingPrice.trim().toDoubleOrNull()
        if (s.sellingPrice.isBlank() || price == null) {
            _state.value = _state.value.copy(sellingPriceError = "Enter a valid price")
            hasError = true
        } else if (price < 0) {
            _state.value = _state.value.copy(sellingPriceError = "Price cannot be negative")
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)

            // Duplicate check (non-blocking warning)
            val brandVal = s.brand.takeIf { it.isNotBlank() }
            val duplicate = repository.checkDuplicate(s.name, brandVal, excludeId = productId)
            if (duplicate != null) {
                _state.value = _state.value.copy(
                    duplicateWarning = "\"${duplicate.displayTitle}\" already exists",
                    isSaving = false
                )
                // Non-blocking: user can still save
            }

            val product = Product(
                id = productId,
                name = s.name.trim(),
                brand = s.brand.takeIf { it.isNotBlank() },
                category = s.category.takeIf { it.isNotBlank() },
                sellingPrice = price!!,
                costPrice = s.costPrice.trim().toDoubleOrNull(),
                unit = s.unit,
                quantityValue = s.quantityValue.trim().toDoubleOrNull(),
                inStock = s.inStock,
                notes = s.notes.takeIf { it.isNotBlank() },
                createdAt = originalProduct?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            if (productId == 0L) repository.insert(product)
            else repository.update(product)

            _state.value = _state.value.copy(
                isSaving = false,
                savedSuccessfully = true,
                snackbarMessage = "Product saved"
            )
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbarMessage = null) }
}

private fun Double.formatForField(): String =
    if (this == kotlin.math.floor(this) && this < 1_000_000) this.toLong().toString()
    else this.toString()
