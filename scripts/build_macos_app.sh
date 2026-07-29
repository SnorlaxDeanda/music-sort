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
	<string>1.0.0</string>
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

# Launcher — always opens the GUI application.
cat > "$MACOS/AlbumArtistCleaner" <<'LAUNCHER'
#!/bin/bash
set -euo pipefail

abort() {
  local message="$1"
  if command -v osascript >/dev/null 2>&1; then
    osascript <<EOF >/dev/null 2>&1 || true
display alert "Album Artist Cleaner" message "$(printf '%s' "$message" | sed 's/"/\\"/g')" as critical
EOF
  else
    echo "$message" >&2
  fi
  exit 1
}

resolve_python() {
  local candidate
  for candidate in \
    "/usr/local/bin/python3" \
    "/opt/homebrew/bin/python3" \
    "/Library/Frameworks/Python.framework/Versions/Current/bin/python3" \
    "python3"
  do
    if command -v "$candidate" >/dev/null 2>&1; then
      if "$candidate" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)' 2>/dev/null; then
        echo "$candidate"
        return 0
      fi
    fi
  done
  return 1
}

HERE="$(cd "$(dirname "$0")" && pwd)"
RESOURCES="$(cd "$HERE/../Resources" && pwd)"
SUPPORT="${HOME}/Library/Application Support/Album Artist Cleaner"
VENV="$SUPPORT/venv"
LOG_DIR="$SUPPORT/Logs"
mkdir -p "$SUPPORT" "$LOG_DIR"
LOG_FILE="$LOG_DIR/launch.log"

exec >>"$LOG_FILE" 2>&1
echo "---- $(date) ----"

PYTHON="$(resolve_python)" || abort "Python 3.10+ is required.

Install Python from python.org or Homebrew, then open Album Artist Cleaner again."

export TCL_LIBRARY="${TCL_LIBRARY:-}"
export TK_LIBRARY="${TK_LIBRARY:-}"

if [[ ! -x "$VENV/bin/python" ]]; then
  echo "Creating virtualenv at $VENV"
  "$PYTHON" -m venv "$VENV"
  "$VENV/bin/pip" install --upgrade pip
  "$VENV/bin/pip" install -r "$RESOURCES/requirements.txt"
fi

# Ensure mutagen is present even if the venv already existed.
"$VENV/bin/python" -c 'import mutagen' 2>/dev/null || \
  "$VENV/bin/pip" install -r "$RESOURCES/requirements.txt"

# Tk must be available for the GUI.
"$VENV/bin/python" -c 'import tkinter' 2>/dev/null || \
  abort "Python Tk support is missing.

If you installed Python from python.org, reinstall and keep the Tcl/Tk option enabled.
Homebrew users can try: brew install python-tk"

cd "$RESOURCES"
export PYTHONPATH="$RESOURCES${PYTHONPATH:+:$PYTHONPATH}"
exec "$VENV/bin/python" "$RESOURCES/clean_album_artists.py" --gui
LAUNCHER

chmod +x "$MACOS/AlbumArtistCleaner"

# PkgInfo marks this as an application bundle.
printf 'APPL????' > "$CONTENTS/PkgInfo"

echo "Built: $APP"
echo "On your Mac, double-click \"$APP_NAME\" (or drag it to Applications)."
