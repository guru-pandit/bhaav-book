package com.bhaavbook.app.csv

/**
 * Maps a CSV column to a product field in the app.
 * [csvHeaders] lists all header spellings that auto-guess to this field.
 */
enum class AppField(val displayName: String, val csvHeaders: List<String>) {
    NAME(
        "Name *",
        listOf("name", "product", "product name", "item", "item name", "product_name")
    ),
    BRAND(
        "Brand",
        listOf("brand", "brand name", "company", "manufacturer", "make", "brand_name")
    ),
    CATEGORY(
        "Category",
        listOf("category", "cat", "type", "group", "department")
    ),
    SELLING_PRICE(
        "Selling Price *",
        listOf(
            "selling_price", "selling price", "price", "mrp", "rate",
            "sale price", "sp", "sell price", "retail price"
        )
    ),
    COST_PRICE(
        "Cost Price",
        listOf(
            "cost_price", "cost price", "purchase price", "buy price", "cp",
            "cost", "purchase"
        )
    ),
    UNIT(
        "Unit",
        listOf("unit", "uom", "measure", "unit of measure")
    ),
    QUANTITY_VALUE(
        "Quantity / Pack Size",
        listOf(
            "quantity_value", "quantity value", "qty value", "pack size",
            "size", "weight", "volume", "quantity"
        )
    ),
    IN_STOCK(
        "In Stock",
        listOf("in_stock", "in stock", "stock", "available", "instock", "availability")
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
