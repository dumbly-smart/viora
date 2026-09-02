# Viora progress

Updated: 2026-09-02

## Current baseline

Viora is a local-first Kotlin and Jetpack Compose Android client for VTOP. It
fetches authenticated VTOP HTML on-device, parses it locally, validates it, and
persists it in Room before displaying it through ViewModels and Compose.

Completed foundations include encrypted VTOP credentials and cookies,
VTOP-only network enforcement, pure redacted-fixture parsers, transactional
Room repositories, offline cached views, WorkManager refresh, notifications,
widgets, user-initiated private material downloads, and local logout.

## Academics and calendar work

The `feat/academics-calendar` branch is ready to be integrated into `main`.
It adds:

- reliable submitted-assignment classification, so Home shows only work that
  is still outstanding;
- marks grouped by course while preserving theory/lab identity;
- CAT-1, CAT-2, and FAT attendance planning from cached timetable and calendar
  data;
- a Schedule calendar view for cached holidays, exams, classes, day-order
  changes, and general academic events.

The branch has JVM tests, debug APK and instrumentation APK compilation, lint,
privacy audit, shell syntax checks, and final code review recorded as passing.
No device was attached for a fresh runtime instrumentation or device-smoke run.

## Next steps

1. Choose the branch integration path: merge locally, open a pull request, or
   retain the branch for later.
2. Run `scripts/device-smoke.sh --fresh` on an attached API 26+ device or
   emulator after integration; include compact/narrow-window calendar checks.
3. Keep parser fixtures representative and redacted whenever VTOP layout
   changes are observed.
4. Add migrations and migration instrumentation coverage before any Room schema
   change; the current database version is 8.

## Guardrails

Viora has no backend, cloud academic-data store, analytics pipeline, or
general-purpose web access. Credentials, cookies, CAPTCHA material, and
academic records must never appear in logs, test fixtures, CI artifacts, or
third-party services.
