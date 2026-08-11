# Viora tech stack

This document explains how Viora is built, why each part exists, and how data moves through the app. It describes the code that is in the repository today rather than an imaginary future architecture.

## 1. Platform and build system

Viora is a native Android application written in Kotlin. It targets Android rather than wrapping a website because the app needs reliable local storage, encrypted credentials, background work, notifications, widgets, adaptive layouts, and proper offline behavior.

The current project is one Gradle application module, `:app`. The code is separated by Kotlin packages—UI, domain, data, database, parser, network, security, synchronization, and notifications—without paying the configuration and build-time cost of many Gradle modules while the app is still young.

The build uses:

- Android Gradle Plugin 9.3.0
- Kotlin and the Kotlin Compose plugin 2.3.10
- KSP 2.3.10 for Room code generation
- Java 17 source and bytecode compatibility
- `compileSdk` and `targetSdk` 36
- `minSdk` 26, so the app supports Android 8.0 and newer

Dependencies come only from Google Maven, Maven Central, and the Gradle Plugin Portal. Repository declarations are centralized in `settings.gradle.kts`; individual modules cannot silently add another repository.

## 2. High-level architecture

Viora is local-first and database-driven:

```text
VTOP HTML pages
      |
      v
HttpVtopGateway ---- encrypted, isolated cookie jar
      |
      v
pure Jsoup parsers
      |
      v
repositories ---- validation/change detection
      |
      v
Room database ---- Kotlin Flow ---- ViewModel ---- Compose UI
      ^                                      |
      |                                      v
WorkManager                         user actions/settings
```

The important rule is that Compose screens do not render network responses directly. A successful fetch is parsed and validated, then committed to Room. Screens observe Room through `Flow`, so cached information remains available during weak Wi-Fi, VTOP downtime, session expiry, or process restarts.

This also makes partial failure manageable. If marks fail but the timetable succeeds, only the marks resource reports an error; valid cached marks are not replaced by an empty or broken page.

## 3. User interface: Jetpack Compose and Material 3

The entire UI is declarative Jetpack Compose using the Compose BOM and Material 3. `MainActivity` hosts the root composition, observes `VioraAppViewModel.state`, and chooses between setup, VTOP verification, and the configured dashboard.

`VioraUiState` is an immutable snapshot containing the selected semester, cached academic records, sync health, notification settings, attendance-planner settings, download states, recent changes, and authentication state. UI callbacks call ViewModel methods; the UI does not mutate repositories or the database itself.

The five main destinations are Home, Schedule, Courses, Tasks, and More. Compact devices use a bottom navigation bar. Widths of 840 dp or more use a navigation rail, which gives tablets and unfolded devices more room without stretching a phone layout across the screen.

Compose is also used for:

- Current and next class cards
- A calendar-aware seven-day academic timeline
- Attendance projections and what-if controls
- Consolidated course, assignment, exam, and material detail views
- Search over the local cache
- Download status and retry UI
- Per-resource sync and error states

Accessibility is treated as part of layout behavior. Screen titles expose heading semantics, important errors use assertive live-region semantics, icons have content descriptions, and controls that commonly overflow at large font scales are arranged vertically or across multiple rows.

## 4. State and lifecycle: ViewModel, StateFlow, and coroutines

`VioraAppViewModel` survives Activity recreation and owns the screen state. It uses a private `MutableStateFlow<VioraUiState>` and exposes a read-only `StateFlow` to Compose.

Kotlin coroutines handle asynchronous work. The ViewModel uses `viewModelScope`, meaning its jobs are cancelled when the ViewModel is permanently cleared. Each Room observation is kept as a `Job` so changing semester cancels the previous semester's collectors before starting new ones.

Room `Flow` streams are collected for timetable slots, attendance, assignments, exams, marks, grades, academic summaries, calendar days, messages, materials, sync resources, semester archives, and detected changes. This keeps UI state synchronized with database transactions without manual refresh callbacks between layers.

Blocking work such as clearing the database or writing files is moved to `Dispatchers.IO`.

## 5. Local database: Room and SQLite

Room 2.8.4 is the app's source of truth. KSP generates DAO implementations and validates SQL queries at build time. The database is currently schema version 6.

Important tables include:

- `semesters`: semester identity, display name, and active/archive state
- `courses` and `class_slots`: normalized weekly timetable data
- `attendance`: raw attended and held counts, separated by theory/lab identity
- `digital_assignments`: due time, upload state, and status
- `exams`: course, exam type, start time, venue, and seat number
- `marks`: individual assessment components and weighted scores
- `grades` and `academic_summaries`: grades, GPA, CGPA, and credits
- `academic_calendar`: holidays, exams, and instructional-day orders
- `class_messages`: cached faculty messages
- `course_materials`: material metadata and VTOP download action
- `sync_resources`: last attempt, success, status, and safe error per resource
- `academic_changes`: durable change events used by the UI and notifications
- `notification_ledger`: deduplication keys for already-published notifications

