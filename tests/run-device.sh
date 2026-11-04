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
mkdir -p "$OUT/classes" "$OUT/dex"
javac -source 8 -target 8 -cp "$PLATFORM" -d "$OUT/classes" \
  src/com/visorcraft/hy450control/AmbientScene.java \
  src/com/visorcraft/hy450control/AmbientScenes.java \
  src/com/visorcraft/hy450control/BackupStore.java \
  src/com/visorcraft/hy450control/BootLaunchService.java \
  src/com/visorcraft/hy450control/BootReceiver.java \
  src/com/visorcraft/hy450control/CapabilityReport.java \
  src/com/visorcraft/hy450control/Control.java \
  src/com/visorcraft/hy450control/ControlAdapter.java \
  src/com/visorcraft/hy450control/ControlBatch.java \
  src/com/visorcraft/hy450control/Errors.java \
  src/com/visorcraft/hy450control/JsonFileStore.java \
  src/com/visorcraft/hy450control/Keystone.java \
  src/com/visorcraft/hy450control/ProjectionBackup.java \
  src/com/visorcraft/hy450control/RoutineEngine.java \
  src/com/visorcraft/hy450control/RoutineStore.java \
  src/com/visorcraft/hy450control/Vendor.java tests/DeviceTests.java
"$BT/d8" --min-api 34 --output "$OUT/dex" "$OUT/classes"/com/visorcraft/hy450control/*.class
cleanup() {
  adb -s "$SERIAL" shell rm -rf "$REMOTE" >/dev/null 2>&1 || true
}
trap cleanup EXIT
adb -s "$SERIAL" shell mkdir -p "$REMOTE"
adb -s "$SERIAL" push "$OUT/dex/classes.dex" "$REMOTE/classes.dex" >/dev/null
adb -s "$SERIAL" shell "CLASSPATH=$REMOTE/classes.dex app_process /system/bin com.visorcraft.hy450control.DeviceTests"
