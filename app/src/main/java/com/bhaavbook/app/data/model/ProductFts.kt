package com.bhaavbook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * FTS4 virtual table backed by the [Product] content entity.
 *
 * Room generates insert/update/delete triggers automatically so this table
 * stays in sync with `products` without any manual sync code.
 *
 * Searchable columns: name, brand, category — sufficient for all lookup patterns.
 */
@Fts4(contentEntity = Product::class)
@Entity(tableName = "products_fts")
data class ProductFts(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "brand")
    val brand: String?,

    @ColumnInfo(name = "category")
    val category: String?
)
