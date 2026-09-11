# Calendar Interchange Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export the active semester to Android Calendar or ICS, share ICS, and transactionally import one ICS timetable into Viora's Schedule view.

**Architecture:** A platform-independent `calendar` package owns event projection and ICS encoding/decoding. Room stores one replacement set of imported events, while Android-specific classes own content-URI I/O, private calendar files, sharing, and `CalendarContract`; `MainActivity` owns runtime permissions and document-picker launchers, and the ViewModel exposes immutable status/state to Schedule.

**Tech Stack:** Kotlin/JDK 17, java.time with `Asia/Kolkata`, Room 2.8.4, Jetpack Compose Material 3, Android `CalendarContract`, Storage Access Framework, FileProvider, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-02-calendar-interchange-design.md`

## Global Constraints

- VTOP-derived Room rows remain authoritative and unchanged.
- Calendar interchange never performs a network request or sends data to a third party.
- All VTOP-local date/time values use `Asia/Kolkata`.
- ICS export covers the export date through the following 180 days and honors cached holidays, exam-day suppression, and day-order substitutions.
- Import validates the full file before transactionally replacing the previous imported set.
- Room moves from version 8 to 9 with a registered forward migration, exported schema, and instrumentation coverage.
- Working/share files stay under private `files/Viora/calendar`; only explicit user export/share leaves the sandbox.
- Device-calendar writes affect only Viora's dedicated local calendar and its events.

---

### Task 1: Pure interchange event model and ICS codec

**Files:**
- Create: `app/src/main/kotlin/app/viora/calendar/CalendarInterchangeEvent.kt`
- Create: `app/src/main/kotlin/app/viora/calendar/IcsCalendarCodec.kt`
- Test: `app/src/test/kotlin/app/viora/calendar/IcsCalendarCodecTest.kt`

**Interfaces:**
- Produces: `CalendarInterchangeEvent(uid, title, details, location, startsAt, endsAt, imported)` using `Instant` boundaries.
- Produces: `IcsDecodeResult(events: List<CalendarInterchangeEvent>, skipped: Int)`.
- Produces: `IcsCalendarCodec.encode(calendarName: String, events: List<CalendarInterchangeEvent>): String`.
- Produces: `IcsCalendarCodec.decode(content: String, importStart: Instant, importEnd: Instant): IcsDecodeResult`.

- [ ] **Step 1: Write failing codec tests**

Cover CRLF output, UTF-8 text escaping/unescaping (`\\`, comma, semicolon, newline), deterministic UID preservation, `TZID=Asia/Kolkata`, folded input lines, timed single events, `RRULE:FREQ=WEEKLY`, malformed calendar rejection, end-before-start rejection, and skipped unsupported all-day rows. A representative assertion is:

```kotlin
val decoded = IcsCalendarCodec.decode(weeklyIcs, start, end)
assertEquals(listOf(1L, 8L, 15L), decoded.events.map { Duration.between(start, it.startsAt).toDays() })
```

- [ ] **Step 2: Run the codec test and verify RED**

Run: `gradle testDebugUnitTest --tests app.viora.calendar.IcsCalendarCodecTest`

Expected: compilation fails because the codec/model do not exist.

- [ ] **Step 3: Implement the minimal codec**

Parse unfolded `VCALENDAR`/`VEVENT` property lines without adding a dependency. Accept UTC (`...Z`) and local `TZID=Asia/Kolkata` date-times; require `DTSTART` and `DTEND`; materialize weekly recurrence only inside `[importStart, importEnd]`; reject malformed container syntax; increment `skipped` for unsupported but structurally valid events. Encode sorted events with stable UIDs and 75-octet-safe folded lines.

- [ ] **Step 4: Run codec tests and verify GREEN**

Run: `gradle testDebugUnitTest --tests app.viora.calendar.IcsCalendarCodecTest`

- [ ] **Step 5: Commit the codec slice**

```bash
git add app/src/main/kotlin/app/viora/calendar app/src/test/kotlin/app/viora/calendar
git commit -m "feat: add calendar ICS codec"
```

### Task 2: Export projection from cached academic data

**Files:**
- Create: `app/src/main/kotlin/app/viora/calendar/CalendarExportProjector.kt`
- Test: `app/src/test/kotlin/app/viora/calendar/CalendarExportProjectorTest.kt`
- Reuse: `app/src/main/kotlin/app/viora/AcademicProjections.kt`

**Interfaces:**
- Consumes: `SlotWithCourse`, `AcademicCalendarEntity`, `ExamEntity`, and `DigitalAssignmentEntity` snapshots.
- Produces: `CalendarExportProjector.project(semesterId, fromDate, slots, calendar, exams, assignments): List<CalendarInterchangeEvent>`.

- [ ] **Step 1: Write failing projection tests**

Assert a class on the first eligible day and last day of the 180-day inclusive window, deterministic `viora:<semester>:class:<date>:<slot>` UIDs, omission on holidays/exam days, weekday substitution for `Monday order`, exam end-time fallback when absent, deadline duration fallback, and full title/location/faculty details.

- [ ] **Step 2: Run projection tests and verify RED**

Run: `gradle testDebugUnitTest --tests app.viora.calendar.CalendarExportProjectorTest`

- [ ] **Step 3: Implement projection through existing calendar rules**

Extract or reuse the existing dated-slot logic rather than duplicating holiday/day-order matching. Use `ZoneId.of("Asia/Kolkata")`; generate classes from `fromDate` through `fromDate.plusDays(179)`; map exams and assignments whose start/due values lie in that range; sort by start then UID.

- [ ] **Step 4: Run projection and existing academic projection tests**

Run: `gradle testDebugUnitTest --tests 'app.viora.calendar.*' --tests app.viora.AcademicProjectionsTest --tests app.viora.ExamAwareScheduleTest`

- [ ] **Step 5: Commit the projection slice**

```bash
git add app/src/main/kotlin/app/viora/calendar/CalendarExportProjector.kt app/src/main/kotlin/app/viora/AcademicProjections.kt app/src/test/kotlin/app/viora/calendar
git commit -m "feat: project cached academics for calendar export"
```

### Task 3: Imported-event Room resource and v8-to-v9 migration

**Files:**
- Modify: `app/src/main/kotlin/app/viora/database/Entities.kt`
- Modify: `app/src/main/kotlin/app/viora/database/AcademicDao.kt`
- Modify: `app/src/main/kotlin/app/viora/database/VioraDatabase.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/database/VioraDatabaseInstrumentedTest.kt`
- Generate: `app/schemas/app.viora.database.VioraDatabase/9.json`

**Interfaces:**
- Produces: `ImportedCalendarEventEntity(id, title, details, location, startsEpochMillis, endsEpochMillis)`.
- Produces: `observeImportedCalendarEvents(): Flow<List<ImportedCalendarEventEntity>>`.
- Produces: `importedCalendarEventSnapshot(): List<ImportedCalendarEventEntity>`.
- Produces: transactional `replaceImportedCalendarEvents(rows)`.

- [ ] **Step 1: Add a failing v8-to-v9 instrumentation test**

Create a version-8 database containing a sentinel academic row, run `MIGRATION_8_9`, and assert that `imported_calendar_events` exists with the expected columns while the sentinel remains.

- [ ] **Step 2: Add failing DAO replacement coverage**

In the in-memory Room test, replace two imported rows, then replace with one and assert only the second set remains in start-time order.

- [ ] **Step 3: Compile instrumentation tests and verify RED**

Run: `gradle assembleDebugAndroidTest`

- [ ] **Step 4: Implement entity, DAO transaction, migration, and registration**

Set the database version to `9`; add `MIGRATION_8_9` creating the table and start-time index; append it to `addMigrations`; keep all existing migrations intact.

- [ ] **Step 5: Generate and inspect schema 9**

Run: `gradle kspDebugKotlin`

Confirm `9.json` contains the imported table and all version-8 tables.

- [ ] **Step 6: Compile tests and commit the database slice**

Run: `gradle assembleDebugAndroidTest`

```bash
git add app/src/main/kotlin/app/viora/database app/src/androidTest/kotlin/app/viora/database app/schemas/app.viora.database.VioraDatabase/9.json
git commit -m "feat: store imported calendar events"
```

### Task 4: Calendar files, import repository, and share/export I/O

**Files:**
- Modify: `app/src/main/kotlin/app/viora/storage/VioraFileStore.kt`
- Modify: `app/src/test/kotlin/app/viora/storage/VioraFileStoreTest.kt`
- Create: `app/src/main/kotlin/app/viora/calendar/CalendarInterchangeRepository.kt`
- Create: `app/src/test/kotlin/app/viora/calendar/CalendarInterchangeRepositoryTest.kt`
- Modify: `app/src/main/res/xml/file_paths.xml`

**Interfaces:**
- Produces: `VioraFileStore.calendarFile(requestedName: String): File` under `files/Viora/calendar`.
- Produces: `ImportedCalendarStore.replace(rows)` with an `AcademicDao` adapter, allowing repository tests to use an in-memory fake without Android Room.
- Produces: `CalendarInterchangeRepository.importIcs(text, now): CalendarImportSummary` that writes Room only after successful complete decode.
- Produces: `CalendarInterchangeRepository.exportIcs(...): String` and `writeShareFile(ics): File`.

- [ ] **Step 1: Write failing storage and repository tests**

Assert calendar filenames are sanitized below the private calendar directory, malformed imports preserve prior rows, valid imports replace prior rows, weekly imports materialize within 180 days, and skipped counts reach the summary without exposing event contents.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `gradle testDebugUnitTest --tests app.viora.storage.VioraFileStoreTest --tests app.viora.calendar.CalendarInterchangeRepositoryTest`

- [ ] **Step 3: Implement repository and private-file support**

Decode fully into memory, map all validated events, then call the single store replacement transaction. The production store delegates to the DAO transaction. Use UTF-8 explicitly and cap imported content at 5 MiB before parsing. Add only `Viora/` FileProvider exposure; do not add broad filesystem paths.

- [ ] **Step 4: Run focused tests and privacy audit**

Run: `gradle testDebugUnitTest --tests 'app.viora.calendar.*' --tests app.viora.storage.VioraFileStoreTest`

Run: `scripts/privacy-audit.sh`

- [ ] **Step 5: Commit the repository slice**

```bash
git add app/src/main/kotlin/app/viora/calendar app/src/main/kotlin/app/viora/storage app/src/test/kotlin/app/viora/calendar app/src/test/kotlin/app/viora/storage app/src/main/res/xml/file_paths.xml
git commit -m "feat: add private calendar interchange storage"
```

### Task 5: Dedicated Android calendar writer

**Files:**
- Create: `app/src/main/kotlin/app/viora/calendar/DeviceCalendarWriter.kt`
- Create: `app/src/test/kotlin/app/viora/calendar/DeviceCalendarPlanTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: pure `DeviceCalendarPlan` containing the dedicated calendar account/name and event values.
- Produces: `DeviceCalendarWriter.replace(events): Result<Int>`; it creates/reuses only the local `Viora timetable` calendar and replaces only events in that calendar.

