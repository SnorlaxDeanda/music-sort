#!/usr/bin/env bash
# Assemble / refresh "Album Artist Cleaner.app" from the project sources.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="Album Artist Cleaner.app"
APP="$ROOT/$APP_NAME"
CONTENTS="$APP/Contents"
MACOS="$CONTENTS/MacOS"
RESOURCES="$CONTENTS/Resources"

mkdir -p "$MACOS" "$RESOURCES"

# Copy application payload into the bundle.
rm -rf "$RESOURCES/album_artist_cleaner"
cp -R "$ROOT/album_artist_cleaner" "$RESOURCES/album_artist_cleaner"
cp "$ROOT/clean_album_artists.py" "$RESOURCES/clean_album_artists.py"
cp "$ROOT/requirements.txt" "$RESOURCES/requirements.txt"
# Drop caches from the copy.
find "$RESOURCES" -type d -name '__pycache__' -prune -exec rm -rf {} +
find "$RESOURCES" -type f -name '*.pyc' -delete

# Info.plist
cat > "$CONTENTS/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key>
	<string>en</string>
	<key>CFBundleDisplayName</key>
	<string>Album Artist Cleaner</string>
	<key>CFBundleExecutable</key>
	<string>AlbumArtistCleaner</string>
	<key>CFBundleIdentifier</key>
	<string>com.musicsort.albumartistcleaner</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundleName</key>
	<string>Album Artist Cleaner</string>
	<key>CFBundlePackageType</key>
	<string>APPL</string>
	<key>CFBundleShortVersionString</key>
	<string>1.2.0</string>
	<key>CFBundleVersion</key>
	<string>1</string>
	<key>LSApplicationCategoryType</key>
	<string>public.app-category.music</string>
	<key>LSMinimumSystemVersion</key>
	<string>11.0</string>
	<key>NSHighResolutionCapable</key>
	<true/>
	<key>NSPrincipalClass</key>
	<string>NSApplication</string>
</dict>
</plist>
PLIST

# Launcher — always opens the GUI application (native Cocoa, no Tk required).
cat > "$MACOS/AlbumArtistCleaner" <<'LAUNCHER'
#!/bin/bash
set -euo pipefail

abort() {
  local message="$1"
  if command -v osascript >/dev/null 2>&1; then
    # Escape for AppleScript string
    local escaped
    escaped="$(printf '%s' "$message" | sed 's/\\/\\\\/g; s/"/\\"/g')"
    osascript -e "display alert \"Album Artist Cleaner\" message \"$escaped\" as critical" \
      >/dev/null 2>&1 || true
  else
    echo "$message" >&2
  fi
  exit 1
}

python_ok() {
  local candidate="$1"
  if ! command -v "$candidate" >/dev/null 2>&1 && [[ ! -x "$candidate" ]]; then
    return 1
  fi
  "$candidate" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)' 2>/dev/null
}

resolve_python() {
  local candidate
  # Prefer python.org framework builds, then Homebrew, then PATH.
  for candidate in \
    "/Library/Frameworks/Python.framework/Versions/Current/bin/python3" \
    "/Library/Frameworks/Python.framework/Versions/3.13/bin/python3" \
    "/Library/Frameworks/Python.framework/Versions/3.12/bin/python3" \
    "/Library/Frameworks/Python.framework/Versions/3.11/bin/python3" \
    "/Library/Frameworks/Python.framework/Versions/3.10/bin/python3" \
    "/opt/homebrew/bin/python3" \
    "/usr/local/bin/python3" \
    "/opt/homebrew/opt/python@3.13/bin/python3" \
    "/opt/homebrew/opt/python@3.12/bin/python3" \
    "/usr/local/opt/python@3.12/bin/python3" \
    "python3"
  do
    if python_ok "$candidate"; then
      # Resolve to absolute path when possible.
      if command -v "$candidate" >/dev/null 2>&1; then
        command -v "$candidate"
      else
        echo "$candidate"
      fi
      return 0
    fi
  done
  return 1
}

HERE="$(cd "$(dirname "$0")" && pwd)"
RESOURCES="$(cd "$HERE/../Resources" && pwd)"
SUPPORT="${HOME}/Library/Application Support/Album Artist Cleaner"
VENV="$SUPPORT/venv"
MARKER="$SUPPORT/venv-python"
LOG_DIR="$SUPPORT/Logs"
mkdir -p "$SUPPORT" "$LOG_DIR"
LOG_FILE="$LOG_DIR/launch.log"

exec >>"$LOG_FILE" 2>&1
echo "---- $(date) ----"

PYTHON="$(resolve_python)" || abort "Python 3.10+ is required.

Install Python from https://www.python.org/downloads/
or with Homebrew: brew install python
Then open Album Artist Cleaner again."

echo "Using Python: $PYTHON ($("$PYTHON" --version 2>&1))"

# Recreate the private env if missing or built with a different Python.
if [[ -x "$VENV/bin/python" ]]; then
  if [[ ! -f "$MARKER" ]] || [[ "$(cat "$MARKER")" != "$PYTHON" ]]; then
    echo "Recreating virtualenv (Python changed)"
    rm -rf "$VENV"
  fi
fi

if [[ ! -x "$VENV/bin/python" ]]; then
  echo "Creating virtualenv at $VENV"
  "$PYTHON" -m venv "$VENV"
  echo "$PYTHON" > "$MARKER"
  "$VENV/bin/pip" install --upgrade pip
  "$VENV/bin/pip" install -r "$RESOURCES/requirements.txt"
fi

# Ensure dependencies are present (mutagen + native Cocoa GUI on macOS).
if ! "$VENV/bin/python" - <<'PY' 2>/dev/null
import mutagen
import AppKit
import PyObjCTools
PY
then
  echo "Installing application dependencies…"
  osascript -e 'display notification "Installing components…" with title "Album Artist Cleaner"' \
    >/dev/null 2>&1 || true
  "$VENV/bin/pip" install -r "$RESOURCES/requirements.txt"
fi

cd "$RESOURCES"
export PYTHONPATH="$RESOURCES${PYTHONPATH:+:$PYTHONPATH}"
exec "$VENV/bin/python" "$RESOURCES/clean_album_artists.py" --gui
LAUNCHER

chmod +x "$MACOS/AlbumArtistCleaner"

# PkgInfo marks this as an application bundle.
printf 'APPL????' > "$CONTENTS/PkgInfo"

echo "Built: $APP"
echo "On your Mac, double-click \"$APP_NAME\" (or drag it to Applications)."
