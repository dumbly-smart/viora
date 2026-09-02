# Academics and Calendar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct submitted-assignment filtering and add dedicated local-first Marks, Attendance milestone, and Calendar views.

**Architecture:** Keep VTOP/Room synchronization unchanged. Add deterministic projections over the existing `VioraUiState` cache: field-aware assignment status, mark sections, milestone attendance allowances, and date-based calendar events. Compose screens consume those projections from the existing Courses and Schedule destinations, retaining five primary navigation items.

**Tech Stack:** Kotlin 2.3, Jetpack Compose Material 3, Room, JUnit 4, kotlinx-coroutines.

**Spec:** `docs/superpowers/specs/2026-09-01-academics-and-calendar-design.md`

## Global Constraints

- Do not add a backend, analytics, calendar-provider integration, permissions, or network hosts.
- All screens read cached Room state through `VioraUiState`; no screen issues a VTOP request.
- Preserve HTTPS-only `vtop.vit.ac.in` traffic, encrypted local credentials/cookies, local-only logout, and no private data in fixtures or logs.
- Keep the primary navigation at five destinations.
- Use `Asia/Kolkata` for VTOP dates and times.
- Preserve whole lab-block attendance behavior; never promise a fractional lab skip.

---

### Task 1: Make submission classification field-aware

**Files:**
- Modify: `app/src/main/kotlin/app/viora/domain/AssignmentStatus.kt`
- Modify: `app/src/test/kotlin/app/viora/HomeAgendaTest.kt`
- Create: `app/src/test/kotlin/app/viora/domain/AssignmentStatusTest.kt`

**Interfaces:**
- Produces: `fun isAssignmentSubmitted(status: String, lastUpload: String): Boolean`
- Consumed by: `VioraUiState.homeDueAssignments` and `TasksScreen` in `MainActivity.kt`.

- [ ] **Step 1: Write failing classifier tests for affirmative and contradictory VTOP fields**

```kotlin
@Test fun `submitted server status wins over a stale negative upload field`() {
    assertTrue(isAssignmentSubmitted("Submitted", "File Not Uploaded"))
}

@Test fun `real upload timestamp is submitted when status is pending`() {
    assertTrue(isAssignmentSubmitted("Pending", "18-Aug-2026 10:15 PM"))
}

@Test fun `explicitly unsubmitted assignment remains pending`() {
    assertFalse(isAssignmentSubmitted("Pending", "File Not Uploaded"))
}
```

- [ ] **Step 2: Run the focused classifier test to verify the contradictory case fails**

Run: `gradle testDebugUnitTest --tests app.viora.domain.AssignmentStatusTest`

Expected: FAIL because the existing merged-text classifier returns `false` for `Submitted` plus `File Not Uploaded`.

- [ ] **Step 3: Implement ordered per-field signals in `AssignmentStatus.kt`**

Use a private `SubmissionSignal` enum and classify `status` before `lastUpload`. Normalize lower-case whitespace. Recognize positive status tokens `submitted`, `uploaded`, and `completed`; recognize negative tokens `not uploaded`, `not submitted`, `pending`, and `missing`. For `lastUpload`, accept a non-placeholder timestamp or positive upload marker only after the status has not provided a positive signal.

```kotlin
private enum class SubmissionSignal { POSITIVE, NEGATIVE, UNKNOWN }

fun isAssignmentSubmitted(status: String, lastUpload: String): Boolean = when (signal(status)) {
    SubmissionSignal.POSITIVE -> true
    SubmissionSignal.NEGATIVE -> signal(lastUpload) == SubmissionSignal.POSITIVE || hasUploadValue(lastUpload)
    SubmissionSignal.UNKNOWN -> signal(lastUpload) == SubmissionSignal.POSITIVE || hasUploadValue(lastUpload)
}
```

Ensure `hasUploadValue` rejects `""`, `"-"`, `"--"`, `"na"`, `"n/a"`, `"none"`, `"file not uploaded"`, and `"not uploaded"`.

- [ ] **Step 4: Expand Home test coverage for contradictory data**

Add a `Submitted` / `File Not Uploaded` assignment to `home due assignments excludes uploaded work` and assert that only the truly pending ID remains.

- [ ] **Step 5: Run focused tests**

Run: `gradle testDebugUnitTest --tests app.viora.domain.AssignmentStatusTest --tests app.viora.HomeAgendaTest`

Expected: PASS.

- [ ] **Step 6: Commit the completed classifier change**

