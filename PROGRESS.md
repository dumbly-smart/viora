# Viora progress

Updated: 2026-09-02

## Done

- Local-first Android foundation: encrypted VTOP session storage, VTOP-only
  networking, pure redacted-fixture parsers, Room-backed repositories, cached
  Compose screens, WorkManager refresh, notifications, widgets, private
  downloads, and local logout.
- Architecture and repository guidance are documented in `AGENTS.md` and
  `docs/ARCHITECTURE.md`.
- The `feat/academics-calendar` branch is ready for integration. It fixes
  submitted-assignment classification and adds marks, attendance milestones,
  and local academic calendar views.

## In progress

- Integrate `feat/academics-calendar` through the protected-branch workflow.

## Blocked / needs follow-up

- Run `make device-smoke` on an attached API 26+ device or emulator after the
  academics/calendar branch lands. Include narrow-window calendar checks.
- Keep VTOP HTML fixtures redacted and representative whenever parser layouts
  change. Add a forward Room migration and instrumentation coverage before any
  schema change.

## Guardrails

Viora has no backend, cloud academic-data store, analytics pipeline, or
general-purpose web access. Credentials, cookies, CAPTCHA material, and
academic records must never appear in logs, fixtures, CI artifacts, or third
party services.
