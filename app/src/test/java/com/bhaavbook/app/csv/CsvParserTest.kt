package com.bhaavbook.app.csv

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CsvParserTest {

    private lateinit var parser: CsvParser
    private val mockContext: Context = mockk(relaxed = true)

    /** What Excel and Google Sheets put between digit groups. Not whitespace. */
    private val nbsp = "\u00A0"

    @Before
    fun setUp() {
        parser = CsvParser(mockContext)
    }

    // -----------------------------------------------------------------------
    // Delimiter detection
    // -----------------------------------------------------------------------

    @Test
    fun `detectDelimiter picks comma when commas dominate`() {
        assertEquals(',', parser.detectDelimiter("name,brand,category,selling_price"))
    }

    @Test
    fun `detectDelimiter picks semicolon for a European Excel export`() {
        assertEquals(';', parser.detectDelimiter("name;brand;category;selling_price"))
    }

    @Test
    fun `detectDelimiter picks tab for a spreadsheet paste`() {
        assertEquals('\t', parser.detectDelimiter("name\tbrand\tcategory\tselling_price"))
    }

    @Test
    fun `detectDelimiter falls back to comma for a single column`() {
        assertEquals(',', parser.detectDelimiter("name"))
    }

    // -----------------------------------------------------------------------
    // Price parsing
    // -----------------------------------------------------------------------

    @Test
    fun `parsePrice reads plain integers and decimals`() {
        assertEquals(45.0, parser.parsePrice("45")!!, 0.001)
        assertEquals(45.50, parser.parsePrice("45.50")!!, 0.001)
    }

    @Test
    fun `parsePrice strips rupee symbols and words`() {
        assertEquals(45.0, parser.parsePrice("₹45")!!, 0.001)
        assertEquals(45.50, parser.parsePrice("₹ 45.50")!!, 0.001)
        assertEquals(100.0, parser.parsePrice("Rs100")!!, 0.001)
        assertEquals(100.0, parser.parsePrice("INR 100")!!, 0.001)
        assertEquals(100.0, parser.parsePrice("100 rupees")!!, 0.001)
    }

    /**
     * The bug that shipped: `"Rs. 45"` lost the `Rs` but kept the dot, leaving
     * `". 45"`, which parsed as null — so every row written with this extremely
     * common spelling failed to import, and the existing test for it was red.
     */
    @Test
    fun `parsePrice reads Rs with a trailing dot`() {
        assertEquals(45.0, parser.parsePrice("Rs. 45")!!, 0.001)
        assertEquals(100.0, parser.parsePrice("Rs. 100")!!, 0.001)
        assertEquals(1200.50, parser.parsePrice("Rs. 1,200.50")!!, 0.001)
    }

    @Test
    fun `parsePrice strips thousands separators`() {
        assertEquals(1200.0, parser.parsePrice("1,200")!!, 0.001)
        assertEquals(1200.50, parser.parsePrice("₹1,200.50")!!, 0.001)
        assertEquals(125000.0, parser.parsePrice("1,25,000")!!, 0.001)
    }

    @Test
    fun `parsePrice handles the non-breaking space spreadsheets emit`() {
        assertEquals(1200.0, parser.parsePrice("1${nbsp}200")!!, 0.001)
        assertEquals(45.0, parser.parsePrice("₹${nbsp}45")!!, 0.001)
    }

    @Test
    fun `parsePrice rejects anything that is not purely a number`() {
        assertNull(parser.parsePrice("free"))
        assertNull(parser.parsePrice(""))
        assertNull(parser.parsePrice("   "))
        assertNull(parser.parsePrice("1.2.3"))
    }

    /**
     * Reading `"abc123xyz"` as 123 would put a silently wrong price on a shelf
     * item. Rejecting it sends the row to the error list, where it gets fixed.
     */
    @Test
    fun `parsePrice rejects a number buried in text rather than guessing`() {
        assertNull(parser.parsePrice("abc123xyz"))
        assertNull(parser.parsePrice("45 per packet"))
    }

    // -----------------------------------------------------------------------
    // in_stock parsing
    // -----------------------------------------------------------------------

    @Test
    fun `parseInStock reads affirmatives`() {
        assertTrue(parser.parseInStock("yes"))
        assertTrue(parser.parseInStock("YES"))
        assertTrue(parser.parseInStock("true"))
        assertTrue(parser.parseInStock("1"))
        assertTrue(parser.parseInStock("y"))
        assertTrue(parser.parseInStock("in stock"))
        assertTrue(parser.parseInStock("available"))
    }

    @Test
    fun `parseInStock reads negatives`() {
        assertFalse(parser.parseInStock("no"))
        assertFalse(parser.parseInStock("NO"))
        assertFalse(parser.parseInStock("false"))
        assertFalse(parser.parseInStock("0"))
        assertFalse(parser.parseInStock("n"))
        assertFalse(parser.parseInStock("out of stock"))
        assertFalse(parser.parseInStock("unavailable"))
    }

    @Test
    fun `parseInStock defaults to in stock for blank and unknown values`() {
        assertTrue(parser.parseInStock(""))
        assertTrue(parser.parseInStock("   "))
        assertTrue(parser.parseInStock("maybe"))
    }
}
