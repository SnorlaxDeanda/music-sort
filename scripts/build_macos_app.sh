#!/usr/bin/env bash
# Assemble a self-contained "Album Artist Cleaner.app".
# No Homebrew, system Python, or Tk is required to run the app.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="Album Artist Cleaner.app"
APP="$ROOT/$APP_NAME"
CONTENTS="$APP/Contents"
MACOS="$CONTENTS/MacOS"
RESOURCES="$CONTENTS/Resources"

PBS_TAG="20260728"
RUNTIME_VERSION="cpython-3.12.13+20260728"

mkdir -p "$MACOS" "$RESOURCES"

# Copy application payload into the bundle.
rm -rf "$RESOURCES/album_artist_cleaner"
cp -R "$ROOT/album_artist_cleaner" "$RESOURCES/album_artist_cleaner"
cp "$ROOT/clean_album_artists.py" "$RESOURCES/clean_album_artists.py"
cp "$ROOT/requirements-macos.txt" "$RESOURCES/requirements-macos.txt"
find "$RESOURCES" -type d -name '__pycache__' -prune -exec rm -rf {} +
find "$RESOURCES" -type f -name '*.pyc' -delete

install_runtime_for_arch() {
  local arch="$1"
  local dest="$RESOURCES/runtime/$arch"
  local tarball="${RUNTIME_VERSION}-${arch}-apple-darwin-install_only.tar.gz"
  local url="https://github.com/astral-sh/python-build-standalone/releases/download/${PBS_TAG}/${tarball}"
  local tmp wheelhouse site
  local platform

  case "$arch" in
    aarch64) platform="macosx_11_0_arm64" ;;
    x86_64) platform="macosx_11_0_x86_64" ;;
    *) echo "Unsupported arch: $arch" >&2; return 1 ;;
  esac

  echo "Embedding macOS runtime for $arch…"
  tmp="$(mktemp -d)"
  wheelhouse="$tmp/wheels"
  mkdir -p "$wheelhouse" "$dest"

  curl -fL --retry 3 --retry-delay 2 -o "$tmp/$tarball" "$url"
  rm -rf "$dest"
  mkdir -p "$dest"
  tar -xzf "$tmp/$tarball" -C "$dest"
  # Expect $dest/python/...
  if [[ ! -x "$dest/python/bin/python3" ]]; then
    echo "Missing embedded python for $arch" >&2
    rm -rf "$tmp"
    return 1
  fi

  # Download macOS wheels on this host, then unpack into the embedded site-packages.
  python3 -m pip download \
    -r "$ROOT/requirements-macos.txt" \
    -d "$wheelhouse" \
    --only-binary=:all: \
    --python-version 312 \
    --platform "$platform" \
    --implementation cp \
    --abi cp312

  site="$(echo "$dest"/python/lib/python3.*/site-packages)"
  mkdir -p "$site"
  # Cross-unpack macOS wheels on Linux (pip refuses foreign platform tags).
  for whl in "$wheelhouse"/*.whl; do
    unzip -o -q "$whl" -d "$site"
  done
  # Drop pip/wheel metadata caches that are not needed at runtime.
  find "$site" -type d -name '__pycache__' -prune -exec rm -rf {} +

  printf '%s\n' "$RUNTIME_VERSION" > "$dest/version"
  rm -rf "$tmp"
  echo "Embedded runtime ready: $dest"
}

# Embed both Apple Silicon and Intel runtimes when network is available.
if [[ "${SKIP_EMBED_RUNTIME:-}" != "1" ]]; then
  rm -rf "$RESOURCES/runtime"
  mkdir -p "$RESOURCES/runtime"
  install_runtime_for_arch aarch64
  install_runtime_for_arch x86_64
else
  echo "SKIP_EMBED_RUNTIME=1 — app will download runtime on first Mac launch."
fi

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
	<string>1.3.0</string>
	<key>CFBundleVersion</key>
	<string>3</string>
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

cat > "$MACOS/AlbumArtistCleaner" <<'LAUNCHER'
#!/bin/bash
set -euo pipefail

APP_NAME="Album Artist Cleaner"
RUNTIME_VERSION="cpython-3.12.13+20260728"
PBS_TAG="20260728"

notify() {
  local message="$1"
  osascript -e "display notification \"$(printf '%s' "$message" | sed 's/"/\\"/g')\" with title \"$APP_NAME\"" \
    >/dev/null 2>&1 || true
}

abort() {
  local message="$1"
  local escaped
  escaped="$(printf '%s' "$message" | sed 's/\\/\\\\/g; s/"/\\"/g')"
  osascript -e "display alert \"$APP_NAME\" message \"$escaped\" as critical" >/dev/null 2>&1 || true
  echo "$message" >&2
  exit 1
}

arch_key() {
  case "$(uname -m)" in
    arm64|aarch64) echo "aarch64" ;;
    x86_64) echo "x86_64" ;;
    *) abort "Unsupported Mac architecture: $(uname -m)" ;;
  esac
}

deps_ok() {
  local python_bin="$1"
  "$python_bin" - <<'PY' >/dev/null 2>&1
import mutagen
import AppKit
import PyObjCTools
PY
}

HERE="$(cd "$(dirname "$0")" && pwd)"
RESOURCES="$(cd "$HERE/../Resources" && pwd)"
SUPPORT="${HOME}/Library/Application Support/Album Artist Cleaner"
LOG_DIR="$SUPPORT/Logs"
mkdir -p "$SUPPORT" "$LOG_DIR"
LOG_FILE="$LOG_DIR/launch.log"

exec >>"$LOG_FILE" 2>&1
echo "---- $(date) ----"
echo "Arch: $(uname -m)"

ARCH="$(arch_key)"
BUNDLED="$RESOURCES/runtime/$ARCH"
BUNDLED_PYTHON="$BUNDLED/python/bin/python3"
FALLBACK="$SUPPORT/runtime"
FALLBACK_PYTHON="$FALLBACK/python/bin/python3"

PYTHON_BIN=""
PYTHON_HOME=""

if [[ -x "$BUNDLED_PYTHON" ]] && deps_ok "$BUNDLED_PYTHON"; then
  PYTHON_BIN="$BUNDLED_PYTHON"
  PYTHON_HOME="$BUNDLED/python"
  echo "Using bundled runtime: $PYTHON_BIN"
elif [[ -x "$FALLBACK_PYTHON" && -f "$FALLBACK/version" && "$(cat "$FALLBACK/version")" == "$RUNTIME_VERSION" ]] \
  && deps_ok "$FALLBACK_PYTHON"; then
  PYTHON_BIN="$FALLBACK_PYTHON"
  PYTHON_HOME="$FALLBACK/python"
  echo "Using cached runtime: $PYTHON_BIN"
else
  # One-time download using only built-in macOS tools (no Homebrew/Python needed).
  local_arch="$ARCH"
  tarball="${RUNTIME_VERSION}-${local_arch}-apple-darwin-install_only.tar.gz"
  url="https://github.com/astral-sh/python-build-standalone/releases/download/${PBS_TAG}/${tarball}"
  tmp="$(mktemp -d /tmp/album-artist-cleaner.XXXXXX)"

  notify "First launch setup — downloading built-in runtime…"
  echo "Downloading $url"
  if ! /usr/bin/curl -fL --retry 3 --retry-delay 2 -o "$tmp/$tarball" "$url"; then
    rm -rf "$tmp"
    abort "Could not download the built-in runtime.

An internet connection is needed the first time only.
You do not need to install Python, Homebrew, or Tk."
  fi

  rm -rf "$FALLBACK"
  mkdir -p "$FALLBACK"
  /usr/bin/tar -xzf "$tmp/$tarball" -C "$FALLBACK"
  rm -rf "$tmp"

  if [[ ! -x "$FALLBACK_PYTHON" ]]; then
    abort "Runtime download succeeded but python3 was missing."
  fi

  notify "Installing app components…"
  "$FALLBACK_PYTHON" -m pip install --upgrade pip
  "$FALLBACK_PYTHON" -m pip install -r "$RESOURCES/requirements-macos.txt"
  printf '%s\n' "$RUNTIME_VERSION" > "$FALLBACK/version"

  PYTHON_BIN="$FALLBACK_PYTHON"
  PYTHON_HOME="$FALLBACK/python"
  notify "Setup complete"
fi

# Clean up obsolete system-python env from older app versions.
rm -rf "$SUPPORT/venv" "$SUPPORT/venv-python" 2>/dev/null || true

cd "$RESOURCES"
export PYTHONHOME="$PYTHON_HOME"
export PYTHONPATH="$RESOURCES${PYTHONPATH:+:$PYTHONPATH}"
export PYTHONNOUSERSITE=1

exec "$PYTHON_BIN" "$RESOURCES/clean_album_artists.py" --gui
LAUNCHER

chmod +x "$MACOS/AlbumArtistCleaner"
printf 'APPL????' > "$CONTENTS/PkgInfo"

# Ignore bulky embedded runtimes in git status helpers (actual ignore in .gitignore).
echo "Built: $APP"
if [[ -d "$RESOURCES/runtime" ]]; then
  echo "Embedded runtimes: $(du -sh "$RESOURCES/runtime" | awk '{print $1}')"
  echo "App is ready to open on a Mac with nothing else installed."
else
  echo "No embedded runtime — first Mac launch will download one automatically."
fi
