# Delivery roadmap

## Phase 0 — contract and safety

- Confirm VTOP terms/acceptable-use constraints and campus variants.
- Build login/session prototype and collect strictly redacted parser fixtures.
- Define models, parser contracts, threat model, and attendance test vectors.
- Exit: login handles VTOP's text CAPTCHA locally without outsourcing or bypassing interactive verification; no secret appears in repository or logs.

## Phase 1 — read-only MVP

- Setup/login/logout, Room cache, foreground sync.
- Home, timetable, attendance projections, DAs, exams, consolidated course view.
- Manual refresh, sync status, offline behavior, parser tests.
- Exit: usable after process death and network loss; partial failures are visible.

Current vertical slices: encrypted session restoration, semester discovery, direct VTOP timetable, attendance, digital-assignment, exam, assessment-mark, grade, and CGPA synchronization; transactional Room caches; Home/Schedule/Courses/Tasks rendering; attendance projections; current/next-class context; sync freshness; six-hour WorkManager refresh; and deduplicated 24-hour local reminders are implemented. Attendance, marks, grades, and CGPA contracts have been validated against an authorized account without retaining its data in fixtures or logs.

The next local-first slices now include academic-calendar/holiday caching, class-message caching, course-material metadata, consolidated course summaries, notification category controls, and a destructive local-only logout that never invokes VTOP's server logout endpoint.

The current checkpoint adds VTOP's own in-app verification fallback, explicit material download/open/share into app-private storage, course/faculty endpoint resolution, calendar-aware class suppression/day-order handling, and a seven-day unified academic timeline.

Attendance planning now supports a configurable target, hypothetical missed blocks, and conservative whole-lab-block projections. Room also keeps deterministic change events for timetable, attendance, assignments, exams, marks, grades, messages, and materials; these drive deduplicated local alerts and an in-app “What changed” feed. Global search runs only over the on-device cache.

Detail views, managed material retries/storage cleanup, semester and sync-cadence settings, stricter embedded verification errors, and additional redacted VTOP layout fixtures are now implemented. A physical Android device was not attached during this checkpoint, so the embedded verification UI still needs its closed-device pass.

The latest reliability pass adds an ADB smoke harness, offline-cache and v5-to-v6 migration instrumentation tests, automatic new-semester detection with archived historical caches, accessibility semantics/large-text-safe controls, and width-adaptive navigation rails for tablets and unfolded devices. CI compiles the instrumentation suite even when no Android target is attached.

## Phase 2 — reminders and polish

- WorkManager background sync, change detection, notification ledger.
- Marks, grades, messages, calendar/holidays, material downloads.
- Accessibility, adaptive UI, widgets, performance and battery profiling.
- Exit: no duplicate notifications; background retry load remains bounded.

Implemented locally: a cache-backed next-class widget, adaptive/monochrome launcher icon, native splash treatment, dynamic color, background power/WorkManager diagnostics, and Perfetto-compatible async sync traces with coarse on-device timings.

## Phase 3 — release readiness

- Closed testing across campuses/account types and semester rollover.
- Privacy policy, data deletion flow, Play data-safety declaration.
- Signed builds, dependency review, parser monitoring and incident playbook.

Release signing/checksum automation and a privacy-aware closed-test/device smoke runbook are now present. Physical-device matrix execution, Play closed-track review, release-key secret provisioning, parser monitoring, and the incident playbook remain release checkpoints.

## First engineering slice

Implement one vertical slice end to end: redacted timetable fixture -> pure parser -> Room upsert -> repository Flow -> Compose “Today” screen. Then add authenticated fetching. This proves the boundaries before duplicating them across every VTOP page.
