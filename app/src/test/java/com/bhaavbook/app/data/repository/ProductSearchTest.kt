package com.bhaavbook.app.data.repository

import com.bhaavbook.app.data.db.ProductDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProductSearchTest {

    private lateinit var repository: ProductRepository
    private val mockDao: ProductDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        repository = ProductRepository(mockDao)
    }

    @Test
    fun `buildFtsQuery formats single word with trailing star`() {
        val query = repository.buildFtsQuery("chandan")
        assertEquals("chandan*", query)
    }

    @Test
    fun `buildFtsQuery formats multi-word query with trailing stars on each word`() {
        val query = repository.buildFtsQuery("chandan cycle")
        assertEquals("chandan* cycle*", query)
    }

    @Test
    fun `buildFtsQuery handles reversed word order producing independent tokens`() {
        val query1 = repository.buildFtsQuery("cycle chandan")
        val query2 = repository.buildFtsQuery("chandan cycle")
        
        // FTS matches tokens using AND logic regardless of position
        assertEquals("cycle* chandan*", query1)
        assertEquals("chandan* cycle*", query2)
    }

    @Test
    fun `buildFtsQuery strips illegal FTS special characters`() {
        val query = repository.buildFtsQuery("cycle (chandan) [50g] \"incense\"")
        assertEquals("cycle* chandan* 50g* incense*", query)
    }

    @Test
    fun `buildFtsQuery handles extra whitespace cleanly`() {
        val query = repository.buildFtsQuery("  cycle   chandan  ")
        assertEquals("cycle* chandan*", query)
    }
}
