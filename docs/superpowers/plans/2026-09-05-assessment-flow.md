# Assessment Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make direct-VTOP submissions refresh correctly and replace the WebView with organized native assessment submission.

**Architecture:** Parse submission evidence and upload-form metadata from authenticated VTOP HTML, upload one capped file through the existing VTOP-only gateway, then refresh Room. Compose shows a seven-day section and course drill-down without storing upload secrets.

**Tech Stack:** Kotlin, Jsoup, OkHttp multipart, Room, Jetpack Compose, Android Storage Access Framework.

**Spec:** `docs/superpowers/specs/2026-09-05-academic-flow-corrections-design.md`

## Global Constraints

- VTOP requests remain HTTPS-only and restricted to `vtop.vit.ac.in`.
- Live VTOP HTML and student data never enter fixtures, logs, or commits.
- Home shows only pending work; Assessments may show submitted work.
- One selected file, capped before upload; no Room schema change.

---

### Task 1: Submission parsing

**Files:**
- Modify: `app/src/main/kotlin/app/viora/parser/DigitalAssignmentParser.kt`
- Modify: `app/src/main/kotlin/app/viora/domain/AssignmentStatus.kt`
- Modify: `app/src/main/kotlin/app/viora/network/VtopGateway.kt`
- Test: `app/src/test/kotlin/app/viora/parser/DigitalAssignmentParserTest.kt`
- Test: `app/src/test/kotlin/app/viora/domain/AssignmentStatusTest.kt`

**Interfaces:**
- Produces: `DigitalAssignmentRecord.uploadLocator: AssignmentUploadLocator?`
- Produces: `AssignmentUploadLocator(requestPath: String, fields: Map<String,String>, fileField: String, acceptedMimeTypes: Set<String>, maxBytes: Long?)`

- [ ] Add synthetic tests where `Uploaded File`, `Last Uploaded Date`, and positive status variants classify as submitted and where a row exposes a VTOP-relative upload request, hidden fields, file field, accepted types, and maximum size.
- [ ] Run `gradle testDebugUnitTest --tests '*DigitalAssignmentParserTest' --tests '*AssignmentStatusTest'`; confirm failures reflect missing aliases/locator.
- [ ] Extend header aliases and positive/negative signals; extract only VTOP-relative request metadata and keep stable assignment IDs unchanged.
- [ ] Re-run the focused tests and commit with `fix: recognize VTOP assessment submissions`.

### Task 2: Native upload transport

**Files:**
- Modify: `app/src/main/kotlin/app/viora/network/VtopGateway.kt`
- Modify: `app/src/main/kotlin/app/viora/network/HttpVtopGateway.kt`
- Create: `app/src/main/kotlin/app/viora/assignment/AssignmentUploadFile.kt`
- Create: `app/src/main/kotlin/app/viora/assignment/AssignmentUploadRepository.kt`
- Test: `app/src/test/kotlin/app/viora/assignment/AssignmentUploadFileTest.kt`
- Test: `app/src/test/kotlin/app/viora/parser/DigitalAssignmentParserTest.kt`
- Modify: gateway fakes under `app/src/test/kotlin/`

**Interfaces:**
- Produces: `suspend fun VtopGateway.uploadDigitalAssignment(semesterId: String, assignmentId: String, fileName: String, mimeType: String, bytes: ByteArray)`
- Produces: `suspend fun AssignmentUploadRepository.upload(semesterId: String, assignmentId: String, uri: Uri): Result<Unit>`

- [ ] Add failing tests proving reads stop above 10 MiB, unsafe filenames are sanitized, missing upload metadata fails, and non-VTOP/non-HTTPS actions are rejected.
- [ ] Run the focused upload/parser tests and confirm the intended failures.
- [ ] Read the selected URI once with the lower of the advertised limit and 10 MiB; enforce accepted MIME types; refetch the assignment to obtain fresh form metadata; resolve actions only against `https://vtop.vit.ac.in`; submit hidden fields plus one file with OkHttp `MultipartBody`.
- [ ] On success refresh `DigitalAssignmentRepository`; on failure retain Room data and expose a safe error.
- [ ] Re-run focused tests and commit with `feat: upload assessments natively`.

### Task 3: Assessments UI

**Files:**
- Modify: `app/src/main/kotlin/app/viora/MainActivity.kt`
- Modify: `app/src/main/kotlin/app/viora/VioraAppViewModel.kt`
- Remove: `app/src/main/kotlin/app/viora/assignment/VtopAssignmentUploadScreen.kt`
- Modify: `app/src/androidTest/kotlin/app/viora/CourseDetailScreenTest.kt`
- Test: `app/src/test/kotlin/app/viora/HomeAgendaTest.kt`

**Interfaces:**
- Consumes: `AssignmentUploadRepository.upload(...)`
- Produces: Assessments overview, course assessment detail, and URI picker callback.

- [ ] Add failing projection tests: Home excludes submitted due work; Assessments’ next-seven-days list includes pending and submitted; course groups preserve all assignments.
- [ ] Add failing Compose tests for Pending/Submitted tags, course drill-down, Submit file, and Replace submission before—but not after—the deadline.
- [ ] Run the focused JVM tests and compile the instrumentation sources; confirm failures are behavioral.
- [ ] Replace the flat Tasks contents with due-this-week cards followed by course cards; move marks/exams out; add assignment/course detail actions and `OpenDocument` file selection.
- [ ] Remove the WebView session state and screen. Refresh only assignments after successful upload and show progress/error state.
- [ ] Re-run focused tests and commit with `feat: organize assessment submission`.
