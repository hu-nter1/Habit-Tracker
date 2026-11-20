#!/bin/sh
set -eu

INSTALL=false
if [ "$#" -gt 1 ]; then
    echo "Usage: $0 [--install]" >&2
    exit 2
fi
if [ "$#" -eq 1 ]; then
    if [ "$1" != "--install" ]; then
        echo "Usage: $0 [--install]" >&2
        exit 2
    fi
    INSTALL=true
fi

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
KEYSTORE="${HOME}/.android/study-tracker-debug.keystore"
APK="$PROJECT_DIR/build/outputs/apk/debug/StudyTracker-debug.apk"
OUTPUT_APK="$PROJECT_DIR/build/StudyTracker.apk"
mkdir -p "$(dirname -- "$KEYSTORE")"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -dname "CN=Android Debug,O=Study Tracker,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000
fi
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" lintDebug assembleDebug
if [ ! -f "$APK" ]; then
    echo "Build succeeded but expected APK was not found: $APK" >&2
    exit 1
fi
cp "$APK" "$OUTPUT_APK"
echo "Built: $OUTPUT_APK"

if [ "$INSTALL" = true ]; then
    if ! command -v adb >/dev/null 2>&1; then
        echo "Cannot install: adb was not found in PATH." >&2
        exit 1
    fi
    adb install -r "$OUTPUT_APK"
fi
