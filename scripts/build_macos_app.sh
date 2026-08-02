#!/usr/bin/env bash
# Assemble a fully self-contained "Album Artist Cleaner.app".
# The finished .app includes its own Python runtime + dependencies.
# Running the app requires no Homebrew, system Python, Tk, or internet.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="Album Artist Cleaner.app"
APP="$ROOT/$APP_NAME"
CONTENTS="$APP/Contents"
MACOS="$CONTENTS/MacOS"
RESOURCES="$CONTENTS/Resources"
PACKS="$RESOURCES/runtime-packs"

PBS_TAG="20260728"
RUNTIME_VERSION="cpython-3.12.13+20260728"

mkdir -p "$MACOS" "$RESOURCES" "$PACKS"

# Copy application payload into the bundle.
rm -rf "$RESOURCES/album_artist_cleaner"
cp -R "$ROOT/album_artist_cleaner" "$RESOURCES/album_artist_cleaner"
cp "$ROOT/clean_album_artists.py" "$RESOURCES/clean_album_artists.py"
cp "$ROOT/requirements-macos.txt" "$RESOURCES/requirements-macos.txt"
find "$RESOURCES/album_artist_cleaner" -type d -name '__pycache__' -prune -exec rm -rf {} +
find "$RESOURCES" -type f -name '*.pyc' -delete

trim_runtime() {
  local python_root="$1"
  # Drop developer tooling and caches that are not needed at runtime.
  rm -rf \
    "$python_root/lib/python3.12/test" \
    "$python_root/lib/python3.12/tkinter" \
    "$python_root/lib/python3.12/idlelib" \
    "$python_root/lib/python3.12/turtledemo" \
    "$python_root/lib/python3.12/ensurepip" \
    "$python_root/lib/python3.12/lib2to3" \
    "$python_root/lib/python3.12/config-3.12"* \
    "$python_root/share" \
    "$python_root/include" 2>/dev/null || true
  find "$python_root" -type d -name '__pycache__' -prune -exec rm -rf {} +
  find "$python_root" -type f \( -name '*.pyc' -o -name '*.pyo' \) -delete
}

