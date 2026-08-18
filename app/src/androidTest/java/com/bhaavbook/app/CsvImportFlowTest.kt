package com.bhaavbook.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bhaavbook.app.csv.CsvExporter
import com.bhaavbook.app.data.repository.BrandRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Drives CSV import end to end, including the SAF file picker: real UI, real
 * [com.bhaavbook.app.csv.CsvImporter], real (in-memory) database — the only
 * stand-in is the picker's result, stubbed with Espresso Intents because the
 * system file-picker UI itself is outside the app and cannot be driven here.
 *
 * The stubbed [Uri] points at a real file under the app's own
 * `cache/shared/` directory (declared in `file_provider_paths.xml`), so the
 * importer's `ContentResolver.openInputStream` call is genuine, not mocked.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CsvImportFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var brandRepository: BrandRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    private fun writeCsv(fileName: String, rows: List<Array<String>>): Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, fileName)
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(CsvExporter.CSV_HEADERS.joinToString(","))
            writer.newLine()
            rows.forEach { row ->
                writer.write(row.joinToString(","))
                writer.newLine()
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun stubFilePicker(uri: Uri) {
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(result)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun importingACsv_addsTheItemToThePriceList() {
        val uri = writeCsv(
            "import_basic.csv",
            listOf(
                arrayOf(
                    "Kapur Tablet", "mangaldeep", "Mangaldeep", "kamphor", "Kamphor",
                    "50 g", "60", "55", "50", "yes", ""
                )
            )
        )
        stubFilePicker(uri)

        // First-run empty state offers "Import from CSV" directly.
        composeRule.onNodeWithText("Import from CSV").performClick()
        composeRule.onNodeWithText("Choose CSV file").performClick()

        waitForText("Map columns →")
        composeRule.onNodeWithText("Map columns →").performClick()

        waitForText("Import 1 rows")
        composeRule.onNodeWithText("Import 1 rows").performClick()

        waitForText("Import finished")
        composeRule.onNodeWithText("Done").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Mangaldeep — Kapur Tablet", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Mangaldeep — Kapur Tablet, ₹60", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun importingACsv_withASlugConflict_reportsARowWarning() {
        // A brand already owns the "zed-black" slug under a different name —
        // the row should still import, but with a mismatch warning attached.
        runBlocking { brandRepository.save("Existing Brand", customSlug = "zed-black") }

        val uri = writeCsv(
            "import_conflict.csv",
            listOf(
                arrayOf(
                    "Agarbatti Chandan", "zed-black", "Zed Black", "", "",
                    "100 g", "45", "", "", "yes", ""
                )
            )
        )
        stubFilePicker(uri)

        composeRule.onNodeWithText("Import from CSV").performClick()
        composeRule.onNodeWithText("Choose CSV file").performClick()

        waitForText("Map columns →")
        composeRule.onNodeWithText("Map columns →").performClick()

        waitForText("Import 1 rows")
        composeRule.onNodeWithText("Import 1 rows").performClick()

        waitForText("Import finished")
        composeRule.onNodeWithText("brand slug mismatch", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("Done").performClick()

        // The brand slug already existed, so the product keeps that brand's
        // real name ("Existing Brand"), not the CSV's mismatched "Zed Black".
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(
                "Existing Brand — Agarbatti Chandan",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
