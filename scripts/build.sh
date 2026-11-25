#!/bin/bash
# Builds PushGuard.apk with plain SDK tools: aapt2 + javac + d8 + zipalign + apksigner.
# No Gradle, no Android Studio.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BT="$SDK/build-tools/35.0.0"
PLAT="$SDK/platforms/android-35/android.jar"
[ -f "$PLAT" ] || { echo "SDK not found in $SDK. Run scripts/setup.sh first."; exit 1; }

if [ -z "${JAVA_HOME:-}" ]; then
  if /usr/libexec/java_home >/dev/null 2>&1; then JAVA_HOME="$(/usr/libexec/java_home)"
  else JAVA_HOME=/opt/homebrew/opt/openjdk; fi
fi
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

rm -rf build && mkdir -p build/classes

echo "[1/5] Manifest and resources"
# The manifest has no package attribute (Gradle supplies it from build.gradle's
# namespace), so give aapt2 a copy with the package name added.
sed 's|<manifest |<manifest package="dev.pushguard" |' app/src/main/AndroidManifest.xml > build/AndroidManifest.xml
grep -q 'package="dev.pushguard"' build/AndroidManifest.xml || { echo "Could not set package name in manifest"; exit 1; }
"$BT/aapt2" compile --dir app/src/main/res -o build/res.zip
"$BT/aapt2" link -o build/base.apk --manifest build/AndroidManifest.xml -I "$PLAT" build/res.zip \
  --min-sdk-version 21 --target-sdk-version 22 --version-code 1 --version-name 0.1.0-beta

echo "[2/5] Compile"
javac --release 17 -nowarn -classpath "$PLAT" -d build/classes app/src/main/java/dev/pushguard/*.java

echo "[3/5] Dex"
"$BT/d8" --release --min-api 21 --lib "$PLAT" --output build build/classes/dev/pushguard/*.class
(cd build && zip -q base.apk classes.dex)

echo "[4/5] Align"
"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk

echo "[5/5] Sign"
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
    -alias pushguard -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=PushGuard" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out PushGuard.apk build/aligned.apk
rm -f PushGuard.apk.idsig
echo
echo "Built: $ROOT/PushGuard.apk"
