#!/usr/bin/env sh
set -eu

APK="${1:-}"
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "usage: $0 path/to/signed-release.apk" >&2
    exit 2
fi

find_android_tool() {
    tool="$1"
    if command -v "$tool" >/dev/null 2>&1; then
        command -v "$tool"
        return
    fi
    sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    if [ -n "$sdk_root" ]; then
        find "$sdk_root/build-tools" -type f -name "$tool" 2>/dev/null | sort -V | tail -n 1
    fi
}

APKSIGNER="$(find_android_tool apksigner)"
ZIPALIGN="$(find_android_tool zipalign)"
if [ -z "$APKSIGNER" ] || [ -z "$ZIPALIGN" ]; then
    echo "Android build-tools apksigner and zipalign are required" >&2
    exit 1
fi

"$ZIPALIGN" -c -P 16 -v 4 "$APK" >/dev/null
VERIFY_OUTPUT="$($APKSIGNER verify --verbose --print-certs "$APK")"
printf '%s\n' "$VERIFY_OUTPUT"

if printf '%s\n' "$VERIFY_OUTPUT" | grep -qi "Android Debug"; then
    echo "release APK is signed with an Android debug certificate" >&2
    exit 1
fi

if [ -n "${VIORA_CERT_SHA256:-}" ]; then
    actual="$(printf '%s\n' "$VERIFY_OUTPUT" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1 | tr -d ': ' | tr '[:lower:]' '[:upper:]')"
    expected="$(printf '%s' "$VIORA_CERT_SHA256" | tr -d ': ' | tr '[:lower:]' '[:upper:]')"
    if [ -z "$actual" ] || [ "$actual" != "$expected" ]; then
        echo "release certificate fingerprint does not match VIORA_CERT_SHA256" >&2
        exit 1
    fi
fi

apk_dir="$(dirname "$APK")"
apk_name="$(basename "$APK")"
(cd "$apk_dir" && sha256sum "$apk_name" > "$apk_name.sha256")
echo "verified $APK and wrote $APK.sha256"
