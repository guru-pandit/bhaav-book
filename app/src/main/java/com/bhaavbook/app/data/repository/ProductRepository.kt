package com.bhaavbook.app.data.repository

import com.bhaavbook.app.data.db.ProductDao
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductVariant
import com.bhaavbook.app.data.model.ProductWithVariants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class SortOrder {
    NAME_ASC, BRAND_ASC, PRICE_ASC, PRICE_DESC, RECENTLY_UPDATED
}

sealed class ProductFilter {
    data object None : ProductFilter()
    data class ByCategory(val category: String) : ProductFilter()
    data class ByBrand(val brand: String) : ProductFilter()
    data object InStockOnly : ProductFilter()
}

@Singleton
class ProductRepository @Inject constructor(
    private val dao: ProductDao
) {

    // -----------------------------------------------------------------------
    // Search & listing
    // -----------------------------------------------------------------------

    /**
     * Returns a Flow of [ProductWithVariants] matching [query], filtered by
     * [filter] and ordered by [sortOrder].
     *
     * Search strategy:
     * - Blank query → sorted full-table query, no FTS overhead.
     * - 1 char, or a query with no usable tokens → `LIKE %q%`.
     * - 2+ chars → FTS4, every token prefix-matched and ANDed.
     *   If FTS throws (stray operator / corrupt index), degrades to LIKE.
     *
     * In-stock filter: a product matches if ANY of its variants is in stock.
     * Price sort: uses the minimum selling price across all variants.
     */
    fun getProducts(
        query: String,
        sortOrder: SortOrder,
        filter: ProductFilter
    ): Flow<List<ProductWithVariants>> {
        val trimmed = query.trim()
        val ftsQuery = buildFtsQuery(trimmed)

        val baseFlow: Flow<List<ProductWithVariants>> = when {
            trimmed.isEmpty() -> sortedFlow(sortOrder)
            trimmed.length < 2 || ftsQuery.isEmpty() -> dao.searchLike(trimmed)
            else -> dao.searchFts(ftsQuery).catch { emitAll(dao.searchLike(trimmed)) }
        }

        return baseFlow.map { list ->
            val filtered = list.filter { filter.matches(it) }
            if (trimmed.isEmpty()) filtered else filtered.orderSearchHits(trimmed, sortOrder)
        }
    }

    private fun ProductFilter.matches(pwv: ProductWithVariants): Boolean = when (this) {
        is ProductFilter.None -> true
        is ProductFilter.InStockOnly -> pwv.isAnyInStock
        is ProductFilter.ByCategory -> pwv.product.category.equalsIgnoreCase(category)
        is ProductFilter.ByBrand -> pwv.product.brand.equalsIgnoreCase(brand)
    }

    private fun sortedFlow(sortOrder: SortOrder): Flow<List<ProductWithVariants>> = when (sortOrder) {
        SortOrder.NAME_ASC -> dao.getAllByName()
        SortOrder.BRAND_ASC -> dao.getAllByBrand()
        SortOrder.PRICE_ASC -> dao.getAllByPriceAsc()
        SortOrder.PRICE_DESC -> dao.getAllByPriceDesc()
        SortOrder.RECENTLY_UPDATED -> dao.getAllByRecent()
    }

    /**
     * Converts a raw search query into an FTS4 MATCH expression. Every token
     * gets a `*` suffix so partial words match, and FTS4 operators are stripped.
     *
     * Returns `""` when nothing searchable is left — callers must treat that as
     * "don't run an FTS query" because `MATCH ''` is a syntax error.
     */
    fun buildFtsQuery(raw: String): String {
        val cleaned = raw.replace(FTS_OPERATORS, " ").trim()
        if (cleaned.isEmpty()) return ""
        return cleaned.split(WHITESPACE)
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
    }

    private fun List<ProductWithVariants>.orderSearchHits(
        query: String,
        sortOrder: SortOrder
    ): List<ProductWithVariants> {
        val byName = compareBy<ProductWithVariants> { it.product.name.lowercase() }
        return when (sortOrder) {
            SortOrder.NAME_ASC -> {
                val needle = query.lowercase()
                sortedWith(compareBy<ProductWithVariants> { it.relevanceRank(needle) }.then(byName))
            }
            SortOrder.BRAND_ASC -> sortedWith(
                compareBy<ProductWithVariants> { it.product.brand.isNullOrBlank() }
                    .thenBy { it.product.brand?.lowercase().orEmpty() }
                    .then(byName)
            )
            SortOrder.PRICE_ASC -> sortedWith(
                compareBy<ProductWithVariants> { it.minSellingPrice ?: Double.MAX_VALUE }.then(byName)
            )
            SortOrder.PRICE_DESC -> sortedWith(
                compareByDescending<ProductWithVariants> { it.minSellingPrice ?: 0.0 }.then(byName)
            )
            SortOrder.RECENTLY_UPDATED -> sortedByDescending { it.product.updatedAt }
        }
    }

    private fun ProductWithVariants.relevanceRank(needle: String): Int {
        val lowerName = product.name.lowercase()
        val lowerBrand = product.brand?.lowercase().orEmpty()
        return when {
            lowerName == needle -> 0
            lowerName.startsWith(needle) -> 1
            lowerBrand.startsWith(needle) -> 2
            lowerName.split(WHITESPACE).any { it.startsWith(needle) } -> 3
            lowerName.contains(needle) -> 4
            else -> 5
        }
    }

    // -----------------------------------------------------------------------
    // CRUD
    // -----------------------------------------------------------------------

    suspend fun insert(product: Product): Long = dao.insert(product)

    suspend fun update(product: Product) =
        dao.update(product.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(product: Product) = dao.delete(product)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun getById(id: Long): Product? = dao.getById(id)

    fun observeById(id: Long): Flow<Product?> = dao.observeById(id)

    fun getProductWithVariants(id: Long): Flow<ProductWithVariants?> =
        dao.getProductWithVariants(id)

    // -----------------------------------------------------------------------
    // Variant CRUD
    // -----------------------------------------------------------------------

    suspend fun upsertVariant(variant: ProductVariant): Long = dao.upsertVariant(variant)

    suspend fun deleteVariant(variant: ProductVariant) = dao.deleteVariant(variant)

    // -----------------------------------------------------------------------
    // Duplicate detection (brand + name)
    // -----------------------------------------------------------------------

    suspend fun checkDuplicate(name: String, brand: String?, excludeId: Long = 0L): Product? =
        dao.getByBrandAndName(name.trim(), brand?.trim())?.takeIf { it.id != excludeId }

    // -----------------------------------------------------------------------
    // Filter chips / autocomplete
    // -----------------------------------------------------------------------

    fun getAllCategories(): Flow<List<String>> = dao.getAllCategories().map { it.distinctByCase() }

    fun getAllBrands(): Flow<List<String>> = dao.getAllBrands().map { it.distinctByCase() }

    // -----------------------------------------------------------------------
    // Export / count
    // -----------------------------------------------------------------------

    suspend fun getAllSnapshot(): List<ProductWithVariants> = dao.getAllSnapshot()

    suspend fun count(): Int = dao.count()

    fun observeCount(): Flow<Int> = dao.observeCount()

    private companion object {
        /**
         * Everything FTS4 reads as syntax: quotes, grouping brackets, the
         * NEAR/OR escapes, the `-` NOT prefix, the `:` column qualifier and
         * the `*` we add ourselves.
         */
        val FTS_OPERATORS = Regex("""["^()\[\]{}|\\*:\-–—,;]""")
        val WHITESPACE = Regex("\\s+")
    }
}

private fun String?.equalsIgnoreCase(other: String): Boolean =
    this != null && this.trim().equals(other.trim(), ignoreCase = true)

private fun List<String>.distinctByCase(): List<String> =
    filter { it.isNotBlank() }
        .distinctBy { it.trim().lowercase() }
        .sortedBy { it.trim().lowercase() }
