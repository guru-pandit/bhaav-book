package com.bhaavbook.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A product category with a slug that is stable across renames.
 *
 * Mirrors the [Brand] design — slug is the CSV canonical key, immutable after
 * creation. Display name can be freely updated without breaking any imported
 * CSV file that references the old name via its slug.
 *
 * Note: deleting a category does NOT cascade-update Product.category strings.
 * Products retain their string value. The list exists for picker convenience,
 * not referential integrity.
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["slug"], unique = true),
        Index(value = ["name"], unique = true)
    ]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Display name, e.g. "Puja Items". */
    val name: String,

    /** Stable lowercase key, e.g. "puja-items". Set on creation; immutable after that. */
    val slug: String,

    @androidx.room.ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
