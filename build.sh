#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
PLATFORM="$SDK/platforms/android-34/android.jar"
BT="$SDK/build-tools/35.0.0"
OUT="$PWD/build"
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/generated" "$OUT/dex"
"$BT/aapt2" compile --dir res -o "$OUT/resources.zip"
"$BT/aapt2" link -o "$OUT/app-unsigned.apk" -I "$PLATFORM" \
  --manifest AndroidManifest.xml --java "$OUT/generated" \
  --min-sdk-version 34 --target-sdk-version 34 "$OUT/resources.zip"
javac -source 8 -target 8 -cp "$PLATFORM" -d "$OUT/classes" \
  "$OUT/generated/com/visorcraft/hy450control/R.java" \
  src/com/visorcraft/hy450control/*.java
"$BT/d8" --min-api 34 --output "$OUT/dex" "$OUT/classes"/com/visorcraft/hy450control/*.class
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"
(cd "$OUT" && zip -0 app-unsigned.apk classes.dex >/dev/null)
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" \
  --ks-pass pass:android --out hy450-control.apk "$OUT/app-unsigned.apk"
"$BT/apksigner" verify --print-certs hy450-control.apk
mkdir -p module/system/priv-app/HY450Control
cp hy450-control.apk module/system/priv-app/HY450Control/HY450Control.apk
echo "built: hy450-control.apk"
