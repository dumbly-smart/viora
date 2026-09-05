# Feature plan

Current implementation also includes live-contract attendance rows (theory/lab preserved), assessment marks, grade history, GPA/CGPA, credits, current/next-class context, and per-resource sync freshness. All are cached and processed on-device.

## Navigation

Five primary destinations: Home, Schedule, Courses, Assessments, and Profile/Settings. Search spans courses, assessments, messages, and materials.

## MVP

### Setup and account

- Campus selection, privacy explanation, VTOP username/password, and local text-CAPTCHA solving with interactive verification fallback.
- Validate the session, select the active semester, choose attendance threshold and notification preferences.
- Logout deletes cookies, encrypted credentials, cached personal data, files, and scheduled work.

### Home

- Current/next class with room and faculty.
- Today’s timeline and tomorrow preview.
- Course-wise attendance risk and skippable-class projections.
- Pending, unsubmitted assessments due in the next seven days.
- Upcoming exams and latest class messages.
- Pull-to-refresh, last successful sync, and actionable partial-sync errors.

### Schedule

- Day/week timetable, academic calendar, holidays, and exam schedule.
- Calendar view showing cached classes, deadlines, exams, holidays, and day-order exceptions; it is local-only and does not add a separate calendar service.
- Current-semester timetable without a semester selector; historical selection
  remains in settings.
- User-initiated export to a dedicated Viora Android calendar, full-detail ICS
  export/share, and transactional ICS timetable import labelled in Schedule.

### Courses

- Consolidated course page: faculty, slots, attendance, marks, grade, messages, and materials.
- Course materials list with explicit download/open/share actions.
- Marks screen with every cached VTOP component grouped by course, including
  raw/max scores, optional weightage, publication status, and theory/lab type.
- Attendance screen with cached attendance rows and forward-looking CAT 1,
  CAT 2, and FAT skip capacity. Global exam windows use explicit VIT dates or
  a clearly labelled conservative estimate.
- Marks and grade history; CGPA summary where available.

### Assessments

- Submitted-inclusive work due within seven days, followed by course drill-down
  containing every cached assignment.
- Pending/Submitted tags, due time, and native single-file submission or
  replacement before the deadline.
- Local reminders; notification tap opens the item.

## Notifications

- DA/assessment: newly posted, due in 24 hours, due in 3 hours, and upload-state change.
- Exams: schedule published/changed, 24-hour reminder, venue/seat change.
- Attendance: threshold crossing and a configurable weekly summary—not a notification after every class.
- Timetable/messages: changed class slot, cancellation/holiday impact, new class message.
- Quiet hours, per-category switches, deduplication, and “why did I get this?” details.
- Attendance changes are combined into one replaceable notification that opens
  Attendance; cached CGPA of 9.00 or higher disables 75% warning criteria.

Android background execution is inexact, so reminders are best effort. For exact alarms, ask only if a proven user need justifies the special permission; otherwise schedule WorkManager checks and local notifications.

## Later releases

- Home-screen widget for next class and urgent deadline.
- Attendance what-if planner and semester trend charts.
- Offline full-text search over downloaded material metadata.
- Accessibility, dynamic color, tablet/foldable layouts, and localization.

## Explicit non-goals

- No bypass or outsourcing of interactive verification challenges such as reCAPTCHA.
- No registration actions or uploads to any host other than authenticated
  `https://vtop.vit.ac.in` form actions.
- No cloud sync of VTOP credentials or personal academic records.
- No claim that a projection grants permission to miss class.
- No ads or analytics SDK that receives academic data.
