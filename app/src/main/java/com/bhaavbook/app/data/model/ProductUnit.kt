package com.bhaavbook.app.data.model

/**
 * Measurement unit enum for pooja / retail items.
 * Stored as String via TypeConverter.
 */
enum class ProductUnit(val displayName: String, val shortLabel: String) {
    GRAM("Gram", "g"),
    KG("Kilogram", "kg"),
    ML("Millilitre", "ml"),
    LITRE("Litre", "L"),
    PIECE("Piece", "pc"),
    PACKET("Packet", "pkt"),
    BOX("Box", "box"),
    BUNDLE("Bundle", "bundle"),
    PAIR("Pair", "pair"),
    DOZEN("Dozen", "dz"),
    METRE("Metre", "m");

    companion object {
        /** Parses unit from CSV or user input — case insensitive, falls back to PIECE. */
        fun fromString(value: String?): ProductUnit {
            if (value.isNullOrBlank()) return PIECE
            val v = value.trim()
            return entries.firstOrNull {
                it.name.equals(v, ignoreCase = true) ||
                    it.displayName.equals(v, ignoreCase = true) ||
                    it.shortLabel.equals(v, ignoreCase = true)
            } ?: PIECE
        }
    }
}
