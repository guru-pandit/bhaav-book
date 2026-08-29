package com.bhaavbook.app.csv

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bhaavbook.app.data.model.ProductWithVariants
import com.bhaavbook.app.data.model.generateSlug
import com.bhaavbook.app.format.toEditableString
import com.opencsv.CSVWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Canonical header row — matches what the importer auto-detects. */
        val CSV_HEADERS = arrayOf(
            "name", "brand_slug", "brand", "category_slug", "category",
            "variant_label", "selling_price", "selling_min", "wholesale_price", "wholesale_min",
            "variant_in_stock", "notes"
        )

        /** Filled-in rows for the "download a sample file" button. */
        val SAMPLE_ROWS: List<Array<String>> = listOf(
            arrayOf("Agarbatti Chandan", "zed-black", "Zed Black", "agarbatti", "Agarbatti", "100 g", "45", "40", "40", "35", "yes", "Fast moving"),
            arrayOf("Agarbatti Chandan", "zed-black", "Zed Black", "agarbatti", "Agarbatti", "250 g", "100", "", "90", "", "yes", "Fast moving"),
            arrayOf("Kapur Tablet", "mangaldeep", "Mangaldeep", "kamphor", "Kamphor", "50 g", "60", "55", "55", "50", "yes", ""),
            arrayOf("Kumkum", "", "Moksh", "pooja-items", "Pooja Items", "50 g", "25", "", "", "", "yes", ""),
            arrayOf("Cotton Wick Long", "", "Local", "pooja-items", "Pooja Items", "Packet", "20", "", "", "", "no", ""),
            arrayOf("Til Oil", "patanjali", "Patanjali", "pooja-items", "Pooja Items", "500 ml", "180", "170", "165", "160", "yes", "")
        )

        private const val SHARE_DIR = "shared"
        private const val SHARE_FILE_NAME = "chaitanya_stores_prices.csv"
    }

    // -----------------------------------------------------------------------
    // Export to a user-chosen location
    // -----------------------------------------------------------------------

    /** Writes [products] as CSV to the SAF document at [uri]. */
    suspend fun exportToCsv(uri: Uri, products: List<ProductWithVariants>) = withContext(Dispatchers.IO) {
        openWriter(uri).use { writer ->
            writer.writeNext(CSV_HEADERS)
            products.forEach { pwv ->
                pwv.toCsvRows().forEach { row -> writer.writeNext(row) }
            }
        }
    }

    /** Writes the sample template to the SAF document at [uri]. */
    suspend fun writeSampleCsv(uri: Uri) = withContext(Dispatchers.IO) {
        openWriter(uri).use { writer ->
            writer.writeNext(CSV_HEADERS)
            SAMPLE_ROWS.forEach { writer.writeNext(it) }
        }
    }

    /**
     * Writes only the rows that failed an import, with an `error_reason` column
     * appended.
     */
    suspend fun exportErrorRows(
        uri: Uri,
        originalHeaders: List<String>,
        errors: List<ImportRowError>
    ) = withContext(Dispatchers.IO) {
        openWriter(uri).use { writer ->
            writer.writeNext((originalHeaders + "error_reason").toTypedArray())
            errors.forEach { error ->
                writer.writeNext((error.rawRow + error.reason).toTypedArray())
            }
        }
    }

    // -----------------------------------------------------------------------
    // Share sheet
    // -----------------------------------------------------------------------

    suspend fun createShareCsvIntent(products: List<ProductWithVariants>): Intent = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(shareDir, SHARE_FILE_NAME)

        CSVWriter(OutputStreamWriter(file.outputStream(), Charsets.UTF_8)).use { writer ->
            writer.writeNext(CSV_HEADERS)
            products.forEach { pwv ->
                pwv.toCsvRows().forEach { row -> writer.writeNext(row) }
            }
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Chaitanya Stores — price list")
            putExtra(Intent.EXTRA_TEXT, "Price list for ${products.size} items.")
            clipData = ClipData.newUri(context.contentResolver, SHARE_FILE_NAME, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun openWriter(uri: Uri): CSVWriter {
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("Cannot open that file for writing. Pick a different location.")
        return CSVWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
    }
}

/**
 * Converts a [ProductWithVariants] to one or more CSV rows (one per variant).
 */
private fun ProductWithVariants.toCsvRows(): List<Array<String>> {
    val brandSlug = product.brand?.takeIf { it.isNotBlank() }?.let { generateSlug(it) }.orEmpty()
    val categorySlug = product.category?.takeIf { it.isNotBlank() }?.let { generateSlug(it) }.orEmpty()

    if (variants.isEmpty()) {
        return listOf(
            arrayOf(
                product.name,
                brandSlug,
                product.brand.orEmpty(),
                categorySlug,
                product.category.orEmpty(),
                "Standard",
                "0",
                "",
                "",
                "",
                "yes",
                product.notes.orEmpty()
            )
        )
    }

    return variants.map { variant ->
        arrayOf(
            product.name,
            brandSlug,
            product.brand.orEmpty(),
            categorySlug,
            product.category.orEmpty(),
            variant.variantLabel,
            variant.sellingPrice.toEditableString(),
            variant.sellingMin?.toEditableString().orEmpty(),
            variant.wholesalePrice?.toEditableString().orEmpty(),
            variant.wholesaleMin?.toEditableString().orEmpty(),
            if (variant.inStock) "yes" else "no",
            product.notes.orEmpty()
        )
    }
}

private fun Double.toEditableString(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
