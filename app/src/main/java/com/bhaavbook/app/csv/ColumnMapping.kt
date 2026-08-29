package com.bhaavbook.app.csv

/**
 * Maps a CSV column to a product / variant field in the app.
 * [csvHeaders] lists all header spellings that auto-guess to this field.
 */
enum class AppField(val displayName: String, val csvHeaders: List<String>) {
    NAME(
        "Name *",
        listOf("name", "product", "product name", "item", "item name", "product_name")
    ),
    BRAND_SLUG(
        "Brand Slug",
        listOf("brand_slug", "brand slug", "brand_id")
    ),
    BRAND(
        "Brand Name",
        listOf("brand", "brand name", "company", "manufacturer", "make", "brand_name")
    ),
    CATEGORY_SLUG(
        "Category Slug",
        listOf("category_slug", "category slug", "cat_slug", "category_id")
    ),
    CATEGORY(
        "Category Name",
        listOf("category", "cat", "type", "group", "department")
    ),
    VARIANT_LABEL(
        "Variant Label",
        listOf(
            "variant_label", "variant label", "variant", "size", "pack size",
            "pack_size", "unit", "weight", "volume", "quantity_value"
        )
    ),
    SELLING_PRICE(
        "Selling Price (Max) *",
        listOf(
            "selling_price", "selling price", "price", "mrp", "rate",
            "sale price", "sp", "sell price", "retail price", "selling_max", "selling price max"
        )
    ),
    SELLING_MIN(
        "Selling Min Price",
        listOf(
            "selling_min", "selling min", "selling_price_min", "selling price min",
            "sp_min", "min_selling_price", "min selling price"
        )
    ),
    WHOLESALE_PRICE(
        "Wholesale Price (Max)",
        listOf(
            "wholesale_price", "wholesale price", "wholesale", "trade_price",
            "trade price", "wp", "bulk_price", "wholesale_max", "wholesale price max"
        )
    ),
    WHOLESALE_MIN(
        "Wholesale Min Price",
        listOf(
            "wholesale_min", "wholesale min", "wholesale_price_min", "wholesale price min",
            "wp_min", "min_wholesale_price", "min wholesale price"
        )
    ),
    VARIANT_IN_STOCK(
        "In Stock",
        listOf(
            "variant_in_stock", "variant in stock", "in_stock", "in stock",
            "stock", "available", "instock", "availability"
        )
    ),
    NOTES(
        "Notes",
        listOf("notes", "note", "description", "desc", "remarks", "comment")
    ),
    IGNORE("— Ignore —", emptyList());

    companion object {
        /** Auto-guess AppField from a CSV header string. Returns IGNORE if no match. */
        fun guessFromHeader(header: String): AppField {
            val norm = header.trim().lowercase()
            // Exact match first
            return entries.firstOrNull { field ->
                field != IGNORE && field.csvHeaders.any { it == norm }
            }
            // Partial / contains match as fallback
                ?: entries.firstOrNull { field ->
                    field != IGNORE && field.csvHeaders.any { norm.contains(it) || it.contains(norm) }
                }
                ?: IGNORE
        }
    }
}

/**
 * The user's confirmed mapping: CSV column index → [AppField].
 */
data class ColumnMapping(
    val csvHeaders: List<String>,
    /** key = 0-based column index, value = the AppField it maps to */
    val mappings: Map<Int, AppField>
) {
    fun fieldForIndex(index: Int): AppField = mappings[index] ?: AppField.IGNORE

    companion object {
        /** Builds an initial auto-guessed mapping from parsed headers. */
        fun autoGuess(headers: List<String>): ColumnMapping {
            val mappings = headers.mapIndexed { idx, header ->
                idx to AppField.guessFromHeader(header)
            }.toMap()
            return ColumnMapping(headers, mappings)
        }
    }
}
