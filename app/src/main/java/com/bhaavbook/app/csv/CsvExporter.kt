package com.bhaavbook.app.csv

import android.content.Context
import android.net.Uri
import com.bhaavbook.app.data.model.Product
import com.opencsv.CSVWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Canonical header row — matches the import spec exactly. */
        val CSV_HEADERS = arrayOf(
            "name", "brand", "category", "selling_price", "cost_price",
            "unit", "quantity_value", "in_stock", "notes"
        )

        /** Sample data for the "Download sample CSV" feature. */
        val SAMPLE_ROWS: List<Array<String>> = listOf(
            arrayOf("Agarbatti Chandan", "Cycle", "Agarbatti & Dhoop", "45", "38", "GRAM", "100", "yes", "Fast moving"),
            arrayOf("Kapur Tablet", "Mangaldeep", "Camphor & Wicks", "60", "50", "GRAM", "50", "yes", ""),
            arrayOf("Kumkum", "Moksh", "Kumkum & Haldi", "25", "19", "GRAM", "50", "yes", ""),
            arrayOf("Cotton Wick Long", "Local", "Camphor & Wicks", "20", "14", "PACKET", "1", "no", "Out of stock"),
            arrayOf("Til Oil", "Patanjali", "Oil & Ghee", "180", "158", "ML", "500", "yes", "")
        )
    }

    // -----------------------------------------------------------------------
    // CSV Export
    // -----------------------------------------------------------------------

    /** Exports all [products] to a CSV file at [uri]. */
    suspend fun exportToCsv(uri: Uri, products: List<Product>) = withContext(Dispatchers.IO) {
        openWriter(uri).use { writer ->
            writer.writeNext(CSV_HEADERS)
            products.forEach { writer.writeNext(it.toCsvRow()) }
        }
    }

    /** Writes a sample CSV template to [uri]. */
    suspend fun writeSampleCsv(uri: Uri) = withContext(Dispatchers.IO) {
        openWriter(uri).use { writer ->
            writer.writeNext(CSV_HEADERS)
            SAMPLE_ROWS.forEach { writer.writeNext(it) }
        }
    }

    /**
     * Exports only the failed rows from an import as a CSV, with an
     * extra "error_reason" column so the user knows what to fix.
     */
    suspend fun exportErrorRows(
        uri: Uri,
        originalHeaders: List<String>,
        errors: List<ImportRowError>
    ) = withContext(Dispatchers.IO) {
        openWriter(uri).use { writer ->
            writer.writeNext((originalHeaders + "error_reason").toTypedArray())
            errors.forEach { err ->
                writer.writeNext((err.rawRow + err.reason).toTypedArray())
            }
        }
    }

    // -----------------------------------------------------------------------
    // JSON Backup
    // -----------------------------------------------------------------------

    /** Exports all [products] as a JSON backup file. */
    suspend fun exportToJson(uri: Uri, products: List<Product>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        products.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("brand", p.brand ?: JSONObject.NULL)
                put("category", p.category ?: JSONObject.NULL)
                put("selling_price", p.sellingPrice)
                put("cost_price", p.costPrice ?: JSONObject.NULL)
                put("unit", p.unit.name)
                put("quantity_value", p.quantityValue ?: JSONObject.NULL)
                put("in_stock", p.inStock)
                put("notes", p.notes ?: JSONObject.NULL)
                put("created_at", p.createdAt)
                put("updated_at", p.updatedAt)
            })
        }
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("Cannot open URI for writing: $uri")
        outputStream.writer(Charsets.UTF_8).use { it.write(array.toString(2)) }
    }

    // -----------------------------------------------------------------------
    // JSON Restore
    // -----------------------------------------------------------------------

    /**
     * Parses a JSON backup and returns a list of [Product] objects.
     * IDs are reset to 0 so Room auto-generates new primary keys on insert.
     */
    suspend fun importFromJson(uri: Uri): List<Product> = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open URI: $uri")
        val json = inputStream.bufferedReader(Charsets.UTF_8).readText()
        val array = JSONArray(json)
        val now = System.currentTimeMillis()
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Product(
                id = 0L,
                name = obj.getString("name"),
                brand = obj.optString("brand").takeIf { it.isNotBlank() },
                category = obj.optString("category").takeIf { it.isNotBlank() },
                sellingPrice = obj.getDouble("selling_price"),
                costPrice = if (obj.isNull("cost_price")) null else obj.getDouble("cost_price"),
                unit = com.bhaavbook.app.data.model.ProductUnit.fromString(obj.optString("unit")),
                quantityValue = if (obj.isNull("quantity_value")) null else obj.getDouble("quantity_value"),
                inStock = obj.optBoolean("in_stock", true),
                notes = obj.optString("notes").takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now
            )
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private fun openWriter(uri: Uri): CSVWriter {
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("Cannot open URI for writing: $uri")
        return CSVWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
    }
}

// ---------------------------------------------------------------------------
// Extension: Product → CSV row
// ---------------------------------------------------------------------------

private fun Product.toCsvRow(): Array<String> = arrayOf(
    name,
    brand ?: "",
    category ?: "",
    sellingPrice.toString(),
    costPrice?.toString() ?: "",
    unit.name,
    quantityValue?.toString() ?: "",
    if (inStock) "yes" else "no",
    notes ?: ""
)
