package com.bhaavbook.app.csv

import com.bhaavbook.app.data.db.ProductDao
import com.bhaavbook.app.data.model.Brand
import com.bhaavbook.app.data.model.Category
import com.bhaavbook.app.data.model.Product
import com.bhaavbook.app.data.model.ProductWithVariants
import com.bhaavbook.app.data.repository.BrandRepository
import com.bhaavbook.app.data.repository.BrandResolution
import com.bhaavbook.app.data.repository.CategoryRepository
import com.bhaavbook.app.data.repository.CategoryResolution
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CsvImporterTest {

    private val dao: ProductDao = mockk(relaxed = true)
    private val brandRepository: BrandRepository = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val parser: CsvParser = mockk(relaxed = true)

    private lateinit var importer: CsvImporter

    @Before
    fun setUp() {
        importer = CsvImporter(dao, brandRepository, categoryRepository, parser)

        // Default mock behaviors
        coEvery { parser.parsePrice(any()) } answers { firstArg<String>().toDoubleOrNull() }
        coEvery { parser.parseInStock(any()) } returns true
        coEvery { brandRepository.resolveFromCsv(any(), any()) } answers {
            val name = secondArg<String?>()?.takeIf { it.isNotBlank() } ?: "DefaultBrand"
            BrandResolution(Brand(name = name, slug = "default-brand"), null)
        }
        coEvery { categoryRepository.resolveFromCsv(any(), any()) } answers {
            val name = secondArg<String?>()?.takeIf { it.isNotBlank() } ?: "DefaultCategory"
            CategoryResolution(Category(name = name, slug = "default-category"), null)
        }
    }

    @Test
    fun `import single row parses name and price and commits product`() = runTest {
        val rows = listOf(
            listOf("Agarbatti Chandan", "zed-black", "Zed Black", "agarbatti", "Agarbatti", "100 g", "45", "40", "38", "yes", "Fast moving")
        )
        val mapping = ColumnMapping.autoGuess(CsvExporter.CSV_HEADERS.toList())

        val result = importer.import(rows, mapping, DuplicateStrategy.ADD_ANYWAY)

        assertEquals(1, result.importedCount)
        assertEquals(0, result.errors.size)
        coVerify(exactly = 1) { dao.applyImport(any()) }
    }

    @Test
    fun `import multi-variant rows groups variants under same product`() = runTest {
        val rows = listOf(
            listOf("Agarbatti Chandan", "zed-black", "Zed Black", "agarbatti", "Agarbatti", "100 g", "45", "", "", "yes", ""),
            listOf("Agarbatti Chandan", "zed-black", "Zed Black", "agarbatti", "Agarbatti", "250 g", "100", "", "", "yes", "")
        )
        val mapping = ColumnMapping.autoGuess(CsvExporter.CSV_HEADERS.toList())

        val committed = mutableListOf<List<ProductWithVariants>>()
        coEvery { dao.applyImport(capture(committed)) } returns Unit

        val result = importer.import(rows, mapping, DuplicateStrategy.ADD_ANYWAY)

        assertEquals(1, result.importedCount)
        assertEquals(0, result.errors.size)
        assertEquals(1, committed.size)
        val pwv = committed.first().first()
        assertEquals("Agarbatti Chandan", pwv.product.name)
        assertEquals(2, pwv.variants.size)
        assertEquals("100 g", pwv.variants[0].variantLabel)
        assertEquals("250 g", pwv.variants[1].variantLabel)
    }

    @Test
    fun `import missing name records error row`() = runTest {
        val rows = listOf(
            listOf("", "zed-black", "Zed Black", "agarbatti", "Agarbatti", "100 g", "45", "", "", "yes", "")
        )
        val mapping = ColumnMapping.autoGuess(CsvExporter.CSV_HEADERS.toList())

        val result = importer.import(rows, mapping, DuplicateStrategy.ADD_ANYWAY)

        assertEquals(0, result.importedCount)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().reason.contains("No item name"))
    }

    @Test
    fun `import invalid price records error row`() = runTest {
        coEvery { parser.parsePrice("invalid") } returns null
        val rows = listOf(
            listOf("Agarbatti Chandan", "", "Zed Black", "", "Agarbatti", "100 g", "invalid", "", "", "yes", "")
        )
        val mapping = ColumnMapping.autoGuess(CsvExporter.CSV_HEADERS.toList())

        val result = importer.import(rows, mapping, DuplicateStrategy.ADD_ANYWAY)

        assertEquals(0, result.importedCount)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().reason.contains("not a number"))
    }

    @Test
    fun `import with SKIP strategy skips existing products`() = runTest {
        val existingProduct = Product(id = 1L, name = "Agarbatti Chandan", brand = "Zed Black")
        coEvery { dao.getByBrandAndName("Agarbatti Chandan", "Zed Black") } returns existingProduct

        val rows = listOf(
            listOf("Agarbatti Chandan", "zed-black", "Zed Black", "agarbatti", "Agarbatti", "100 g", "45", "", "", "yes", "")
        )
        val mapping = ColumnMapping.autoGuess(CsvExporter.CSV_HEADERS.toList())

        val result = importer.import(rows, mapping, DuplicateStrategy.SKIP)

        assertEquals(0, result.importedCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `import with slug conflict records warning in error list`() = runTest {
        coEvery { brandRepository.resolveFromCsv("zed-black", "Different Name") } returns BrandResolution(
            Brand(name = "Zed Black", slug = "zed-black"),
            "brand slug mismatch: slug 'zed-black' already mapped to 'Zed Black', ignoring supplied name 'Different Name'"
        )

        val rows = listOf(
            listOf("Agarbatti Chandan", "zed-black", "Different Name", "", "", "100 g", "45", "", "", "yes", "")
        )
        val mapping = ColumnMapping.autoGuess(CsvExporter.CSV_HEADERS.toList())

        val result = importer.import(rows, mapping, DuplicateStrategy.ADD_ANYWAY)

        assertEquals(1, result.importedCount)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().reason.contains("slug mismatch"))
    }
}
