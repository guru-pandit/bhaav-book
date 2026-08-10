package com.bhaavbook.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.bhaavbook.app.R
import com.bhaavbook.app.data.model.DEFAULT_CATEGORIES
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductUnit
import com.bhaavbook.app.data.repository.ProductRepository
import com.bhaavbook.app.data.settings.SettingsRepository
import com.bhaavbook.app.format.toEditableString
import com.bhaavbook.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Anything above this is far more likely a typo than a real price. */
private const val MAX_PRICE = 10_000_000.0

data class ProductEditUiState(
    val name: String = "",
    val brand: String = "",
    val category: String = "",
    val sellingPrice: String = "",
    val costPrice: String = "",
    val unit: ProductUnit = ProductUnit.PIECE,
    val quantityValue: String = "",
    val inStock: Boolean = true,
    val notes: String = "",

    @StringRes val nameError: Int? = null,
    @StringRes val sellingPriceError: Int? = null,
    /** Pre-formatted, because it embeds the name of the clashing item. */
    val duplicateWarning: String? = null,

    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val saveError: String? = null,

    val availableCategories: List<String> = DEFAULT_CATEGORIES,
    val availableBrands: List<String> = emptyList()
)

@HiltViewModel
class ProductEditViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** `0` means Add; anything else is Edit. */
    private val productId: Long = savedStateHandle[Screen.EditProduct.ARG_PRODUCT_ID] ?: 0L
    private var originalProduct: Product? = null

    /**
     * Set once the user has been shown the duplicate warning. The second tap on
     * Save goes through — the old code warned *and* saved in the same pass,
     * which made the warning a lie.
     */
    private var duplicateAcknowledged = false

    private val _state = MutableStateFlow(ProductEditUiState(isEditMode = productId != 0L))

    val state: StateFlow<ProductEditUiState> = combine(
        _state,
        repository.getAllCategories(),
        repository.getAllBrands()
    ) { current, dbCategories, dbBrands ->
        current.copy(
            availableCategories = (DEFAULT_CATEGORIES + dbCategories).distinctBy { it.lowercase() },
            availableBrands = dbBrands
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _state.value)

    init {
        viewModelScope.launch {
            if (productId != 0L) {
                loadProduct(productId)
            } else {
                // A single read, not a subscription: the default unit seeds a new
                // form once. Collecting it would reset the picker under the user
                // every time any setting changed.
                val defaultUnit = ProductUnit.fromString(settingsRepository.settings.first().defaultUnit)
                _state.value = _state.value.copy(unit = defaultUnit)
            }
        }
    }

    private suspend fun loadProduct(id: Long) {
        val product = repository.getById(id) ?: return
        originalProduct = product
        _state.value = _state.value.copy(
            name = product.name,
            brand = product.brand.orEmpty(),
            category = product.category.orEmpty(),
            sellingPrice = product.sellingPrice.toEditableString(),
            costPrice = product.costPrice?.toEditableString().orEmpty(),
            unit = product.unit,
            quantityValue = product.quantityValue?.toEditableString().orEmpty(),
            inStock = product.inStock,
            notes = product.notes.orEmpty(),
            isEditMode = true
        )
    }

    // -----------------------------------------------------------------------
    // Field updates
    // -----------------------------------------------------------------------

    fun onNameChange(value: String) = update {
        // Changing the identity of the item invalidates the duplicate decision.
        duplicateAcknowledged = false
        it.copy(name = value, nameError = null, duplicateWarning = null)
    }

    fun onBrandChange(value: String) = update {
        duplicateAcknowledged = false
        it.copy(brand = value, duplicateWarning = null)
    }

    fun onCategoryChange(value: String) = update { it.copy(category = value) }

    fun onSellingPriceChange(value: String) = update {
        it.copy(sellingPrice = value.filterPriceInput(), sellingPriceError = null)
    }

    fun onCostPriceChange(value: String) = update {
        it.copy(costPrice = value.filterPriceInput())
    }

    fun onUnitChange(value: ProductUnit) = update { it.copy(unit = value) }

    fun onQuantityValueChange(value: String) = update {
        it.copy(quantityValue = value.filterPriceInput())
    }

    fun onInStockChange(value: Boolean) = update { it.copy(inStock = value) }

    fun onNotesChange(value: String) = update { it.copy(notes = value) }

    fun clearSaveError() = update { it.copy(saveError = null) }

    private inline fun update(block: (ProductEditUiState) -> ProductEditUiState) {
        _state.value = block(_state.value)
    }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    fun save() {
        val snapshot = _state.value
        if (snapshot.isSaving) return

        val name = snapshot.name.trim()
        val price = snapshot.sellingPrice.trim().toDoubleOrNull()

        val nameError = if (name.isEmpty()) R.string.error_name_required else null
        val priceError = when {
            price == null -> R.string.error_price_required
            price < 0 -> R.string.error_price_negative
            price > MAX_PRICE -> R.string.error_price_too_large
            else -> null
        }

        if (nameError != null || priceError != null) {
            update { it.copy(nameError = nameError, sellingPriceError = priceError) }
            return
        }
        requireNotNull(price)

        viewModelScope.launch {
            update { it.copy(isSaving = true, saveError = null) }

            val brand = snapshot.brand.trim().takeIf { it.isNotEmpty() }

            if (!duplicateAcknowledged) {
                val duplicate = repository.checkDuplicate(name, brand, excludeId = productId)
                if (duplicate != null) {
                    duplicateAcknowledged = true
                    update {
                        it.copy(isSaving = false, duplicateWarning = duplicate.displayTitle)
                    }
                    return@launch
                }
            }

            val product = Product(
                id = productId,
                name = name,
                brand = brand,
                category = snapshot.category.trim().takeIf { it.isNotEmpty() },
                sellingPrice = price,
                costPrice = snapshot.costPrice.trim().toDoubleOrNull(),
                unit = snapshot.unit,
                quantityValue = snapshot.quantityValue.trim().toDoubleOrNull()?.takeIf { it > 0 },
                inStock = snapshot.inStock,
                notes = snapshot.notes.trim().takeIf { it.isNotEmpty() },
                createdAt = originalProduct?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            runCatching {
                if (productId == 0L) repository.insert(product) else repository.update(product)
            }.onSuccess {
                update { it.copy(isSaving = false, savedSuccessfully = true) }
            }.onFailure { error ->
                update { it.copy(isSaving = false, saveError = error.readableMessage()) }
            }
        }
    }

}

/**
 * Keeps a numeric field numeric.
 *
 * `"1,200".toDoubleOrNull()` is null, and a Decimal keyboard on an Indian
 * layout happily offers both `,` and `.` — so a shopkeeper who types the
 * thousands separator out of habit would be told a perfectly ordinary price is
 * invalid. Commas are silently dropped; the first `.` is kept as the decimal
 * point and any later one is ignored.
 */
private fun String.filterPriceInput(): String {
    val kept = StringBuilder()
    var seenDecimalPoint = false
    for (char in this) {
        when {
            char.isDigit() -> kept.append(char)
            char == '.' && !seenDecimalPoint -> {
                seenDecimalPoint = true
                kept.append('.')
            }
            else -> Unit
        }
    }
    return kept.toString()
}
