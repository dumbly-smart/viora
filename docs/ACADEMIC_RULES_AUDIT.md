# Academic rules audit

The custom Compose UI is retained. The following rules run against Room data;
failed VTOP requests preserve valid cached snapshots.

| Area | Rules and integration |
| --- | --- |
| Active semester | An active live exam period takes precedence over a newer registration term. Explicit historical selections in settings are respected. Saved selection is preserved when exam discovery fails; the existing rollover policy is used when discovery provides no active-period evidence without a request failure. |
| Exam periods | CAT 1, CAT 2 and FAT series suppress classes through their complete period. Explicit calendar start/end/resumption dates take precedence; calendar-only boundaries work before individual exams sync. Conservative resumption estimates remain labelled as estimates. |
| Classes | Home, dated timetable, calendar projections, attendance projections, next-class widget and class reminders use the exam-period rules. Holidays and instructional day-order exceptions still apply. |
| Home | Active exam periods prioritize exams in the upcoming panel. The clock advances while the screen remains open. Missing exam/calendar data is disclosed instead of presenting the weekly timetable as confirmed exam-period activity. |
| Assessments | Submitted/pending classification uses upload state as well as status. Course drill-down, due dates and native submission/replacement remain present. Pending reminders and submitted confirmations retain the existing schedule. |
| Exam notifications | Existing twelve-hour and forty-minute reminders, published schedule changes, rooms and seats remain supported. Exam reminders do not depend on class reminders being enabled. |
| Notification delivery | Category preferences, quiet hours in Asia/Kolkata and deduplication apply. Already scheduled class notifications recheck cached exam suppression before delivery. Rescheduling cancels obsolete alarms. |
| Attendance | Theory/lab identity, recovery and skip calculations, milestone projections, batched notifications and the nine-point CGPA rule remain present. |
| Marks and grades | Every cached assessment component remains available with raw score, maximum, weightage and publication state. Grade headers can occur outside thead. Explicit unpublished grades preserve existing cached grades. |
| CGPA | Both adjacent label/value rows and horizontal credit/CGPA/grade-count tables are supported. Missing or malformed summary data still fails validation. |
| Calendar and files | CLI-Top-compatible month/class-group calendar requests and month-grid parsing, holidays, noninstructional days, day-order exceptions, local calendar projections, ICS interchange, timetable sharing, and private user-initiated materials downloads remain present. |
| Refresh and account | Saved-account reauthentication, CAPTCHA fallback, per-resource refresh status, cache preservation and local logout remain present. |

## Verification

Focused regression cases cover calendar-only exam suppression, dated home/schedule
projections, class reminder suppression, the widget, active-semester precedence,
horizontal CGPA and explicit unpublished grade responses. Existing domain,
parser, repository and calendar tests run with these regressions.

The opt-in live probe uses production authentication, semester resolution and
all six academic repositories. It emits only fixed error categories and assertions;
account data, response bodies, cookies and CAPTCHA contents are not saved in test
fixtures or committed artifacts. Synthetic Compose and Room tests cover the UI
and cache behavior independently of VTOP availability.

## Validation results

- 188 JVM tests passed after the complete changes.
- Live Pixel refresh passed authentication, semester resolution, all six resource
  groups, and class-reminder exam suppression. Calendar month-grid data is fetched
  through the real VTOP protocol.
- Focused device tests passed custom-home exam priority and Room grade/GPA cache
  preservation (three tests).
- The broader legacy Compose suite was also attempted: 18 of 31 tests failed on
  custom-layout assertions, visibility/scroll actions, and test-host startup.
  That suite is not reported as passing. Its existing source is retained; the
  new focused tests wait for the first semantics root without fixed sleeps.

<details>
<summary>Legacy UI assertions that failed in the broader audit run</summary>

- `app.viora.HomeScreenTest.homeRowsOpenTheirAcademicDetail`
- `app.viora.HomeScreenTest.todayTimelineOrdersAcademicDeadlinesChronologically`
- `app.viora.HomeScreenTest.dashboardOpensPlanAndLibraryContent`
- `app.viora.HomeScreenTest.homeShowsReferenceHeroAndChronologicalAcademicRows`
- `app.viora.HomeScreenTest.dashboardDefaultsToExactlyThreeAcademicDestinations`
- `app.viora.HomeScreenTest.attendanceDeepLinkReturnsToCoursesAfterExplicitLibrarySelection`
- `app.viora.CalendarScreenTest.planKeepsInterchangeActionsInTimelineButNotCalendar`
- `app.viora.CalendarScreenTest.calendarShowsEveryMarkerInTheLegendAndDayCell`
- `app.viora.CalendarScreenTest.scheduleOffersCalendarInterchangeAndLabelsImportedEvents`
- `app.viora.CalendarScreenTest.selectingADayShowsOnlyThatDatesEvents`
- `app.viora.CalendarScreenTest.scheduleCalendarShowsTheSelectedDatesExam`
- `app.viora.CalendarScreenTest.scheduleShowsCurrentTimetableBeforeInterchangeWithoutSemesterChips`
- `app.viora.CourseDetailScreenTest.assessmentsShowWeeklyStatusesAndCourseDrillDown`
- `app.viora.CourseDetailScreenTest.attendanceShowsCalculationSourceOnceAtTheBottom`
- `app.viora.CourseDetailScreenTest.marksShowCgpaAfterEveryComponent`
- `app.viora.CourseDetailScreenTest.courseDetailOpenedFromLibraryPreservesLabIdentityAndMessages`
- `app.viora.CourseDetailScreenTest.courseShowsEveryMarkAcrossCodeFormatsWhenVtopTitlesDiffer`
- `app.viora.CourseDetailScreenTest.courseShowsAssignmentsDueAndMaterialsAcrossCodeFormats`

</details>
