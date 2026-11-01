#!/usr/bin/env bash
set -euo pipefail
DEVICE="${1:?usage: capture.sh <adb-serial> <video-node> <output.png>}"
NODE="${2:?video node required}"
OUTPUT="${3:?output PNG required}"
case "$OUTPUT" in
  /*) ;;
  *) OUTPUT="$PWD/$OUTPUT" ;;
esac
cd "$(dirname "$0")"
case "$NODE" in
  /dev/video0|/dev/video1) ;;
  *) echo "Only /dev/video0 and /dev/video1 are allowed" >&2; exit 2 ;;
esac
test -x build/v4l2_capture
REMOTE=/data/local/tmp/hy450-v4l2
RAW=/data/local/tmp/hy450-frame.raw
META=/data/local/tmp/hy450-frame.meta
cleanup() {
  adb -s "$DEVICE" shell 'rm -f /data/local/tmp/hy450-v4l2 /data/local/tmp/hy450-frame.raw /data/local/tmp/hy450-frame.meta' >/dev/null 2>&1 || true
}
trap cleanup EXIT
adb -s "$DEVICE" push build/v4l2_capture "$REMOTE" >/dev/null
adb -s "$DEVICE" shell "chmod 700 $REMOTE; $REMOTE $NODE capture $RAW $META"
mkdir -p "$(dirname "$OUTPUT")"
adb -s "$DEVICE" pull "$RAW" "$OUTPUT.raw" >/dev/null
adb -s "$DEVICE" pull "$META" "$OUTPUT.meta" >/dev/null
width=
height=
fourcc=
stride=
source "$OUTPUT.meta"
if [[ "$fourcc" == "MJPG" ]]; then
  ffmpeg -hide_banner -loglevel error -y -i "$OUTPUT.raw" -frames:v 1 "$OUTPUT"
elif [[ "$fourcc" == "YUYV" ]]; then
  if [[ "$stride" -ne $((width * 2)) ]]; then
    echo "Unsupported YUYV stride: $stride" >&2
    exit 1
  fi
  ffmpeg -hide_banner -loglevel error -y -f rawvideo -pixel_format yuyv422 \
    -video_size "${width}x${height}" -i "$OUTPUT.raw" -frames:v 1 "$OUTPUT"
else
  echo "Unsupported captured format: $fourcc" >&2
  exit 1
fi
rm -f "$OUTPUT.raw" "$OUTPUT.meta"
adb -s "$DEVICE" shell 'rm -f /data/local/tmp/hy450-v4l2'
echo "captured: $OUTPUT (${width}x${height}, ${fourcc})"
