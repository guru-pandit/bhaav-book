package com.bhaavbook.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.bhaavbook.app.data.model.Brand
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {

    @Query("SELECT * FROM brands ORDER BY name ASC")
    fun getAllBrands(): Flow<List<Brand>>

    @Query("SELECT * FROM brands WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): Brand?

    @Query("SELECT * FROM brands WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): Brand?

    /**
     * Returns all brands whose slug starts with [prefix].
     * Used by the slug collision-avoidance loop: if "cycle" exists, tries
     * "cycle-2", "cycle-3", etc. until a free slot is found.
     */
    @Query("SELECT * FROM brands WHERE slug LIKE :prefix || '%' ORDER BY slug ASC")
    suspend fun getSlugsWithPrefix(prefix: String): List<Brand>

    @Upsert
    suspend fun upsert(brand: Brand)

    @Delete
    suspend fun delete(brand: Brand)
}
