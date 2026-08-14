package com.bhaavbook.app.data.repository

import com.bhaavbook.app.data.db.CategoryDao
import com.bhaavbook.app.data.model.Category
import com.bhaavbook.app.data.model.generateSlug
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao
) {

    fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()

    suspend fun getBySlug(slug: String): Category? = dao.getBySlug(slug)

    suspend fun getByName(name: String): Category? = dao.getByName(name)

    /**
     * Resolves a category from a CSV row using the same slug-first logic as
     * [BrandRepository.resolveFromCsv]. Returns a [CategoryResolution].
     */
    suspend fun resolveFromCsv(slug: String?, name: String?): CategoryResolution {
        val slugTrimmed = slug?.trim().orEmpty()
        val nameTrimmed = name?.trim().orEmpty()

        if (slugTrimmed.isEmpty() && nameTrimmed.isEmpty()) return CategoryResolution(null, null)

        val effectiveSlug = if (slugTrimmed.isNotEmpty()) slugTrimmed
        else generateSlug(nameTrimmed)

        val existing = dao.getBySlug(effectiveSlug)
        if (existing != null) {
            val warning = if (nameTrimmed.isNotEmpty() &&
                !existing.name.equals(nameTrimmed, ignoreCase = true)
            ) {
                "category slug mismatch: slug '$effectiveSlug' already mapped to " +
                    "'${existing.name}', ignoring supplied name '$nameTrimmed'"
            } else null
            return CategoryResolution(existing, warning)
        }

        val displayName = nameTrimmed.ifEmpty { effectiveSlug }
        val newCategory = Category(name = displayName, slug = effectiveSlug)
        dao.upsert(newCategory)
        val saved = dao.getBySlug(effectiveSlug)
            ?: Category(name = displayName, slug = effectiveSlug)
        return CategoryResolution(saved, null)
    }

    /**
     * Saves a new category with automatic slug generation and collision avoidance.
     */
    suspend fun save(name: String, customSlug: String? = null): Category {
        val baseSlug = customSlug?.trim() ?: generateSlug(name)
        val uniqueSlug = findUniqueSlug(baseSlug)
        val category = Category(name = name.trim(), slug = uniqueSlug)
        dao.upsert(category)
        return dao.getBySlug(uniqueSlug) ?: category
    }

    suspend fun update(category: Category) = dao.upsert(category)

    suspend fun delete(category: Category) = dao.delete(category)

    suspend fun findUniqueSlug(base: String): String {
        val existing = dao.getSlugsWithPrefix(base).map { it.slug }.toSet()
        if (base !in existing) return base
        var counter = 2
        while ("$base-$counter" in existing) counter++
        return "$base-$counter"
    }
}

data class CategoryResolution(
    val category: Category?,
    val warning: String?
)
