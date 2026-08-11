#!/usr/bin/env sh
set -eu

APK="${1:-}"
OUTPUT_DIR="${2:-build/closed-test}"
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "usage: $0 path/to/signed-release.apk [output-directory]" >&2
    exit 2
fi

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
"$SCRIPT_DIR/verify-release-apk.sh" "$APK"
mkdir -p "$OUTPUT_DIR"
cp "$APK" "$OUTPUT_DIR/"
cp "$APK.sha256" "$OUTPUT_DIR/"

echo "Closed-test bundle prepared in $OUTPUT_DIR"
echo "Next: upload the APK to the Play closed track, record the release version, and run device-smoke.sh on the matrix in docs/CLOSED_TEST.md."
