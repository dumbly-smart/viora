# Feature plan

Current implementation also includes live-contract attendance rows (theory/lab preserved), assessment marks, grade history, GPA/CGPA, credits, current/next-class context, and per-resource sync freshness. All are cached and processed on-device.

## Navigation

Five primary destinations: Home, Schedule, Courses, Tasks, and Profile/Settings. Search spans courses, assessments, messages, and materials.

## MVP

### Setup and account

- Campus selection, privacy explanation, VTOP username/password, and local text-CAPTCHA solving with interactive verification fallback.
- Validate the session, select the active semester, choose attendance threshold and notification preferences.
- Logout deletes cookies, encrypted credentials, cached personal data, files, and scheduled work.

### Home

- Current/next class with room and faculty.
- Today’s timeline and tomorrow preview.
- Course-wise attendance risk and skippable-class projections.
- DAs and assessments due soon.
- Upcoming exams and latest class messages.
- Pull-to-refresh, last successful sync, and actionable partial-sync errors.

### Schedule

- Day/week timetable, academic calendar, holidays, and exam schedule.
- Filters by semester and exam type.
- Calendar export using Android’s calendar intent/provider only after user confirmation.

### Courses

- Consolidated course page: faculty, slots, attendance, marks, grade, messages, and materials.
- Course materials list with explicit download/open/share actions.
- Marks and grade history; CGPA summary where available.

### Tasks

- Unified DA and assessment feed grouped by due date.
- Due time, upload state, question-paper availability, and deep link to the relevant screen.
- Local reminders; notification tap opens the item.

## Notifications

- DA/assessment: newly posted, due in 24 hours, due in 3 hours, and upload-state change.
- Exams: schedule published/changed, 24-hour reminder, venue/seat change.
- Attendance: threshold crossing and a configurable weekly summary—not a notification after every class.
- Timetable/messages: changed class slot, cancellation/holiday impact, new class message.
- Quiet hours, per-category switches, deduplication, and “why did I get this?” details.

Android background execution is inexact, so reminders are best effort. For exact alarms, ask only if a proven user need justifies the special permission; otherwise schedule WorkManager checks and local notifications.

## Later releases

- Home-screen widget for next class and urgent deadline.
- Attendance what-if planner and semester trend charts.
- Offline full-text search over downloaded material metadata.
- ICS import/export and shareable redacted timetable.
- Multiple VIT campus adapters if endpoint behavior differs.
- Optional app lock using device biometrics.
- Accessibility, dynamic color, tablet/foldable layouts, and localization.

## Explicit non-goals

- No bypass or outsourcing of interactive verification challenges such as reCAPTCHA.
- No submission of assignments or registration actions in the first releases.
- No cloud sync of VTOP credentials or personal academic records.
- No claim that a projection grants permission to miss class.
- No ads or analytics SDK that receives academic data.
