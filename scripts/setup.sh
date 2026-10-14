#!/bin/bash
# One-time (macOS): installs Google's command-line Android build tools.
# No Android Studio. Downloads about 150 MB into ~/android-sdk.
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"

if [ -z "${JAVA_HOME:-}" ]; then
  if /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home)"
  elif [ -d /opt/homebrew/opt/openjdk ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk
  else
    echo "Installing Java..."; brew install openjdk; JAVA_HOME=/opt/homebrew/opt/openjdk
  fi
fi
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

command -v sdkmanager >/dev/null || brew install --cask android-commandlinetools

mkdir -p "$SDK"
yes | sdkmanager --sdk_root="$SDK" --licenses >/dev/null || true
sdkmanager --sdk_root="$SDK" "build-tools;35.0.0" "platforms;android-35"
echo
echo "Setup done. SDK is in $SDK"
