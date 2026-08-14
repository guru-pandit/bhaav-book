package com.bhaavbook.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bhaavbook.app.data.model.Brand
import com.bhaavbook.app.data.model.Category
import com.bhaavbook.app.data.model.generateSlug
import com.bhaavbook.app.data.model.isValidSlug
import com.bhaavbook.app.data.repository.BrandRepository
import com.bhaavbook.app.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ManageTab { BRANDS, CATEGORIES }

data class ItemFormState(
    val id: Long = 0L,
    val name: String = "",
    val slug: String = "",
    val originalSlug: String = "",
    val isCustomSlug: Boolean = false,
    val isEditingSlug: Boolean = false,
    val nameError: String? = null,
    val slugError: String? = null,
    val showSlugWarning: Boolean = false
)

data class ManageBrandsUiState(
    val activeTab: ManageTab = ManageTab.BRANDS,
    val brands: List<Brand> = emptyList(),
    val categories: List<Category> = emptyList(),
    val formState: ItemFormState? = null,
    val itemToDelete: Any? = null, // Brand or Category
    val message: String? = null
)

@HiltViewModel
class ManageBrandsViewModel @Inject constructor(
    private val brandRepository: BrandRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageBrandsUiState())

    val uiState: StateFlow<ManageBrandsUiState> = combine(
        _uiState,
        brandRepository.getAllBrands(),
        categoryRepository.getAllCategories()
    ) { current, dbBrands, dbCategories ->
        current.copy(brands = dbBrands, categories = dbCategories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageBrandsUiState())

    fun selectTab(tab: ManageTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab, formState = null)
    }

    // -----------------------------------------------------------------------
    // Add / Edit Form Actions
    // -----------------------------------------------------------------------

    fun openAddForm() {
        _uiState.value = _uiState.value.copy(formState = ItemFormState())
    }

    fun openEditBrandForm(brand: Brand) {
        _uiState.value = _uiState.value.copy(
            formState = ItemFormState(
                id = brand.id,
                name = brand.name,
                slug = brand.slug,
                originalSlug = brand.slug
            )
        )
    }

    fun openEditCategoryForm(category: Category) {
        _uiState.value = _uiState.value.copy(
            formState = ItemFormState(
                id = category.id,
                name = category.name,
                slug = category.slug,
                originalSlug = category.slug
            )
        )
    }

    fun dismissForm() {
        _uiState.value = _uiState.value.copy(formState = null)
    }

    fun onNameChange(value: String) {
        val form = _uiState.value.formState ?: return
        val newSlug = if (form.id == 0L && !form.isCustomSlug) generateSlug(value) else form.slug
        _uiState.value = _uiState.value.copy(
            formState = form.copy(name = value, slug = newSlug, nameError = null)
        )
    }

    fun onSlugChange(value: String) {
        val form = _uiState.value.formState ?: return
        _uiState.value = _uiState.value.copy(
            formState = form.copy(slug = value, isCustomSlug = true, slugError = null)
        )
    }

    fun toggleEditSlug() {
        val form = _uiState.value.formState ?: return
        _uiState.value = _uiState.value.copy(
            formState = form.copy(isEditingSlug = !form.isEditingSlug)
        )
    }

    fun saveForm() {
        val form = _uiState.value.formState ?: return
        val name = form.name.trim()
        val slug = form.slug.trim()

        if (name.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                formState = form.copy(nameError = "Name is required")
            )
            return
        }

        if (slug.isNotEmpty() && !isValidSlug(slug)) {
            _uiState.value = _uiState.value.copy(
                formState = form.copy(slugError = "Invalid slug (use lowercase a-z, 0-9, hyphens)")
            )
            return
        }

        // Warn if changing existing slug
        if (form.id != 0L && form.originalSlug.isNotEmpty() &&
            slug != form.originalSlug && !form.showSlugWarning
        ) {
            _uiState.value = _uiState.value.copy(
                formState = form.copy(showSlugWarning = true)
            )
            return
        }

        viewModelScope.launch {
            val currentTab = _uiState.value.activeTab
            if (currentTab == ManageTab.BRANDS) {
                if (form.id == 0L) {
                    brandRepository.save(name, customSlug = slug.ifEmpty { null })
                } else {
                    brandRepository.update(Brand(id = form.id, name = name, slug = slug))
                }
            } else {
                if (form.id == 0L) {
                    categoryRepository.save(name, customSlug = slug.ifEmpty { null })
                } else {
                    categoryRepository.update(Category(id = form.id, name = name, slug = slug))
                }
            }
            _uiState.value = _uiState.value.copy(formState = null)
        }
    }

    // -----------------------------------------------------------------------
    // Delete Actions
    // -----------------------------------------------------------------------

    fun requestDelete(item: Any) {
        _uiState.value = _uiState.value.copy(itemToDelete = item)
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(itemToDelete = null)
    }

    fun confirmDelete() {
        val item = _uiState.value.itemToDelete ?: return
        viewModelScope.launch {
            when (item) {
                is Brand -> brandRepository.delete(item)
                is Category -> categoryRepository.delete(item)
            }
            _uiState.value = _uiState.value.copy(itemToDelete = null)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
