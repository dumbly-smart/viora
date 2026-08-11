#!/usr/bin/env sh
set -eu

ADB="${ADB:-adb}"
APK="app/build/outputs/apk/debug/app-debug.apk"
OUTPUT_DIR="build/device-smoke"
FRESH=0
OFFLINE=0
PACKAGE="app.viora"

usage() {
    echo "usage: $0 [--apk file] [--output dir] [--fresh] [--offline]" >&2
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --apk) APK="$2"; shift 2 ;;
        --output) OUTPUT_DIR="$2"; shift 2 ;;
        --fresh) FRESH=1; shift ;;
        --offline) OFFLINE=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) usage; exit 2 ;;
    esac
done

if [ ! -f "$APK" ]; then
    echo "APK not found: $APK" >&2
    exit 1
fi
if ! command -v "$ADB" >/dev/null 2>&1; then
    echo "adb not found; set ADB or install Android platform-tools" >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
"$ADB" wait-for-device
api_level="$($ADB shell getprop ro.build.version.sdk | tr -d '\r')"
if [ "$api_level" -lt 26 ]; then
    echo "Viora requires API 26+, attached device is API $api_level" >&2
    exit 1
fi

restore_network() {
    if [ "$OFFLINE" -eq 1 ]; then
        "$ADB" shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
    fi
}
trap restore_network EXIT INT TERM

"$ADB" install -r "$APK" >/dev/null
if [ "$FRESH" -eq 1 ]; then
    "$ADB" shell pm clear "$PACKAGE" >/dev/null
fi
if [ "$OFFLINE" -eq 1 ]; then
    "$ADB" shell cmd connectivity airplane-mode enable >/dev/null
fi

"$ADB" logcat -c
"$ADB" shell am force-stop "$PACKAGE"
"$ADB" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null

attempt=0
pid=""
while [ "$attempt" -lt 20 ]; do
    pid="$($ADB shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
    [ -n "$pid" ] && break
    attempt=$((attempt + 1))
    sleep 0.5
done
if [ -z "$pid" ]; then
    "$ADB" logcat -d > "$OUTPUT_DIR/logcat.txt"
    echo "Viora did not remain running after launch" >&2
    exit 1
fi

"$ADB" shell uiautomator dump /sdcard/viora-window.xml >/dev/null
"$ADB" pull /sdcard/viora-window.xml "$OUTPUT_DIR/window.xml" >/dev/null
"$ADB" exec-out screencap -p > "$OUTPUT_DIR/screenshot.png"
"$ADB" logcat -d > "$OUTPUT_DIR/logcat.txt"
"$ADB" shell dumpsys package "$PACKAGE" > "$OUTPUT_DIR/package.txt"

if grep -E "FATAL EXCEPTION|ANR in $PACKAGE|Process: $PACKAGE.*has died" "$OUTPUT_DIR/logcat.txt" >/dev/null; then
    echo "crash or ANR signature found; see $OUTPUT_DIR/logcat.txt" >&2
    exit 1
fi
if ! grep -E "Welcome to Viora|Viora|Today" "$OUTPUT_DIR/window.xml" >/dev/null; then
    echo "expected Viora UI was not found; see $OUTPUT_DIR/window.xml" >&2
    exit 1
fi

device="$($ADB shell getprop ro.product.manufacturer | tr -d '\r') $($ADB shell getprop ro.product.model | tr -d '\r')"
echo "PASS: $device (API $api_level), pid $pid"
echo "Artifacts: $OUTPUT_DIR"
