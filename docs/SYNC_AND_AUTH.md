# Sync and authentication

## Login once, realistically

“Login once” means the user enters credentials during setup and is not prompted on every launch. VTOP sessions can expire independently, and CAPTCHA may make silent login impossible. The app therefore uses this state machine:

```text
Unconfigured -> Authenticating -> Active
                      |             |
                      v             v
                NeedsCaptcha <- SessionExpired
                      |
                      v
                    Active

Active -> LoggedOut (user action; wipe all account data)
```

Prefer persisting the authenticated cookie jar. If the session expires, attempt re-authentication only when policy permits and a CAPTCHA is not required. If a challenge appears, keep cached data readable and show one clear “Session expired—verify to sync” action.

If password retention is required for re-authentication, encrypt it with an app-specific AES-GCM key in Android Keystore, exclude it from backup, redact it from logs, and delete it on logout. A safer setup option is “remember session only,” which may prompt for the password after a long expiry.

## Foreground sync

Every cold start and user refresh enqueues unique sync work. Apply a short freshness window to avoid duplicate traffic during rotations/relaunches. Fetch independent resources concurrently with a small cap; serialize calls that share CSRF/session state.

Suggested priority:

1. Validate session and active semester.
2. Today/timetable, attendance, DAs, exams, messages.
3. Marks, grades, calendar.
4. Course-page metadata. Download material bodies only on demand.

Each resource commits independently in a Room transaction. One broken VTOP page must not erase good cached data or fail the entire dashboard.

## Background sync

Use unique periodic WorkManager work with network and battery constraints. Android periodic work has a minimum interval and is not exact; use a conservative cadence such as 4–6 hours, plus app-open sync. Back off exponentially on VTOP errors, stop retry storms on authentication failure, and add jitter.

Notifications are change-driven: compare the new snapshot with the last committed snapshot, write a durable ledger entry, then notify once. Never infer “new DA” merely because a parser temporarily returned an empty page.

## Scraper resilience

- Detect login/session-timeout HTML before parsing feature pages.
- Validate semantic invariants (nonblank course IDs, plausible dates/counts).
- Keep selector fallbacks local to versioned parsers.
- Save only redacted diagnostic fingerprints in release builds.
- Remote parser changes ship through signed app updates, not downloaded executable code.
- Rate-limit calls and identify the app honestly where VTOP policy allows.

## Threat model highlights

- Device theft: secrets encrypted at rest; optional biometric app lock.
- Malicious backup: credentials/cookies excluded from Android backup.
- Logs/crash reports: centralized redaction; academic payloads disabled by default.
- MITM: normal platform certificate validation; no trust-all client.
- Rooted device: warn that hardware-backed protection cannot guarantee secrecy.
- Supply chain: dependency locking, secret scanning, signed release builds.

