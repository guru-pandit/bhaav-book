package com.bhaavbook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single size / price variant of a [Product].
 *
 * A product like "Cycle Agarbatti" has variants "100 g", "250 g", "1 kg" each
 * with their own retail price, optional wholesale price, optional cost price,
 * and an independent in-stock flag.
 *
 * The combination (productId, variantLabel) is unique — you cannot have two
 * "100 g" variants on the same product.
 */
@Entity(
    tableName = "product_variants",
    foreignKeys = [ForeignKey(
        entity = Product::class,
        parentColumns = ["id"],
        childColumns = ["productId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("productId"),
        Index(value = ["productId", "variantLabel"], unique = true)
    ]
)
data class ProductVariant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val productId: Long,

    /** Size label shown in the price sheet, e.g. "100 g", "Single", "Box of 12". */
    val variantLabel: String,

    /** Retail selling price (max) — always shown to the customer. */
    val sellingPrice: Double,

    /** Retail selling minimum price — optional lower bound of selling price. */
    @ColumnInfo(name = "selling_min")
    val sellingMin: Double? = null,

    /** Wholesale price (max) — shown only when Settings › Show wholesale price is ON. */
    val wholesalePrice: Double? = null,

    /** Wholesale minimum price — optional lower bound of wholesale price. */
    @ColumnInfo(name = "wholesale_min")
    val wholesaleMin: Double? = null,

    /** Purchase cost — shown only when Settings › Show cost price is ON. */
    val costPrice: Double? = null,

    val inStock: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
