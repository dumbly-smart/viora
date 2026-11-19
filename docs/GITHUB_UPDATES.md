# GitHub app updates

Viora schedules a separate WorkManager check every six hours with a connected
network and sufficient storage. Android may defer work for battery/network
restrictions. The first periodic check is eligible immediately after the app starts. Opening
the app also requests a check; concurrent checks are serialized and duplicate
requests within 30 seconds are suppressed.
Academic sync frequency and logout do not cancel this updater.

Only stable releases from `dumbly-smart/viora` are eligible. Publish the signed
`Viora-<version>.apk` and `.apk.sha256` using the existing release workflow.
Version names and Android version codes must both increase. Downloads use a
separate unauthenticated client restricted to HTTPS GitHub release hosts; no
VTOP cookies, credentials, academic data, or registration numbers are attached.

The APK is capped at 150 MiB, stored privately and checked against the release
SHA-256 (GitHub asset digest, or the published checksum), installed package name,
newer Android version code, and the installed signing certificate. Android's
installer performs the final APK signature verification. Unrelated signing keys and different package names are rejected. On Android 9+
a single-signer update may rotate keys only when Android's verified signing
history includes the current signer and the new signer. Multi-signer rotation
and signing-key rollback are rejected. Android verifies the rotation proof at
installation.

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

## Moving an existing development installation to the production key

The optional `Viora-<version>-migration.apk` uses v3 signing with a proof from
the previous local key to the existing production certificate. It supports
Android 9+ only. The normal APK keeps v1/v2/v3 support and its existing
production identity for Android 8 and newer. Both APKs use the same source,
package, version and production signer. Development builds prefer the migration
asset; production builds prefer the normal asset. Subsequent normal updates
work after migration without changing the package or clearing data.

Create the public proof locally with `scripts/signing/CreateSigningLineage.java`
and the Android build-tools apksigner JAR. Supply the old key only via local
`VIORA_OLD_KEYSTORE_PATH`, `VIORA_OLD_KEYSTORE_PASSWORD`, `VIORA_OLD_KEY_ALIAS`
and `VIORA_OLD_KEY_PASSWORD` environment variables; the arguments are the new
public X.509 certificate and output proof path. The helper uses the old private
key to authorize the new public certificate. It does not require the new
private key or export the old key. Only base64 of the public proof is stored as
`VIORA_SIGNING_LINEAGE_BASE64` in GitHub Secrets. Installed-data transfer is
allowed, and the bridge carries Android's signature permission capability so
the rotated package can retain ownership of the AndroidX receiver permission it
already declared. Old-key rollback, shared UID and authenticator capabilities
are disabled.

The existing release workflow signs and verifies the optional migration APK
with the production key, retaining the ordinary primary APK unchanged.
`VIORA_VERIFY_MIN_SDK=28` is used only for migration verification. Tagged
release version codes are `1000 + GITHUB_RUN_NUMBER`, reserving lower codes for
local development and keeping future tagged updates above the tested local
builds. Manually dispatched version codes must remain monotonic.
