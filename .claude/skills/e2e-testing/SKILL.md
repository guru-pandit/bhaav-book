---
name: e2e-testing
description: Write and run end-to-end (instrumented Compose UI) tests for the Bhaav Book Android app — real navigation, real Hilt-wired ViewModels and repositories, an in-memory Room database. Use when asked to add an E2E/UI/instrumented test, cover a user flow (add/edit product, CSV import, settings, manage brands), or run `connectedAndroidTest`.
---

# E2E testing for Bhaav Book

This app had no instrumented tests before this skill was added. Everything
below — the Hilt test runner, the in-memory DB module, the Gradle wiring — now
exists in the repo. This skill documents the conventions so new E2E tests stay
consistent, and what's still a manual follow-up (CI).

## What "E2E" means here

A real device/emulator launches `MainActivity` for real, through Hilt, with
real `NavGraph` navigation and real ViewModels talking to real Room DAOs —
just pointed at an in-memory database instead of the shopkeeper's actual file.
No mocks, no fakes. That's the line that separates these from the unit tests
in `app/src/test` (which do use MockK/Turbine and belong there instead).

Only write an E2E test for a user-visible flow that crosses a screen boundary
or hits the database — not for logic a unit test already covers faster and
more reliably (parsing, formatting, a single ViewModel's state machine).

## Where things live

```
app/src/androidTest/java/com/bhaavbook/app/
├── HiltTestRunner.kt                    # testInstrumentationRunner — boots HiltTestApplication
├── di/TestAppModule.kt                  # @TestInstallIn, replaces AppModule with an in-memory DB
├── AddProductFlowTest.kt                # add a product, see it in the price list
├── ProductEditDeleteFlowTest.kt         # edit a variant's price; delete with undo
├── ProductSearchFilterSortFlowTest.kt   # search, filter chips (category/in-stock), sort sheet
├── ManageBrandsFlowTest.kt              # add/edit(+slug warning)/delete a brand; add a category
├── SettingsFlowTest.kt                  # currency symbol
└── CsvImportFlowTest.kt                 # full import flow + a slug-conflict warning row
```

Gradle wiring already in place (`app/build.gradle.kts`, `gradle/libs.versions.toml`):
`testInstrumentationRunner = "com.bhaavbook.app.HiltTestRunner"`, plus
`hilt-android-testing`, `kspAndroidTest(libs.hilt.compiler)`, the Compose test
artifacts, `androidx-test-runner`, and `androidx-espresso-intents` (needed only
for stubbing the CSV import file picker — see below). You shouldn't need to
touch any of that to add a new test — just add a new `.kt` file under
`androidTest`.

## Writing a new test

Every E2E test class follows this shape:

```kotlin
package com.bhaavbook.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SomeFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun scenario_expectedOutcome() {
        // drive the UI, assert what the shopkeeper would see
    }
}
```

The rule order matters: Hilt's test component must exist before
`MainActivity` launches, which is why `hiltRule` is `order = 0`.

### Finding nodes

The app has no `Modifier.testTag`s, and adding them just for tests is not
worth the churn — every screen already has unique, stable, user-facing text
(button labels, field labels, placeholders) that `onNodeWithText` can match
directly. This works for a real M3 `OutlinedTextField`'s label: tapping/typing
on the label's text node acts on the underlying field, which is the standard
Compose testing idiom. It does **not** work for the hand-rolled `SearchField`
on `ProductListScreen` (a bare `BasicTextField` with a sibling placeholder
`Text`, not a label slot) — locate that one with
`onNode(hasSetTextAction())` instead, since it's the only editable field on
that screen.

**`onNodeWithText` does not match `contentDescription`.** Icon-only buttons
(FAB, sort/overflow icons, row-level edit/delete icons) are reachable only via
`onNodeWithContentDescription`. A product row's own semantics are set with
`.semantics { contentDescription = "$displayTitle, $priceLabel" }`
(`ProductRow` in `ProductListScreen.kt`) — and the price `Text` inside
`PricePill` is wrapped in `clearAndSetSemantics {}`, so the price is *only*
reachable through that contentDescription, never through `onNodeWithText`.
Assert rows with `onNodeWithContentDescription("$displayTitle, $priceLabel",
substring = true)`, not `onNodeWithText`. (An earlier version of
`AddProductFlowTest` used `onNodeWithText` for both the empty-state button and
the row assertion and neither actually matched anything live — a reminder to
read the source strings and semantics, not assume from a description.)

Reuse these known-good anchors:
- `"Add item"` (empty-state button; the FAB has the same string but only as a
  contentDescription), `"Save"`, `"Add variant"`, `"Add"` / `"Update"`
  (variant sheet), `"Product name *"`, `"Variant label *"`,
  `"Selling price (₹) *"`.
- `"Edit"` / `"Delete"` (PriceSheet's own text buttons) vs. `"Edit variant"` /
  `"Delete variant"` and `"Edit"` / `"Delete"` as bare content descriptions
  elsewhere (the per-row icon buttons on `ProductEditScreen` and
  `ManageBrandsScreen`) — same words, different matcher, don't mix them up.
- Category filter chips are safe to click via exact `onNodeWithText` (a
  product row folds its category into one combined "pack · category" string,
  so the bare category name never appears as its own text node). Brand filter
  chips are **not** safe the same way — a row's brand is its own standalone
  `Text`, so it merges into the row's semantics as an exact-match string too,
  and `onNodeWithText("SomeBrand")` becomes ambiguous once a matching row
  exists. Prefer category chips, or brand names that don't collide.
- `SettingsRepository` is backed by real DataStore, not swapped by
  `TestAppModule` — its state persists across every test in the same
  instrumentation run. Any test that changes a setting (currency symbol, show
  cost/wholesale price, …) must reset it in `@After`, or it leaks into
  unrelated tests that assume defaults (see `SettingsFlowTest`).
- CSV import's file picker (`ActivityResultContracts.OpenDocument`) opens a
  real system UI that can't be driven from Compose test APIs. Stub it with
  Espresso Intents (`androidx-espresso-intents`): write a real file under the
  app's own `cache/shared/` (the one directory `file_provider_paths.xml`
  exposes), get a `content://` `Uri` via `FileProvider`, then
  `Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(...)`
  before tapping "Choose CSV file". See `CsvImportFlowTest` for the full
  pattern, including `Intents.init()`/`release()` placement.

Check exact button/label text in the screen's source before writing a new
selector — don't assume a string without reading the composable.

### Waiting for async state

ViewModels write through real coroutines to a real (in-memory) Room database
— there's a genuine round trip, not a synchronous fake. Compose's test clock
auto-advances, but a DB write/read still needs:

```kotlin
composeRule.waitUntil(timeoutMillis = 5_000) {
    composeRule.onAllNodesWithText("Amul Butter", substring = true)
        .fetchSemanticsNodes().isNotEmpty()
}
```

Never `Thread.sleep` — it's the classic source of flaky instrumented tests.

### Seeding state without driving the UI

If a test needs existing data (e.g. testing search or filters), don't build it
by tapping through "Add product" repeatedly — inject the repository/DAO
directly and insert before the assertions:

```kotlin
@Inject lateinit var productRepository: ProductRepository

@Before
fun setUp() {
    hiltRule.inject()
}

@Test
fun searching_filtersToMatchingProduct() = runTest {
    productRepository.insert(Product(name = "Tata Salt", ...))
    // ... drive only the part of the UI actually under test
}
```

Each test gets its own fresh in-memory database (`TestAppModule` provides a
new one per process/run) — there's no state to clean up between tests.

## Running

Instrumented tests need a connected device or running emulator — this repo's
own note in `build.gradle.kts`/CI applies here too: there is no Android SDK on
a developer laptop by default, so run these either in Android Studio (which
has an SDK + emulator) or let CI run them once wired up (see below).

```
./gradlew connectedDebugAndroidTest
# single class:
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bhaavbook.app.AddProductFlowTest
```

## Not yet done: CI

`connectedAndroidTest` is **not** wired into `.github/workflows/build.yml` or
`develop.yml` — both only run `testDebugUnitTest` + `lintDebug` today. Adding
an emulator job (e.g. `reactivecircus/android-emulator-runner`, or Gradle
Managed Devices) is a real CI cost/time tradeoff and a CI pipeline change, so
it's left as a deliberate decision for a human, not something to add silently
alongside a test. Ask before touching the workflow files if a future task
seems to call for it.
