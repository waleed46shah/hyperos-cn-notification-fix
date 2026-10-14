#!/bin/bash
# Quick health check. Run any time notifications seem late.
set -uo pipefail
PKG=dev.pushguard
GMS_UID=$(adb shell dumpsys package com.google.android.gms | grep -m1 -oE "userId=[0-9]+" | cut -d= -f2)

echo "== Build";            adb shell getprop ro.build.version.incremental
echo "== Region";           adb shell getprop ro.miui.region
echo "== PushGuard running"; adb shell pidof $PKG || echo "NOT RUNNING (open the app to restart it)"
echo "== Play services on exemption list"
adb shell settings get system MILLET_NO_RESTRICT_APP | grep -oE "com\.google\.android\.gms(,|$)" || echo "MISSING"
echo "== Recent Play services freezes (uid $GMS_UID). Empty is good."
adb shell dumpsys greezer | grep "FZ uid = $GMS_UID" | tail -5
echo "== Google push connection"
adb shell dumpsys activity service com.google.android.gms/.gcm.GcmService \
  | grep -iE "^ *(Connected|Not connected)|Is client connected" | head -3
echo "== PushGuard log"
adb logcat -d -s PushGuard | tail -5
