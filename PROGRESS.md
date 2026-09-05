# Viora progress

Updated: 2026-09-05

## Implemented

### Application foundation and privacy

- Single-module native Android application using Kotlin, Jetpack Compose,
  Material 3, ViewModels, `StateFlow`, Room, WorkManager, OkHttp, and Jsoup.
- Local-first data flow: authenticated VTOP pages are parsed and validated
  before transactional Room updates; screens observe Room and retain the last
  valid cache when a resource refresh fails.
- HTTPS-only networking restricted to `vtop.vit.ac.in`, including redirect,
  material-download, and embedded-WebView host checks.
- Encrypted app-private credential and cookie storage backed by Android
  Keystore AES-GCM. Android backup is disabled for personal app data.
- Local-only logout clears Viora credentials, cookies, academic caches,
  downloads, reminders, and scheduled work without logging other VTOP browser
  or device sessions out.
- No Viora backend, cloud academic-data store, analytics pipeline, or
  third-party CAPTCHA service.

### Authentication and synchronization

- VTOP setup, sign-in, session validation, encrypted session restoration, and
  silent reauthentication with bounded retries.
- On-device six-character VTOP image-CAPTCHA solver and a restricted VTOP-only
  interactive WebView fallback for challenges that require user interaction.
- Semester discovery and selection, automatic new-semester detection, archived
  historical semester caches, and rollover handling.
- Manual/app-open synchronization and configurable periodic WorkManager
  refresh with network constraints, bounded retries, partial-resource errors,
  last-success timestamps, and sync diagnostics.
- Deterministic synced-record identities, per-resource sync state, durable
  academic change detection, and cache preservation on malformed or
  authentication responses.

### Academic data and screens

- Five adaptive destinations: Home, Schedule, Courses, Assessments, and More, using a
  bottom bar on compact screens and a navigation rail at 840dp and wider.
- Home dashboard with current/next class, today and tomorrow context, upcoming
  assignments and exams, attendance risk, recent messages, sync status, and a
  durable “What changed” feed.
- Timetable and seven-day academic timeline with rooms, faculty, holidays,
  exam-day suppression, weekday/day-order substitution, and Asia/Kolkata time
  handling.
- Dedicated local academic calendar with month navigation, cached class,
  assignment, exam, holiday, and day-order markers, plus accessible selected-day
  event details.
- Calendar interchange for the active semester: 180-day class materialization,
  exam and assignment export to a dedicated Android calendar, full-detail ICS
  export/share, and transactional ICS timetable import into Schedule.
- Attendance synchronization preserving theory/lab identity, configurable
  target percentages, exact integer projections, hypothetical missed classes,
  conservative whole-lab-block allowances, and CAT 1/CAT 2/FAT milestone
  planning that credits projected attended classes before each milestone.
- CAT/FAT periods suppress all projected classes through explicit VIT calendar
  end/resumption dates when available. Otherwise Viora uses the latest cached
  slot exam and the next instructional Monday/day-order, visibly labelled as
  an estimate.
- Digital-assignment synchronization with shared field-aware submitted-status
  classification across Home, Assessments, and reminders. Home shows pending
  due work only; Assessments shows submitted-inclusive weekly work and course
  drill-down.
- Native single-file assessment submission and pre-deadline replacement using
  fresh multipart form metadata parsed from authenticated VTOP HTML, with a
  10 MiB app cap, MIME enforcement, filename sanitization, and VTOP-only HTTPS.
- Exam schedule, every parsed VTOP mark component grouped by course with
  theory/lab identity, grades, GPA, CGPA, credits, academic summaries, and
  target-CGPA planning. Marks persist independently when grade/CGPA refresh
  fails.
- Consolidated course details covering faculty, slots, attendance, marks,
  grades, messages, and course materials.
- Assignment detail with native document selection and upload progress/errors.
- Local search across cached courses, assignments, exams, marks, messages, and
  material metadata.

### Notifications, widgets, files, and sharing

