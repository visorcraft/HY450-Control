#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="$PWD/tests/build/host"
CLASSES="$OUT/classes"
rm -rf "$OUT"
mkdir -p "$CLASSES"
javac -source 8 -target 8 -cp "$PLATFORM" -d "$CLASSES" \
  src/com/visorcraft/hy450control/Control.java \
  src/com/visorcraft/hy450control/ControlAdapter.java \
  src/com/visorcraft/hy450control/ControlBatch.java \
  src/com/visorcraft/hy450control/Errors.java \
  src/com/visorcraft/hy450control/JsonFileStore.java \
  src/com/visorcraft/hy450control/Keystone.java \
  src/com/visorcraft/hy450control/Vendor.java tests/HostTests.java
java -cp "$PLATFORM:$CLASSES" com.visorcraft.hy450control.HostTests
