#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SERIAL="${1:?usage: run-device.sh <adb-serial>}"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
PLATFORM="$SDK/platforms/android-34/android.jar"
BT="$SDK/build-tools/35.0.0"
OUT="$PWD/tests/build/device"
REMOTE="/data/local/tmp/hy450-control-device-tests"
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/generated"
"$BT/aapt2" compile --dir res -o "$OUT/resources.zip"
"$BT/aapt2" link -o "$OUT/tests.apk" -I "$PLATFORM" \
  --manifest AndroidManifest.xml --java "$OUT/generated" \
  --min-sdk-version 34 --target-sdk-version 34 "$OUT/resources.zip"
javac -source 8 -target 8 -cp "$PLATFORM" -d "$OUT/classes" \
  "$OUT/generated/com/visorcraft/hy450control/R.java" \
  src/com/visorcraft/hy450control/*.java tests/DeviceTests.java
"$BT/d8" --min-api 34 --output "$OUT/dex" "$OUT/classes"/com/visorcraft/hy450control/*.class
cleanup() {
  adb -s "$SERIAL" shell rm -rf "$REMOTE" >/dev/null 2>&1 || true
}
trap cleanup EXIT
adb -s "$SERIAL" shell mkdir -p "$REMOTE"
adb -s "$SERIAL" push "$OUT/dex/classes.dex" "$REMOTE/classes.dex" >/dev/null
adb -s "$SERIAL" shell "CLASSPATH=$REMOTE/classes.dex app_process /system/bin com.visorcraft.hy450control.DeviceTests"
