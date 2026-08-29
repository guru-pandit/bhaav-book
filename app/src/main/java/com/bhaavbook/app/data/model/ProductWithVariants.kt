package com.bhaavbook.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room relation that joins a [Product] with all of its [ProductVariant] rows.
 *
 * Used by the DAO, ViewModel, CSV importer/exporter, and price-sheet UI. A
 * product with no variants is structurally invalid but the empty-list case is
 * handled defensively everywhere it matters.
 */
data class ProductWithVariants(
    @Embedded val product: Product,
    @Relation(
        parentColumn = "id",
        entityColumn = "productId"
    )
    val variants: List<ProductVariant>
) {
    /** Lowest retail selling price across all variants. Null when there are no variants. */
    val minSellingPrice: Double?
        get() = variants.minOfOrNull { it.sellingMin ?: it.sellingPrice }

    /** True if at least one variant is in stock. */
    val isAnyInStock: Boolean
        get() = variants.any { it.inStock }

    /**
     * Price label for the product list row.
     * Single variant → "₹XX" or "₹XX - ₹YY"; multiple variants → "from ₹XX".
     */
    fun priceLabel(currencySymbol: String): String {
        if (variants.isEmpty()) return ""
        if (variants.size == 1) {
            val v = variants.first()
            return com.bhaavbook.app.format.formatPriceRange(v.sellingMin, v.sellingPrice, currencySymbol)
        }
        val min = minSellingPrice ?: return ""
        val formatted = if (min == min.toLong().toDouble()) {
            "${min.toLong()}"
        } else {
            "$min"
        }
        return "from $currencySymbol$formatted"
    }
}
