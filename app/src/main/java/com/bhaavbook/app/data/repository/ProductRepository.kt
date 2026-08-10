package com.bhaavbook.app.data.repository

import com.bhaavbook.app.data.db.ProductDao
import com.bhaavbook.app.data.model.Product
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
     * Returns a Flow of products matching [query], filtered by [filter] and
     * ordered by [sortOrder].
     *
     * Search strategy:
     * - Blank query → sorted full-table query, no FTS overhead.
     * - 1 char, or a query with no usable tokens → `LIKE %q%`.
     * - 2+ chars → FTS4, every token prefix-matched and ANDed, so word order
     *   doesn't matter: `"cycle chandan"` finds `"Cycle — Agarbatti Chandan"`.
     *   If FTS throws (a stray operator survived cleaning, corrupt index),
     *   the flow degrades to LIKE rather than showing the user an error.
     *
     * Search hits are re-ordered by relevance (see [relevanceRank]) so the
     * item the shopkeeper is typing towards lands first. An explicitly chosen
     * sort order other than the default [SortOrder.NAME_ASC] always wins.
     */
    fun getProducts(
        query: String,
        sortOrder: SortOrder,
        filter: ProductFilter
    ): Flow<List<Product>> {
        val trimmed = query.trim()
        val ftsQuery = buildFtsQuery(trimmed)

        val baseFlow: Flow<List<Product>> = when {
            trimmed.isEmpty() -> sortedFlow(sortOrder)
            trimmed.length < 2 || ftsQuery.isEmpty() -> dao.searchLike(trimmed)
            else -> dao.searchFts(ftsQuery).catch { emitAll(dao.searchLike(trimmed)) }
        }

        return baseFlow.map { list ->
            val filtered = list.filter { filter.matches(it) }
            // The full-table queries are already sorted in SQL; search results
            // come back name-ordered from FTS and have to be ordered here.
            if (trimmed.isEmpty()) filtered else filtered.orderSearchHits(trimmed, sortOrder)
        }
    }

    private fun ProductFilter.matches(product: Product): Boolean = when (this) {
        is ProductFilter.None -> true
        is ProductFilter.InStockOnly -> product.inStock
        is ProductFilter.ByCategory -> product.category.equalsIgnoreCase(category)
        is ProductFilter.ByBrand -> product.brand.equalsIgnoreCase(brand)
    }

    private fun sortedFlow(sortOrder: SortOrder): Flow<List<Product>> = when (sortOrder) {
        SortOrder.NAME_ASC -> dao.getAllByName()
        SortOrder.BRAND_ASC -> dao.getAllByBrand()
        SortOrder.PRICE_ASC -> dao.getAllByPriceAsc()
        SortOrder.PRICE_DESC -> dao.getAllByPriceDesc()
        SortOrder.RECENTLY_UPDATED -> dao.getAllByRecent()
    }

    /**
     * Converts a raw search query into an FTS4 MATCH expression: every token
     * gets a `*` suffix so partial words match, and everything FTS4 treats as
     * an operator is stripped first.
     *
     * `"chandan cycle"` → `"chandan* cycle*"`
     *
     * Returns `""` when nothing searchable is left (e.g. the user typed only
     * punctuation) — callers must treat that as "don't run an FTS query",
     * because `MATCH ''` is an FTS4 syntax error.
     */
    fun buildFtsQuery(raw: String): String {
        val cleaned = raw.replace(FTS_OPERATORS, " ").trim()
        if (cleaned.isEmpty()) return ""
        return cleaned.split(WHITESPACE)
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
    }

    /**
     * Orders search hits.
     *
     * With the default sort, that means relevance: a name that starts with what
     * was typed beats one that merely contains it, which beats a hit that only
     * came from the brand or category column. When the user has actually picked
     * a sort order, that order is honoured instead — the point of choosing
     * "price high to low" is to see the most expensive match first.
     */
    private fun List<Product>.orderSearchHits(
        query: String,
        sortOrder: SortOrder
    ): List<Product> {
        val byName = compareBy<Product> { it.name.lowercase() }
        return when (sortOrder) {
            SortOrder.NAME_ASC -> {
                val needle = query.lowercase()
                sortedWith(compareBy<Product> { it.relevanceRank(needle) }.then(byName))
            }
            SortOrder.BRAND_ASC -> sortedWith(
                compareBy<Product> { it.brand.isNullOrBlank() }
                    .thenBy { it.brand?.lowercase().orEmpty() }
                    .then(byName)
            )
            SortOrder.PRICE_ASC -> sortedWith(compareBy<Product> { it.sellingPrice }.then(byName))
            SortOrder.PRICE_DESC -> sortedWith(
                compareByDescending<Product> { it.sellingPrice }.then(byName)
            )
            SortOrder.RECENTLY_UPDATED -> sortedByDescending { it.updatedAt }
        }
    }

    private fun Product.relevanceRank(needle: String): Int {
        val lowerName = name.lowercase()
        val lowerBrand = brand?.lowercase().orEmpty()
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

    // -----------------------------------------------------------------------
    // Duplicate detection (brand + name)
    // -----------------------------------------------------------------------

    suspend fun checkDuplicate(name: String, brand: String?, excludeId: Long = 0L): Product? =
        dao.getByBrandAndName(name.trim(), brand?.trim())?.takeIf { it.id != excludeId }

    // -----------------------------------------------------------------------
    // Filter chips / autocomplete
    // -----------------------------------------------------------------------

    /**
     * Distinct categories, collapsed case-insensitively so `"Agarbatti"` typed
     * once as `"agarbatti"` does not produce two filter chips.
     */
    fun getAllCategories(): Flow<List<String>> = dao.getAllCategories().map { it.distinctByCase() }

    fun getAllBrands(): Flow<List<String>> = dao.getAllBrands().map { it.distinctByCase() }

    // -----------------------------------------------------------------------
    // Export
    // -----------------------------------------------------------------------

    suspend fun getAllSnapshot(): List<Product> = dao.getAllSnapshot()

    suspend fun count(): Int = dao.count()

    /** Total items in the price list, regardless of any active search or filter. */
    fun observeCount(): Flow<Int> = dao.observeCount()

    private companion object {
        /**
         * Everything FTS4 reads as syntax rather than text: quotes, grouping
         * brackets, the `NEAR`/`OR` escapes, the `-` NOT prefix, the `:`
         * column qualifier and the `*` we add ourselves.
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
