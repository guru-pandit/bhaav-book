package com.bhaavbook.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bhaavbook.app.data.model.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    // -----------------------------------------------------------------------
    // Writes
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
    // Single reads
    // -----------------------------------------------------------------------

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Product?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Product?>

    /**
     * Duplicate check: find by brand + name combination.
     * Both matched case-insensitively.
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
    // -----------------------------------------------------------------------

    @Query("SELECT * FROM products ORDER BY LOWER(name) ASC")
    fun getAllByName(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY LOWER(brand) ASC, LOWER(name) ASC")
    fun getAllByBrand(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY selling_price ASC, LOWER(name) ASC")
    fun getAllByPriceAsc(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY selling_price DESC, LOWER(name) ASC")
    fun getAllByPriceDesc(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY updated_at DESC")
    fun getAllByRecent(): Flow<List<Product>>

    // -----------------------------------------------------------------------
    // FTS search (multi-word, word-order independent)
    // -----------------------------------------------------------------------

    /**
     * Full-text search: query must already be formatted with prefix wildcards,
     * e.g. "cycle* chandan*". FTS4 matches all tokens (AND logic) across
     * name + brand + category regardless of word order.
     */
    @Query(
        """
        SELECT p.* FROM products p
        INNER JOIN products_fts fts ON p.id = fts.rowid
        WHERE products_fts MATCH :ftsQuery
        ORDER BY LOWER(p.name) ASC
        """
    )
    fun searchFts(ftsQuery: String): Flow<List<Product>>

    /**
     * Fallback LIKE search for very short queries (< 2 chars) where FTS
     * minimum token length would return nothing.
     */
    @Query(
        """
        SELECT * FROM products
        WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(brand) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(category) LIKE '%' || LOWER(:query) || '%'
        ORDER BY LOWER(name) ASC
        """
    )
    fun searchLike(query: String): Flow<List<Product>>

    // -----------------------------------------------------------------------
    // Distinct lists for filter chips and dropdowns
    // -----------------------------------------------------------------------

    @Query(
        "SELECT DISTINCT category FROM products WHERE category IS NOT NULL ORDER BY category ASC"
    )
    fun getAllCategories(): Flow<List<String>>

    @Query(
        "SELECT DISTINCT brand FROM products WHERE brand IS NOT NULL ORDER BY brand ASC"
    )
    fun getAllBrands(): Flow<List<String>>

    // -----------------------------------------------------------------------
    // Count / duplicate helpers
    // -----------------------------------------------------------------------

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Query(
        """
        SELECT COUNT(*) FROM products
        WHERE LOWER(name) = LOWER(:name)
          AND ((:brand IS NULL AND brand IS NULL) OR LOWER(brand) = LOWER(:brand))
          AND id != :excludeId
        """
    )
    suspend fun countByBrandAndName(name: String, brand: String?, excludeId: Long = 0L): Int

    // -----------------------------------------------------------------------
    // Export / snapshot
    // -----------------------------------------------------------------------

    @Query("SELECT * FROM products ORDER BY LOWER(name) ASC")
    suspend fun getAllSnapshot(): List<Product>

    // -----------------------------------------------------------------------
    // Batch upsert for CSV import
    // -----------------------------------------------------------------------

    @Transaction
    suspend fun upsertBatch(products: List<Product>) {
        insertAll(products)
    }
}
