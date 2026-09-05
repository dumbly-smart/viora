# Complete Marks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display every marks component VTOP returns and retain valid marks when grades or CGPA fail.

**Architecture:** Broaden the pure marks parser for supported VTOP table variants, then split marks persistence from grade/CGPA persistence inside the repository while retaining Room as source of truth.

**Tech Stack:** Kotlin, Jsoup, Room, Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-09-05-academic-flow-corrections-design.md`

## Global Constraints

- Fixtures are synthetic and redacted.
- Course plus theory/lab identity is preserved.
- A malformed response never replaces valid cached rows.
- No Room schema change.

---

### Task 1: Parse all mark components

**Files:**
- Modify: `app/src/main/kotlin/app/viora/parser/AcademicResultsParsers.kt`
- Create: `app/src/test/resources/fixtures/marks_variants.html`
- Create: `app/src/test/kotlin/app/viora/parser/MarksParserTest.kt`

**Interfaces:**
- Produces: `MarksParser.parse(html): ParseResult<List<MarkRecord>>` containing CAT, FAT, quiz, DA, lab, and unweighted rows.

- [ ] Add a failing fixture test with `thead/th`, `tableHeader/td`, missing weightage, colspan course headings, and theory/lab rows; assert literal titles and scores.
- [ ] Run `gradle testDebugUnitTest --tests '*MarksParserTest'` and confirm the parser rejects or omits those rows.
- [ ] Normalize both header styles, carry course context into rows, require an identifiable component rather than weightage columns, and retain unavailable numeric fields as null.
- [ ] Re-run the test and commit with `fix: parse every VTOP mark component`.

### Task 2: Persist partial result success

**Files:**
- Modify: `app/src/main/kotlin/app/viora/data/ResultsRepository.kt`
- Create: `app/src/androidTest/kotlin/app/viora/database/ResultsRepositoryInstrumentedTest.kt`

**Interfaces:**
- Consumes: existing `replaceMarks` and `replaceGrades` DAO transactions.
- Produces: independent marks and grade/CGPA refresh outcomes under the combined results status.

- [ ] Add an in-memory Room test where marks succeed and grades fail; assert new marks persist and previous grades remain.
- [ ] Compile/run the focused instrumentation test and confirm current all-or-nothing refresh fails it.
- [ ] Fetch and persist marks independently; fetch grades/CGPA independently; set `results` fresh only when both paths succeed and a safe partial-error state otherwise.
- [ ] Re-run the test and commit with `fix: retain marks on partial results failure`.

### Task 3: Complete marks UI

**Files:**
- Modify: `app/src/main/kotlin/app/viora/AcademicsScreens.kt`
- Modify: `app/src/main/kotlin/app/viora/AcademicProjections.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/CourseDetailScreenTest.kt`

**Interfaces:**
- Consumes: `List<MarkUi>.markSections()`.
- Produces: course-grouped component rows with nullable score/weightage/status display.

- [ ] Add a failing Compose test containing CAT, FAT, quiz, DA, and lab rows and assert each remains visible under the correct course/type.
- [ ] Change headings from assessment-only wording to “Marks”; show all raw/max/weighted fields only when provided and retain publication status.
- [ ] Re-run focused tests and commit with `feat: show complete course marks`.

