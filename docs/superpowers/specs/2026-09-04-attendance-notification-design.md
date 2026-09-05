# Attendance notification design

## Goal

Prevent attendance synchronization from producing a burst of notifications,
while removing percentage-threshold warnings for students whose cached CGPA is
9.00 or higher.

## Eligibility rule

The attendance-warning cutoff is inclusive: a cached CGPA greater than or equal
to 9.00 disables all 75-percent attendance warnings and risk messaging. Raw
attendance remains visible, and ordinary attendance-change updates remain
enabled. A missing CGPA does not imply eligibility, so the normal 75-percent
rule remains active until VTOP provides a qualifying CGPA.

The UI must describe this as Viora applying the 9-point attendance rule; it must
not promise institutional eligibility or state that attendance can be skipped
without limit. The cached VTOP CGPA is the only input to this rule.

## Notification batching

Viora retains one `AcademicChangeEntity` per changed attendance row so the
on-device change history remains detailed. At notification publication time,
all previously unpublished attendance changes from the completed sync are
combined into one `Attendance updated` notification. It contains a concise
summary, expands to show individual updated course values, and uses one stable
Android notification identifier so attendance notifications replace rather
than stack.

Publishing the batch records every included change in the notification ledger.
A later sync therefore includes only newly detected attendance changes. Quiet
hours and notification-permission denial leave changes unpublished so a later
eligible publication can surface them. Other academic-change categories retain
their existing behavior.

For students below 9.00 CGPA, below-target rows are summarized in the same
single attendance notification instead of generating one warning per course.
For students at or above 9.00, that warning portion is omitted. A sync with no
new attendance changes and no newly relevant below-target state emits no
attendance-update notification.

## Navigation and UI

The notification carries an `attendance` destination. `MainActivity` maps that
destination to the Courses navigation item and initializes `AcademicsScreen`
on its Attendance tab. The screen shows the current Room-backed values; it does
not depend on notification payload data.

Attendance cards and home guidance continue to show raw attended/held values.
When cached CGPA is at least 9.00, percentage-threshold warnings, recovery
counts, and skip-limit warnings are replaced by a short 9-point-rule status.
Changing or losing the qualifying cached CGPA immediately restores normal
75-percent projections.

## Data and privacy boundaries

No academic information leaves the device. Notification visibility stays
private. CGPA and attendance are read from Room, and no new network request is
introduced. The notification text contains only the same course labels and
counts already used by existing local notifications.

## Verification

Pure tests cover the inclusive 9.00 cutoff, below-cutoff and missing-CGPA
behavior, batch summaries, and stable batch identity. Notification tests cover
one publish call for many attendance changes, ledger deduplication, stable
replacement IDs, and omission of threshold warnings for qualifying students.
Compose tests cover the direct Attendance destination and the qualifying-CGPA
status without 75-percent warning copy. Existing notification, attendance, and
privacy checks must continue to pass.
