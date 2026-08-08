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

    @Before
    fun setUp() {
        parser = CsvParser(mockContext)
    }

    // -----------------------------------------------------------------------
    // Delimiter Detection
    // -----------------------------------------------------------------------

    @Test
    fun `detectDelimiter detects comma when commas outnumber semicolons`() {
        val line = "name,brand,category,selling_price,cost_price"
        val delimiter = parser.detectDelimiter(line)
        assertEquals(',', delimiter)
    }

    @Test
    fun `detectDelimiter detects semicolon when semicolons outnumber commas`() {
        val line = "name;brand;category;selling_price;cost_price"
        val delimiter = parser.detectDelimiter(line)
        assertEquals(';', delimiter)
    }

    // -----------------------------------------------------------------------
    // Price Parsing
    // -----------------------------------------------------------------------

    @Test
    fun `parsePrice parses standard integer and decimal strings`() {
        assertEquals(45.0, parser.parsePrice("45")!!, 0.001)
        assertEquals(45.50, parser.parsePrice("45.50")!!, 0.001)
    }

    @Test
    fun `parsePrice handles Indian Rupee currency symbols and spaces`() {
        assertEquals(45.0, parser.parsePrice("₹45")!!, 0.001)
        assertEquals(45.50, parser.parsePrice("₹ 45.50")!!, 0.001)
        assertEquals(100.0, parser.parsePrice("Rs. 100")!!, 0.001)
        assertEquals(100.0, parser.parsePrice("Rs100")!!, 0.001)
    }

    @Test
    fun `parsePrice strips thousands separators correctly`() {
        assertEquals(1200.0, parser.parsePrice("1,200")!!, 0.001)
        assertEquals(1200.50, parser.parsePrice("₹1,200.50")!!, 0.001)
    }

    @Test
    fun `parsePrice returns null for invalid non-numeric strings`() {
        assertNull(parser.parsePrice("free"))
        assertNull(parser.parsePrice(""))
        assertNull(parser.parsePrice("   "))
        assertNull(parser.parsePrice("abc123xyz"))
    }

    // -----------------------------------------------------------------------
    // in_stock Parsing
    // -----------------------------------------------------------------------

    @Test
    fun `parseInStock handles affirmative values correctly`() {
        assertTrue(parser.parseInStock("yes"))
        assertTrue(parser.parseInStock("YES"))
        assertTrue(parser.parseInStock("true"))
        assertTrue(parser.parseInStock("1"))
        assertTrue(parser.parseInStock("y"))
        assertTrue(parser.parseInStock("in stock"))
        assertTrue(parser.parseInStock("available"))
        assertTrue(parser.parseInStock("")) // blank defaults to in stock
    }

    @Test
    fun `parseInStock handles negative values correctly`() {
        assertFalse(parser.parseInStock("no"))
        assertFalse(parser.parseInStock("NO"))
        assertFalse(parser.parseInStock("false"))
        assertFalse(parser.parseInStock("0"))
        assertFalse(parser.parseInStock("n"))
        assertFalse(parser.parseInStock("out of stock"))
        assertFalse(parser.parseInStock("unavailable"))
    }
}
