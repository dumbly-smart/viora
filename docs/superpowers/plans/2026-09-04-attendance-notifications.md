# Attendance Notification Policy and Batching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the inclusive 9.00-CGPA attendance-warning rule and publish at most one replaceable attendance notification containing all new attendance changes.

**Architecture:** A pure notification planner decides exemption, warning content, changed rows, ledger keys, and summary copy from Room snapshots. `VioraNotifications` publishes that plan once with a stable Android ID, while navigation opens the Room-backed Attendance tab and Compose hides threshold-risk guidance for qualifying CGPAs.

**Tech Stack:** Kotlin/JDK 17, Room, Android notifications, Jetpack Compose Material 3, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-04-attendance-notification-design.md`

## Global Constraints

- CGPA greater than or equal to 9.00 disables 75-percent warnings; exactly 9.00 qualifies.
- Missing CGPA and CGPA below 9.00 keep normal 75-percent behavior.
- Raw attendance and ordinary attendance-change updates remain visible for every student.
- One publish pass emits at most one attendance notification and uses a stable Android ID so it replaces instead of stacking.
- Detailed `AcademicChangeEntity` rows remain intact in Room.
- Tapping the notification opens Courses directly on Attendance.
- Notification content remains private and entirely on device.

---

### Task 1: Pure attendance notification policy

**Files:**
- Create: `app/src/main/kotlin/app/viora/notifications/AttendanceNotificationPolicy.kt`
- Test: `app/src/test/kotlin/app/viora/notifications/AttendanceNotificationPolicyTest.kt`

**Interfaces:**
- Produces: `AttendanceNotificationPolicy.hasNinePointRule(cgpa: Double?): Boolean`.
- Produces: `AttendanceNotificationPlan(title, summary, expandedLines, ledgerKeys)` or `null`.
- Produces: `AttendanceNotificationPolicy.plan(cgpa, attendance, unpublishedChanges, target = 75)`.

- [ ] **Step 1: Write failing policy tests**

Assert `8.999` is not eligible, `9.00` and `9.01` are eligible, and `null` is not eligible. For 15 changed rows assert one plan with 15 change ledger keys, bounded expanded lines plus `+N more`, no 75-percent warning at CGPA 9.00, warning summary below 9.00, and `null` when neither changes nor unpublished warning states exist.

- [ ] **Step 2: Run the policy tests and verify RED**

Run: `gradle testDebugUnitTest --tests app.viora.notifications.AttendanceNotificationPolicyTest`

- [ ] **Step 3: Implement minimal deterministic policy**

Use integer attendance comparison (`attended * 100 < target * held`), sort changes deterministically, bound notification detail lines, generate per-change keys `change:<id>` and per-warning-state keys `attendance-warning:<semester>:<id>:<attended>:<held>:<target>`, and never use CGPA rounding.

- [ ] **Step 4: Run policy and existing attendance tests**

Run: `gradle testDebugUnitTest --tests app.viora.notifications.AttendanceNotificationPolicyTest --tests 'app.viora.domain.Attendance*'`

- [ ] **Step 5: Commit the policy slice**

```bash
git add app/src/main/kotlin/app/viora/notifications/AttendanceNotificationPolicy.kt app/src/test/kotlin/app/viora/notifications/AttendanceNotificationPolicyTest.kt
git commit -m "feat: define nine-point attendance notification policy"
```

### Task 2: Room snapshots and single Android publication

**Files:**
- Modify: `app/src/main/kotlin/app/viora/database/AcademicDao.kt`
- Modify: `app/src/main/kotlin/app/viora/notifications/VioraNotifications.kt`
- Test: `app/src/test/kotlin/app/viora/notifications/AttendanceNotificationPublisherTest.kt`

**Interfaces:**
- Produces: `academicSummarySnapshot(): AcademicSummaryEntity?`.
- Produces: `notificationLedgerKeys(keys: List<String>): List<String>` and batch ledger insertion.
- Produces: `AttendanceNotificationStore` with an `AcademicDao` adapter for snapshot/ledger access and a small fake for JVM publisher tests.
- `publishUpcoming(semesterId)` excludes attendance from per-change publication and delegates one attendance plan to `publishAttendance`.

- [ ] **Step 1: Write a failing publisher test around an injected notification sink**

Use a fake notification sink and fake `AttendanceNotificationStore` to supply 15 unpublished attendance changes. Assert one sink call with stable ID `ATTENDANCE_UPDATE_NOTIFICATION_ID`, destination `attendance`, 15 ledger keys written after publication, no repeat call on the second pass, and non-attendance categories retaining their current individual behavior.

- [ ] **Step 2: Run the publisher test and verify RED**

Run: `gradle testDebugUnitTest --tests app.viora.notifications.AttendanceNotificationPublisherTest`

- [ ] **Step 3: Refactor publication behind a small sink and implement batching**

Keep Android builder/manager operations in the production sink. Build one private expandable InboxStyle notification; use the fixed attendance notification ID for every attendance plan; insert all plan ledger keys only after the sink accepts publication. Exclude category `attendance` from the existing generic change loop.

- [ ] **Step 4: Apply the CGPA snapshot rule**

Read the cached `current` academic summary. Pass its unrounded `cgpa` to the pure policy. At CGPA at least 9.00, omit all threshold warning keys/content while preserving change content.

- [ ] **Step 5: Run notification and reminder tests**

Run: `gradle testDebugUnitTest --tests 'app.viora.notifications.*'`

- [ ] **Step 6: Commit the publication slice**

```bash
git add app/src/main/kotlin/app/viora/database/AcademicDao.kt app/src/main/kotlin/app/viora/notifications app/src/test/kotlin/app/viora/notifications
git commit -m "fix: consolidate attendance update notifications"
```

### Task 3: Direct Attendance navigation

**Files:**
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt`
- Modify: `app/src/main/kotlin/app/viora/AcademicsScreens.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/AcademicsScreenTest.kt`

