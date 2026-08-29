# Chaitanya Stores — BhaavBook Architecture & Developer Documentation

> **Tagline:** *"Every price, in one second."*
> **App Display Name:** `Chaitanya Stores`
> **Application ID / Package:** `com.bhaavbook.app`
> **Module Name:** `bhaavbook`
> **Target SDK:** 35 | **Min SDK:** 24 | **Java / JVM:** 17

---

## 1. Executive Summary & Purpose

**Chaitanya Stores** is an offline-first Android price-lookup app for retail pooja and religious materials shops. Store owners stock hundreds of products across multiple brands (Cycle, Mangaldeep, Moksh, Patanjali, etc.) and need to look up the selling price **in under 3 seconds**, hand the phone to the customer, and move on.

It is deliberately a small app. The one job — show a price, fast, in large legible text — is the whole product. Every feature below exists to serve that job or to keep the price list stocked (add/edit, CSV import/export) without adding surface area the job doesn't need.

### Core Features
- **Sub-second lookup:** auto-focused search on launch, 180ms-debounced FTS4 search, results ranked by relevance (exact name match → starts-with → brand match → contains).
- **Arm's-length price display:** a bottom sheet showing the selling price in large, high-contrast, tabular-figure text (56–80 sp depending on the Settings size, auto-shrinking for long numbers so `₹1,23,456` never wraps).
- **Indian price formatting:** `1,23,456` grouping, whole rupees drop the paise (`45` not `45.00`).
- **Bulk CSV import/export:** header auto-detection, 10-row preview, column mapping, duplicate handling (Skip / Update / Add anyway), per-row error collection with a re-exportable "fix these rows" CSV, atomic commit (all rows land or none do).
- **One-tap CSV sharing:** WhatsApp / Email / Drive via the Android share sheet, backed by `FileProvider`.
- **100% offline & private:** no `INTERNET` permission anywhere in the manifest. Nothing leaves the phone except through a share the user explicitly triggers.
- **Light and dark themes**, both built from the same warm terracotta/maroon/gold palette — dark mode is a distinct warm near-black, not an inverted grey.

---

## 2. Technology Stack

| Layer | Technology | Details |
|---|---|---|
| **Language** | Kotlin | Coroutines, StateFlow, Flow operators |
| **UI Framework** | Jetpack Compose + Material 3 | Custom color scheme, custom `Shapes`, tabular-figure price text |
| **Architecture** | MVVM | StateFlow UI State → ViewModel → Repository → DAO |
| **Database** | Room + SQLite FTS4 | FTS4 virtual table (`products_fts`) with Room auto-triggers |
| **Dependency Injection** | Hilt (Dagger) | `@HiltViewModel`, `@Singleton` repositories, one `@ApplicationScope` `CoroutineScope` |
| **Preferences** | Jetpack DataStore | Preferences DataStore (`bhaavbook_settings`) |
| **CSV Engine** | OpenCSV + SAF | OpenCSV 5.9 + Storage Access Framework (no legacy storage permissions) |
| **Build Tooling** | Gradle Kotlin DSL (`.kts`) | Version Catalog (`libs.versions.toml`), KSP code generation, AGP 8.7.3 |

---

## 3. Brand & Design System

The UI is warm and traditional rather than generic Material blue-on-white, and — unlike the first pass at this app — the same warmth carries into dark mode instead of falling back to cold greys.

### Color roles (`ui/theme/Color.kt`, `ui/theme/Theme.kt`)

Screens must read colors through `MaterialTheme.colorScheme`, never the raw `Color.kt` constants directly, so light/dark both work from one code path. The role conventions:

| Role | Used for | Light | Dark |
|---|---|---|---|
| `primary` / `onPrimary` | Prices, primary buttons | Terracotta / Cream | Bright terracotta / deep maroon |
| `secondary` / `onSecondary` | Top app bar, selected filter chips | Maroon / Cream | Maroon / warm cream text |
| `onPrimaryContainer` | Headings drawn on the page background | Maroon | Bright terracotta |
| `tertiary` | Brand name text | Gold (darkened for AA on cream) | Light gold |
| `surfaceContainer` | Cards, search field, unselected chips | Warm cream-dark | Warm near-black surface |
| `error` / `errorContainer` | Out-of-stock badges, delete action | Deep red | Lighter red for dark contrast |

