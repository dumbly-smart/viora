GRADLE ?= gradle

.PHONY: help setup test build lint privacy-audit check device-smoke

help:
	@printf '%s\n' \
	  'make setup         Check required local tools' \
	  'make test          Run JVM unit tests' \
	  'make build         Assemble the debug APK and instrumentation APK' \
	  'make lint          Run Android lint' \
	  'make privacy-audit Run repository privacy checks' \
	  'make check         Run tests, builds, lint, privacy, and shell checks' \
	  'make device-smoke  Assemble and smoke-test on an attached API 26+ device'

setup:
	@command -v java >/dev/null || { echo 'java (JDK 17) is required' >&2; exit 1; }
	@command -v "$(GRADLE)" >/dev/null || { echo 'gradle is required (no wrapper is committed)' >&2; exit 1; }
	@java -version 2>&1 | head -n 1 | grep -q '"17\.' || { echo 'JDK 17 is required; set JAVA_HOME and PATH accordingly' >&2; exit 1; }
	@printf 'Java: '; java -version 2>&1 | head -n 1
	@printf 'Gradle: '; "$(GRADLE)" --version | sed -n 's/^Gradle //p' | head -n 1

test:
	"$(GRADLE)" testDebugUnitTest

build:
	"$(GRADLE)" assembleDebug assembleDebugAndroidTest

lint:
	"$(GRADLE)" lintDebug

privacy-audit:
	scripts/privacy-audit.sh

check:
	"$(GRADLE)" testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
	scripts/privacy-audit.sh
	sh -n scripts/*.sh
	git diff --check

device-smoke: build
	scripts/device-smoke.sh --fresh
