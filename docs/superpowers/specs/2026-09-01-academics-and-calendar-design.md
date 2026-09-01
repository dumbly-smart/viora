# Academics, attendance milestones, and calendar design

## Goal

Correct the Home screen so it shows only future digital assignments that VTOP has not submitted, and give students dedicated local-first views for marks, attendance planning, and their academic calendar.

The feature remains a direct VTOP client: it introduces no backend, analytics, external calendar integration, permissions, or new network hosts.

## Navigation and screens

The five primary destinations remain Home, Schedule, Courses, Tasks, and More. The Courses destination becomes the entry point for three distinct academic screens:

- **Courses:** the existing consolidated course list and details.
- **Marks:** all cached assessment components, grouped by course. Each component shows its VTOP title (for example CAT, FAT, quiz, digital-assignment assessment), score/max marks, weighted mark when present, and VTOP status.
- **Attendance:** current course attendance plus attendance-safe skip allowances through CAT 1, CAT 2, and FAT.

Schedule receives a dedicated **Calendar** view. Its month grid marks cached holidays, exam dates, assignment deadlines, and class/day-order calendar entries. Selecting a date opens the complete local event list for that date, including exam time/venue/seat, assignments, and calendar instructions such as a substitute weekday order.

These are separate composable screens/views, rather than extra bottom-navigation destinations, so the compact-device navigation remains at five items.

## Submitted-assignment classification

Home currently filters assignments through a text classifier that merges VTOP's `status` and `lastUpload` values. A negative phrase in either field can override a positive submission signal in the other field, causing contradictory VTOP rows to appear as pending.

Replace this with a field-aware, normalized classifier:

1. A positive terminal server status such as submitted, uploaded, or completed is submitted.
2. Otherwise, a real upload timestamp or affirmative upload/file marker is submitted.
3. Explicit negative values such as not uploaded, not submitted, pending, or missing are not submitted.
4. Blank or unrecognized values are treated as not submitted, which preserves visibility for work that needs confirmation.

The Home due-soon list continues to require a due time after now and within its existing seven-day horizon, then includes only assignments not classified as submitted. The Tasks screen uses the same classifier so Home and Tasks cannot disagree.

## Marks data and presentation

No new VTOP endpoint or persistence schema is required. The existing marks sync stores title, raw score, maximum score, weighted score, status, course code/title, and type in Room.

The Marks screen projects those rows into course sections. Components are sorted using a readable assessment order (CAT 1, CAT 2, quizzes, assignments, FAT, then other VTOP titles) and retain the original title rather than inventing a category. Missing scores/weights render as unavailable, not zero. A course with no mark components is not displayed in the marks list.

## Attendance milestone projection

The Attendance screen computes an allowance per course and milestone from cached data:

1. Find the next CAT 1, CAT 2, or FAT exam for the active semester using normalized VTOP exam types.
2. Starting after the present time, materialize that course's scheduled class occurrences strictly before the exam start. Reuse the app's timetable, holiday suppression, and substitute-weekday calendar rules.
3. Use the course's attendance units and its existing lab-block behavior. A lab session remains a whole block; the UI never promises a fraction of a lab.
4. Determine the largest number of those future occurrences the student may miss while maintaining the configured attendance target, beginning from the currently cached attended/held totals.

Show “Not scheduled” when a milestone is absent, “Passed” when it has already begun, and an explanation when no matching future class sessions exist. The values are planning guidance only: VTOP attendance remains authoritative.

## Error handling and privacy

All screens render the most recent valid Room cache while sync is running or partially failing. If calendar, exam, marks, attendance, or timetable data is missing, the corresponding screen uses an explicit empty/unavailable state; it does not infer dates or fabricate scores.

The feature must not log, expose, upload, or embed personal academic data. Tests use redacted fixtures only. The existing VTOP host boundary, encrypted credential/cookie storage, and local-only logout behavior are unchanged.

## Test plan

- Unit tests for submitted-assignment classification, including contradictory status/upload strings and recognized VTOP variants.
- Home due-assignment tests confirming submitted work is excluded.
- Unit tests for mark grouping and deterministic assessment ordering.
- Attendance milestone tests for CAT/FAT matching, class occurrences before an exam, holidays/day-order substitutions, target boundaries, and lab blocks.
- Calendar projection tests for date markers and selected-day event composition.
- Compose/instrumentation coverage for accessible labels, empty states, and navigation between academic/calendar views where practical.

## Non-goals

- Syncing to Google Calendar or requesting calendar permissions.
- Calling VTOP for a one-off screen render; all UI reads the local cache.
- Predicting unannounced exams, score values, or future attendance data.
- Altering the existing VTOP transport, authentication, or Room schema for this feature.
