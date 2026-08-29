package com.bhaavbook.app.csv

import com.bhaavbook.app.data.db.ProductDao
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductVariant
import com.bhaavbook.app.data.model.ProductWithVariants
import com.bhaavbook.app.data.repository.BrandRepository
import com.bhaavbook.app.data.repository.CategoryRepository
import com.bhaavbook.app.format.MAX_PRICE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class DuplicateStrategy {
    /** Leave the existing item untouched; skip the row. */
    SKIP,
    /** Overwrite the existing item, matched on brand + name. */
    UPDATE,
    /** Insert regardless, even if it duplicates something. */
    ADD_ANYWAY
}

data class ImportRowError(
    /** 1-based, counting from the first data row — matches what the user sees in Excel. */
    val rowNumber: Int,
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
    private val brandRepository: BrandRepository,
    private val categoryRepository: CategoryRepository,
    private val parser: CsvParser
) {

    /**
     * Turns [rows] into products with variants using [mapping] and commits them under
     * [strategy].
     */
    suspend fun import(
        rows: List<List<String>>,
        mapping: ColumnMapping,
        strategy: DuplicateStrategy,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }
    ): ImportResult = withContext(Dispatchers.IO) {

        val errors = mutableListOf<ImportRowError>()
        var skippedCount = 0
        val now = System.currentTimeMillis()

        // Helper data structure to aggregate rows into products with variants
        data class ParsedVariant(
            val label: String,
            val sellingPrice: Double,
            val sellingMin: Double?,
            val wholesalePrice: Double?,
            val wholesaleMin: Double?,
            val inStock: Boolean
        )

        data class ParsedProduct(
            val name: String,
            val brand: String?,
            val category: String?,
            val notes: String?,
            val variants: MutableList<ParsedVariant>
        )

        val productMap = LinkedHashMap<String, ParsedProduct>()

        rows.forEachIndexed { index, row ->
            val rowNumber = index + 1
            val fields = extractFields(row, mapping)

            val name = fields[AppField.NAME]?.takeIf { it.isNotBlank() }
            if (name == null) {
                errors += ImportRowError(rowNumber, row, "No item name in this row")
                return@forEachIndexed
            }

            val priceRaw = fields[AppField.SELLING_PRICE]?.takeIf { it.isNotBlank() }
            if (priceRaw == null) {
                errors += ImportRowError(rowNumber, row, "No selling price for \"$name\"")
                return@forEachIndexed
            }

            val sellingPrice = parser.parsePrice(priceRaw)
            if (sellingPrice == null) {
                errors += ImportRowError(rowNumber, row, "Price \"$priceRaw\" is not a number")
                return@forEachIndexed
            }
            if (sellingPrice < 0) {
                errors += ImportRowError(rowNumber, row, "Price \"$priceRaw\" is negative")
                return@forEachIndexed
            }
            if (sellingPrice > MAX_PRICE) {
                errors += ImportRowError(rowNumber, row, "Price \"$priceRaw\" is too large")
                return@forEachIndexed
            }

            val sellingMin = fields[AppField.SELLING_MIN]?.takeIf { it.isNotBlank() }
                ?.let { parser.parsePrice(it) }
            if (sellingMin != null && sellingMin <= 0) {
                errors += ImportRowError(rowNumber, row, "Selling min price ($sellingMin) must be greater than zero")
                return@forEachIndexed
            }
            if (sellingMin != null && sellingMin > MAX_PRICE) {
                errors += ImportRowError(rowNumber, row, "Selling min price ($sellingMin) is too large")
                return@forEachIndexed
            }
            if (sellingMin != null && sellingMin > sellingPrice) {
                errors += ImportRowError(rowNumber, row, "Selling min price ($sellingMin) cannot be greater than selling price ($sellingPrice)")
                return@forEachIndexed
            }

            // Slug-first brand & category resolution
            val brandSlug = fields[AppField.BRAND_SLUG]
            val brandName = fields[AppField.BRAND]
            val brandRes = brandRepository.resolveFromCsv(brandSlug, brandName)
            if (brandRes.warning != null) {
                errors += ImportRowError(rowNumber, row, brandRes.warning)
            }
            val resolvedBrandName = brandRes.brand?.name ?: brandName?.takeIf { it.isNotBlank() }

            val catSlug = fields[AppField.CATEGORY_SLUG]
            val catName = fields[AppField.CATEGORY]
            val catRes = categoryRepository.resolveFromCsv(catSlug, catName)
            if (catRes.warning != null) {
                errors += ImportRowError(rowNumber, row, catRes.warning)
            }
            val resolvedCategoryName = catRes.category?.name ?: catName?.takeIf { it.isNotBlank() }

            val variantLabel = fields[AppField.VARIANT_LABEL]?.takeIf { it.isNotBlank() } ?: "Standard"
            val wholesalePrice = fields[AppField.WHOLESALE_PRICE]?.takeIf { it.isNotBlank() }
                ?.let { parser.parsePrice(it) }
            if (wholesalePrice != null && wholesalePrice <= 0) {
                errors += ImportRowError(rowNumber, row, "Wholesale price ($wholesalePrice) must be greater than zero")
                return@forEachIndexed
            }
            if (wholesalePrice != null && wholesalePrice > MAX_PRICE) {
                errors += ImportRowError(rowNumber, row, "Wholesale price ($wholesalePrice) is too large")
                return@forEachIndexed
            }
            val wholesaleMin = fields[AppField.WHOLESALE_MIN]?.takeIf { it.isNotBlank() }
                ?.let { parser.parsePrice(it) }
            if (wholesaleMin != null && wholesaleMin <= 0) {
                errors += ImportRowError(rowNumber, row, "Wholesale min price ($wholesaleMin) must be greater than zero")
                return@forEachIndexed
            }
            if (wholesaleMin != null && wholesaleMin > MAX_PRICE) {
                errors += ImportRowError(rowNumber, row, "Wholesale min price ($wholesaleMin) is too large")
                return@forEachIndexed
            }
            if (wholesaleMin != null && wholesalePrice != null && wholesaleMin > wholesalePrice) {
                errors += ImportRowError(rowNumber, row, "Wholesale min price ($wholesaleMin) cannot be greater than wholesale price ($wholesalePrice)")
                return@forEachIndexed
            }
            val inStock = parser.parseInStock(fields[AppField.VARIANT_IN_STOCK].orEmpty())
            val notes = fields[AppField.NOTES]?.takeIf { it.isNotBlank() }

            val pKey = productKey(name, resolvedBrandName)
            val parsedVariant = ParsedVariant(
                label = variantLabel,
                sellingPrice = sellingPrice,
                sellingMin = sellingMin,
                wholesalePrice = wholesalePrice,
                wholesaleMin = wholesaleMin,
                inStock = inStock
            )

            val existingProduct = productMap[pKey]
            if (existingProduct != null) {
                // If variant with same label exists, update it; otherwise append.
                // Product-level fields (category, notes) are intentionally NOT
                // refreshed from this row — the first row for a given product
                // key sets them and later variant rows for the same product
                // are assumed to repeat, not override, that info.
                val vIndex = existingProduct.variants.indexOfFirst {
                    it.label.equals(variantLabel, ignoreCase = true)
                }
                if (vIndex >= 0) {
                    existingProduct.variants[vIndex] = parsedVariant
                } else {
                    existingProduct.variants.add(parsedVariant)
                }
            } else {
                productMap[pKey] = ParsedProduct(
                    name = name,
                    brand = resolvedBrandName,
                    category = resolvedCategoryName,
                    notes = notes,
                    variants = mutableListOf(parsedVariant)
                )
            }

            if (rowNumber % PROGRESS_INTERVAL == 0) onProgress(rowNumber, rows.size)
        }

        val toCommit = mutableListOf<ProductWithVariants>()
        var importedCount = 0
        var updatedCount = 0

        for ((_, parsed) in productMap) {
            val existingInDb = dao.getByBrandAndName(parsed.name, parsed.brand)

            when (strategy) {
                DuplicateStrategy.SKIP -> {
                    if (existingInDb != null) {
                        skippedCount++
                    } else {
                        val product = Product(
                            name = parsed.name,
                            brand = parsed.brand,
                            category = parsed.category,
                            notes = parsed.notes,
                            createdAt = now,
                            updatedAt = now
                        )
                        val variants = parsed.variants.map { v ->
                            ProductVariant(
                                productId = 0L,
                                variantLabel = v.label,
                                sellingPrice = v.sellingPrice,
                                sellingMin = v.sellingMin,
                                wholesalePrice = v.wholesalePrice,
                                wholesaleMin = v.wholesaleMin,
                                inStock = v.inStock,
                                createdAt = now,
                                updatedAt = now
                            )
                        }
                        toCommit += ProductWithVariants(product, variants)
                        importedCount++
                    }
                }
                DuplicateStrategy.UPDATE -> {
                    val productId = existingInDb?.id ?: 0L
                    val createdAt = existingInDb?.createdAt ?: now
                    val product = Product(
                        id = productId,
                        name = parsed.name,
                        brand = parsed.brand,
                        category = parsed.category,
                        notes = parsed.notes,
                        createdAt = createdAt,
                        updatedAt = now
                    )
                    val variants = parsed.variants.map { v ->
                        ProductVariant(
                            productId = productId,
                            variantLabel = v.label,
                            sellingPrice = v.sellingPrice,
                            sellingMin = v.sellingMin,
                            wholesalePrice = v.wholesalePrice,
                            wholesaleMin = v.wholesaleMin,
                            inStock = v.inStock,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                    toCommit += ProductWithVariants(product, variants)
                    if (existingInDb != null) updatedCount++ else importedCount++
                }
                DuplicateStrategy.ADD_ANYWAY -> {
                    val product = Product(
                        name = parsed.name,
                        brand = parsed.brand,
                        category = parsed.category,
                        notes = parsed.notes,
                        createdAt = now,
                        updatedAt = now
                    )
                    val variants = parsed.variants.map { v ->
                        ProductVariant(
                            productId = 0L,
                            variantLabel = v.label,
                            sellingPrice = v.sellingPrice,
                            sellingMin = v.sellingMin,
                            wholesalePrice = v.wholesalePrice,
                            wholesaleMin = v.wholesaleMin,
                            inStock = v.inStock,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                    toCommit += ProductWithVariants(product, variants)
                    importedCount++
                }
            }
        }

        dao.applyImport(toCommit)
        onProgress(rows.size, rows.size)

        ImportResult(
            importedCount = importedCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
            errors = errors,
            totalRows = rows.size
        )
    }

    private fun productKey(name: String, brand: String?): String =
        "${brand?.lowercase().orEmpty()} ${name.lowercase()}"

    private fun extractFields(row: List<String>, mapping: ColumnMapping): Map<AppField, String> {
        val result = mutableMapOf<AppField, String>()
        row.forEachIndexed { columnIndex, value ->
            val field = mapping.fieldForIndex(columnIndex)
            if (field != AppField.IGNORE && result[field].isNullOrBlank()) {
                result[field] = value
            }
        }
        return result
    }

    private companion object {
        const val PROGRESS_INTERVAL = 50
    }
}
