# Academic Flow Corrections Design

## Goal

Correct assignment freshness and organization, expose every VTOP mark component,
make attendance milestone allowances forward-looking, and simplify Schedule.

## Assignments

- Home shows only pending, unsubmitted assignments due in the next seven days.
- The Assessments page starts with every assignment due in the next seven days,
  including submitted work, with explicit Pending or Submitted tags.
- Below that, the page shows one card per current-semester course. Opening a
  course shows all its assignments without flattening every course into one list.
- A pending assignment accepts one file through Android's document picker.
  A submitted assignment may replace its file before its deadline.
- Native upload replaces the assignment WebView. Viora obtains the upload action,
  hidden form values, accepted type, and server limit from authenticated VTOP
  markup, rejects non-HTTPS/non-`vtop.vit.ac.in` actions, caps reads, uploads with
  the existing encrypted session, and refreshes assignments after success.
- Parser fixtures cover alternate VTOP status/upload headers so submissions made
  directly in VTOP stop appearing as pending after a successful refresh.
- Failed refreshes retain cached assignments and visibly remain stale; Viora does
  not infer a remote submission when VTOP cannot be reached.

## Marks

- Marks shows every component returned by VTOP: CATs, FAT, quizzes, digital
  assessments, labs, and other scored components.
- Parsing accepts supported `th` and `td` header layouts and components that omit
  weightage fields. Course and theory/lab identity remain intact.
- Successfully parsed marks are committed independently from grades and CGPA, so
  a later result-resource failure cannot suppress valid marks.
- The UI groups components by course and labels raw score, maximum, weightage,
  publication status, and unavailable fields without inventing values.

## Attendance milestones

- Prefer an explicit, verified VTOP future-class total if VTOP exposes one. Do
  not guess an endpoint or trust an undocumented value.
- Otherwise project VIT-sourced current-semester timetable slots from now until
  each CAT-1, CAT-2, or FAT start in `Asia/Kolkata`.
- Apply academic-calendar holidays, weekday/day-order substitutions, exam-day
  suppression, course/type matching, and whole lab-block sizes.
- Exam suppression is global for each CAT/FAT series, not course-specific.
  Prefer an explicit VIT academic-calendar exam-period end. If VIT does not
  expose the complete all-slot window, suppress through the latest cached exam
  in that series and conservatively resume on the next valid instructional
  Monday or explicit day-order date. Present this fallback as an estimate.
- Reuse this global exam window in Home, Schedule, calendar export, and
  attendance projections so students whose own slots finish early are not
  projected as attending classes while other slot exams are still running.
- Let `A` be attended units, `H` held units, and `F` all projected units before
  the milestone. Choose the greatest number of whole occurrences whose skipped
  units `S` satisfy:

  `(A + F - S) / (H + F) >= target`

  This assumes every projected occurrence not skipped is attended. It therefore
  credits classes after CAT-2 when calculating FAT capacity and can show future
  recovery even when current attendance is below target.

## Schedule

- Remove the previous-semester selector from Schedule. Historical selection
  remains available elsewhere.
- Keep Schedule backed by the active current-semester state.
- Place calendar import/export/share controls after the timetable entries and
  before ancillary academic-calendar/exam sections.

## Safety and testing

- No schema change is required.
- Use synthetic, redacted parser fixtures only; never retain live VTOP markup.
- Add failing-first domain, parser, repository, and Compose tests for each change.
- Preserve Room cache-on-failure behavior, VTOP-only networking, private file
  handling, accessibility semantics, and `Asia/Kolkata` time calculations.
