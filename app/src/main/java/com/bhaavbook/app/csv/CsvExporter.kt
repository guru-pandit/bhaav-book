package com.bhaavbook.app.csv

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bhaavbook.app.data.model.Product
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
            "name", "brand", "category", "selling_price", "cost_price",
            "unit", "quantity_value", "in_stock", "notes"
        )

        /** Filled-in rows for the "download a sample file" button. */
        val SAMPLE_ROWS: List<Array<String>> = listOf(
            arrayOf("Agarbatti Chandan", "Cycle", "Agarbatti & Dhoop", "45", "38", "GRAM", "100", "yes", "Fast moving"),
            arrayOf("Kapur Tablet", "Mangaldeep", "Camphor & Wicks", "60", "50", "GRAM", "50", "yes", ""),
            arrayOf("Kumkum", "Moksh", "Kumkum & Haldi", "25", "19", "GRAM", "50", "yes", ""),
            arrayOf("Cotton Wick Long", "Local", "Camphor & Wicks", "20", "14", "PACKET", "1", "no", ""),
            arrayOf("Til Oil", "Patanjali", "Oil & Ghee", "180", "158", "ML", "500", "yes", "")
        )

        /**
         * Files handed to other apps live in their own cache subdirectory so the
         * FileProvider grant covers exactly this, and not everything else the app
         * happens to have cached.
         */
        private const val SHARE_DIR = "shared"
        private const val SHARE_FILE_NAME = "chaitanya_stores_prices.csv"
    }

    // -----------------------------------------------------------------------
    // Export to a user-chosen location
    // -----------------------------------------------------------------------

    /** Writes [products] as CSV to the SAF document at [uri]. */
    suspend fun exportToCsv(uri: Uri, products: List<Product>) = withContext(Dispatchers.IO) {
        openWriter(uri).use { writer ->
            writer.writeNext(CSV_HEADERS)
            products.forEach { writer.writeNext(it.toCsvRow()) }
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
     * appended, so the user can fix that file and re-import just those rows.
     *
     * [originalHeaders] must be the headers of the file that was imported, not
     * the app's canonical ones: the raw cells are echoed back verbatim, and
     * pairing them with a different set of headers would misalign every column.
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

    /**
     * Builds a chooser that hands the price list to WhatsApp, Gmail, Drive or
     * anything else that takes a file.
     *
     * The read grant is set on the inner intent *and* mirrored into `clipData`:
     * several popular targets read the attachment from the clip instead of
     * `EXTRA_STREAM`, and without it they receive a URI they cannot open.
     */
    suspend fun createShareCsvIntent(products: List<Product>): Intent = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val file = File(shareDir, SHARE_FILE_NAME)

        CSVWriter(OutputStreamWriter(file.outputStream(), Charsets.UTF_8)).use { writer ->
            writer.writeNext(CSV_HEADERS)
            products.forEach { writer.writeNext(it.toCsvRow()) }
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

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private fun openWriter(uri: Uri): CSVWriter {
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("Cannot open that file for writing. Pick a different location.")
        return CSVWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
    }
}

/**
 * Prices are written plainly — `45`, not `45.0` — because this file is meant to
 * be opened in a spreadsheet and read by a person.
 */
private fun Product.toCsvRow(): Array<String> = arrayOf(
    name,
    brand.orEmpty(),
    category.orEmpty(),
    sellingPrice.toEditableString(),
    costPrice?.toEditableString().orEmpty(),
    unit.name,
    quantityValue?.toEditableString().orEmpty(),
    if (inStock) "yes" else "no",
    notes.orEmpty()
)
