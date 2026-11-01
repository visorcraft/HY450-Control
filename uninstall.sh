#!/usr/bin/env bash
set -euo pipefail
DEVICE="${1:?usage: uninstall.sh <adb-serial>}"
adb -s "$DEVICE" shell 'su -c "rm -rf /data/adb/modules/hy450control"'
echo "Removed hy450control. Reboot to complete removal."
