# Viora architecture

This document describes the repository as it is implemented today. Viora is a
single Gradle application module (`:app`); its logical boundaries are Kotlin
packages, not separate Gradle modules.

## Decision

Build a native, offline-first Android app. Room is the single source of truth: screens observe the database and never render directly from a network response. Opening the app requests a refresh, but stale cached data remains visible with “last synced” and per-section error states.

```text
Compose screens -> ViewModels -> use cases -> repositories -> Room
                                      |              ^
                                      v              |
                                VtopGateway -> sync transaction
                                      |
                        authenticated HTML endpoints
```

## Package boundaries

Keep these responsibilities separate inside `app/src/main/kotlin/app/viora`:

```text
network/            VTOP transport, sessions, cookies, host enforcement
parser/             pure Jsoup HTML-to-domain parsing
data/               validation and transactional Room repositories
database/           entities, DAOs, schema exports, migrations
domain/             deterministic academic calculations
ui/, setup/,        Compose UI and Android integration
assignment/,
widget/,
notifications/
```

`MainActivity` owns top-level navigation. Compose screens call ViewModel
actions only; they do not access DAOs or VTOP gateways directly.

## Layer boundaries

- UI: Compose and immutable `UiState`; emits user actions only.
- Domain: attendance projections, timeline merging, deadline prioritization, and notification rules.
- Data: repositories reconcile remote snapshots into Room in transactions.
- Integration: `VtopGateway` owns endpoint calls; `parser/` owns HTML
  knowledge. No CSS selector may leak into repositories or UI.

## Core data model

- `Account`, `Campus`, `Semester`
- `Course`, `CourseOffering`, `Faculty`, `ClassSlot`, `ClassOccurrence`
- `AttendanceSnapshot` (attended, held, percentage, source timestamp)
- `Assessment`, `MarkComponent`, `Grade`
- `DigitalAssignment` (posted, due, upload state, server identity)
- `Exam` (type, course, date, session, venue, seat number)
- `CourseMaterial` (metadata locally; file only after explicit download)
- `AcademicCalendarDay`, `ClassMessage`
- `ImportedCalendarEvent` (one local replacement set from user-selected ICS)
- `SyncRun`, `SyncResourceState`, `NotificationLedger`

Remote identities are scoped by account + semester. Parsed records get deterministic fingerprints so a repeated sync is idempotent and change notifications can show exactly what changed.

The Room database is currently version 9. A schema change needs a forward
migration, registration in `VioraDatabase`, an updated exported schema, and
migration instrumentation coverage.

## Attendance engine

Store the raw numerator and denominator and calculate projections in the domain layer. For a required threshold `p`, current attended `a`, and held `h`:

- skippable future classes: largest `x >= 0` for which `a / (h + x) >= p`
- classes needed to recover: smallest `x >= 0` for which `(a + x) / (h + x) >= p`

Use exact rational/integer comparisons to avoid floating-point boundary errors. Treat cancelled classes, approved duty leave, debar rules, lab blocks, and future timetable occurrences as explicit assumptions. The UI says “projection,” displays the threshold, and never promises institutional eligibility.

CAT-1, CAT-2, and FAT planning projects cached timetable slots against cached
calendar rows in `Asia/Kolkata`. It counts starts strictly after the current
instant and before the relevant exam. For projected units `F` and skipped units
`S`, the largest whole-occurrence prefix must satisfy
`(a + F - S) * 100 >= p * (h + F)`, so later attended classes can recover a
currently low percentage and lab blocks remain indivisible.

The same global CAT/FAT suppression windows drive Home, Schedule, calendar
export, and attendance planning. Explicit VIT calendar end/resumption rows win;
otherwise Viora suppresses through the latest cached exam in the series and
resumes on the next valid instructional Monday or explicit day-order date. The
fallback is surfaced as an estimate. These values are planning aids, not
institutional attendance rulings.

## Academic views

- Home uses the shared assignment-status classifier to show only pending work
  due within seven days. Assessments includes submitted work in its weekly
  section, followed by per-course drill-down.
- Courses provides course, marks, and attendance views. Marks retain theory/lab
  identity even when display names match.
- Schedule provides current-semester timetable and local calendar views for
  cached classes, holidays, exams, instructional day orders, and other academic
  events. Calendar interchange controls follow timetable entries.
- Calendar interchange projects 180 days of cached classes with the same
  holiday/day-order rules, adds exams and assignment deadlines, and supports
  explicit Android-calendar export, ICS export/share, and transactional ICS
  import. Imported rows are local, visibly labelled, and never synced to VTOP.
- Assessment uploads use Android's document picker, read one capped file, and
  submit only to a fresh multipart action parsed from authenticated VTOP HTML.
  Upload form secrets are never stored in Room; success refreshes assignments.
- Marks and grades refresh independently. A valid marks snapshot commits even
  when the grades/CGPA path fails, while the combined resource state reports
  the partial error and preserves the prior grade cache.

## Testing and validation

- Parser golden tests using redacted HTML fixtures.
- Repository tests against in-memory Room.
- Property tests for attendance boundary calculations.
- Sync tests for partial failure, session expiry, duplicate records, and semester rollover.
- Compose/accessibility tests for key screens, including academics and calendar
  semantics.
- Migration instrumentation coverage whenever a schema path changes.
- A manual redacted-fixture contract check when parser layouts change; never
  use student credentials or live academic records in CI.

Use JDK 17 and the repository Gradle installation (`gradle`; no wrapper):

```sh
gradle testDebugUnitTest
gradle assembleDebug assembleDebugAndroidTest lintDebug
scripts/privacy-audit.sh
```

With an attached API 26+ device or emulator, run
`scripts/device-smoke.sh --fresh`. Its screenshots, UI dumps, and logs remain
private local artifacts.

## Why no backend initially

A server would require holding student credentials or reusable sessions, creates a high-value breach target, and adds operating cost. Keep authentication and scraping on-device. A future backend may distribute app configuration or parser health metadata, but must not receive personal academic data or VTOP secrets.