Timetable tables use foreign keys and cascade deletion within a semester. Remote records receive deterministic IDs so repeated syncs are idempotent. Repositories generally replace one resource for one semester inside a Room transaction; unrelated semesters and unrelated resources remain intact.

Schema migrations preserve installed-user data. The migration chain currently covers versions 1 through 6. Instrumentation tests include an offline in-memory database check and a direct v5-to-v6 migration assertion. CI compiles the Android instrumentation APK even when no emulator is attached.

Semester rollover is based on remote semester ordering plus locally known semester IDs. When a genuinely new first semester appears, Viora selects it, marks older semester rows inactive, and preserves their cached records as archives.

## 6. Networking: OkHttp with a VTOP-only boundary

OkHttp 5.3.2 performs network requests. The client has 20-second connection and 30-second read timeouts, follows normal redirects, and refuses SSL-to-non-SSL redirect behavior.

`VtopOnlyInterceptor` is a hard integration boundary: requests must target the expected VTOP host. Material downloads perform an additional host check before fetching bytes. This prevents a malformed or compromised HTML link from turning Viora into a general-purpose HTTP client.

`VtopGateway` is the interface between the rest of the app and VTOP. `HttpVtopGateway` owns endpoint paths, request forms, CSRF tokens, authorized student IDs, session detection, and conversion from parser results into typed snapshots.

The gateway covers semesters, timetable, attendance, assignments, exams, marks, grades, CGPA, calendar, class messages, course-page faculty/material metadata, and on-demand material bodies.

There is no application server. Viora communicates directly with VTOP from the phone. Search, parsing, attendance math, caching, change detection, notifications, and downloads all happen locally.

## 7. HTML parsing: Jsoup and pure parser contracts

VTOP is an HTML application, so Viora uses Jsoup 1.22.1 rather than pretending there is a stable JSON API. Endpoint code and HTML knowledge are deliberately separated:

- The gateway fetches authenticated documents.
- Parser classes receive HTML strings.
- Parsers return `ParseResult.Success`, `AuthenticationRequired`, or `InvalidDocument`.
- Repositories decide whether a parsed snapshot is safe to commit.

Parsers normalize headers and support known aliases—for example, `Course Code` versus `Subject Code`, or `Time` versus `Session`. Attendance supports both separate attended/held columns and the combined `13/15` form used by current VTOP layouts. Theory and lab rows have separate stable identities and are never collapsed just because their subject names match.

Parser tests use redacted HTML fixtures. Fixtures cover alternate attendance, exam-session, grade-history, and course-material layouts without storing registration numbers, cookies, credentials, or personal course content.

## 8. Authentication and secret storage

Viora maintains its own VTOP session. It does not share Chrome cookies and does not call VTOP's logout endpoint when the user logs out of Viora. This prevents a local Viora action from intentionally disturbing a browser or another device session.

Credentials and cookies are serialized into app-private preferences only after encryption. `AndroidKeystoreCipher` creates an app-specific AES key in Android Keystore and uses AES-GCM with a fresh randomized IV for every encryption. GCM provides confidentiality and authentication: modified ciphertext fails to decrypt rather than producing corrupted secrets.

The encrypted cookie store retains the fields OkHttp needs—name, value, expiry, domain, path, secure, HTTP-only, persistent, and host-only state. `IsolatedCookieJar` removes expired cookies, matches them to outgoing VTOP URLs, and can be cleared without touching any other client.

On launch, `SessionManager` first tries the encrypted cookie session. If VTOP has expired it and stored credentials exist, it attempts a silent login. VTOP's six-character image CAPTCHA is decoded and classified on-device using a small bundled linear model; images and answers are neither persisted nor sent to another service. Failed answers use a fresh session and challenge, with bounded retries. If VTOP presents reCAPTCHA or another interactive verification flow, Viora opens the VTOP-hosted page inside an app-contained WebView. JavaScript and DOM storage are enabled only for this VTOP-hosted interactive flow. The WebView blocks all non-HTTPS/non-VTOP navigation and subresources, third-party cookies, mixed content, file/content access, and popup windows; cancels certificate errors; moves the successful session into Viora's encrypted OkHttp jar; and clears WebView cookies when the screen closes.

Local logout cancels Viora work, clears encrypted credentials and cookies, deletes the Room database and downloaded files, and leaves server-side/browser logout alone.

## 9. Domain logic: attendance and academic timeline

Attendance is calculated from raw integers rather than trusting a displayed rounded percentage. For target percentage `p`, attended count `a`, and held count `h`, Viora finds:

- the largest `x` where `a / (h + x) >= p`, representing future missed attendance units;
- the smallest `x` where `(a + x) / (h + x) >= p`, representing consecutive attended units needed to recover.