```bash
git add app/src/main/kotlin/app/viora/domain/AssignmentStatus.kt \
  app/src/test/kotlin/app/viora/domain/AssignmentStatusTest.kt \
  app/src/test/kotlin/app/viora/HomeAgendaTest.kt
git commit -m "fix: classify submitted assignments reliably"
```

### Task 2: Project and order marks by course

**Files:**
- Modify: `app/src/main/kotlin/app/viora/VioraAppViewModel.kt`
- Create: `app/src/main/kotlin/app/viora/AcademicProjections.kt`
- Create: `app/src/test/kotlin/app/viora/AcademicProjectionsTest.kt`

**Interfaces:**
- Produces: `data class MarkSectionUi(val courseCode: String, val courseTitle: String, val marks: List<MarkUi>)`
- Produces: `internal fun List<MarkUi>.markSections(): List<MarkSectionUi>`
- Consumed by: the Marks Compose screen in Task 5.

- [ ] **Step 1: Extend the UI mark model and write failing projection tests**

Add `courseCode`, `courseType`, and `weightagePercent` to `MarkUi`, and pass those existing Room fields in `observeResults`.

```kotlin
@Test fun `groups marks by course and orders known assessment types`() {
    val sections = listOf(mark("FAT"), mark("Quiz 1"), mark("CAT 1"))
        .markSections()

    assertEquals(listOf("CAT 1", "Quiz 1", "FAT"), sections.single().marks.map(MarkUi::title))
}
```

- [ ] **Step 2: Run the focused projection test to verify it fails**

Run: `gradle testDebugUnitTest --tests app.viora.AcademicProjectionsTest`

Expected: FAIL because `markSections` does not exist.

- [ ] **Step 3: Implement `markSections` in `AcademicProjections.kt`**

Group by normalized course code, falling back to course title only when code is blank. Sort sections by course code/title. Sort marks with this rank: CAT 1, CAT 2, quiz, assignment, FAT, then other titles alphabetically. Do not rename the VTOP-provided title.

```kotlin
internal fun List<MarkUi>.markSections(): List<MarkSectionUi> =
    groupBy { it.courseCode.ifBlank { it.courseTitle } }
        .map { (key, rows) ->
            MarkSectionUi(
                courseCode = rows.first().courseCode.ifBlank { key },
                courseTitle = rows.first().courseTitle,
                marks = rows.sortedWith(compareBy(::assessmentRank).thenBy { it.title.lowercase() }),
            )
        }
        .sortedBy { it.courseCode.lowercase() }
```

- [ ] **Step 4: Add tests for unknown titles and unavailable values**

Assert that an unrecognized title follows FAT and that `null` scored/maximum/weighted values are preserved rather than converted to zero.

- [ ] **Step 5: Run focused tests**

Run: `gradle testDebugUnitTest --tests app.viora.AcademicProjectionsTest`

Expected: PASS.

- [ ] **Step 6: Commit the mark projection**

```bash
git add app/src/main/kotlin/app/viora/VioraAppViewModel.kt \
  app/src/main/kotlin/app/viora/AcademicProjections.kt \
  app/src/test/kotlin/app/viora/AcademicProjectionsTest.kt
git commit -m "feat: organize assessment marks by course"
```

### Task 3: Calculate attendance allowances through CAT and FAT milestones

**Files:**
- Create: `app/src/main/kotlin/app/viora/domain/AttendanceMilestone.kt`
- Create: `app/src/test/kotlin/app/viora/domain/AttendanceMilestoneTest.kt`
- Modify: `app/src/main/kotlin/app/viora/AcademicProjections.kt`
- Modify: `app/src/test/kotlin/app/viora/AcademicProjectionsTest.kt`

**Interfaces:**
- Produces: `enum class AttendanceMilestone { CAT_1, CAT_2, FAT }`
- Produces: `fun maximumSkippableOccurrences(attended: Int, held: Int, targetPercent: Int, occurrenceUnits: List<Int>): Int`
- Produces: `internal fun VioraUiState.attendanceMilestones(nowEpochMillis: Long): List<CourseAttendanceMilestoneUi>`
- Consumed by: the Attendance Compose screen in Task 5.

- [ ] **Step 1: Write failing pure-calculator tests**

```kotlin
@Test fun `caps skips at classes available before milestone`() {
    assertEquals(2, maximumSkippableOccurrences(18, 20, 75, listOf(1, 1)))
}

@Test fun `lab blocks are skipped as a complete occurrence`() {
    assertEquals(1, maximumSkippableOccurrences(18, 20, 75, listOf(2, 2)))
}
```

