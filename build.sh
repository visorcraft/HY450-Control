#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
VERSION_NAME=
VERSION_CODE=
while IFS='=' read -r key value; do
  case "$key" in
    version) VERSION_NAME="$value" ;;
    versionCode) VERSION_CODE="$value" ;;
  esac
done < module/module.prop
: "${VERSION_NAME:?module version is required}"
: "${VERSION_CODE:?module versionCode is required}"
KEYSTORE="${ANDROID_KEYSTORE:-$HOME/.android/debug.keystore}"
KEYSTORE_PASS="${ANDROID_KEYSTORE_PASS:-android}"
KEY_ALIAS="${ANDROID_KEY_ALIAS:-androiddebugkey}"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
PLATFORM="$SDK/platforms/android-34/android.jar"
BT="$SDK/build-tools/35.0.0"
OUT="$PWD/build"
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/generated" "$OUT/dex"
"$BT/aapt2" compile --dir res -o "$OUT/resources.zip"
"$BT/aapt2" link -o "$OUT/app-unsigned.apk" -I "$PLATFORM" \
  --manifest AndroidManifest.xml --java "$OUT/generated" \
  --min-sdk-version 34 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" "$OUT/resources.zip"
javac -source 8 -target 8 -cp "$PLATFORM" -d "$OUT/classes" \
  "$OUT/generated/com/visorcraft/hy450control/R.java" \
  src/com/visorcraft/hy450control/*.java
"$BT/d8" --min-api 34 --output "$OUT/dex" "$OUT/classes"/com/visorcraft/hy450control/*.class
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"
(cd "$OUT" && zip -0 app-unsigned.apk classes.dex >/dev/null)
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASS" --key-pass "pass:$KEYSTORE_PASS" \
  --out hy450-control.apk "$OUT/app-unsigned.apk"
"$BT/apksigner" verify --print-certs hy450-control.apk
if "$BT/aapt2" dump badging hy450-control.apk | grep -q 'application-debuggable'; then
  echo "release APK must not be debuggable" >&2
  exit 1
fi
mkdir -p module/system/priv-app/HY450Control module/system/bin
cp hy450-control.apk module/system/priv-app/HY450Control/HY450Control.apk
NDK="${ANDROID_NDK_ROOT:-$HOME/Android/Sdk/ndk/27.0.12077973}"
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi34-clang"
if [[ -x "$CC" ]]; then
  bash capture/build.sh
  cp capture/build/v4l2_capture module/system/bin/hy450-v4l2
  chmod 755 module/system/bin/hy450-v4l2
fi
echo "built: hy450-control.apk"
