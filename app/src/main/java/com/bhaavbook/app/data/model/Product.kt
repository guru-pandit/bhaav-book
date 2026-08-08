package com.bhaavbook.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Default category list for pooja / religious materials shops. */
val DEFAULT_CATEGORIES = listOf(
    "Agarbatti & Dhoop",
    "Camphor & Wicks",
    "Kumkum & Haldi",
    "Diya & Lamps",
    "Oil & Ghee",
    "Puja Thread & Kalava",
    "Havan Samagri",
    "Idols & Frames",
    "Puja Thali & Utensils",
    "Coconut & Dry Fruits",
    "Flowers & Garlands",
    "Books & Calendars",
    "Other"
)

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

    /** Brand name, e.g. "Cycle", "Mangaldeep". Optional but highly recommended. */
    @ColumnInfo(name = "brand")
    val brand: String? = null,

    /** Category from DEFAULT_CATEGORIES or user-added. */
    @ColumnInfo(name = "category")
    val category: String? = null,

    /** The price shown to the customer. Required. */
    @ColumnInfo(name = "selling_price")
    val sellingPrice: Double,

    /** Purchase cost — only visible when Settings "Show cost price" is ON. */
    @ColumnInfo(name = "cost_price")
    val costPrice: Double? = null,

    @ColumnInfo(name = "unit")
    val unit: ProductUnit = ProductUnit.PIECE,

    /** Pack size, e.g. 100 with unit GRAM = "100 g pack". */
    @ColumnInfo(name = "quantity_value")
    val quantityValue: Double? = null,

    /** Simple in-stock flag. No counts. Default true. */
    @ColumnInfo(name = "in_stock")
    val inStock: Boolean = true,

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

    /**
     * Price + quantity string shown in rows and the big price sheet.
     * Examples: "₹45 / 100 g", "₹180 / 500 ml", "₹25 / pc"
     */
    fun priceDisplayString(currencySymbol: String = "₹"): String {
        val price = "$currencySymbol${sellingPrice.formatPrice()}"
        val unitPart = if (quantityValue != null && quantityValue > 0) {
            "/ ${quantityValue.formatQty()} ${unit.shortLabel}"
        } else {
            "/ ${unit.shortLabel}"
        }
        return "$price $unitPart"
    }
}

// ---------------------------------------------------------------------------
// Private formatting helpers
// ---------------------------------------------------------------------------

private fun Double.formatPrice(): String =
    if (this == kotlin.math.floor(this) && this < 1_000_000)
        this.toLong().toString()
    else "%.2f".format(this)

private fun Double.formatQty(): String =
    if (this == kotlin.math.floor(this)) this.toLong().toString()
    else "%.1f".format(this)
