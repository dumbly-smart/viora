# Academic Command Center Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the five-destination presentation with a polished, dark-only Today/Plan/Library command center focused on classes and deadlines.

**Architecture:** Keep `VioraUiState`, ViewModels, Room, and repositories unchanged. Recompose the presentation shell in `MainActivity.kt`, split destination UI into focused files where useful, and route existing Schedule, Calendar, Academics, assessment, course, sync, and settings actions through the new destinations.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, StateFlow-backed `VioraUiState`, existing Android instrumentation and JVM tests.

**Spec:** `docs/superpowers/specs/2026-09-10-academic-command-center-design.md`

## Global Constraints

- The app is permanently dark; no device setting may produce a light Viora surface.
- Room remains the source of truth; UI never calls a DAO or VTOP gateway directly.
- Preserve VTOP-only networking, encrypted credentials/cookies, theory/lab identity, Asia/Kolkata times, and cache preservation on failures.
- Keep compact navigation at three labeled destinations and adaptive navigation at 840dp.
- Every critical state uses text/icon semantics as well as color, with 48dp minimum touch targets.
- Do not commit APKs, screenshots, UI dumps, logs, credentials, cookies, or live academic data.

### Task 1: Establish the three-destination shell

**Files:**
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt` (Dashboard/navigation shell)
- Modify: `app/src/androidTest/kotlin/app/viora/HomeScreenTest.kt` (navigation assertions)

**Interfaces:**
- Consumes: existing `VioraUiState`, `Dashboard` callbacks, `ScheduleScreen`, `AcademicsScreen`, `AssessmentsScreen`, `MoreScreen`.
- Produces: a responsive `Today`/`Plan`/`Library` destination selector; existing detail routes remain callable from destination content.

- [ ] **Step 1: Write failing Compose assertions** for exactly three labels, Today selected by default, Plan opening Schedule, and Library opening the course collection.
- [ ] **Step 2: Run `:app:connectedDebugAndroidTest --tests app.viora.HomeScreenTest`** and confirm the new assertions fail because the old five-destination shell is still present.
- [ ] **Step 3: Replace the destination model and compact/expanded navigation implementations** while preserving existing callbacks and the 840dp rail breakpoint. Move settings actions behind the More/profile action sheet without deleting functionality.
- [ ] **Step 4: Run the same instrumentation test** and confirm it passes with no accessibility assertion regressions.
- [ ] **Step 5: Commit** with `git add app/src/main/kotlin/app/viora/MainActivity.kt app/src/androidTest/kotlin/app/viora/HomeScreenTest.kt && git commit -m "feat: add academic command center navigation"`.

### Task 2: Redesign Today around classes and deadlines

**Files:**
- Modify: `app/src/main/kotlin/app/viora/HomeScreen.kt`
- Modify: `app/src/main/kotlin/app/viora/HomeTimeline.kt`
- Test: `app/src/androidTest/kotlin/app/viora/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `VioraUiState.homeTimeline`, existing assignment-status classification, `DetailSelection`, and current refresh/reauthentication callbacks.
- Produces: Today content with next-up hero, date rail, chronological academic timeline, contextual detail action, and conditional Needs attention group.

- [ ] **Step 1: Add failing Compose tests** for next class precedence, deadline ordering, absence of an empty Needs attention section, and accessible labels for risk/overdue states.
- [ ] **Step 2: Run the focused instrumentation tests** and confirm failures identify missing Today behavior.
- [ ] **Step 3: Implement the Today composition**: next-up hero with time/room/action, date rail, one timeline for classes/exams/assignments, and a Needs attention group driven only by existing state. Use MaterialTheme tokens instead of screen-local light colors.
- [ ] **Step 4: Run focused Home tests** and confirm all pass at compact dimensions; exercise large-font-safe labels in the same test fixture.
- [ ] **Step 5: Commit** with `git add app/src/main/kotlin/app/viora/HomeScreen.kt app/src/main/kotlin/app/viora/HomeTimeline.kt app/src/androidTest/kotlin/app/viora/HomeScreenTest.kt && git commit -m "feat: redesign today academic timeline"`.

