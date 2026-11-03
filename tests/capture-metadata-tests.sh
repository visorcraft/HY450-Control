#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source capture/metadata.sh
TMP="$(mktemp -d /tmp/hy450-metadata-tests.XXXXXX)"
trap 'rm -rf "$TMP"' EXIT

printf 'width=1920\nheight=1080\nfourcc=MJPG\nstride=0\n' > "$TMP/valid-mjpg"
hy450_read_metadata "$TMP/valid-mjpg"
[[ "$width" == 1920 && "$height" == 1080 && "$fourcc" == MJPG && "$stride" == 0 ]]

printf 'width=640\nheight=480\nfourcc=YUYV\nstride=1280\n' > "$TMP/valid-yuyv"
hy450_read_metadata "$TMP/valid-yuyv"
[[ "$width" == 640 && "$height" == 480 && "$fourcc" == YUYV && "$stride" == 1280 ]]

expect_invalid() {
  local file="$1"
  if hy450_read_metadata "$file"; then
    echo "metadata was accepted unexpectedly: $file" >&2
    exit 1
  fi
}

printf 'width=$(touch %s/injected)\nheight=480\nfourcc=YUYV\nstride=1280\n' "$TMP" > "$TMP/injection"
expect_invalid "$TMP/injection"
[[ ! -e "$TMP/injected" ]]

printf 'width=640\nwidth=800\nheight=480\nfourcc=YUYV\nstride=1280\n' > "$TMP/duplicate"
expect_invalid "$TMP/duplicate"

printf 'width=0\nheight=480\nfourcc=YUYV\nstride=0\n' > "$TMP/zero-width"
expect_invalid "$TMP/zero-width"

printf 'width=10#640\nheight=480\nfourcc=YUYV\nstride=1280\n' > "$TMP/arithmetic"
expect_invalid "$TMP/arithmetic"

printf 'width=640\nheight=480\nfourcc=H264\nstride=0\n' > "$TMP/fourcc"
expect_invalid "$TMP/fourcc"

printf 'width=640\nheight=480\nfourcc=YUYV\nstride=1280\nunexpected=value\n' > "$TMP/unexpected"
expect_invalid "$TMP/unexpected"

printf 'width=640\nheight=480\nfourcc=YUYV\nstride=1000\n' > "$TMP/stride"
expect_invalid "$TMP/stride"

printf 'width=640\nheight=480\nfourcc=YUYV\n' > "$TMP/missing"
expect_invalid "$TMP/missing"

echo "Capture metadata tests passed"
