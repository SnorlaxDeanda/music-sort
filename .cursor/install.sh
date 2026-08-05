#!/usr/bin/env bash
# Idempotent Cloud Agent setup for the music-sort repository.
# Prepares both projects:
#   * Album Artist Cleaner  (Python 3 + mutagen, pytest)
#   * Harmonium             (Android / Kotlin, Gradle wrapper + Android SDK 35)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"

echo "==> Album Artist Cleaner: installing Python dependencies"
python3 -m pip install --user --upgrade -r "$REPO_ROOT/requirements.txt" pytest

echo "==> Harmonium: ensuring Android SDK is present at $ANDROID_HOME"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  echo "    Downloading Android command-line tools ($CMDLINE_TOOLS_VERSION)"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmpdir="$(mktemp -d)"
  curl -fsSL -o "$tmpdir/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest" "$tmpdir/extract"
  unzip -q "$tmpdir/cmdline-tools.zip" -d "$tmpdir/extract"
  mv "$tmpdir/extract/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmpdir"
fi

# Accept licenses (no-op once accepted) and install the packages the build needs.
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# Point the Gradle build at the SDK (local.properties is gitignored).
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$REPO_ROOT/harmonium/local.properties"

echo "==> Setup complete."
echo "    Album Artist Cleaner: python3 -m pytest -q"
echo "    Harmonium:            (cd harmonium && ANDROID_HOME=$ANDROID_HOME ./gradlew :app:assembleDebug :app:testDebugUnitTest)"
