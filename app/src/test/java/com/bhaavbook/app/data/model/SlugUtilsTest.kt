package com.bhaavbook.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlugUtilsTest {

    // -----------------------------------------------------------------------
    // generateSlug — spec examples
    // -----------------------------------------------------------------------

    @Test fun `Cycle becomes cycle`() =
        assertEquals("cycle", generateSlug("Cycle"))

    @Test fun `Zed Black becomes zed-black`() =
        assertEquals("zed-black", generateSlug("Zed Black"))

    @Test fun `Puja Items becomes puja-items`() =
        assertEquals("puja-items", generateSlug("Puja Items"))

    @Test fun `Pooja Samagri becomes pooja-samagri`() =
        assertEquals("pooja-samagri", generateSlug("Pooja Samagri"))

    @Test fun `M&B Fragrances becomes mb-fragrances`() =
        assertEquals("mb-fragrances", generateSlug("M&B Fragrances"))

    // -----------------------------------------------------------------------
    // generateSlug — edge cases
    // -----------------------------------------------------------------------

    @Test fun `leading and trailing whitespace is stripped`() =
        assertEquals("cycle", generateSlug("  Cycle  "))

    @Test fun `multiple spaces collapse to one hyphen`() =
        assertEquals("zed-black", generateSlug("Zed   Black"))

    @Test fun `existing hyphens are preserved and collapsed`() =
        assertEquals("zed-black", generateSlug("Zed--Black"))

    @Test fun `trailing hyphens are removed`() =
        assertEquals("cycle", generateSlug("Cycle!"))

    @Test fun `leading hyphens are removed`() =
        assertEquals("cycle", generateSlug("!Cycle"))

    @Test fun `special characters are stripped`() =
        assertEquals("shree-ganesh-agarbatti", generateSlug("Shree Ganesh Agarbatti"))

    @Test fun `digits are preserved`() =
        assertEquals("product-100g", generateSlug("Product 100g"))

    @Test fun `already lowercase input is unchanged`() =
        assertEquals("agarbatti", generateSlug("agarbatti"))

    @Test fun `mixed punctuation cleaned up`() =
        assertEquals("abc-def", generateSlug("ABC & DEF"))

    @Test fun `all-punctuation input gives empty after trim`() {
        // generateSlug of pure punctuation → empty string (caller should
        // guard against using an empty slug)
        assertEquals("", generateSlug("!!!"))
    }

    // -----------------------------------------------------------------------
    // isValidSlug
    // -----------------------------------------------------------------------

    @Test fun `valid simple slug passes`() =
        assertTrue(isValidSlug("cycle"))

    @Test fun `valid hyphenated slug passes`() =
        assertTrue(isValidSlug("zed-black"))

    @Test fun `valid slug with digits passes`() =
        assertTrue(isValidSlug("product-100g"))

    @Test fun `slug too short fails`() =
        assertFalse(isValidSlug("a"))

    @Test fun `slug starting with hyphen fails`() =
        assertFalse(isValidSlug("-cycle"))

    @Test fun `slug ending with hyphen fails`() =
        assertFalse(isValidSlug("cycle-"))

    @Test fun `slug with uppercase fails`() =
        assertFalse(isValidSlug("Cycle"))

    @Test fun `slug with space fails`() =
        assertFalse(isValidSlug("zed black"))

    @Test fun `slug with special char fails`() =
        assertFalse(isValidSlug("m&b"))

    @Test fun `exactly 2 char slug passes`() =
        assertTrue(isValidSlug("ab"))

    @Test fun `exactly 60 char slug passes`() =
        assertTrue(isValidSlug("a".repeat(30) + "-" + "b".repeat(29)))

    @Test fun `61 char slug fails`() =
        assertFalse(isValidSlug("a".repeat(61)))
}
