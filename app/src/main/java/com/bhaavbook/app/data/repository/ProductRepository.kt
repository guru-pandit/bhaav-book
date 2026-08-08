package com.bhaavbook.app.data.repository

import com.bhaavbook.app.data.db.ProductDao
import com.bhaavbook.app.data.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

data class DuplicateCheckResult(
    val existing: Product? = null
)

@Singleton
class ProductRepository @Inject constructor(
    private val dao: ProductDao
) {

    // -----------------------------------------------------------------------
    // Search & listing
    // -----------------------------------------------------------------------

    /**
     * Returns a Flow of products matching [query], sorted by [sortOrder],
     * post-filtered by [filter].
     *
     * Search strategy:
     * - Blank query → sorted full-table query (fast, no FTS overhead)
     * - 1 char      → LIKE fallback (FTS min token = 3 chars by default)
     * - 2+ chars    → FTS4 with all tokens ANDed and prefix wildcards,
     *                 enabling word-order independent search:
     *                 "cycle chandan" → "cycle* chandan*"
     *                 which matches "Cycle — Agarbatti Chandan"
     */
    fun getProducts(
        query: String,
        sortOrder: SortOrder,
        filter: ProductFilter
    ): Flow<List<Product>> {
        val baseFlow: Flow<List<Product>> = when {
            query.isBlank() -> sortedFlow(sortOrder)
            query.length < 2 -> dao.searchLike(query)
            else -> dao.searchFts(buildFtsQuery(query))
                .catch { emit(dao.searchLike(query)) } // fallback on FTS error
        }

        return baseFlow.mapFilter(filter)
    }

    private fun Flow<List<Product>>.mapFilter(filter: ProductFilter): Flow<List<Product>> =
        kotlinx.coroutines.flow.map(this) { list ->
            when (filter) {
                is ProductFilter.None -> list
                is ProductFilter.ByCategory -> list.filter {
                    it.category.equals(filter.category, ignoreCase = true)
                }
                is ProductFilter.ByBrand -> list.filter {
                    it.brand.equals(filter.brand, ignoreCase = true)
                }
                is ProductFilter.InStockOnly -> list.filter { it.inStock }
            }
        }

    private fun sortedFlow(sortOrder: SortOrder): Flow<List<Product>> = when (sortOrder) {
        SortOrder.NAME_ASC -> dao.getAllByName()
        SortOrder.BRAND_ASC -> dao.getAllByBrand()
        SortOrder.PRICE_ASC -> dao.getAllByPriceAsc()
        SortOrder.PRICE_DESC -> dao.getAllByPriceDesc()
        SortOrder.RECENTLY_UPDATED -> dao.getAllByRecent()
    }

    /**
     * Converts a raw search query into an FTS4 MATCH expression.
     *
     * Each whitespace-separated token gets a '*' suffix (prefix search).
     * Special FTS4 characters are stripped to avoid syntax errors.
     *
     * "chandan cycle" → "chandan* cycle*"
     * → FTS4 AND-matches both tokens across name/brand/category
     * → finds "Cycle — Agarbatti Chandan"
     */
    fun buildFtsQuery(raw: String): String {
        val cleaned = raw.replace(Regex("""["^()\[\]{}|\\]"""), " ").trim()
        return cleaned.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "${it}*" }
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
        dao.getByBrandAndName(name, brand)?.takeIf { it.id != excludeId }

    // -----------------------------------------------------------------------
    // Dropdowns
    // -----------------------------------------------------------------------

    fun getAllCategories(): Flow<List<String>> = dao.getAllCategories()

    fun getAllBrands(): Flow<List<String>> = dao.getAllBrands()

    // -----------------------------------------------------------------------
    // Bulk import
    // -----------------------------------------------------------------------

    suspend fun bulkInsert(products: List<Product>, batchSize: Int = 100) {
        products.chunked(batchSize).forEach { dao.insertAll(it) }
    }

    suspend fun bulkUpdate(products: List<Product>) {
        products.forEach { dao.update(it) }
    }

    // -----------------------------------------------------------------------
    // Export
    // -----------------------------------------------------------------------

    suspend fun getAllSnapshot(): List<Product> = dao.getAllSnapshot()

    suspend fun count(): Int = dao.count()
}
