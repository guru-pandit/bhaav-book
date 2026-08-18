---
name: qa-audit
description: Run a systematic manual QA pass over the Bhaav Book app by tracing every write path in the code (no emulator required) and produce/update issue.md with severity-ranked findings. Use when asked to "test the app end to end", "QA the app", "find bugs", "audit for issues", or before a release when there's no attached device/emulator to drive the UI live.
---

# QA audit for Bhaav Book

This app is a shopkeeper's price book: Room DB + DataStore settings, no
backend, no undo outside the explicit 4.5s delete-undo window. That means
every silent data-loss bug is permanent for the shopkeeper the moment it
happens — this skill exists because that class of bug does not show up by
skimming the UI, only by tracing what each write path actually persists.

## When there's no device/emulator (the common case here)

Check first: `adb devices`, and whether a JDK is on `PATH`
(`java -version`) so `./gradlew` can even run. Both are frequently absent in
a CLI-only environment. If so, **this is a code-reading audit, not a
click-through** — say so explicitly in the output rather than implying the
UI was driven. Cross-check every suspected bug against:

- `app/src/test/**` (JVM unit tests) — do they exercise the real
  implementation, or a `mockk(relaxed = true)` that hides it? A relaxed mock
  of a DAO/repository means the *real* persistence logic (deletes, cascades,
  upserts) was never actually run by that test.
- `app/src/androidTest/**` (instrumented E2E tests, see the `e2e-testing`
  skill) — does an existing flow test cover the *edit-then-reload* or
  *dismiss-then-reopen* round trip, or only the happy path (add, or edit
  without reloading)?

A bug that survives both checks uncovered is a real, evidenced finding, not
a guess — say which tests you checked and confirmed don't cover it.

## Where this app's bugs actually hide

Four classes of mistake produced every real bug found in the first audit
(see `issue.md` at the repo root for the full writeup) — check for these
specifically in every screen/ViewModel pair, not just the ones below:

1. **UI state that looks deleted but was never told to the DB.** A
   ViewModel method that mutates only `_state.value` (e.g. removing an item
   from a list) and a `save()` that only *upserts* the remaining items,
   never diffs against what was originally loaded to issue deletes. Confirm
   by reading the `save()`/`commit()` function fully — don't assume a method
   named `deleteX` reaches a DAO; check where it's actually called from.
   Found in `ProductEditViewModel.deleteVariant()`.

2. **A reactive `Flow` collector clobbering local edits.** Any screen that
   both collects a DB-backed `Flow` into `_state` *and* lets the user mutate
   `_state` locally before save is a candidate: if the local mutation isn't
   also written through to the DB, the next Flow emission silently restores
   the pre-mutation value. Trace what the collector does on every emission,
   not just the first one.

3. **Dialog `onDismissRequest` wired to the wrong action.** Every
   `AlertDialog`/`ModalBottomSheet` with a destructive or hard-to-reverse
   confirm action: check that `onDismissRequest` (back button / tap-outside)
   goes to the *cancel* path, not silently copy-pasted from the confirm
   button's `onClick`. This is a one-line, high-confidence, easy-to-miss bug
   class — grep for `onDismissRequest = on[A-Z]` and verify each one by name.
   Found in `ManageBrandsScreen.kt`'s slug-change warning.

4. **Bulk write paths (CSV import, migrations) that delete-then-reinsert
   instead of diff-and-upsert.** Any `@Transaction` DAO method that does
   `deleteAllXForY(id)` followed by re-inserting only what's in the current
   batch is a data-loss risk if the batch can legitimately be a *subset* of
   what already exists (a partial price-update sheet, for instance). Ask:
   "what happens to the rows currently in the DB that aren't in this
   batch?" — if the answer is "gone, with no summary line saying so," that's
   a finding. Found in `ProductDao.applyImport()` under
   `DuplicateStrategy.UPDATE`.

## Also always check

- **Migrations** (`Migrations.kt`): does `AppModule`'s `Room.databaseBuilder`
  call `fallbackToDestructiveMigration()`? It shouldn't — this DB has no
  cloud backup, so a missing `Migration` object must crash loudly, never
  silently wipe the shopkeeper's price list. Also check every migration
  actually carries old data forward (e.g. v1→v2's price/cost/stock →
  "Standard" variant backfill) rather than just changing the schema.
- **CSV round-trip**: export → re-import should reproduce the same data.
  Specifically check `CsvExporter.CSV_HEADERS` order matches what
  `AppField.guessFromHeader` auto-detects, and that every field written by
  the exporter has a corresponding parse path in `CsvImporter`.
- **Validation parity**: does manual entry (ViewModel form validation, e.g.
  `MAX_PRICE` in `ProductEditViewModel`) apply the same rules as the CSV
  import path? A gap here means a spreadsheet typo can smuggle in data a
  human typing the same value would have been blocked from entering.
- **Entity `createdAt`/immutable-field handling**: any `update()`/`save()`
  path that reconstructs an entity from scratch (`Brand(id = ..., name =
  ...)`) rather than copying the loaded entity (`existing.copy(name = ...)`)
  silently resets every field with a default value, most commonly
  `createdAt`. Grep for entity constructors inside `update`/`edit`/`save`
  functions and check every field has a real source, not a default.

## Output format

Write (or update) `issue.md` at the repo root. Structure per finding:

- **Severity** — High (silent data loss / wrong control action), Medium
  (incorrect state, not yet user-visible), Low (validation/consistency gap).
- **Where** — file + line range, linked.
- **What happens** — the actual code, quoted.
- **Repro** — concrete steps a shopkeeper would take.
- **Why it matters** — the real-world consequence.
- **Test coverage** — name the specific test file(s) checked and confirm
  whether the scenario is covered; if mocked, say what the mock hides.
- **Suggested fix** — concrete, not "add more validation."

End with a "What was checked and found OK" section — this makes clear the
audit was systematic, not a lucky grep, and tells the next reader what
doesn't need re-checking.

## After the audit: close the loop

For each **High** finding, consider whether it's cheap to add a regression
test via the `e2e-testing` skill (an edit-delete-reload round trip, a
dialog-dismiss path, an UPDATE-strategy CSV import against a
multi-variant product) so the fix — whenever it lands — can't silently
regress again. Don't fix the bugs yourself unless asked; this skill is for
finding and documenting them.
