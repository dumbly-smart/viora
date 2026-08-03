# Architecture

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

## Modules

Start with bounded Gradle modules without creating one module per screen:

```text
app                 application, navigation, top-level UI
core:model          domain models and identifiers
core:database       Room entities, DAOs, migrations
core:network        cookies, CSRF, retry policy, VTOP gateway
core:parser         pure HTML-to-model parsers and fixtures
core:designsystem   theme and reusable components
core:notifications  channels, scheduling, deep links
core:testing        fakes and fixture helpers
feature:setup       login, CAPTCHA, consent, campus selection
feature:home        today, next class, deadlines, sync health
feature:schedule    timetable, calendar, exams
feature:courses     attendance, marks, grades, materials
feature:tasks       DAs and assessment timeline
feature:settings    notification rules, data export, logout
sync                orchestration and WorkManager workers
```

## Layer boundaries

- UI: Compose and immutable `UiState`; emits user actions only.
- Domain: attendance projections, timeline merging, deadline prioritization, and notification rules.
- Data: repositories reconcile remote snapshots into Room in transactions.
- Integration: `VtopGateway` owns endpoint calls; `core:parser` owns HTML knowledge. No CSS selector may leak into repositories or UI.

## Core data model

- `Account`, `Campus`, `Semester`
- `Course`, `CourseOffering`, `Faculty`, `ClassSlot`, `ClassOccurrence`
- `AttendanceSnapshot` (attended, held, percentage, source timestamp)
- `Assessment`, `MarkComponent`, `Grade`
- `DigitalAssignment` (posted, due, upload state, server identity)
- `Exam` (type, course, date, session, venue, seat number)
- `CourseMaterial` (metadata locally; file only after explicit download)
- `AcademicCalendarDay`, `ClassMessage`
- `SyncRun`, `SyncResourceState`, `NotificationLedger`

Remote identities are scoped by account + semester. Parsed records get deterministic fingerprints so a repeated sync is idempotent and change notifications can show exactly what changed.

## Attendance engine

Store the raw numerator and denominator and calculate projections in the domain layer. For a required threshold `p`, current attended `a`, and held `h`:

- skippable future classes: largest `x >= 0` for which `a / (h + x) >= p`
- classes needed to recover: smallest `x >= 0` for which `(a + x) / (h + x) >= p`

Use exact rational/integer comparisons to avoid floating-point boundary errors. Treat cancelled classes, approved duty leave, debar rules, lab blocks, and future timetable occurrences as explicit assumptions. The UI says “projection,” displays the threshold, and never promises institutional eligibility.

## Testing

- Parser golden tests using redacted HTML fixtures.
- Repository tests against in-memory Room.
- Property tests for attendance boundary calculations.
- Sync tests for partial failure, session expiry, duplicate records, and semester rollover.
- Compose screenshot/accessibility tests for key screens.
- A nightly fixture contract suite, run manually against a test account where permitted; never in public CI with student credentials.

## Why no backend initially

A server would require holding student credentials or reusable sessions, creates a high-value breach target, and adds operating cost. Keep authentication and scraping on-device. A future backend may distribute app configuration or parser health metadata, but must not receive personal academic data or VTOP secrets.