- [ ] **Step 2: Run the pure calculator test to verify it fails**

Run: `gradle testDebugUnitTest --tests app.viora.domain.AttendanceMilestoneTest`

Expected: FAIL because the milestone calculator does not exist.

- [ ] **Step 3: Implement the pure allowance calculator**

Sort positive `occurrenceUnits` ascending and count an occurrence only while integer percentage arithmetic keeps `attended / (held + skippedUnits)` at or above `targetPercent`. Return zero when current attendance is already below the target. Do not change `AttendanceCalculator`.

```kotlin
fun maximumSkippableOccurrences(
    attended: Int,
    held: Int,
    targetPercent: Int,
    occurrenceUnits: List<Int>,
): Int {
    if (attended.toLong() * 100 < targetPercent.toLong() * held) return 0
    var skippedUnits = 0
    return occurrenceUnits.sorted().takeWhile { units ->
        val allowed = attended.toLong() * 100 >= targetPercent.toLong() * (held + skippedUnits + units)
        if (allowed) skippedUnits += units
        allowed
    }.size
}
```

- [ ] **Step 4: Add `attendanceMilestones` projection over cached schedule data**

Define `MilestoneState` (`SCHEDULED`, `PASSED`, `NOT_SCHEDULED`, `NO_CLASSES`) and `CourseAttendanceMilestoneUi`. Normalize exam types so `CAT-I`, `CAT 1`, and `CAT1` map to `CAT_1`, similarly for CAT 2 and FAT. For each `AttendanceUi` and future exam, iterate dates from the local date after `nowEpochMillis` through the date before the exam; call the existing `slotsForDate(date)` so holidays, weekday substitutions, and exam overlap suppression remain authoritative. Count matching course slots using `attendanceFor(slot)` and use `attendance.blockSize` as each occurrence's unit count.

- [ ] **Step 5: Write failing projection tests for calendar and lab rules**

Construct a `VioraUiState` with a CAT-I exam, matching weekly slot, and a holiday `AcademicCalendarEntity`; assert the holiday produces no occurrence. Add a lab attendance row (`blockSize == 2`) and assert the displayed allowance is a whole lab session.

- [ ] **Step 6: Run focused tests**

Run: `gradle testDebugUnitTest --tests app.viora.domain.AttendanceMilestoneTest --tests app.viora.AcademicProjectionsTest --tests app.viora.ExamAwareScheduleTest`

Expected: PASS.

- [ ] **Step 7: Commit the milestone projection**

```bash
git add app/src/main/kotlin/app/viora/domain/AttendanceMilestone.kt \
  app/src/test/kotlin/app/viora/domain/AttendanceMilestoneTest.kt \
  app/src/main/kotlin/app/viora/AcademicProjections.kt \
  app/src/test/kotlin/app/viora/AcademicProjectionsTest.kt
git commit -m "feat: plan attendance through exam milestones"
```

### Task 4: Build date-based academic calendar projections

**Files:**
- Modify: `app/src/main/kotlin/app/viora/AcademicProjections.kt`
- Modify: `app/src/test/kotlin/app/viora/AcademicProjectionsTest.kt`

**Interfaces:**
- Produces: `enum class AcademicCalendarMarker { HOLIDAY, EXAM, ASSIGNMENT, CLASS, DAY_ORDER }`
- Produces: `data class AcademicDayEvent(val id: String, val marker: AcademicCalendarMarker, val at: Long?, val title: String, val detail: String)`
- Produces: `internal fun VioraUiState.calendarMarkers(month: YearMonth): Map<LocalDate, Set<AcademicCalendarMarker>>`
- Produces: `internal fun VioraUiState.eventsForDate(date: LocalDate): List<AcademicDayEvent>`
- Consumed by: Calendar Compose screen in Task 6.

- [ ] **Step 1: Write failing calendar projection tests**

```kotlin
@Test fun `calendar marks holiday exam assignment and class on their dates`() {
    val markers = state.calendarMarkers(YearMonth.of(2026, 8))

    assertTrue(AcademicCalendarMarker.HOLIDAY in markers[holidayDate].orEmpty())
    assertTrue(AcademicCalendarMarker.EXAM in markers[examDate].orEmpty())
    assertTrue(AcademicCalendarMarker.ASSIGNMENT in markers[dueDate].orEmpty())
}
```

