#!/usr/bin/env bash
# Undo everything on this Sony: re-enable every package in debloat.txt, bring back the stock
# Google TV launcher as home, and remove the custom launcher (pass --keep-launcher to keep it).
# Uses the stock adb binary. Usage: TV_HOST=<tv ip> examples/sony-bravia-4k-ur2/restore.sh
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_SERIAL="${TV_HOST:?set TV_HOST to the TV IP address}:5555"
adb connect "$ANDROID_SERIAL" >/dev/null
grep -vE '^\s*(#|$)' debloat.txt | awk '{print $1}' | while read -r p; do
  adb shell pm enable "$p" </dev/null
done
adb shell pm enable com.google.android.tvlauncher
adb shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
[ "${1:-}" = "--keep-launcher" ] || adb shell pm uninstall com.gathin.tvhome
adb shell input keyevent KEYCODE_HOME
echo "Restored. Disabled packages left: $(adb shell pm list packages -d | wc -l)"
