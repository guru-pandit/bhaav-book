package com.bhaavbook.app.data.repository

import app.cash.turbine.test
import com.bhaavbook.app.data.db.ProductDao
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductVariant
import com.bhaavbook.app.data.model.ProductWithVariants
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductSearchTest {

    private lateinit var repository: ProductRepository
    private val dao: ProductDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        repository = ProductRepository(dao)
    }

    // -----------------------------------------------------------------------
    // FTS query building
    // -----------------------------------------------------------------------

    @Test
    fun `single word gets a trailing wildcard`() {
        assertEquals("chandan*", repository.buildFtsQuery("chandan"))
    }

    @Test
    fun `every word gets its own wildcard`() {
        assertEquals("chandan* cycle*", repository.buildFtsQuery("chandan cycle"))
    }

    /** FTS4 ANDs the tokens, so the same two words match in either order. */
    @Test
    fun `word order only changes token order`() {
        assertEquals("cycle* chandan*", repository.buildFtsQuery("cycle chandan"))
        assertEquals("chandan* cycle*", repository.buildFtsQuery("chandan cycle"))
    }

    @Test
    fun `illegal FTS characters are stripped`() {
        assertEquals(
            "cycle* chandan* 50g* incense*",
            repository.buildFtsQuery("cycle (chandan) [50g] \"incense\"")
        )
    }

    @Test
    fun `extra whitespace collapses`() {
        assertEquals("cycle* chandan*", repository.buildFtsQuery("  cycle   chandan  "))
    }

    /**
     * A hyphen is FTS4's NOT operator and a colon is its column qualifier, so
     * leaving either in place turns a shopkeeper's search into a syntax error.
     */
    @Test
    fun `FTS operators the user might type are stripped`() {
        assertEquals("kapur* tablet*", repository.buildFtsQuery("kapur-tablet"))
        assertEquals("cycle* agarbatti*", repository.buildFtsQuery("cycle: agarbatti"))
        assertEquals("chandan*", repository.buildFtsQuery("chandan*"))
    }

    /**
     * `MATCH ''` is an FTS4 syntax error, so a query with nothing searchable in
     * it has to come back empty and be routed to the LIKE path instead.
     */
    @Test
    fun `a query of pure punctuation yields no FTS query`() {
        assertEquals("", repository.buildFtsQuery("---"))
        assertEquals("", repository.buildFtsQuery("()"))
        assertEquals("", repository.buildFtsQuery("   "))
    }

    // -----------------------------------------------------------------------
    // Search routing
    // -----------------------------------------------------------------------

    @Test
    fun `a blank query reads the sorted table and never touches FTS`() = runTest {
        every { dao.getAllByName() } returns flowOf(listOf(productWithVariants(1, "Kapur")))

        repository.getProducts("", SortOrder.NAME_ASC, ProductFilter.None).test {
            assertEquals(1, awaitItem().size)
            awaitComplete()
        }
    }

    @Test
    fun `a one-character query uses LIKE, which FTS prefixes handle poorly`() = runTest {
        every { dao.searchLike("k") } returns flowOf(listOf(productWithVariants(1, "Kapur")))

        repository.getProducts("k", SortOrder.NAME_ASC, ProductFilter.None).test {
            assertEquals(1, awaitItem().size)
            awaitComplete()
        }
    }

    @Test
    fun `a punctuation-only query falls back to LIKE instead of crashing FTS`() = runTest {
        every { dao.searchLike("--") } returns flowOf(emptyList())

        repository.getProducts("--", SortOrder.NAME_ASC, ProductFilter.None).test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    // -----------------------------------------------------------------------
    // Relevance ordering
    // -----------------------------------------------------------------------

    /**
     * FTS returns hits alphabetically. Typing "chandan" should surface the item
     * actually called Chandan before one that merely mentions it further along.
     */
    @Test
    fun `a name that starts with the query sorts above one that contains it`() = runTest {
        val contains = productWithVariants(1, "Agarbatti Chandan Premium")
        val startsWith = productWithVariants(2, "Chandan Powder")
        every { dao.searchFts(any()) } returns flowOf(listOf(contains, startsWith))

        repository.getProducts("chandan", SortOrder.NAME_ASC, ProductFilter.None).test {
            assertEquals(
                listOf("Chandan Powder", "Agarbatti Chandan Premium"),
                awaitItem().map { it.product.name }
            )
            awaitComplete()
        }
    }

    @Test
    fun `an explicitly chosen sort order wins over relevance`() = runTest {
        val cheap = productWithVariants(1, "Zzz Chandan", price = 10.0)
        val dear = productWithVariants(2, "Chandan Powder", price = 900.0)
        every { dao.searchFts(any()) } returns flowOf(listOf(dear, cheap))

        repository.getProducts("chandan", SortOrder.PRICE_DESC, ProductFilter.None).test {
            assertEquals(
                listOf("Chandan Powder", "Zzz Chandan"),
                awaitItem().map { it.product.name }
            )
            awaitComplete()
        }
    }

    // -----------------------------------------------------------------------
    // Filters
    // -----------------------------------------------------------------------

    @Test
    fun `a brand filter matches regardless of how the brand was capitalised`() = runTest {
        every { dao.getAllByName() } returns flowOf(
            listOf(
                productWithVariants(1, "Agarbatti", brand = "cycle"),
                productWithVariants(2, "Kapur", brand = "Mangaldeep")
            )
        )

        repository.getProducts("", SortOrder.NAME_ASC, ProductFilter.ByBrand("Cycle")).test {
            assertEquals(listOf("Agarbatti"), awaitItem().map { it.product.name })
            awaitComplete()
        }
    }

    @Test
    fun `brand chips collapse spellings that differ only in case`() = runTest {
        every { dao.getAllBrands() } returns flowOf(listOf("Cycle", "cycle", "CYCLE", "Moksh"))

        repository.getAllBrands().test {
            assertEquals(listOf("Cycle", "Moksh"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `an out-of-stock item is hidden by the in-stock filter`() = runTest {
        every { dao.getAllByName() } returns flowOf(
            listOf(
                productWithVariants(1, "Available", inStock = true),
                productWithVariants(2, "Finished", inStock = false)
            )
        )

        repository.getProducts("", SortOrder.NAME_ASC, ProductFilter.InStockOnly).test {
            assertEquals(listOf("Available"), awaitItem().map { it.product.name })
            awaitComplete()
        }
    }

    // -----------------------------------------------------------------------
    // Duplicate detection
    // -----------------------------------------------------------------------

    @Test
    fun `an item is not treated as a duplicate of itself when edited`() = runTest {
        val existing = product(7, "Kapur", brand = "Moksh")
        coEvery { dao.getByBrandAndName("Kapur", "Moksh") } returns existing

        assertEquals(null, repository.checkDuplicate("Kapur", "Moksh", excludeId = 7))
        assertEquals(existing, repository.checkDuplicate("Kapur", "Moksh", excludeId = 0))
    }

    /** A bare product row, for DAO methods that still deal in [Product] directly. */
    private fun product(
        id: Long,
        name: String,
        brand: String? = null
    ) = Product(id = id, name = name, brand = brand)

    /** A product with a single "Standard" variant, for the DAO's list/search queries. */
    private fun productWithVariants(
        id: Long,
        name: String,
        brand: String? = null,
        price: Double = 45.0,
        inStock: Boolean = true
    ) = ProductWithVariants(
        product = product(id, name, brand),
        variants = listOf(
            ProductVariant(
                id = id,
                productId = id,
                variantLabel = "Standard",
                sellingPrice = price,
                inStock = inStock
            )
        )
    )
}
