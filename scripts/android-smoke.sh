#!/usr/bin/env sh
set -eu

# Backwards-compatible entry point. New options are documented by device-smoke.sh.
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec "$SCRIPT_DIR/device-smoke.sh" --apk "${1:-app/build/outputs/apk/debug/app-debug.apk}"
