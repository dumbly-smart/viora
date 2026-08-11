#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

git ls-files | while IFS= read -r path; do
    case "$path" in
        *.apk|*.aab|*.jks|*.keystore|*.cookies|*.session|*.log|\
        *captcha*.jpg|*captcha*.png|*vtop-response*.html|\
        *student-data*|*/logcat.txt|*/window.xml|*/screenshot.png)
            echo "private or generated artifact is tracked: $path" >&2
            exit 1
            ;;
    esac
done

MANIFEST="app/src/main/AndroidManifest.xml"
grep -q 'android:allowBackup="false"' "$MANIFEST"
grep -q 'android:fullBackupContent="false"' "$MANIFEST"
grep -q 'android:dataExtractionRules="@xml/data_extraction_rules"' "$MANIFEST"
grep -q 'android:usesCleartextTraffic="false"' "$MANIFEST"

if grep -Eiq 'firebase|crashlytics|sentry|appcenter|segment|amplitude|mixpanel|flurry|datadog' app/build.gradle.kts; then
    echo "analytics, telemetry, or remote crash-reporting dependency detected" >&2
    exit 1
fi

if grep -q 'upload-artifact' .github/workflows/android.yml; then
    echo "normal CI must not upload build, test, log, screenshot, or UI-dump artifacts" >&2
    exit 1
fi

echo "privacy audit passed"