- Local assignment, exam, attendance, timetable, mark, grade, message, and
  material-change notifications with category controls, quiet hours,
  destination-aware taps, and a notification ledger for deduplication.
- Attendance changes are consolidated into one replaceable notification that
  opens the Attendance view. Cached CGPA values of 9.00 or higher suppress the
  75% warning criteria while retaining raw attendance and update notifications.
- Cache-backed next-class home-screen widget with holiday and day-order
  handling.
- User-initiated, VTOP-only course-material downloads with size caps, sanitized
  filenames, retry state, storage usage/cleanup, and organization under the
  private `files/Viora/materials/<course>` tree. Tracked legacy public
  downloads migrate only after a verified private copy.
- Shareable timetable QR image and text payload generated under the private
  `files/Viora/shared` tree from the active semester cache; `FileProvider`
  grants temporary access only when the user opens or shares a file.
- Adaptive/monochrome launcher icon, native splash screen, dynamic color,
  large-font-safe layouts, screen-reader semantics, assertive error
  announcements, and tablet/foldable navigation behavior.

### Testing, diagnostics, and delivery

- Redacted parser fixtures and JVM coverage for parser variants, domain
  calculations, network host enforcement, cookie/session behavior, semester
  rollover, notifications, widgets, QR sharing, and academic projections.
- Android instrumentation coverage for Room behavior/migrations and key Compose
  screens, including course details and the academic calendar.
- Room schema version 9 with exported schemas and forward migrations.
- CI checks for JVM tests, debug and instrumentation APK compilation, lint,
  privacy rules, and shell syntax without uploading private diagnostic
  artifacts.
- Privacy audit, ADB fresh/offline smoke harness, release-version validation,
  signed-APK verification, checksum generation, and a closed-test runbook.
- Academics/calendar work is integrated into the current codebase, including
  marks, attendance milestones, local calendar views, accessibility fixes, and
  submitted-assignment classification.

## Remaining product work

- Add semester attendance trend charts. The existing attendance what-if and
  milestone planners are already implemented.
- Extend the next-class widget with urgent assignment/exam deadlines.
- Add Room full-text search for downloaded material metadata if the local cache
  outgrows the current in-memory search.
- Add a configurable weekly attendance-summary notification.
- Add user-facing “why did I get this?” details for published notifications.
- Add localization; the current interface is English-only.

Multiple-campus adapters and a biometric app lock are not planned.

## Validation and release follow-up

- Run `make device-smoke` on an attached API 26+ device or emulator after the
  academics/calendar integration, including narrow-window calendar checks and
  fresh/offline-cache runs.
- Complete the private physical-device matrix across Android 8/9, Android
  12/13, Android 15+, a device at 840dp or wider, and an OEM with aggressive
  background limits.
- Validate interactive VTOP verification, notifications, widgets, background
  refresh, upgrades, large fonts, screen-reader focus, dark mode, rotation,
  and downloaded-file behavior on real devices without retaining private test
  artifacts.
- Provision the protected release-signing secrets and validate a signed
  candidate before publishing the next release.
- Establish parser-health monitoring and write the parser-change incident
  playbook.
- Continue closed testing across supported account/layout variants and verify
  semester rollover with authorized, privacy-safe test accounts.

## Ongoing engineering requirements

- Keep VTOP HTML fixtures synthetic, redacted, and representative whenever a
  parser layout changes; never commit live HTML, credentials, cookies, CAPTCHA
  material, registration numbers, academic records, logs, screenshots, or UI
  dumps.
- Preserve cached records on partial, malformed, or authentication failures.
- Preserve theory/lab course identity and use deterministic synced-record IDs.
- Use `Asia/Kolkata` for VTOP-local dates and times.
- Any Room schema change must increment the database version, add and register
  a forward migration, update the exported schema, and add migration
  instrumentation coverage. Never use destructive migration as a shortcut.
- Keep all VTOP traffic HTTPS-only and restricted to `vtop.vit.ac.in` unless a
  separately approved design changes that boundary.
