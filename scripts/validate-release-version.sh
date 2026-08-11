#!/usr/bin/env sh
set -eu

VERSION_NAME="${1:-}"
VERSION_CODE="${2:-}"
SEMVER='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'

if ! printf '%s\n' "$VERSION_NAME" | grep -Eq "$SEMVER"; then
    echo "Release version must be SemVer without build metadata: $VERSION_NAME" >&2
    exit 1
fi
case "$VERSION_CODE" in
    ''|*[!0-9]*)
        echo "Release version code must be an integer from 1 to 2100000000: $VERSION_CODE" >&2
        exit 1
        ;;
esac
if [ "$VERSION_CODE" -lt 1 ] || [ "$VERSION_CODE" -gt 2100000000 ]; then
    echo "Release version code must be an integer from 1 to 2100000000: $VERSION_CODE" >&2
    exit 1
fi

printf 'VIORA_VERSION_NAME=%s\n' "$VERSION_NAME"
printf 'VIORA_VERSION_CODE=%s\n' "$VERSION_CODE"
