package com.bhaavbook.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.bhaavbook.app.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): Category?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): Category?

    /**
     * Returns all categories whose slug starts with [prefix].
     * Used by the slug collision-avoidance loop.
     */
    @Query("SELECT * FROM categories WHERE slug LIKE :prefix || '%' ORDER BY slug ASC")
    suspend fun getSlugsWithPrefix(prefix: String): List<Category>

    @Upsert
    suspend fun upsert(category: Category)

    @Delete
    suspend fun delete(category: Category)
}
