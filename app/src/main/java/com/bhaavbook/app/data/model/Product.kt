package com.bhaavbook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Parent product entity — name, brand, category, and notes only.
 * All price / unit / stock data now lives on [ProductVariant].
 */
@Entity(
    tableName = "products",
    indices = [
        Index(value = ["name"]),
        Index(value = ["brand"]),
        Index(value = ["category"]),
        Index(value = ["updated_at"])
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Product name, e.g. "Agarbatti Chandan". Required. */
    @ColumnInfo(name = "name")
    val name: String,

    /** Brand display name, e.g. "Cycle", "Mangaldeep". Optional. */
    @ColumnInfo(name = "brand")
    val brand: String? = null,

    /** Category display name. Optional. */
    @ColumnInfo(name = "category")
    val category: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Combined display title: "Cycle — Agarbatti Chandan".
     * Falls back to name alone when brand is null.
     */
    val displayTitle: String
        get() = if (!brand.isNullOrBlank()) "$brand — $name" else name
}