`secondary` is a **background** role (the top bar), not a text color — a heading that used `secondary` for its text would disappear against the dark-mode top bar. Headings use `onPrimaryContainer` instead. This distinction was a live bug in the app's first pass and is now enforced by convention: see the doc comment at the top of `Theme.kt`.

### Typography (`ui/theme/Type.kt`)
- **Headings / brand name:** `FontFamily.Serif` (maps to Noto Serif on-device — no bundled font files).
- **Body, list rows, prices:** `FontFamily.SansSerif`.
- **Prices specifically** use `TabularFigures` (`fontFeatureSettings = "tnum"`) so digits are monospaced — a changing price total doesn't jitter as digit widths shift.

### Shapes
A custom `Shapes` (`extraSmall` 6dp → `extraLarge` 28dp) — rounded but not pill-shaped, which reads as retail-premium rather than playful.

---

## 4. Data Model & Database Schema

### Entity: `Product` (`products` table)

```kotlin
@Entity(tableName = "products", indices = [...name, brand, category, updated_at...])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,                  // Required
    val brand: String? = null,
    val category: String? = null,
    val sellingPrice: Double,          // Required, shown to the customer
    val unit: ProductUnit = ProductUnit.PIECE,
    val quantityValue: Double? = null, // Pack size, e.g. 100 with unit GRAM
    val inStock: Boolean = true,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

`Product.packLabel` (e.g. `"100 g"`, `"pc"`) replaces the old `priceDisplayString()` — price formatting and pack-size formatting are separate concerns now, so a screen can compose them differently (list row vs. hero price sheet) without a hardcoded combined string.

### `ProductUnit`
`GRAM, KG, ML, LITRE, PIECE, PACKET, BOX, BUNDLE, PAIR, DOZEN, METRE`, parsed case-insensitively from display name, short code, or enum name via `ProductUnit.fromString()`.

### FTS4 virtual table: `ProductFts` (`products_fts`)
Content-linked to `Product`, indexing `name`, `brand`, `category`.

### Room migrations
`AppModule` builds the database **without** `fallbackToDestructiveMigration()`. This is deliberate: the price list is the shop's only copy of its data, and a schema bump with no supplied `Migration` must fail loudly in development rather than silently wipe a live install. Any future schema change needs a real `Migration`.

---

## 5. Search Architecture

`ProductRepository.getProducts(query, sortOrder, filter)`:

1. **Blank query** → sorted full-table Room query, no FTS overhead.
2. **1 character, or nothing left after stripping FTS operators** → `LIKE %q%` fallback (FTS4's minimum indexable token is 3 characters, and `MATCH ''` is a syntax error).
3. **2+ characters** → FTS4, every token prefix-matched and ANDed:
   ```kotlin
   fun buildFtsQuery(raw: String): String {
       val cleaned = raw.replace(FTS_OPERATORS, " ").trim()
       if (cleaned.isEmpty()) return ""
       return cleaned.split(WHITESPACE).filter { it.isNotBlank() }
           .joinToString(" ") { "$it*" }
   }
   ```
   `FTS_OPERATORS` strips everything FTS4 treats as syntax — quotes, brackets, `-` (NOT), `:` (column qualifier), `,`/`;`, and the `*` the function adds itself — so a shopkeeper typing `"kapur-tablet"` or `"cycle: agarbatti"` gets a search, not a crash. If FTS still throws for any reason, the flow degrades to the `LIKE` path rather than surfacing an error.
4. **Result ordering.** FTS returns hits in name order regardless of match quality, which buries what the shopkeeper is actually typing toward. When the sort order is the default (`NAME_ASC`), hits are re-ranked by relevance: exact name match → name starts with query → brand starts with query → a word inside the name starts with query → name merely contains query. Choosing an explicit sort (price, brand, recency) overrides relevance and is honoured as-is.
5. **Filters** (`ProductFilter.ByBrand`, `ByCategory`, `InStockOnly`) are applied case-insensitively after the search/sort step.

---

## 6. CSV Import / Export Engine

### Expected headers (case-insensitive, any order, auto-mapped with a manual override step)
`name, brand, category, selling_price, unit, quantity_value, in_stock, notes`

### Importer (`csv/CsvImporter.kt`)
- Delimiter auto-detection now covers **comma, semicolon, and tab** (a majority vote over the header line).
- Price parsing accepts `₹45`, `Rs. 45`, `1,200.50`, `INR 45`, non-breaking-space thousands separators — and **rejects** anything not purely numeric after cleaning (`"abc123xyz"` is an error row, not a guess at `123`).
- Per-row errors are collected without halting the import; the file's other 299 good rows still land.
- **The database write is a single transaction** (`ProductDao.applyImport`): all rows commit or none do. Parsing failures are per-row and non-fatal; a failure during the actual write is all-or-nothing.
- Within one file, a later row for the same brand+name supersedes an earlier one before either touches the database.
- Blank trailing lines (normal in spreadsheet exports) are dropped before row-counting, so they don't inflate the error count.

### Exporter (`csv/CsvExporter.kt`)
- SAF document creation for exporting to phone storage.
- One-tap share via `FileProvider`, scoped to a dedicated `cache/shared/` subdirectory (see `file_provider_paths.xml`) — the grant covers exactly the shared file, not the app's whole cache.
- `error_reason` CSV export for failed import rows, aligned to the *original* file's headers so the user can fix and re-import.

---

## 7. Directory & File Structure

```
d:\PROJECTS\bhaav-book\
├── .github/workflows/build.yml      # CI: unit tests + lint, then debug/release APK builds
├── app/
│   ├── build.gradle.kts             # App config, optional release signing from env/secrets
│   ├── proguard-rules.pro           # R8 rules for Room, OpenCSV reflection, enum-by-name
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml  # No permissions; FileProvider; splash theme
│       │   ├── java/com/bhaavbook/app/
│       │   │   ├── BhaavBookApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── csv/             # ColumnMapping, CsvExporter, CsvImporter, CsvParser
│       │   │   ├── data/
│       │   │   │   ├── db/          # AppDatabase, ProductDao, Converters
│       │   │   │   ├── model/       # Product, ProductUnit, ProductFts
│       │   │   │   ├── repository/  # ProductRepository
│       │   │   │   └── settings/    # SettingsRepository
│       │   │   ├── di/              # AppModule (DB, DAO, ApplicationScope)
│       │   │   ├── format/          # PriceFormat.kt — Indian grouping, editable/quantity strings
│       │   │   └── ui/
│       │   │       ├── navigation/  # NavGraph, Screen
│       │   │       ├── screens/     # ProductListScreen, PriceSheet, ProductEditScreen,
│       │   │       │                # CsvImportScreen, SettingsScreen
│       │   │       ├── theme/       # Color, Theme, Type
│       │   │       └── viewmodel/   # ProductListViewModel, ProductEditViewModel,
│       │   │                        # CsvImportViewModel, SettingsViewModel
│       │   └── res/
│       │       ├── drawable/        # logo_master.png, ic_launcher_*, ic_splash_logo
│       │       ├── mipmap*/         # Adaptive icon (v26+) and a flat fallback (<v26)
│       │       ├── values*/         # strings.xml, themes.xml, colors.xml (+ values-night)
│       │       └── xml/             # backup_rules, data_extraction_rules, file_provider_paths
│       └── test/                    # CsvParserTest, ProductSearchTest, PriceFormatTest
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/libs.versions.toml
├── .gitignore                       # Excludes build output and any local keystore
└── settings.gradle.kts
```

---

## 8. Build & CI

There is intentionally no local Android setup for this project — **the APK is only ever built by GitHub Actions.** `.github/workflows/build.yml` runs on every push/PR to `main`/`master`:

1. **`verify`** — unit tests (`gradle testDebugUnitTest`) and Android lint (`gradle lintDebug`, `abortOnError = true`), reports uploaded as artifacts.
2. **`debug-apk`** — `assembleDebug`, uploaded as `chaitanya-stores-debug`.
3. **`release-apk`** — `assembleRelease`. Signing is optional: with `RELEASE_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` set as repo secrets it produces a signed APK; unset, it still builds (proving R8 didn't break anything) and produces an unsigned one.
4. **`publish`** — on a `v*` tag push, attaches the signed release APK to a GitHub Release.

Local commands (for reference — normally run by CI, not on a dev machine):
```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew lintDebug --stacktrace
./gradlew assembleDebug --stacktrace
```

### 8.1 One-time release signing setup

**Android will not install an unsigned APK** — it fails with "invalid package" / "There was a problem parsing the package". Until the four secrets below exist, the release job produces `app-release-unsigned.apk` and CI labels the artifact `UNSIGNED-cannot-be-installed`. Use the debug APK in the meantime; it is signed with the standard Android debug key and installs fine.

Generating the key needs `keytool`, which ships with any JDK. With no JDK on the machine, Docker gives you one without installing anything permanently. Start Docker Desktop, then run this **once** from the repo root.

**PowerShell** (preferred on Windows — Git Bash mangles the `-v` path):

```powershell
docker run --rm -v "${PWD}:/work" -w /work eclipse-temurin:17-jdk `
  keytool -genkeypair -v -keystore release.keystore -alias bhaavbook `
    -keyalg RSA -keysize 2048 -validity 10000 `
    -storepass 'CHOOSE_A_STRONG_PASSWORD' -keypass 'CHOOSE_A_STRONG_PASSWORD' `
    -dname "CN=Chaitanya Stores, O=Chaitanya Stores, L=Pune, C=IN"