- [ ] **Step 2: Run the focused calendar test to verify it fails**

Run: `gradle testDebugUnitTest --tests app.viora.AcademicProjectionsTest`

Expected: FAIL because `calendarMarkers` and `eventsForDate` do not exist.

- [ ] **Step 3: Implement deterministic marker and selected-day event projections**

Convert epoch values with `academicZone`. Include cached `AcademicCalendarEntity` rows, `ExamUi` rows, assignment due dates, and `slotsForDate(date)` classes. Use stable IDs prefixed with `calendar:`, `exam:`, `assignment:`, and `class:`. Sort selected-day events by nullable timestamp last, then marker, then title. Mark a calendar row with `DAY_ORDER` when its title/type includes a weekday-order instruction; otherwise mark it `HOLIDAY` only when it indicates a holiday.

- [ ] **Step 4: Add selected-day tests**

Assert that a selected date returns exam venue/time details, an assignment title, and a day-order calendar instruction. Assert that a holiday date does not return a class event because `slotsForDate` suppresses it.

- [ ] **Step 5: Run focused tests**

Run: `gradle testDebugUnitTest --tests app.viora.AcademicProjectionsTest --tests app.viora.ExamAwareScheduleTest`

Expected: PASS.

- [ ] **Step 6: Commit the calendar projection**

```bash
git add app/src/main/kotlin/app/viora/AcademicProjections.kt \
  app/src/test/kotlin/app/viora/AcademicProjectionsTest.kt
git commit -m "feat: project local academic calendar events"
```

### Task 5: Add distinct Courses, Marks, and Attendance screens

**Files:**
- Create: `app/src/main/kotlin/app/viora/AcademicsScreens.kt`
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/CourseDetailScreenTest.kt`

**Interfaces:**
- Consumes: `VioraUiState.markSections()` and `VioraUiState.attendanceMilestones(nowEpochMillis)`.
- Produces: `@Composable internal fun AcademicsScreen(state: VioraUiState, showCourseDetail: (String, String) -> Unit)`.
- Reuses: `internal fun CoursesScreen(state: VioraUiState, showDetail: (String, String) -> Unit)` after changing its visibility from `private` to `internal`.

- [ ] **Step 1: Add a failing Compose test for the academic-screen tabs**

```kotlin
composeTestRule.onNodeWithText("Courses").performClick()
composeTestRule.onNodeWithText("Marks").performClick()
composeTestRule.onNodeWithText("Assessment marks").assertExists()
composeTestRule.onNodeWithText("Attendance").performClick()
composeTestRule.onNodeWithText("Skip allowance").assertExists()
```

- [ ] **Step 2: Run the instrumentation test to verify it fails to compile or find tabs**

Run: `gradle assembleDebugAndroidTest --tests app.viora.CourseDetailScreenTest`

Expected: FAIL because `AcademicsScreen` and its tabs do not exist.

- [ ] **Step 3: Implement `AcademicsScreen` in a focused new file**

Use a `SingleChoiceSegmentedButtonRow` or horizontally scrollable `FilterChip` row with Courses, Marks, and Attendance. Keep each selection as a distinct composable: `CoursesScreen`, `MarksScreen`, and `AttendanceScreen`.

`MarksScreen` renders `MarkSectionUi` cards and text in this order: VTOP title, raw score/max score, weighted score, percentage weight, and status. Render `—` for a missing numeric field.

`AttendanceScreen` renders each course's current `AttendanceCard` information followed by CAT 1, CAT 2, and FAT rows. Use exact state copy: “Safe to skip N classes”, “Passed”, “Not scheduled”, or “No matching classes before this exam”. Include the active target percentage in explanatory text.

- [ ] **Step 4: Replace the Courses destination call site**

In `Dashboard`, replace:

```kotlin
2 -> CoursesScreen(state) { kind, id -> detail = DetailSelection(kind, id) }
```

with:

```kotlin
2 -> AcademicsScreen(state) { kind, id -> detail = DetailSelection(kind, id) }
```

Do not alter the `destinations` list.

- [ ] **Step 5: Run unit, instrumentation compilation, and lint checks**

Run: `gradle testDebugUnitTest assembleDebugAndroidTest lintDebug`

Expected: PASS.

- [ ] **Step 6: Commit the academic screens**

```bash
git add app/src/main/kotlin/app/viora/AcademicsScreens.kt \
  app/src/main/kotlin/app/viora/MainActivity.kt \
  app/src/androidTest/kotlin/app/viora/CourseDetailScreenTest.kt
