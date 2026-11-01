#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
DEVICE="${1:?usage: install.sh <adb-serial>}"
test -f module/system/priv-app/HY450Control/HY450Control.apk
adb -s "$DEVICE" shell 'rm -rf /data/local/tmp/hy450control-module'
adb -s "$DEVICE" push module /data/local/tmp/hy450control-module
adb -s "$DEVICE" shell 'su -c "rm -rf /data/adb/modules/hy450control && cp -a /data/local/tmp/hy450control-module /data/adb/modules/hy450control && rm -rf /data/local/tmp/hy450control-module"'
echo "Installed hy450control. Reboot to activate."
