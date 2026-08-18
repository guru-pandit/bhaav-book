package com.bhaavbook.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductVariant
import com.bhaavbook.app.data.model.ProductWithVariants
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    // -----------------------------------------------------------------------
    // Product writes
    // -----------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<Product>): List<Long>

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Long)

    // -----------------------------------------------------------------------
    // Variant writes
    // -----------------------------------------------------------------------

    @Upsert
    suspend fun upsertVariant(variant: ProductVariant): Long

    @Upsert
    suspend fun upsertVariants(variants: List<ProductVariant>)

    @Delete
    suspend fun deleteVariant(variant: ProductVariant)

    @Query("DELETE FROM product_variants WHERE id = :id")
    suspend fun deleteVariantById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllVariants(variants: List<ProductVariant>): List<Long>

    @Query("DELETE FROM product_variants WHERE productId = :productId")
    suspend fun deleteAllVariantsForProduct(productId: Long)

    /** One-shot (non-Flow) read of a product's current variants — used to merge a CSV import. */
    @Query("SELECT * FROM product_variants WHERE productId = :productId")
    suspend fun getVariantsForProductSnapshot(productId: Long): List<ProductVariant>

    // -----------------------------------------------------------------------
    // Single reads
    // -----------------------------------------------------------------------

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Product?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Product?>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductWithVariants(id: Long): Flow<ProductWithVariants?>

    @Query("SELECT * FROM product_variants WHERE productId = :productId ORDER BY variantLabel ASC")
    fun getVariantsForProduct(productId: Long): Flow<List<ProductVariant>>

    /**
     * Duplicate check: find by brand + name combination, case-insensitively.
     */
    @Query(
        """
        SELECT * FROM products
        WHERE LOWER(name) = LOWER(:name)
          AND ((:brand IS NULL AND brand IS NULL) OR LOWER(brand) = LOWER(:brand))
        LIMIT 1
        """
    )
    suspend fun getByBrandAndName(name: String, brand: String?): Product?

    // -----------------------------------------------------------------------
    // All-product queries — sorted variants
    // Sorting by price now uses the minimum selling price across variants.
    // -----------------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM products ORDER BY LOWER(name) ASC")
    fun getAllByName(): Flow<List<ProductWithVariants>>

    /** Unbranded items sort last. */
    @Transaction
    @Query(
        """
        SELECT * FROM products
        ORDER BY (brand IS NULL OR TRIM(brand) = '') ASC, LOWER(brand) ASC, LOWER(name) ASC
        """
    )
    fun getAllByBrand(): Flow<List<ProductWithVariants>>

    @Transaction
    @Query(
        """
        SELECT p.* FROM products p
        LEFT JOIN (
            SELECT productId, MIN(sellingPrice) AS min_price
            FROM product_variants
            GROUP BY productId
        ) v ON p.id = v.productId
        ORDER BY v.min_price ASC, LOWER(p.name) ASC
        """
    )
    fun getAllByPriceAsc(): Flow<List<ProductWithVariants>>

    @Transaction
    @Query(
        """
        SELECT p.* FROM products p
        LEFT JOIN (
            SELECT productId, MIN(sellingPrice) AS min_price
            FROM product_variants
            GROUP BY productId
        ) v ON p.id = v.productId
        ORDER BY v.min_price DESC, LOWER(p.name) ASC
        """
    )
    fun getAllByPriceDesc(): Flow<List<ProductWithVariants>>

    @Transaction
    @Query("SELECT * FROM products ORDER BY updated_at DESC")
    fun getAllByRecent(): Flow<List<ProductWithVariants>>

    // -----------------------------------------------------------------------
    // FTS search (multi-word, word-order independent)
    // -----------------------------------------------------------------------

    /**
     * Full-text search: [ftsQuery] must already be formatted with prefix wildcards,
     * e.g. "cycle* chandan*". FTS4 matches across name + brand + category.
     */
    @Transaction
    @Query(
        """
        SELECT p.* FROM products p
        INNER JOIN products_fts fts ON p.id = fts.rowid
        WHERE products_fts MATCH :ftsQuery
        ORDER BY LOWER(p.name) ASC
        """
    )
    fun searchFts(ftsQuery: String): Flow<List<ProductWithVariants>>

    /**
     * Fallback LIKE search for very short queries (< 2 chars).
     */
    @Transaction
    @Query(
        """
        SELECT * FROM products
        WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(brand) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(category) LIKE '%' || LOWER(:query) || '%'
        ORDER BY LOWER(name) ASC
        """
    )
    fun searchLike(query: String): Flow<List<ProductWithVariants>>

    // -----------------------------------------------------------------------
    // Distinct lists for filter chips
    // -----------------------------------------------------------------------

    @Query(
        """
        SELECT DISTINCT category FROM products
        WHERE category IS NOT NULL AND TRIM(category) != ''
        ORDER BY LOWER(category) ASC
        """
    )
    fun getAllCategories(): Flow<List<String>>

    @Query(
        """
        SELECT DISTINCT brand FROM products
        WHERE brand IS NOT NULL AND TRIM(brand) != ''
        ORDER BY LOWER(brand) ASC
        """
    )
    fun getAllBrands(): Flow<List<String>>

    // -----------------------------------------------------------------------
    // Count / export
    // -----------------------------------------------------------------------

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM products")
    fun observeCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM products ORDER BY LOWER(name) ASC")
    suspend fun getAllSnapshot(): List<ProductWithVariants>

    // -----------------------------------------------------------------------
    // CSV import — atomic single-transaction commit
    // -----------------------------------------------------------------------

    /**
     * Commits a whole CSV import as one atomic transaction.
     *
     * For each [ProductWithVariants] in the list:
     * - A product with a non-zero id is updated in place (a real SQL UPDATE);
     *   a zero id is inserted fresh. Never routed through the REPLACE-conflict
     *   [insert] for an existing id — REPLACE deletes the row first, which
     *   would cascade-delete every variant before this method gets a chance
     *   to preserve them.
     * - Incoming variants are matched to existing ones by label (case
     *   insensitive) and upserted, keeping the existing row's id/created_at.
     *   Existing variants with a label absent from this batch are left
     *   untouched — a CSV that only lists some of a product's variants (a
     *   partial price update) must not delete the ones it doesn't mention.
     *
     * Either every row lands or none does, so a partial failure cannot leave
     * the price list holding half a spreadsheet.
     */
    @Transaction
    suspend fun applyImport(products: List<ProductWithVariants>) {
        products.forEach { pwv ->
            val productId = if (pwv.product.id != 0L) {
                update(pwv.product)
                pwv.product.id
            } else {
                insert(pwv.product)
            }

            if (pwv.variants.isEmpty()) return@forEach

            val existingByLabel = getVariantsForProductSnapshot(productId)
                .associateBy { it.variantLabel.trim().lowercase() }
            val merged = pwv.variants.map { incoming ->
                val existing = existingByLabel[incoming.variantLabel.trim().lowercase()]
                if (existing != null) {
                    incoming.copy(id = existing.id, productId = productId, createdAt = existing.createdAt)
                } else {
                    incoming.copy(id = 0L, productId = productId)
                }
            }
            upsertVariants(merged)
        }
    }
}
