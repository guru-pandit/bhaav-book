package com.bhaavbook.app.csv

import android.content.Context
import android.net.Uri
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedCsvResult(
    val headers: List<String>,
    val rows: List<List<String>>,
    val previewRows: List<List<String>>,   // first 10 rows
    val totalRowCount: Int,
    val delimiter: Char
)

@Singleton
class CsvParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Opens a CSV at [uri] via SAF, strips BOM, detects delimiter, and parses.
     * [hasHeaderRow] = true (default) treats the first row as headers.
     */
    fun parse(uri: Uri, hasHeaderRow: Boolean = true): ParsedCsvResult {
        val bytes = readBytes(uri)
        val text = decodeStripBom(bytes)
        val lines = text.lines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return ParsedCsvResult(emptyList(), emptyList(), emptyList(), 0, ',')
        }

        val delimiter = detectDelimiter(lines.first())
        val csvParser = CSVParserBuilder()
            .withSeparator(delimiter)
            .withIgnoreQuotations(false)
            .build()

        val csvReader = CSVReaderBuilder(text.reader())
            .withCSVParser(csvParser)
            .build()

        val allRows = csvReader.readAll()
        csvReader.close()

        if (allRows.isEmpty()) {
            return ParsedCsvResult(emptyList(), emptyList(), emptyList(), 0, delimiter)
        }

        val headers: List<String>
        val dataRows: List<List<String>>

        if (hasHeaderRow) {
            headers = allRows.first().map { it.trim() }
            dataRows = allRows.drop(1).map { row -> row.map { it.trim() } }
        } else {
            headers = (1..allRows.first().size).map { "Column $it" }
            dataRows = allRows.map { row -> row.map { it.trim() } }
        }

        return ParsedCsvResult(
            headers = headers,
            rows = dataRows,
            previewRows = dataRows.take(10),
            totalRowCount = dataRows.size,
            delimiter = delimiter
        )
    }

    // -----------------------------------------------------------------------
    // Delimiter detection
    // -----------------------------------------------------------------------

    /** Counts commas vs semicolons in the first line and picks the majority. */
    fun detectDelimiter(firstLine: String): Char {
        val commas = firstLine.count { it == ',' }
        val semis = firstLine.count { it == ';' }
        return if (semis > commas) ';' else ','
    }

    // -----------------------------------------------------------------------
    // Price parsing
    // -----------------------------------------------------------------------

    /**
     * Parses price strings in various formats used by Indian retail:
     *  - "45", "45.50", "₹45", "₹ 45", "1,200", "1,200.50", "₹1,200"
     * Returns null when the input cannot be interpreted as a number.
     */
    fun parsePrice(raw: String): Double? {
        if (raw.isBlank()) return null
        val cleaned = raw
            .replace("₹", "")
            .replace("Rs", "", ignoreCase = true)
            .replace("rs.", "", ignoreCase = true)
            .replace("INR", "", ignoreCase = true)
            .replace(",", "")  // remove thousands separators
            .trim()
        return cleaned.toDoubleOrNull()
    }

    // -----------------------------------------------------------------------
    // in_stock parsing
    // -----------------------------------------------------------------------

    /**
     * Parses the in_stock CSV field.
     * Accepts: yes/no, true/false, 1/0, y/n (all case-insensitive).
     * Blank → true (default: in stock).
     */
    fun parseInStock(raw: String): Boolean {
        return when (raw.trim().lowercase()) {
            "", "yes", "true", "1", "y", "in stock", "available" -> true
            "no", "false", "0", "n", "out of stock", "unavailable" -> false
            else -> true // unknown → assume in stock
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private fun readBytes(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open URI: $uri")
        return stream.use { it.readBytes() }
    }

    /** Decode as UTF-8, stripping a leading BOM (EF BB BF) if present. */
    private fun decodeStripBom(bytes: ByteArray): String {
        val hasBom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        return if (hasBom) {
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        } else {
            String(bytes, Charsets.UTF_8)
        }
    }
}
