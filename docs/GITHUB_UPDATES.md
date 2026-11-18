# GitHub app updates

Viora schedules a separate WorkManager check every six hours with a connected
network and sufficient storage. Android may defer work for battery/network
restrictions. The first check is eligible immediately after the app starts.
Academic sync frequency and logout do not cancel this updater.

Only stable releases from `dumbly-smart/viora` are eligible. Publish the signed
`Viora-<version>.apk` and `.apk.sha256` using the existing release workflow.
Version names and Android version codes must both increase. Downloads use a
separate unauthenticated client restricted to HTTPS GitHub release hosts; no
VTOP cookies, credentials, academic data, or registration numbers are attached.

The APK is capped at 150 MiB, stored privately and checked against the release
SHA-256 (GitHub asset digest, or the published checksum), installed package name,
newer Android version code, and the installed signing certificate. Android's
installer performs the final APK signature verification. Different signing keys
and package names are rejected; development packages cannot install production
releases. Certificate rotation is intentionally not supported by this updater.

A notification offers installation. On the next foreground resume a prompt also
offers the update, including when notification permission is denied. The user
may need to allow Viora as an installation source once and confirm installation
in Android. There is no guaranteed silent installation or exact six-hour timing.
Declining installation keeps the downloaded update; later checks still discover
newer releases. All account storage, encrypted sessions, preferences, Room
records and academic work remain in place; installation never invokes logout,
uninstall, or account clearing. VTOP may independently expire remote sessions,
which use the existing automatic authentication recovery.

Device validation must upgrade the same package with the same signing key and
verify cache/credentials are retained without printing their contents. Never
uninstall or clear app data as an update test on a user's installed app.

GitHub connections use Android's system certificate trust, independently of the
existing VTOP certificate pins. GitHub/CDN TLS certificates are not pinned: the
installed APK signing key is the end-to-end update trust anchor. Even a
compromised release transport cannot install a package signed with another key;
checksum and monotonic version checks additionally reject corruption/rollback.

## Device verification (2026-11-18)

On the connected Android 16 phone, the unique six-hour schedule, invalid/equal
version rejection, valid newer APK staging, and live public GitHub metadata
request passed. The updater's foreground prompt, one-time source permission,
Android update confirmation, Play Protect scan and final installation were
exercised with two locally signed builds of the existing package (codes 15 and
16). All ten existing account/cache files checked before and after both the
initial deployment and the actual installer update were byte-identical. The
test package was removed, leaving one Viora application installed. Production
release APKs must use the same signing key as the installed release; the local
signing key is for development and is not published as a release key.
