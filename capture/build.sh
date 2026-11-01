#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
NDK="${ANDROID_NDK_ROOT:-$HOME/Android/Sdk/ndk/27.0.12077973}"
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi34-clang"
mkdir -p build
"$CC" -std=c11 -O2 -Wall -Wextra -Werror \
  -D_FILE_OFFSET_BITS=64 -o build/v4l2_capture v4l2_capture.c
file build/v4l2_capture
