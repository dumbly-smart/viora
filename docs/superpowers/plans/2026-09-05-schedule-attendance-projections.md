# Schedule and Attendance Projections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify Schedule and calculate milestone skip capacity from all projected future classes with a global exam window.

**Architecture:** Centralize exam-period suppression in the deterministic projection layer reused by Home, Schedule, calendar export, and attendance. The allowance function credits every future occurrence not skipped.

**Tech Stack:** Kotlin/JVM domain logic and Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-09-05-academic-flow-corrections-design.md`

## Global Constraints

- All academic times use `Asia/Kolkata`.
- VIT timetable, exam schedule, and academic calendar are authoritative inputs.
- Fallback exam-end estimates are visibly labelled as estimates.
- Whole lab occurrences remain indivisible.

---

### Task 1: Global exam-period projection

**Files:**
- Modify: `app/src/main/kotlin/app/viora/domain/ExamSchedule.kt`
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt`
- Modify: `app/src/test/kotlin/app/viora/ExamAwareScheduleTest.kt`
- Modify: `app/src/test/kotlin/app/viora/HomeAgendaTest.kt`
- Modify: `app/src/test/kotlin/app/viora/AcademicProjectionsTest.kt`

**Interfaces:**
- Produces: `ExamSuppressionWindow(startDate: LocalDate, resumeDate: LocalDate, estimated: Boolean)`.
- Produces: one shared predicate used by `slotsForDate(date)`.

- [ ] Add failing tests where one student exam ends early but another cached slot exam ends later; assert no classes until the series ends.
- [ ] Add failing tests preferring explicit calendar exam-period/resumption rows and otherwise resuming on the next valid Monday/day-order with `estimated=true`.
- [ ] Run focused schedule/projection tests and confirm same-day overlap logic fails them.
- [ ] Build normalized CAT-1/CAT-2/FAT windows, apply explicit calendar bounds first and conservative cached-exam fallback second, then route `slotsForDate` through them.
- [ ] Re-run focused tests and commit with `fix: suppress classes through complete exam periods`.

### Task 2: Forward-looking skip capacity

**Files:**
- Modify: `app/src/main/kotlin/app/viora/domain/AttendanceMilestone.kt`
- Modify: `app/src/main/kotlin/app/viora/AcademicProjections.kt`
- Modify: `app/src/test/kotlin/app/viora/domain/AttendanceMilestoneTest.kt`
- Modify: `app/src/test/kotlin/app/viora/AcademicProjectionsTest.kt`

**Interfaces:**
- Produces: `maximumSkippableOccurrences(attended, held, targetPercent, occurrenceUnits)` using final projected attendance.
- Produces: `CourseAttendanceMilestoneUi.estimatedWindow: Boolean` for user-facing estimate copy.

- [ ] Add failing literal tests for `(A + F - S) * 100 >= target * (H + F)`, including current-below-target recovery, CAT-2 versus FAT horizons, and indivisible lab blocks.
- [ ] Run focused attendance tests and confirm the current no-future-credit algorithm fails.
- [ ] Sum all positive future units, sort occurrences by unit cost, and select the greatest whole-occurrence prefix whose skipped units satisfy the final-ratio inequality.
- [ ] Ensure occurrence generation reuses globally suppressed `slotsForDate` and counts strictly after now and before the milestone.
- [ ] Show “Estimated from timetable and exam dates” when the global exam window lacks an explicit VIT end date.
- [ ] Re-run focused tests and commit with `fix: project attendance through each milestone`.

### Task 3: Schedule layout

**Files:**
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/CalendarScreenTest.kt`

**Interfaces:**
- Produces: `ScheduleScreen` without `selectSemester`; interchange controls follow timetable/imported entries.

- [ ] Add a failing Compose test asserting no semester chips and that timetable entries precede “Export to Viora calendar”.
- [ ] Remove Schedule’s semester callback/chips and move the interchange block below timetable/imported entries but before calendar/exam sections.
- [ ] Compile focused Compose tests and commit with `fix: simplify current timetable schedule`.

### Task 4: Focused verification and docs

**Files:**
- Modify: `PROGRESS.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/FEATURES.md`
- Modify: `docs/TECH_STACK.md`

**Interfaces:** None.

- [ ] Run `gradle testDebugUnitTest` and `gradle compileDebugAndroidTestKotlin`; do not assemble/install an APK unless requested.
- [ ] Run `scripts/privacy-audit.sh` and `git diff --check`.
- [ ] Document the implemented formula, estimated exam-window fallback, assessments flow, and partial marks persistence.
- [ ] Commit with `docs: update academic flow behavior`.
