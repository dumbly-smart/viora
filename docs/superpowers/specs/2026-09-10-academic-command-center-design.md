# Academic command-center redesign

## Purpose

Replace Viora's current five-destination presentation with a focused,
dark-only academic command center. The opening experience prioritizes today's
classes and deadlines; planning and course records remain one action away.
This is a presentation and navigation redesign only. It does not alter Room,
sync, authentication, VTOP transport, or privacy boundaries.

## Information architecture

### Today

Today is the default destination and is ordered by immediacy:

1. A next-up hero presents the current or next class, its time, room, and
   primary action.
2. A compact date rail offers immediate day context.
3. One chronological timeline combines classes, assessment deadlines, and
   exams, preserving their source ordering and identities.
4. A `Needs attention` group appears only for overdue/near-due work and
   attendance risk.

Selecting a timeline item opens a contextual bottom sheet. Selecting its
course opens that course in Library. Existing sync and reauthentication actions
remain reachable from the top bar and surface their status without displacing
cached content.

### Plan

Plan is the schedule workspace. A segmented control switches between Timeline
and Calendar views over the same cached classes, exams, deadlines, holidays,
and day-order changes. Timeline is the default and uses one time axis; Calendar
keeps its existing event detail behavior and calendar interchange actions.

### Library

Library is a searchable course collection. A course detail contains its
overview, attendance, assessments, marks, materials, and messages. The
underlying course identity and theory/lab distinction remain unchanged.

Account, sync diagnostics, downloads, notification preferences, and other
settings move to a profile/action sheet invoked from the top bar. They do not
occupy a primary destination.

### Navigation

Compact screens use three labeled bottom-navigation destinations: Today, Plan,
and Library. A central sync action is visually distinct but does not replace a
destination. At 840dp and above, navigation becomes a left rail. Course
navigation stays inside Library rather than introducing another permanent rail.

## Visual system

The UI is permanently dark and uses a restrained, accessible surface system:

- Canvas: deep blue-black `#0B0D12`.
- Elevation: three nearby graphite surface steps; elevation is never expressed
  with bright cards or an all-black page.
- Accent: periwinkle/indigo for active navigation, progress, and primary
  actions.
- Semantic colors: amber for due soon, coral for overdue/risk, and green for
  complete/safe. Color is always accompanied by text or iconography.
- Typography: compact editorial headlines for dates/next actions, muted
  metadata, and tabular numerals for times and percentages.
- Composition: a small number of 20--24dp grouped surfaces; next-up receives
  visual prominence while normal entries are lightweight rows.
- Motion: short content transitions and expand/collapse or bottom-sheet
  transitions only. No decorative animation.

## State, accessibility, and error behavior

Room remains the screen source of truth. A screen renders its valid cache
immediately. Refresh progress is limited to the changing area; loading never
replaces useful cached data. Sync failures remain compact, actionable banners.

Each destination has a contextual empty state: a clear day, no cached plan, or
no library match. Critical states convey meaning in text as well as color.
Controls retain 48dp minimum targets, headings and destination selection retain
semantics, timelines maintain chronological reading order, and sheets expose
actionable labels for screen readers.

## Implementation scope

- Replace the current five-destination shell and floating navigation with the
  three-destination responsive shell.
- Build Today, Plan, and Library composables from existing `VioraUiState` data
  and ViewModel actions; do not introduce direct DAO or gateway calls in UI.
- Consolidate dark theme tokens and remove hard-coded light surfaces from the
  redesigned screens, Android theme, splash, and widget defaults.
- Reuse current Schedule, Calendar, course detail, material, export, and sync
  behavior behind the new entry points before considering new data features.

## Testing

Add Compose coverage for:

- default Today ordering and its priority/empty states;
- the three-destination navigation shell on compact and expanded layouts;
- Plan's timeline/calendar selection;
- Library search and course opening;
- important content descriptions, selected state, and actionable error text.

Keep existing JVM tests intact. Run debug unit tests, debug/instrumentation APK
assembly, lint, the privacy audit, and diff/shell checks. Validate the final
APK on a connected device with the repository smoke harness; do not retain or
commit its private screenshots, UI dumps, or logs.