### Task 3: Build Plan and Library presentation workspaces

**Files:**
- Modify: `app/src/main/kotlin/app/viora/CalendarScreen.kt`
- Modify: `app/src/main/kotlin/app/viora/AcademicsScreens.kt`
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/CalendarScreenTest.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/CourseDetailScreenTest.kt`

**Interfaces:**
- Consumes: existing schedule/calendar composables, course/attendance/marks/assessment/material state, and ViewModel actions.
- Produces: Plan segmented Timeline/Calendar presentation and Library searchable course collection with course details unchanged in capability.

- [ ] **Step 1: Add failing Compose tests** for Plan’s Timeline/Calendar switch, Library search/open behavior, and preservation of theory/lab labels in course detail.
- [ ] **Step 2: Run the focused instrumentation tests** and verify the old presentation does not satisfy the new semantics.
- [ ] **Step 3: Implement Plan’s segmented control** over the existing schedule/calendar content and implement Library’s grouped course cards/search entry point; keep export/import, materials, attendance, marks, messages, and upload actions routed to existing callbacks.
- [ ] **Step 4: Run focused calendar/course tests** and verify empty, loading, error, large-font, and screen-reader states.
- [ ] **Step 5: Commit** with `git add app/src/main/kotlin/app/viora/CalendarScreen.kt app/src/main/kotlin/app/viora/AcademicsScreens.kt app/src/main/kotlin/app/viora/MainActivity.kt app/src/androidTest/kotlin/app/viora/CalendarScreenTest.kt app/src/androidTest/kotlin/app/viora/CourseDetailScreenTest.kt && git commit -m "feat: organize plan and library workspaces"`.

### Task 4: Apply and verify the final dark visual system

**Files:**
- Modify: `app/src/main/kotlin/app/viora/ui/VioraTheme.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Test: affected Compose tests under `app/src/androidTest/kotlin/app/viora/`

**Interfaces:**
- Consumes: all redesigned composables through `MaterialTheme.colorScheme` and existing semantic colors.
- Produces: permanent dark Android/Compose/splash/widget appearance with indigo primary and restrained semantic accents.

- [ ] **Step 1: Add a Compose assertion** that the rendered shell uses dark background/surface colors and active navigation uses the indigo primary role.
- [ ] **Step 2: Run it against the current implementation** and confirm it fails if any light surface remains.
- [ ] **Step 3: Consolidate palette tokens**, remove hard-coded light surfaces in touched screens, set the base Android theme to `Theme.Material.NoActionBar` with dark system bars, and align night resources to the same dark contract.
- [ ] **Step 4: Run the affected Compose tests** and confirm the dark contract and accessibility labels pass.
- [ ] **Step 5: Commit** with `git add app/src/main/kotlin/app/viora/ui/VioraTheme.kt app/src/main/res/values app/src/main/res/values-night app/src/androidTest/kotlin/app/viora && git commit -m "feat: finalize dark command center visual system"`.

### Task 5: Full verification and device handoff

**Files:**
- No production files unless verification exposes a failure.
- Private output only: `build/device-smoke/` (must remain untracked).

- [ ] **Step 1: Run** `gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` with JDK 17 and the configured Android SDK.
- [ ] **Step 2: Run** `scripts/privacy-audit.sh`, `sh -n scripts/*.sh`, and `git diff --check`; resolve only failures caused by this redesign.
- [ ] **Step 3: Run** `scripts/device-smoke.sh --fresh` on an API 26+ device and inspect the private screenshot/UI dump for Today, Plan, Library, and no-light-mode behavior.
- [ ] **Step 4: Confirm** no credentials, academic records, APKs, screenshots, UI dumps, or logs are staged; report test counts and any pre-existing warnings.
- [ ] **Step 5: Commit any verification-only fix** with an imperative message and provide the installed debug APK path for the phone.
