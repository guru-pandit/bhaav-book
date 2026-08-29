package com.bhaavbook.app.format

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatTest {

    // -----------------------------------------------------------------------
    // Indian digit grouping
    // -----------------------------------------------------------------------

    @Test
    fun `small amounts are left alone`() {
        assertEquals("0", 0.0.toPriceString())
        assertEquals("5", 5.0.toPriceString())
        assertEquals("45", 45.0.toPriceString())
        assertEquals("999", 999.0.toPriceString())
    }

    /** Three digits, then pairs — 12,34,567 rather than 12,345,67 or 12,345,670. */
    @Test
    fun `grouping follows the Indian convention, not the western one`() {
        assertEquals("1,000", 1000.0.toPriceString())
        assertEquals("12,345", 12345.0.toPriceString())
        assertEquals("1,23,456", 123456.0.toPriceString())
        assertEquals("12,34,567", 1234567.0.toPriceString())
        assertEquals("1,23,45,678", 12345678.0.toPriceString())
    }

    // -----------------------------------------------------------------------
    // Paise
    // -----------------------------------------------------------------------

    /** A shop counter writes 45, not 45.00. */
    @Test
    fun `whole rupees drop the paise`() {
        assertEquals("45", 45.0.toPriceString())
        assertEquals("1,200", 1200.00.toPriceString())
    }

    @Test
    fun `paise are kept when they are there`() {
        assertEquals("45.50", 45.5.toPriceString())
        assertEquals("45.05", 45.05.toPriceString())
        assertEquals("1,200.99", 1200.99.toPriceString())
    }

    @Test
    fun `paise round half up rather than truncating`() {
        assertEquals("45.51", 45.505.toPriceString())
        assertEquals("46", 45.999.toPriceString())
    }

    @Test
    fun `negative amounts keep their sign outside the grouping`() {
        assertEquals("-45", (-45.0).toPriceString())
        assertEquals("-1,23,456", (-123456.0).toPriceString())
    }

    @Test
    fun `a currency symbol is prefixed without a space`() {
        assertEquals("₹45", 45.0.toPriceString("₹"))
        assertEquals("₹1,25,000", 125000.0.toPriceString("₹"))
        assertEquals("$45", 45.0.toPriceString("$"))
    }

    @Test
    fun `non-finite values show a dash instead of the word NaN`() {
        assertEquals("—", Double.NaN.toPriceString())
        assertEquals("—", Double.POSITIVE_INFINITY.toPriceString())
    }

    // -----------------------------------------------------------------------
    // Editable / machine-readable forms
    // -----------------------------------------------------------------------

    /**
     * Text fields and CSV columns get plain digits: grouping separators would
     * have to be stripped again before parsing, and `45.0` in a spreadsheet
     * column reads as noise.
     */
    @Test
    fun `the editable form has no separators and no trailing zeros`() {
        assertEquals("45", 45.0.toEditableString())
        assertEquals("125000", 125000.0.toEditableString())
        assertEquals("45.5", 45.5.toEditableString())
        assertEquals("0", 0.0.toEditableString())
    }

    @Test
    fun `pack sizes drop a pointless decimal`() {
        assertEquals("100", 100.0.toQuantityString())
        assertEquals("1.5", 1.5.toQuantityString())
        assertEquals("500", 500.0.toQuantityString())
    }

    // -----------------------------------------------------------------------
    // Price range formatting
    // -----------------------------------------------------------------------

    @Test
    fun `formatPriceRange formats distinct range and single price correctly`() {
        assertEquals("₹40 - ₹45", formatPriceRange(40.0, 45.0, "₹"))
        assertEquals("₹45", formatPriceRange(null, 45.0, "₹"))
        assertEquals("₹45", formatPriceRange(45.0, 45.0, "₹"))
    }
}
