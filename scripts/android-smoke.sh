#!/usr/bin/env sh
set -eu
ADB="${ADB:-adb}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
"$ADB" wait-for-device
"$ADB" install -r "$APK"
"$ADB" shell am force-stop app.viora
"$ADB" shell monkey -p app.viora -c android.intent.category.LAUNCHER 1
"$ADB" shell uiautomator dump /sdcard/viora-window.xml >/dev/null
"$ADB" shell cat /sdcard/viora-window.xml
