# Calendar interchange design

## Goal

Let a user export Viora's active-semester timetable, cached exams, and
assignment deadlines to a dedicated device calendar; export or share the same
full-detail data as an ICS file; and import one ICS timetable into Viora's
local Schedule view.

There is no redaction mode. Sharing is always an explicit user action through
Android's system share sheet. Viora does not upload calendar data, call VTOP
for an export/import, or add a backend or analytics service.

## Data boundaries

VTOP-derived Room data remains authoritative and unchanged. A new Room-backed
`ImportedCalendarEvent` resource holds one replacement-set of user-imported
ICS events. Importing an ICS file validates it completely and transactionally
replaces that set only after validation succeeds. Imported rows are visibly
labelled as imported, never sent to VTOP, and do not produce VTOP change
notifications.

The database moves from version 8 to 9 through a forward migration and an
instrumentation test. No destructive migration is used.

## ICS codec

A platform-independent codec produces and consumes UTF-8 `text/calendar`
content. It uses `Asia/Kolkata` for VTOP values, escapes text per the ICS
format, and emits deterministic event UIDs.

Export materializes weekly timetable slots from the export date through the
following 180 days. It reuses Viora's existing date/slot projection so cached
holidays, exam-day suppression, and weekday-order substitutions are honored.
It additionally emits cached exams and assignment deadlines. Each event
retains its full cached course/title, venue, faculty/detail, and timing data.

Import accepts timed single events and weekly recurring events. It rejects a
malformed calendar or rows without usable start/end times, preserves supported
event title/location/details, and reports skipped unsupported rows in a safe
user-facing summary. The imported schedule stays local to Viora.

## Device calendar export

When the user chooses **Export to Viora calendar**, Viora requests calendar
access at that point only. It creates or reuses a separate `Viora timetable`
calendar and retains enough local ownership metadata to replace only events it
created on a later export. It never modifies user-created or unrelated
calendar events. Permission denial or a removed calendar produces a recoverable
message and leaves Viora's cached data intact.

## UI flows

Schedule exposes four user-initiated actions for the active semester:

- Export to Viora calendar
- Export ICS
- Import ICS
- Share timetable

Export ICS uses Android's document creator; share uses the generated full
ICS snapshot with Android's share sheet. Import uses the system document
picker and confirms that the incoming timetable replaces existing imported
events. Imported events appear in Schedule with an `Imported` label and have
clear empty and invalid-file states.

Calendar import/export working files are organized under Viora's private
`files/Viora/calendar` tree. Only the final document explicitly created or
shared by the user leaves the app-private sandbox.

## Verification

Unit tests cover ICS escaping, stable IDs, `Asia/Kolkata` timestamps, weekly
recurrence, date-range materialization, holiday/day-order behavior, malformed
files, skipped records, and replacement semantics. Compose tests cover the
four Schedule actions and accessible import/export status. Android
instrumentation covers the v8-to-v9 migration; device validation covers
calendar permission grant/denial and an export update without duplicate events.
