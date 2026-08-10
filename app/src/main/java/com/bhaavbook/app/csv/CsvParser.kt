package com.bhaavbook.app.csv

import android.content.Context
import android.net.Uri
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedCsvResult(
    val headers: List<String>,
    val rows: List<List<String>>,
    /** The first few rows, for the confirm-before-import table. */
    val previewRows: List<List<String>>,
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
        require(bytes.size <= MAX_FILE_BYTES) {
            "That file is ${bytes.size / (1024 * 1024)} MB. Split it into files under " +
                "${MAX_FILE_BYTES / (1024 * 1024)} MB and import them one at a time."
        }
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


        val allRows = csvReader.use { it.readAll() }

        if (allRows.isEmpty()) {
            return ParsedCsvResult(emptyList(), emptyList(), emptyList(), 0, delimiter)
        }

        // Blank lines are dropped here rather than earlier: a trailing newline is
        // normal in every spreadsheet export and must not count as a failed row.
        val cleanRows = allRows
            .map { row -> row.map { it.trim() } }
            .filter { row -> row.any { it.isNotEmpty() } }

        val headers: List<String>
        val dataRows: List<List<String>>
        if (hasHeaderRow) {
            headers = cleanRows.first()
            dataRows = cleanRows.drop(1)
        } else {
            headers = (1..cleanRows.first().size).map { "Column $it" }
            dataRows = cleanRows
        }

        return ParsedCsvResult(
            headers = headers,
            rows = dataRows,
            previewRows = dataRows.take(PREVIEW_ROW_COUNT),
            totalRowCount = dataRows.size,
            delimiter = delimiter
        )
    }

    // -----------------------------------------------------------------------
    // Delimiter detection
    // -----------------------------------------------------------------------

    /**
     * Counts commas against semicolons and tabs in the header line and picks the
     * winner. Semicolons come from Excel on a European locale; tabs from a paste
     * out of Google Sheets. Comma is the tie-break, being by far the most common.
     */
    fun detectDelimiter(firstLine: String): Char {
        val counts = listOf(
            ',' to firstLine.count { it == ',' },
            ';' to firstLine.count { it == ';' },
            '	' to firstLine.count { it == '	' }
        )
        val best = counts.maxByOrNull { it.second } ?: return ','
        return if (best.second == 0) ',' else best.first
    }

    // -----------------------------------------------------------------------
    // Price parsing
    // -----------------------------------------------------------------------

    /**
     * Parses the price formats that turn up in real Indian retail spreadsheets:
     * `45`, `45.50`, `₹45`, `₹ 45`, `Rs. 45`, `INR 45`, `1,200`, `1,200.50`.
     *
     * A currency word or symbol is removed, along with thousands separators and
     * the non-breaking spaces spreadsheets like to emit. Whatever is left must
     * then be *entirely* numeric — `"abc123xyz"` is rejected rather than read as
     * 123, because silently importing a wrong price is worse than flagging the
     * row for the user to fix.
     */
    fun parsePrice(raw: String): Double? {
        if (raw.isBlank()) return null
        val cleaned = raw
            .replace(CURRENCY_TOKEN, "")
            .replace(",", "")
            .replace(NON_BREAKING_SPACE, "")
            .filterNot { it.isWhitespace() }
        return if (NUMERIC.matches(cleaned)) cleaned.toDoubleOrNull() else null
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
            "no", "false", "0", "n", "out", "out of stock", "unavailable", "nil" -> false
            // Anything else, including blank and anything unrecognised, means in
            // stock: hiding a product the shop actually has is the worse mistake.
            else -> true
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

    private companion object {
        /** `₹`, `Rs`, `Rs.`, `INR`, `rupee`, `rupees` — case-insensitive. */
        val CURRENCY_TOKEN = Regex("""(?i)(₹|rs\.?|inr|rupees?)""")

        /** `45`, `45.`, `45.50`, `.5`, and the negative of each. */
        val NUMERIC = Regex("""-?(\d+\.?\d*|\.\d+)""")

        /** Spreadsheets emit U+00A0 as a thousands separator; it is not whitespace. */
        const val NON_BREAKING_SPACE = "\u00A0"

        /** How many rows the preview table shows before the mapping step. */
        const val PREVIEW_ROW_COUNT = 10

        /**
         * A guard against someone picking a 200 MB export by mistake: the parser
         * holds the whole file in memory, and a phone at a shop counter has very
         * little of it to spare.
         */
        const val MAX_FILE_BYTES = 8 * 1024 * 1024
    }
}
