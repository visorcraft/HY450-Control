#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/35.0.0"
rm -rf build
mkdir -p build/classes build/dex
javac -source 8 -target 8 \
  -cp "$SDK/platforms/android-34/android.jar" \
  -d build/classes *.java
"$BT/d8" --min-api 34 --output build/dex build/classes/*.class
cp build/dex/classes.dex build/tvprobe.jar