[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) |
  Set-Content -NoNewline release.keystore.b64
```

**macOS / Linux:**

```bash
docker run --rm -v "$PWD":/work -w /work eclipse-temurin:17-jdk \
  keytool -genkeypair -v -keystore release.keystore -alias bhaavbook \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass 'CHOOSE_A_STRONG_PASSWORD' -keypass 'CHOOSE_A_STRONG_PASSWORD' \
    -dname "CN=Chaitanya Stores, O=Chaitanya Stores, L=Pune, C=IN"

base64 -w0 release.keystore > release.keystore.b64
```

Add these four repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the entire contents of `release.keystore.b64` |
| `RELEASE_KEYSTORE_PASSWORD` | the password chosen above |
| `RELEASE_KEY_ALIAS` | `bhaavbook` |
| `RELEASE_KEY_PASSWORD` | the password chosen above |

Then back the keystore up somewhere private and delete the working copies. Both filenames are covered by `.gitignore`, but they should not linger in the repo folder either:

```bash
rm release.keystore release.keystore.b64
```

Re-run the workflow after adding the secrets. The artifact will be named `chaitanya-stores-release` (not `UNSIGNED-cannot-be-installed`) and the APK inside will be `app-release.apk` rather than `app-release-unsigned.apk`. Remember the download is a **ZIP** — unzip it before moving the APK to the phone.

> **Keep a backup of the keystore somewhere safe and private before deleting it.** Android will only let an installed app be updated by an APK signed with the *same* key. Lose this key and the only way to ship an update is to uninstall first — which deletes the shop's entire price list. (Recoverable only if they exported a CSV beforehand.)
>
> Never commit the keystore, and never paste it into a CI log or a workflow artifact — on a public repository both are readable by anyone.

---

## 9. Developer Guidelines for AI Agents

1. **Preserve offline privacy.** Never add `android.permission.INTERNET` or a remote SDK.
2. **Preserve Storage Access Framework.** Use SAF (`OpenDocument` / `CreateDocument`) or `FileProvider` URIs for all file I/O. Never request legacy storage permissions.
3. **Preserve search speed and correctness.** Any change to `ProductRepository.getProducts()` must keep the FTS-operator stripping intact (see §5) — an unstripped `-`, `:`, or bare `MATCH ''` will crash or silently misbehave the search, not just slow it down.
4. **Preserve the import transaction boundary.** `CsvImporter.import()` must keep committing through `ProductDao.applyImport()` as one transaction. Per-row validation failures are fine to collect and continue past; a partial database write on a real error is not.
5. **Respect the color-role convention.** Headings use `onPrimaryContainer`, never `secondary` (a background role — see §3). When adding a new screen, pull colors from `MaterialTheme.colorScheme`, not the raw constants in `Color.kt`.
6. **No local build assumptions.** Do not add tooling, scripts, or docs that assume a local Android SDK/emulator exists — this project is built and tested exclusively through the GitHub Actions workflow in §8.
7. **Room schema changes need a real `Migration`.** `fallbackToDestructiveMigration()` is deliberately absent from `AppModule`; do not re-add it.