The comparison uses integer multiplication, avoiding floating-point boundary errors. The target is configurable from 50% to 95%. The what-if planner can add hypothetical missed blocks without changing the stored VTOP snapshot.

Lab projections use a block size. If a lab contributes two attendance hours as one session, Viora converts unit-level results into conservative whole blocks; it does not claim that half a lab session is skippable.

The academic timeline materializes weekly slots into dated occurrences for the next seven days. Calendar entries can suppress classes on holidays/exam days or substitute another weekday through labels such as `Monday order`. Classes, assignments, exams, calendar events, and messages are merged and sorted by time.

## 10. Repositories, synchronization, and change detection

Each resource has a repository responsible for fetching, mapping, validating, and committing it. `refreshResource`-style flows write `SYNCING`, `FRESH`, or `ERROR` resource states with safe user-facing errors.

`VioraSyncWorker` uses WorkManager 2.11.0 for periodic network-constrained refresh. The selected cadence is stored locally and can be configured between one and 24 hours. WorkManager is intentionally treated as inexact; opening the app and manual Sync remain important freshness paths.

The worker refreshes the timetable first, then attendance, assignments, exams, results, calendar/messages/material metadata, and notifications. Authentication failures stop retry storms, while transient failures can return `retry`.

Before replacement, repositories compare stable records with the previous database snapshot. Meaningful changes—timetable movements, attendance updates, assignment changes, exam venue/time changes, marks, grades, messages, and materials—become deterministic `academic_changes` rows. These survive process death and power both the “What changed” feed and deduplicated notifications.

## 11. Notifications

Android notification channels separate deadlines, examinations, and general academic updates. Viora can publish:

- assignment reminders within 24 hours or three hours;
- exam reminders;
- attendance-below-target warnings;
- detected schedule, mark, grade, message, or material changes.

The notification ledger prevents the same semantic event from being emitted repeatedly. Pending intents carry a destination so tapping a notification opens Schedule, Courses, Tasks, or More. Quiet hours default to 10 PM–7 AM, and assignment/exam categories can be disabled locally.

## 12. Course material files

Material metadata syncs in the background, but file bodies download only after an explicit user action. Downloads are restricted to VTOP, capped at 50 MB, sanitized to safe filenames, and written to `files/course-materials` inside Viora's private storage.

The download manager exposes `StateFlow` states such as `DOWNLOADING`, `READY`, and `ERROR`, retries failures up to three times, reports storage usage, and supports cleanup. Open/share actions use Android `FileProvider`, granting temporary read access without exposing Viora's private directory as a filesystem path.

## 13. Search

Search is an in-memory projection over already-cached UI state. It matches courses, assignments, exams, messages, materials, and marks. The query never leaves the phone and does not trigger VTOP requests.

For the current dataset size this is simpler and fast enough. If the cache grows substantially, the same feature can move to Room FTS while preserving the local-only behavior.

## 14. Testing and CI

Local JVM tests use JUnit 4 and `kotlinx-coroutines-test`. They cover attendance boundaries, lab blocks, session restoration, VTOP-only request enforcement, cookie isolation, semester rollover, date parsing, and redacted HTML parser contracts.

Android instrumentation dependencies include AndroidX Test, Compose UI Test, and Room testing. The repository also contains `scripts/device-smoke.sh`, which installs an APK, launches Viora, and captures the accessibility/UI hierarchy through ADB.

GitHub Actions uses JDK 17 and Gradle 9.5.0. Every push and pull request runs JVM tests, compiles the debug instrumentation APK, and runs Android lint. No student credentials or live VTOP session are placed in CI.

## 15. APK delivery

Viora is an unofficial personal APK shared directly from GitHub, not a Play Store product or a VIT/VTOP publication. A tag-driven GitHub Actions workflow builds the APK, verifies its signature, certificate, version metadata, and alignment, publishes it to GitHub Releases, and attaches a SHA-256 checksum. The remaining external step is to configure the personal signing identity outside the repository and test the APK on the intended devices before creating the first tag.

Signing secrets must be stored in GitHub Actions secrets or a secure local environment, never committed. Debug APKs are suitable for development; shared personal builds use the same signing key so an update installs over the existing copy without erasing its local data.

## 16. Current tradeoffs

- A single Gradle module keeps iteration simple, but package boundaries should be preserved so features can be split later if build times justify it.
- VTOP HTML can change without warning, so parser fixtures and live device checks remain important.
- WorkManager cannot guarantee exact reminder timing.
- WebView verification is used only when VTOP requires interaction; normal sync remains OkHttp-based.
- Cached academic information is convenient but sensitive, so local logout and cache/download controls are first-class features.
- Rooted or compromised devices can weaken Android Keystore and app-sandbox guarantees.

The overall design favors a useful offline student app with a small attack surface: no backend, no cloud academic database, no analytics pipeline, and no dependency on a browser session.
