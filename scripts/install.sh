#!/bin/bash
# Installs PushGuard on the connected phone and lets it run in the background.
# Usage: scripts/install.sh [path/to/PushGuard.apk]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/PushGuard.apk}"
PKG=dev.pushguard
[ -f "$APK" ] || { echo "APK not found: $APK (build it or download it from Releases)"; exit 1; }

# PushGuard targets API 22 on purpose (see docs/RESEARCH.md), so newer
# Android needs this flag to install it.
adb install -r --bypass-low-target-sdk-block "$APK"

adb shell cmd appops set $PKG 10008 allow 2>/dev/null || true   # Xiaomi autostart
adb shell cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow
adb shell cmd appops set $PKG RUN_IN_BACKGROUND allow
adb shell cmd appops set $PKG WRITE_SETTINGS allow
adb shell dumpsys deviceidle whitelist +$PKG >/dev/null
adb shell am set-standby-bucket $PKG active

adb shell am start -n $PKG/.MainActivity >/dev/null
sleep 3
echo
echo "Exemption list now:"
adb shell settings get system MILLET_NO_RESTRICT_APP
echo
echo "PushGuard log:"
adb logcat -d -s PushGuard | tail -5
echo
echo "Next: Settings > Battery > PushGuard > No restrictions."
