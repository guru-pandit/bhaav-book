package com.bhaavbook.app.csv

import com.bhaavbook.app.data.db.ProductDao
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class DuplicateStrategy {
    /** Leave existing products untouched; skip the row. */
    SKIP,
    /** Update the existing product (match on brand + name). */
    UPDATE,
    /** Always insert as a new row regardless of duplicates. */
    ADD_ANYWAY
}

data class ImportRowError(
    val rowNumber: Int,        // 1-based, counting from first data row
    val rawRow: List<String>,
    val reason: String
)

data class ImportResult(
    val importedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val errors: List<ImportRowError>,
    val totalRows: Int
)

@Singleton
class CsvImporter @Inject constructor(
    private val dao: ProductDao,
    private val parser: CsvParser
) {

    /**
     * Imports [rows] into Room using the given [mapping] and [strategy].
     * Runs entirely on [Dispatchers.IO]; calls [onProgress] every 50 rows.
     */
    suspend fun import(
        rows: List<List<String>>,
        mapping: ColumnMapping,
        strategy: DuplicateStrategy,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }
    ): ImportResult = withContext(Dispatchers.IO) {

        val errors = mutableListOf<ImportRowError>()
        val toInsert = mutableListOf<Product>()
        val toUpdate = mutableListOf<Product>()
        var skippedCount = 0
        val now = System.currentTimeMillis()

        rows.forEachIndexed { index, row ->
            val rowNum = index + 1

            try {
                val fields = extractFields(row, mapping)

                // --- Required fields ---
                val name = fields[AppField.NAME]?.takeIf { it.isNotBlank() }
                    ?: run {
                        errors += ImportRowError(rowNum, row, "name is required but blank")
                        return@forEachIndexed
                    }

                val priceRaw = fields[AppField.SELLING_PRICE]?.takeIf { it.isNotBlank() }
                    ?: run {
                        errors += ImportRowError(rowNum, row, "selling_price is required but blank")
                        return@forEachIndexed
                    }

                val sellingPrice = parser.parsePrice(priceRaw)
                    ?: run {
                        errors += ImportRowError(
                            rowNum, row,
                            "selling_price '${priceRaw}' is not a valid number"
                        )
                        return@forEachIndexed
                    }

                if (sellingPrice < 0) {
                    errors += ImportRowError(rowNum, row, "selling_price cannot be negative")
                    return@forEachIndexed
                }

                // --- Optional fields ---
                val brand = fields[AppField.BRAND]?.takeIf { it.isNotBlank() }
                val category = fields[AppField.CATEGORY]?.takeIf { it.isNotBlank() }
                val costPrice = fields[AppField.COST_PRICE]?.let { parser.parsePrice(it) }
                val unit = ProductUnit.fromString(fields[AppField.UNIT])

                val quantityValue = fields[AppField.QUANTITY_VALUE]?.let { raw ->
                    raw.toDoubleOrNull().also {
                        if (it == null && raw.isNotBlank()) {
                            errors += ImportRowError(
                                rowNum, row,
                                "quantity_value '$raw' is not a number — ignored"
                            )
                        }
                    }
                }

                val inStock = parser.parseInStock(fields[AppField.IN_STOCK] ?: "")
                val notes = fields[AppField.NOTES]?.takeIf { it.isNotBlank() }

                val newProduct = Product(
                    name = name,
                    brand = brand,
                    category = category,
                    sellingPrice = sellingPrice,
                    costPrice = costPrice,
                    unit = unit,
                    quantityValue = quantityValue,
                    inStock = inStock,
                    notes = notes,
                    createdAt = now,
                    updatedAt = now
                )

                // --- Duplicate handling ---
                when (strategy) {
                    DuplicateStrategy.ADD_ANYWAY -> toInsert.add(newProduct)

                    DuplicateStrategy.SKIP -> {
                        val existing = dao.getByBrandAndName(name, brand)
                        if (existing != null) skippedCount++ else toInsert.add(newProduct)
                    }

                    DuplicateStrategy.UPDATE -> {
                        val existing = dao.getByBrandAndName(name, brand)
                        if (existing != null) {
                            toUpdate.add(
                                newProduct.copy(
                                    id = existing.id,
                                    createdAt = existing.createdAt,
                                    updatedAt = now
                                )
                            )
                        } else {
                            toInsert.add(newProduct)
                        }
                    }
                }
            } catch (e: Exception) {
                errors += ImportRowError(rowNum, row, "Unexpected: ${e.message ?: "unknown error"}")
            }

            if (rowNum % 50 == 0) onProgress(rowNum, rows.size)
        }

        // Commit to DB in batches of 100
        toInsert.chunked(100).forEach { dao.insertAll(it) }
        toUpdate.forEach { dao.update(it) }

        onProgress(rows.size, rows.size)

        ImportResult(
            importedCount = toInsert.size,
            updatedCount = toUpdate.size,
            skippedCount = skippedCount,
            errors = errors,
            totalRows = rows.size
        )
    }

    private fun extractFields(row: List<String>, mapping: ColumnMapping): Map<AppField, String> {
        val result = mutableMapOf<AppField, String>()
        row.forEachIndexed { colIdx, value ->
            val field = mapping.fieldForIndex(colIdx)
            if (field != AppField.IGNORE && (!result.containsKey(field) || result[field].isNullOrBlank())) {
                result[field] = value
            }
        }
        return result
    }
}