**Interfaces:**
- `Dashboard(initialDestination = "attendance")` selects navigation index 2.
- `AcademicsScreen(state, initialTab: Int, showCourseDetail)` initializes and updates its selected tab from the destination.

- [ ] **Step 1: Add a failing Compose navigation test**

Render Dashboard with `initialDestination = "attendance"` and assert `Attendance` is selected and `Skip allowance`/the exemption heading is displayed without an extra tap. Also verify a later changed destination resets the requested tab.

- [ ] **Step 2: Compile instrumentation tests and verify RED**

Run: `gradle assembleDebugAndroidTest`

- [ ] **Step 3: Implement destination-to-tab mapping**

Map `attendance` to Courses at the dashboard level and pass initial tab index `2`. Key `remember` by `initialTab` so `onNewIntent` updates an already-running activity. Preserve ordinary `courses` destination behavior at tab `0`.

- [ ] **Step 4: Compile instrumentation tests and commit**

Run: `gradle assembleDebugAndroidTest`

```bash
git add app/src/main/kotlin/app/viora/MainActivity.kt app/src/main/kotlin/app/viora/AcademicsScreens.kt app/src/androidTest/kotlin/app/viora/AcademicsScreenTest.kt
git commit -m "feat: open attendance updates directly"
```

### Task 4: CGPA-aware attendance UI

**Files:**
- Modify: `app/src/main/kotlin/app/viora/AcademicsScreens.kt`
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/AcademicsScreenTest.kt`
- Modify: relevant JVM projection tests if a UI policy projection is extracted.

- [ ] **Step 1: Add failing Compose tests for the inclusive cutoff**

At CGPA `9.00`, assert raw `attended/held` values and a `9-point attendance rule` status are shown, while `below 75%`, recovery counts, skip limits, and milestone skip warnings are absent. At `8.99` and `null`, assert existing 75-percent guidance remains.

- [ ] **Step 2: Compile instrumentation tests and verify RED**

Run: `gradle assembleDebugAndroidTest`

- [ ] **Step 3: Implement exemption-aware presentation**

Use only `AttendanceNotificationPolicy.hasNinePointRule(state.cgpa)` as the cutoff source. Preserve raw values. In Attendance and Home/course cards, replace threshold projections, recovery/skip copy, and milestone warnings with restrained status text that does not claim institutional eligibility or unlimited skipping.

- [ ] **Step 4: Run focused JVM tests and instrumentation compilation**

Run: `gradle testDebugUnitTest --tests app.viora.notifications.AttendanceNotificationPolicyTest`

Run: `gradle assembleDebugAndroidTest`

- [ ] **Step 5: Commit the UI policy slice**

```bash
git add app/src/main/kotlin/app/viora/MainActivity.kt app/src/main/kotlin/app/viora/AcademicsScreens.kt app/src/androidTest/kotlin/app/viora/AcademicsScreenTest.kt
git commit -m "feat: apply nine-point attendance guidance"
```

### Task 5: Attendance documentation and full verification

**Files:**
- Modify: `PROGRESS.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/FEATURES.md`

- [ ] **Step 1: Update product and architecture documentation**

Record the inclusive 9.00 rule, missing-CGPA fallback, one-notification batching, stable replacement behavior, detailed local change retention, and direct Attendance destination.

- [ ] **Step 2: Run full local verification**

Run: `make check`

Run: `gradle assembleDebug assembleDebugAndroidTest lintDebug`

Run: `scripts/privacy-audit.sh`

- [ ] **Step 3: Inspect final diff and commit docs**

```bash
git diff --check
git status --short
git add PROGRESS.md docs/ARCHITECTURE.md docs/FEATURES.md
git commit -m "docs: record attendance notification policy"
```