git commit -m "feat: add marks and attendance screens"
```

### Task 6: Add the Schedule calendar screen

**Files:**
- Create: `app/src/main/kotlin/app/viora/CalendarScreen.kt`
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/CourseDetailScreenTest.kt`

**Interfaces:**
- Consumes: `VioraUiState.calendarMarkers(month)` and `VioraUiState.eventsForDate(date)`.
- Produces: `@Composable internal fun CalendarScreen(state: VioraUiState, initialDate: LocalDate = LocalDate.now(academicZone))`.
- Reuses: Schedule's existing `showExam` detail callback only for exam-card navigation; the calendar can render the same `ExamCard` information inline.

- [ ] **Step 1: Add a failing Compose test for calendar navigation and a selected date**

```kotlin
composeTestRule.onNodeWithText("Calendar").performClick()
composeTestRule.onNodeWithText("Academic calendar").assertExists()
composeTestRule.onNodeWithText("Exam").assertExists()
```

- [ ] **Step 2: Run the instrumentation test to verify it fails**

Run: `gradle assembleDebugAndroidTest --tests app.viora.CourseDetailScreenTest`

Expected: FAIL because the Schedule screen has no Calendar control.

- [ ] **Step 3: Implement the month calendar view**

In `CalendarScreen.kt`, keep `YearMonth` and selected `LocalDate` in Compose state. Provide previous/next-month buttons with content descriptions. Render a seven-column month grid; cells use marker dots/chips for holiday, exam, assignment, class, and day-order markers. Give each day an accessible label containing the formatted date and marker names. Clicking a day updates the selected-day list below the grid.

- [ ] **Step 4: Integrate Calendar without adding a sixth destination**

Add a Timetable/Calendar tab row at the top of `ScheduleScreen`. When Calendar is selected, call `CalendarScreen(state)`; otherwise retain the existing timetable body. Keep semester selection available in timetable mode and preserve the existing share button.

- [ ] **Step 5: Run required validation**

Run: `gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug && scripts/privacy-audit.sh`

Expected: PASS and `privacy audit passed`.

- [ ] **Step 6: Commit the calendar screen**

```bash
git add app/src/main/kotlin/app/viora/CalendarScreen.kt \
  app/src/main/kotlin/app/viora/MainActivity.kt \
  app/src/androidTest/kotlin/app/viora/CourseDetailScreenTest.kt
git commit -m "feat: add local academic calendar view"
```

### Task 7: Perform final device-aware verification and documentation update

**Files:**
- Modify: `docs/FEATURES.md`
- Modify: `docs/TECH_STACK.md`

**Interfaces:**
- Documents: cached-only Marks, Attendance milestone, and Calendar screens; no interface changes.

- [ ] **Step 1: Update product and architecture documentation**

Add the Marks and Attendance screens to `docs/FEATURES.md`. State in `docs/TECH_STACK.md` that milestone allowances use cached exam dates, timetable occurrences, and calendar exceptions, and that the calendar view is local-only.

- [ ] **Step 2: Run the full local validation suite**

Run: `gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug && scripts/privacy-audit.sh && sh -n scripts/*.sh`

Expected: PASS and `privacy audit passed`.

- [ ] **Step 3: Run device smoke validation when an API 26+ emulator/device is attached**

Run: `scripts/device-smoke.sh --fresh`

Expected: `PASS:` output. Do not commit its generated `build/device-smoke` logs, screenshot, or UI dump. If no device is attached, record that device smoke could not run; do not fabricate a pass.

- [ ] **Step 4: Inspect the final diff and commit documentation**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only intended source, test, and documentation changes.

```bash
git add docs/FEATURES.md docs/TECH_STACK.md
git commit -m "docs: describe academic planning views"
```

## Plan self-review

- Spec coverage: Task 1 implements submitted-only Home filtering; Task 2 implements grouped mark data; Task 3 implements CAT/FAT attendance planning with calendar and lab rules; Task 4 builds the cached calendar data projection; Tasks 5 and 6 provide separate academic and calendar UI screens; Task 7 covers documentation and full validation.
- Type consistency: `MarkUi`, `MarkSectionUi`, `AttendanceMilestone`, `CourseAttendanceMilestoneUi`, `AcademicCalendarMarker`, and `AcademicDayEvent` are introduced before their screens consume them.
- Scope: no task changes VTOP transport, Room schema, permissions, server behavior, or analytics.