build_pack_for_arch() {
  local arch="$1"
  local tarball="${RUNTIME_VERSION}-${arch}-apple-darwin-install_only_stripped.tar.gz"
  local url="https://github.com/astral-sh/python-build-standalone/releases/download/${PBS_TAG}/${tarball}"
  local tmp wheelhouse stage site platform pack

  case "$arch" in
    aarch64) platform="macosx_11_0_arm64" ;;
    x86_64) platform="macosx_11_0_x86_64" ;;
    *) echo "Unsupported arch: $arch" >&2; return 1 ;;
  esac

  echo "Building bundled runtime pack for $arch…"
  tmp="$(mktemp -d)"
  wheelhouse="$tmp/wheels"
  stage="$tmp/stage"
  mkdir -p "$wheelhouse" "$stage"

  curl -fL --retry 3 --retry-delay 2 -o "$tmp/$tarball" "$url"
  tar -xzf "$tmp/$tarball" -C "$stage"
  if [[ ! -x "$stage/python/bin/python3" ]]; then
    echo "Missing python3 in stripped runtime for $arch" >&2
    rm -rf "$tmp"
    return 1
  fi

  python3 -m pip download \
    -r "$ROOT/requirements-macos.txt" \
    -d "$wheelhouse" \
    --only-binary=:all: \
    --python-version 312 \
    --platform "$platform" \
    --implementation cp \
    --abi cp312

  site="$stage/python/lib/python3.12/site-packages"
  mkdir -p "$site"
  for whl in "$wheelhouse"/*.whl; do
    unzip -o -q "$whl" -d "$site"
  done

  trim_runtime "$stage/python"
  printf '%s\n' "$RUNTIME_VERSION" > "$stage/version"

  pack="$PACKS/${arch}.tar.gz"
  tar -czf "$pack" -C "$stage" python version
  rm -rf "$tmp"
  echo "Wrote $pack ($(du -h "$pack" | awk '{print $1}'))"
}

# Runtime packs are required — the app must not depend on external downloads.
rm -rf "$PACKS"
mkdir -p "$PACKS"
build_pack_for_arch aarch64
build_pack_for_arch x86_64

# Also expand into Resources/runtime for machines that can write inside the bundle.
# The launcher prefers these paths when present; otherwise it extracts the packs
# into Application Support (still from files shipped inside the .app).
rm -rf "$RESOURCES/runtime"
mkdir -p "$RESOURCES/runtime"
for arch in aarch64 x86_64; do
  mkdir -p "$RESOURCES/runtime/$arch"
  tar -xzf "$PACKS/${arch}.tar.gz" -C "$RESOURCES/runtime/$arch"
done

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
	<string>1.4.0</string>
	<key>CFBundleVersion</key>
	<string>4</string>
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

# Canonical no-build-required launcher (runtime packs are already inside the .app).
cat > "$MACOS/AlbumArtistCleaner" <<'LAUNCHER'
#!/bin/bash
# Self-contained launcher. Uses only the runtime shipped inside this .app.
# Does not require Homebrew, system Python, Tk, internet, or a build step.
set -euo pipefail

APP_NAME="Album Artist Cleaner"
RUNTIME_VERSION="cpython-3.12.13+20260728"

notify() {
  local message="$1"
  if command -v osascript >/dev/null 2>&1; then
    osascript -e "display notification \"$(printf '%s' "$message" | sed 's/"/\\"/g')\" with title \"$APP_NAME\"" \
      >/dev/null 2>&1 || true
  fi
}

abort() {
  local message="$1"
  local escaped
  escaped="$(printf '%s' "$message" | sed 's/\\/\\\\/g; s/"/\\"/g')"
  if command -v osascript >/dev/null 2>&1; then
    osascript -e "display alert \"$APP_NAME\" message \"$escaped\" as critical" >/dev/null 2>&1 || true
  fi
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
BUNDLED_DIR="$RESOURCES/runtime/$ARCH"
BUNDLED_PYTHON="$BUNDLED_DIR/python/bin/python3"
PACK="$RESOURCES/runtime-packs/${ARCH}.tar.gz"
EXTRACTED_DIR="$SUPPORT/runtime/$ARCH"
EXTRACTED_PYTHON="$EXTRACTED_DIR/python/bin/python3"

PYTHON_BIN=""
PYTHON_HOME=""

if [[ -x "$BUNDLED_PYTHON" ]] && deps_ok "$BUNDLED_PYTHON"; then
  PYTHON_BIN="$BUNDLED_PYTHON"
  PYTHON_HOME="$BUNDLED_DIR/python"
  echo "Using in-bundle runtime: $PYTHON_BIN"
elif [[ -x "$EXTRACTED_PYTHON" && -f "$EXTRACTED_DIR/version" && "$(cat "$EXTRACTED_DIR/version")" == "$RUNTIME_VERSION" ]] \
  && deps_ok "$EXTRACTED_PYTHON"; then
  PYTHON_BIN="$EXTRACTED_PYTHON"
  PYTHON_HOME="$EXTRACTED_DIR/python"
  echo "Using extracted bundled runtime: $PYTHON_BIN"
else
  if [[ ! -f "$PACK" ]]; then
    abort "This copy of $APP_NAME is missing its built-in runtime.

Please re-download the complete app from the repository.
Nothing else needs to be installed."
  fi

  notify "Preparing built-in runtime (first open)…"
  echo "Extracting bundled runtime pack for $ARCH"
  rm -rf "$EXTRACTED_DIR"
  mkdir -p "$EXTRACTED_DIR"
  if ! /usr/bin/tar -xzf "$PACK" -C "$EXTRACTED_DIR"; then
    abort "Could not prepare the built-in runtime from the app bundle."
  fi
  if [[ ! -x "$EXTRACTED_PYTHON" ]] || ! deps_ok "$EXTRACTED_PYTHON"; then
    abort "The built-in runtime looks incomplete.

Please re-download the complete app from the repository."
  fi
  PYTHON_BIN="$EXTRACTED_PYTHON"
  PYTHON_HOME="$EXTRACTED_DIR/python"
  notify "Ready"
fi

# Remove leftovers from older versions that used system Python.
rm -rf "$SUPPORT/venv" "$SUPPORT/venv-python" 2>/dev/null || true

cd "$RESOURCES"
export PYTHONHOME="$PYTHON_HOME"
export PYTHONPATH="$RESOURCES${PYTHONPATH:+:$PYTHONPATH}"
export PYTHONNOUSERSITE=1

exec "$PYTHON_BIN" "$RESOURCES/clean_album_artists.py" --gui
LAUNCHER

chmod +x "$MACOS/AlbumArtistCleaner"
printf 'APPL????' > "$CONTENTS/PkgInfo"

echo "Built: $APP"
echo "Runtime packs: $(du -sh "$PACKS" | awk '{print $1}')"
echo "Full app size: $(du -sh "$APP" | awk '{print $1}')"
echo "Self-contained: no Homebrew, system Python, Tk, or internet required to run."
