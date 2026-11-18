#!/usr/bin/env sh
set -eu

# Optional public proof, signed by the previous key. No previous private key is
# uploaded to CI. The production key signs both the regular and migration APK.
if [ -z "${VIORA_SIGNING_LINEAGE_BASE64:-}" ]; then
    echo "No signing migration configured"
    exit 0
fi
source_apk="${1:?signed production APK required}"
test -f "$source_apk"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Android SDK required}}"
apksigner="$(find "$sdk_root/build-tools" -type f -name apksigner | sort -V | tail -n 1)"
test -n "$apksigner"
lineage="$(mktemp "${TMPDIR:-/tmp}/viora-lineage.XXXXXX")"
trap 'rm "$lineage"' EXIT
chmod 600 "$lineage"
printf '%s' "$VIORA_SIGNING_LINEAGE_BASE64" | base64 --decode > "$lineage"
migration_apk="${source_apk%.apk}-migration.apk"
"$apksigner" sign --ks "$VIORA_KEYSTORE_PATH" --ks-key-alias "$VIORA_KEY_ALIAS" \
    --ks-pass env:VIORA_KEYSTORE_PASSWORD --key-pass env:VIORA_KEY_PASSWORD \
    --lineage "$lineage" --rotation-min-sdk-version 28 \
    --v1-signing-enabled false --v2-signing-enabled false --v3-signing-enabled true \
    --out "$migration_apk" "$source_apk"
VIORA_VERIFY_MIN_SDK=28 scripts/verify-release-apk.sh "$migration_apk"
