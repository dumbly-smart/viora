# Viora repository guide

## Entry points

Viora is a single-module Android project: application code is under `app/`,
while repository guidance and project status live at the root. Use `make help`
to see standardized commands; `make check` runs the normal local verification
set. Read `PROGRESS.md` for current work status and `docs/ARCHITECTURE.md` for
the detailed data flow and package responsibilities.

## Scope and architecture

Viora is a single-module native Android application (`:app`) written in Kotlin and Jetpack Compose. It is a local-first client for VTOP: authenticated VTOP HTML is fetched through OkHttp, parsed by pure Jsoup parsers, validated and persisted in Room, then exposed to Compose through `Flow` and ViewModels. There is no Viora backend, analytics service, or cloud academic-data store.

Keep package boundaries intact:

- `network/`: VTOP transport, authentication/session boundaries, cookie handling, and host checks.
- `parser/`: pure HTML-to-domain parsing. Do not issue requests or mutate the database here.
- `data/`: repositories that validate parsed snapshots, preserve valid cached data on failures, and transact with Room.
- `database/`: Room entities, DAO, schema exports, and migrations.
- `domain/`: deterministic, platform-independent academic logic.
- `ui/`, `setup/`, `assignment/`, `widget/`, and `notifications/`: Compose/UI and Android integration. UI calls ViewModel actions; it never calls the gateway or DAO directly.

## Required invariants

- VTOP traffic is HTTPS-only and restricted to `vtop.vit.ac.in`. Preserve `VtopOnlyInterceptor`, material-download host validation, WebView navigation restrictions, certificate handling, and the network-security configuration. Do not add general-purpose HTTP access or a second host without explicit approval.
- Never send credentials, cookies, CAPTCHA images/answers, registration numbers, or academic records to analytics, logs, tests, CI artifacts, or any third party. Use only redacted, synthetic fixtures under `app/src/test/resources/fixtures`.
- Credentials and VTOP cookies must remain encrypted in app-private storage. Local logout clears Viora-owned state only; it must not log the user out of VTOP in a browser or on another device.
- Room is the source of truth for screens. A failed or malformed VTOP response must not replace valid cached data with empty records.
- Preserve theory/lab identity; courses with the same display name are not automatically interchangeable.
- Treat VTOP HTML as unstable. Parser changes require representative redacted fixture coverage, including relevant header/layout variants and authentication/invalid-document behavior.
- All times representing VTOP local values use `Asia/Kolkata`; avoid implicit device-time-zone conversions.

## Data and schema changes

- The Room database is currently version 8. Any schema change must increment the version, add a forward migration in `VioraDatabase.kt`, register it in `addMigrations`, and update the exported schema in `app/schemas/app.viora.database.VioraDatabase/`.
- Add or update migration instrumentation coverage when changing a migration path. Never use destructive migration as a shortcut.
- Use stable, deterministic identifiers for synced records so refreshes are idempotent and change detection remains reliable.

## UI and Android behavior

- Follow existing Compose Material 3 and `VioraTheme` patterns. Keep state immutable and held by ViewModels via `StateFlow`/`Flow`.
- Preserve accessibility semantics, meaningful content descriptions, assertive error announcements, large-font-safe layouts, and the 840dp adaptive navigation behavior.
- Keep background refresh in WorkManager and treat it as inexact. Do not introduce exact alarms or special permissions without a demonstrated product need.
- Course-file downloads are user initiated, VTOP-only, capped, sanitized, private to the app, and shared only through `FileProvider` grants.

## Build, test, and validation

Use JDK 17 and the repository's Gradle installation (`gradle`; there is no committed Gradle wrapper).

```sh
gradle testDebugUnitTest
gradle assembleDebug assembleDebugAndroidTest lintDebug
scripts/privacy-audit.sh
```

- Prefer focused JVM tests in `app/src/test/kotlin` for domain, parser, network-boundary, and repository changes. Use `app/src/androidTest/kotlin` for Room migrations, Compose behavior, and Android integration.
- For device/emulator validation, build the debug APK, then run `scripts/device-smoke.sh --fresh`; use `--offline` only when validating cached behavior. Its screenshots, UI dumps, and logs are private local artifacts—never commit or upload them.
- Before release work, also run `scripts/validate-release-version.sh` and `scripts/verify-release-apk.sh` on the signed APK. Release credentials and certificate material stay in secure environment variables/GitHub secrets.
- CI is defined in `.github/workflows/`. Keep GitHub Actions pinned to immutable commit SHAs and do not add CI artifact uploads containing build, test, log, screenshot, or UI-dump data.

## Change discipline

- Make focused changes and tests together. Do not reformat unrelated code.
- Keep commits imperative and narrowly scoped.
- Do not commit APK/AAB files, keystores, SDK paths, cookies, sessions, logs, live VTOP HTML, CAPTCHA captures, student data, downloaded materials, screenshots, or UI dumps.
- Read `docs/TECH_STACK.md`, `docs/ARCHITECTURE.md`, and `docs/CLOSED_TEST.md` before altering architecture, auth, sync, privacy controls, testing strategy, or release flow.
