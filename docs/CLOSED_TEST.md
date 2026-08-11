# Closed-test and device smoke runbook

Use only redacted or dedicated test accounts. Never attach live VTOP HTML, cookies, credentials, screenshots, UI dumps, or logcat output to a public issue.

## Prepare the candidate

1. Provision the five repository secrets described in `README.md` and run the **Signed APK release** workflow.
2. Download the signed APK and checksum artifact.
3. Run `scripts/prepare-closed-test.sh Viora-<version>.apk`. This checks alignment, signature, the optional pinned certificate fingerprint, version metadata, and SHA-256.
4. Install that exact APK on your and your friends' devices. Record the workflow run, version, and artifact checksum privately. Do not create the downloadable GitHub release until those devices pass the checks below.

## Minimum matrix

- Android 8 or 9 phone: min-SDK behavior and legacy icon.
- Android 12 or 13 phone: splash screen, notifications, and background restrictions.
- Android 15 or newer phone: edge-to-edge, predictive system UI, and current target-SDK behavior.
- One tablet, foldable, or emulator at 840 dp or wider: navigation rail and resize behavior.
- At least one OEM known for aggressive battery management when available.

For each target, install the privately distributed candidate, then run:

```bash
scripts/device-smoke.sh --apk Viora-<version>.apk --fresh --output build/smoke/<device>
scripts/device-smoke.sh --apk Viora-<version>.apk --offline --output build/smoke/<device>-offline
```

The offline option temporarily enables airplane mode and restores it on exit. The fresh option explicitly clears Viora's app data.

## Manual checkpoints

- Fresh install shows the branded splash and setup screen without a crash.
- Upgrade preserves the Room cache and encrypted local session.
- Home, schedule, course, task, search, details, downloads, QR share, and logout work.
- The next-class widget handles an active class, a later class, a holiday/day-order exception, and an empty cache.
- Background diagnostics show scheduled work; a completed sync updates duration/outcome and the widget.
- With battery saver/restriction enabled, cached content remains usable and sync retries stay bounded.
- Notification permission denial does not block app use; reminders remain deduplicated.
- Rotation, large font, screen reader focus, dark mode, and wide layout remain usable.
- Interactive VTOP verification is tested only where authorized, and no test artifact contains account data.

Keep the generated smoke artifacts private and delete them after triage.