- [ ] **Step 1: Write failing plan tests**

Assert local account type, `Asia/Kolkata` timezone, deterministic event sync IDs, full details/location, and replacement selection scoped to the resolved Viora calendar ID.

- [ ] **Step 2: Run focused test and verify RED**

Run: `gradle testDebugUnitTest --tests app.viora.calendar.DeviceCalendarPlanTest`

- [ ] **Step 3: Implement provider writer and permissions**

Declare `READ_CALENDAR` and `WRITE_CALENDAR`. Query/create a calendar with account name `Viora`, account type `LOCAL`, display name `Viora timetable`, and owner account `Viora`; delete/insert events only for its resolved ID in one provider batch. Return recoverable failures for permission denial or removed calendar.

- [ ] **Step 4: Run unit tests and compile app**

Run: `gradle testDebugUnitTest --tests app.viora.calendar.DeviceCalendarPlanTest assembleDebug`

- [ ] **Step 5: Commit the provider slice**

```bash
git add app/src/main/kotlin/app/viora/calendar app/src/test/kotlin/app/viora/calendar app/src/main/AndroidManifest.xml
git commit -m "feat: export academics to Android calendar"
```

### Task 6: ViewModel state, Activity contracts, and Schedule UI

**Files:**
- Modify: `app/src/main/kotlin/app/viora/VioraGraph.kt`
- Modify: `app/src/main/kotlin/app/viora/VioraAppViewModel.kt`
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt`
- Modify: `app/src/main/kotlin/app/viora/AcademicProjections.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/CalendarScreenTest.kt`

**Interfaces:**
- Adds to `VioraUiState`: `importedCalendarEvents` and `calendarInterchangeMessage`.
- Adds ViewModel actions for preparing export content, importing a picked URI, writing a created URI, sharing ICS, and completing device-calendar export after permission grant.
- Adds Schedule callbacks: export to device calendar, export ICS, import ICS, and share timetable.

- [ ] **Step 1: Extend Compose tests and verify RED**

Assert four accessible actions named exactly `Export to Viora calendar`, `Export ICS`, `Import ICS`, and `Share timetable`; assert an imported row renders with an `Imported` label; assert import errors/status are exposed as an assertive live region.

- [ ] **Step 2: Compile instrumentation tests and verify RED**

Run: `gradle assembleDebugAndroidTest`

- [ ] **Step 3: Wire state and imported-event observation**

Observe imported rows from Room in the ViewModel, map them into immutable UI state, clear them with academic cache/logout, and display imported events for the selected Schedule date without merging them into VTOP change notifications.

- [ ] **Step 4: Wire Android activity-result flows**

Use `CreateDocument("text/calendar")` for export, `OpenDocument` restricted to `text/calendar`/`text/*` for import, `RequestMultiplePermissions` only when device-calendar export is tapped, and FileProvider plus `ACTION_SEND` for share. Do not request calendar permission during setup or launch.

- [ ] **Step 5: Build the Schedule action UI**

Place the four actions in a large-font-safe flow/column near the Timetable heading, disable exports when no active semester/cache exists, confirm before replacing imported events, display safe success/failure summaries, and mark imported rows visibly and semantically.

- [ ] **Step 6: Run calendar tests, instrumentation compilation, lint, and privacy audit**

Run: `gradle testDebugUnitTest --tests 'app.viora.calendar.*' --tests app.viora.storage.VioraFileStoreTest`

Run: `gradle assembleDebug assembleDebugAndroidTest lintDebug`

Run: `scripts/privacy-audit.sh`

- [ ] **Step 7: Commit the UI slice**

```bash
git add app/src/main/kotlin/app/viora app/src/androidTest/kotlin/app/viora/CalendarScreenTest.kt
git commit -m "feat: add calendar interchange actions"
```

### Task 7: Calendar documentation and end-to-end verification

**Files:**
- Modify: `PROGRESS.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/FEATURES.md`
- Modify: `docs/TECH_STACK.md`

- [ ] **Step 1: Update documentation**

Document Room version 9, imported-event replacement semantics, private calendar files, explicit share/export boundaries, Android calendar permission timing, and the completed feature status.

- [ ] **Step 2: Run the full local verification set**

Run: `make check`

Run: `gradle assembleDebug assembleDebugAndroidTest lintDebug`

Run: `scripts/privacy-audit.sh`

- [ ] **Step 3: Run device validation when a target is attached**

Run: `adb devices`

If an API 26+ target is attached, run `scripts/device-smoke.sh --fresh`, then manually validate permission denial/grant, repeat export without duplicates, ICS create/open/share, imported replacement, narrow width, and large font. Never commit its artifacts.

- [ ] **Step 4: Inspect the final calendar diff and commit docs**

```bash
git diff --check
git status --short
git add PROGRESS.md docs/ARCHITECTURE.md docs/FEATURES.md docs/TECH_STACK.md
git commit -m "docs: record calendar interchange"
```
