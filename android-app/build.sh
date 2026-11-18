#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
KEYSTORE="${HOME}/.android/study-tracker-debug.keystore"
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
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" assembleDebug
cp "$PROJECT_DIR"/build/outputs/apk/debug/*.apk "$PROJECT_DIR/build/StudyTracker.apk"
echo "Built: $PROJECT_DIR/build/StudyTracker.apk"
