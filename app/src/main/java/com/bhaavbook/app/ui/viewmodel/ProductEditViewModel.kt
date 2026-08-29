package com.bhaavbook.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.bhaavbook.app.R
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductVariant
import com.bhaavbook.app.data.model.ProductWithVariants
import com.bhaavbook.app.data.repository.ProductRepository
import com.bhaavbook.app.data.settings.SettingsRepository
import com.bhaavbook.app.format.MAX_PRICE
import com.bhaavbook.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State for the inline "add / edit variant" form. */
data class VariantFormState(
    val id: Long = 0L,           // 0 = new; non-zero = editing existing
    val label: String = "",
    val sellingPrice: String = "",
    val sellingMin: String = "",
    val wholesalePrice: String = "",
    val wholesaleMin: String = "",
    val inStock: Boolean = true,
    @StringRes val labelError: Int? = null,
    @StringRes val sellingPriceError: Int? = null,
    val sellingMinError: String? = null,
    val wholesalePriceError: String? = null,
    val wholesaleMinError: String? = null,
    val duplicateLabelError: String? = null
)

data class ProductEditUiState(
    val name: String = "",
    val brand: String = "",
    val category: String = "",
    val notes: String = "",

    /** Current committed variants on this product. */
    val variants: List<ProductVariant> = emptyList(),

    /** Non-null when the "Add/Edit variant" sheet is open. */
    val variantForm: VariantFormState? = null,

    @StringRes val nameError: Int? = null,
    /** Pre-formatted, because it embeds the name of the clashing item. */
    val duplicateWarning: String? = null,

    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val saveError: String? = null,

    val availableCategories: List<String> = emptyList(),
    val availableBrands: List<String> = emptyList(),

    val showWholesalePrice: Boolean = false
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

    /** Variant ids present in the DB when the product was first loaded — used
     * on save to tell "removed in this session" apart from "never existed". */
    private var originalVariantIds: Set<Long> = emptySet()

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
        repository.getAllBrands(),
        settingsRepository.settings
    ) { current, dbCategories, dbBrands, settings ->
        current.copy(
            availableCategories = dbCategories,
            availableBrands = dbBrands,
            showWholesalePrice = settings.showWholesalePrice
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _state.value)

    init {
        if (productId != 0L) {
            viewModelScope.launch { loadProduct(productId) }
        }
    }

    private suspend fun loadProduct(id: Long) {
        repository.getProductWithVariants(id).collect { pwv ->
            if (pwv != null && _state.value.name.isEmpty()) {
                originalProduct = pwv.product
                originalVariantIds = pwv.variants.map { it.id }.toSet()
                _state.value = _state.value.copy(
                    name = pwv.product.name,
                    brand = pwv.product.brand.orEmpty(),
                    category = pwv.product.category.orEmpty(),
                    notes = pwv.product.notes.orEmpty(),
                    variants = pwv.variants,
                    isEditMode = true
                )
            } else if (pwv != null) {
                // Refresh variants if they change underneath us (e.g. after a save)
                _state.value = _state.value.copy(variants = pwv.variants)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Product-level field updates
    // -----------------------------------------------------------------------

    fun onNameChange(value: String) = update {
        duplicateAcknowledged = false
        it.copy(name = value, nameError = null, duplicateWarning = null)
    }

    fun onBrandChange(value: String) = update {
        duplicateAcknowledged = false
        it.copy(brand = value, duplicateWarning = null)
    }

    fun onCategoryChange(value: String) = update { it.copy(category = value) }

    fun onNotesChange(value: String) = update { it.copy(notes = value) }

    fun clearSaveError() = update { it.copy(saveError = null) }

    /**
     * Dismisses the duplicate-product warning without saving. Also resets the
     * acknowledgement so a later edit is re-checked against the DB.
     */
    fun dismissDuplicateWarning() {
        duplicateAcknowledged = false
        update { it.copy(duplicateWarning = null) }
    }

    // -----------------------------------------------------------------------
    // Variant form management
    // -----------------------------------------------------------------------

    /** Opens the variant sheet for a new variant with an optional pre-filled label. */
    fun openAddVariantForm(prefillLabel: String = "") {
        update {
            it.copy(variantForm = VariantFormState(label = prefillLabel))
        }
    }

    /** Opens the variant sheet pre-filled with an existing variant for editing. */
    fun openEditVariantForm(variant: ProductVariant) {
        update {
            it.copy(
                variantForm = VariantFormState(
                    id = variant.id,
                    label = variant.variantLabel,
                    sellingPrice = variant.sellingPrice.toEditableString(),
                    sellingMin = variant.sellingMin?.toEditableString().orEmpty(),
                    wholesalePrice = variant.wholesalePrice?.toEditableString().orEmpty(),
                    wholesaleMin = variant.wholesaleMin?.toEditableString().orEmpty(),
                    inStock = variant.inStock
                )
            )
        }
    }

    fun dismissVariantForm() = update { it.copy(variantForm = null) }

    fun onVariantLabelChange(value: String) = updateForm {
        it.copy(label = value, labelError = null, duplicateLabelError = null)
    }

    fun onVariantSellingPriceChange(value: String) = updateForm {
        it.copy(sellingPrice = value.filterPriceInput(), sellingPriceError = null)
    }

    fun onVariantSellingMinChange(value: String) = updateForm {
        it.copy(sellingMin = value.filterPriceInput(), sellingMinError = null)
    }

    fun onVariantWholesalePriceChange(value: String) = updateForm {
        it.copy(wholesalePrice = value.filterPriceInput(), wholesalePriceError = null)
    }

    fun onVariantWholesaleMinChange(value: String) = updateForm {
        it.copy(wholesaleMin = value.filterPriceInput(), wholesaleMinError = null)
    }

    fun onVariantInStockChange(value: Boolean) = updateForm { it.copy(inStock = value) }

    /** Validates and commits the variant form into the variants list. */
    fun saveVariantForm() {
        val form = _state.value.variantForm ?: return
        val label = form.label.trim()
        val price = form.sellingPrice.trim().toDoubleOrNull()
        val sellingMinVal = form.sellingMin.trim().toDoubleOrNull()
        val wholesalePriceVal = form.wholesalePrice.trim().toDoubleOrNull()
        val wholesaleMinVal = form.wholesaleMin.trim().toDoubleOrNull()

        val labelError = if (label.isEmpty()) R.string.error_name_required else null
        val priceError = when {
            price == null -> R.string.error_price_required
            price <= 0 -> R.string.error_price_negative
            price > MAX_PRICE -> R.string.error_price_too_large
            else -> null
        }

        val sellingMinError = when {
            sellingMinVal != null && price != null && sellingMinVal > price -> "Selling min price cannot be greater than max price"
            sellingMinVal != null && sellingMinVal <= 0 -> "Selling min price must be greater than zero"
            sellingMinVal != null && sellingMinVal > MAX_PRICE -> "Selling min price is too large"
            else -> null
        }

        val wholesalePriceError = when {
            wholesalePriceVal != null && wholesalePriceVal <= 0 -> "Wholesale max price must be greater than zero"
            wholesalePriceVal != null && wholesalePriceVal > MAX_PRICE -> "Wholesale max price is too large"
            else -> null
        }

        val wholesaleMinError = when {
            wholesaleMinVal != null && wholesalePriceVal != null && wholesaleMinVal > wholesalePriceVal -> "Wholesale min price cannot be greater than max price"
            wholesaleMinVal != null && wholesaleMinVal <= 0 -> "Wholesale min price must be greater than zero"
            wholesaleMinVal != null && wholesaleMinVal > MAX_PRICE -> "Wholesale min price is too large"
            else -> null
        }

        // Duplicate label check (within the same product, excluding the variant being edited)
        val existingLabels = _state.value.variants
            .filter { it.id != form.id }
            .map { it.variantLabel.trim().lowercase() }
        val duplicateLabel = if (label.lowercase() in existingLabels) {
            "A variant labelled \"$label\" already exists"
        } else null

        if (labelError != null || priceError != null || sellingMinError != null ||
            wholesalePriceError != null || wholesaleMinError != null || duplicateLabel != null
        ) {
            updateForm {
                it.copy(
                    labelError = labelError,
                    sellingPriceError = priceError,
                    sellingMinError = sellingMinError,
                    wholesalePriceError = wholesalePriceError,
                    wholesaleMinError = wholesaleMinError,
                    duplicateLabelError = duplicateLabel
                )
            }
            return
        }

        requireNotNull(price)

        val now = System.currentTimeMillis()
        val newVariant = ProductVariant(
            id = form.id,
            productId = productId,
            variantLabel = label,
            sellingPrice = price,
            sellingMin = sellingMinVal,
            wholesalePrice = wholesalePriceVal,
            wholesaleMin = wholesaleMinVal,
            inStock = form.inStock,
            createdAt = if (form.id == 0L) now else
                _state.value.variants.find { it.id == form.id }?.createdAt ?: now,
            updatedAt = now
        )

        update { state ->
            val updated = if (form.id == 0L) {
                state.variants + newVariant
            } else {
                state.variants.map { if (it.id == form.id) newVariant else it }
            }
            state.copy(variants = updated, variantForm = null)
        }
    }

    fun deleteVariant(variant: ProductVariant) = update { state ->
        state.copy(variants = state.variants.filter { it.id != variant.id })
    }

    // -----------------------------------------------------------------------
    // Save
    // -----------------------------------------------------------------------

    fun save() {
        val snapshot = _state.value
        if (snapshot.isSaving) return

        val name = snapshot.name.trim()
        val nameError = if (name.isEmpty()) R.string.error_name_required else null

        if (nameError != null) {
            update { it.copy(nameError = nameError) }
            return
        }

        if (snapshot.variants.isEmpty()) {
            update { it.copy(saveError = "Add at least one variant before saving.") }
            return
        }

        viewModelScope.launch {
            update { it.copy(isSaving = true, saveError = null) }

            val brand = snapshot.brand.trim().takeIf { it.isNotEmpty() }

            if (!duplicateAcknowledged) {
                val duplicate = repository.checkDuplicate(name, brand, excludeId = productId)
                if (duplicate != null) {
                    duplicateAcknowledged = true
                    update { it.copy(isSaving = false, duplicateWarning = duplicate.displayTitle) }
                    return@launch
                }
            }

            val now = System.currentTimeMillis()
            val product = Product(
                id = productId,
                name = name,
                brand = brand,
                category = snapshot.category.trim().takeIf { it.isNotEmpty() },
                notes = snapshot.notes.trim().takeIf { it.isNotEmpty() },
                createdAt = originalProduct?.createdAt ?: now,
                updatedAt = now
            )

            runCatching {
                val savedId = if (productId == 0L) repository.insert(product)
                else { repository.update(product); productId }

                // Persist all variant changes: upsert survivors, delete removed ones.
                val currentVariantIds = snapshot.variants.map { it.id }.toSet()
                val removedVariantIds = originalVariantIds - currentVariantIds
                removedVariantIds.forEach { id -> repository.deleteVariantById(id) }

                snapshot.variants.forEach { v ->
                    repository.upsertVariant(v.copy(productId = savedId))
                }
            }.onSuccess {
                update { it.copy(isSaving = false, savedSuccessfully = true) }
            }.onFailure { error ->
                update { it.copy(isSaving = false, saveError = error.readableMessage()) }
            }
        }
    }

    private inline fun update(block: (ProductEditUiState) -> ProductEditUiState) {
        _state.value = block(_state.value)
    }

    private inline fun updateForm(block: (VariantFormState) -> VariantFormState) {
        val form = _state.value.variantForm ?: return
        _state.value = _state.value.copy(variantForm = block(form))
    }
}

private fun Double.toEditableString(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()

private fun String.filterPriceInput(): String {
    val kept = StringBuilder()
    var seenDecimalPoint = false
    for (char in this) {
        when {
            char.isDigit() -> kept.append(char)
            char == '.' && !seenDecimalPoint -> { seenDecimalPoint = true; kept.append('.') }
            else -> Unit
        }
    }
    return kept.toString()
}
