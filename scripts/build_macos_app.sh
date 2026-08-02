#!/usr/bin/env bash
# Assemble a fully self-contained "Album Artist Cleaner.app".
# The finished .app includes its own Python+Tk runtime + dependencies.
# Running the app requires no Homebrew, system Python, or internet.
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

rm -rf "$RESOURCES/album_artist_cleaner"
cp -R "$ROOT/album_artist_cleaner" "$RESOURCES/album_artist_cleaner"
cp "$ROOT/clean_album_artists.py" "$RESOURCES/clean_album_artists.py"
cp "$ROOT/requirements-macos.txt" "$RESOURCES/requirements-macos.txt"
find "$RESOURCES/album_artist_cleaner" -type d -name '__pycache__' -prune -exec rm -rf {} +
find "$RESOURCES" -type f -name '*.pyc' -delete

# Keep a tiny native UI helper for fallback dialogs if needed.
cat > "$RESOURCES/run_worker.py" <<'PY'
from album_artist_cleaner.cli import main
raise SystemExit(main())
PY

trim_runtime() {
  local python_root="$1"
  # Keep tkinter — the app GUI uses the bundled Tcl/Tk.
  rm -rf \
    "$python_root/lib/python3.12/test" \
    "$python_root/lib/python3.12/idlelib" \
    "$python_root/lib/python3.12/turtledemo" \
    "$python_root/lib/python3.12/ensurepip" \
    "$python_root/lib/python3.12/lib2to3" \
    "$python_root/lib/python3.12/config-3.12"* \
    "$python_root/include" 2>/dev/null || true
  # PyObjC ships bulky test/debug symbols we do not need.
  rm -rf "$python_root/lib/python3.12/site-packages/PyObjCTest" 2>/dev/null || true
  find "$python_root" -type d -name '*.dSYM' -prune -exec rm -rf {} +
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

  # mutagen only — GUI uses bundled Tk, not PyObjC.
  python3 -m pip download \
    mutagen \
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

rm -rf "$PACKS"
mkdir -p "$PACKS"
build_pack_for_arch aarch64
build_pack_for_arch x86_64

rm -rf "$RESOURCES/runtime"
mkdir -p "$RESOURCES/runtime"
for arch in aarch64 x86_64; do
  mkdir -p "$RESOURCES/runtime/$arch"
  tar -xzf "$PACKS/${arch}.tar.gz" -C "$RESOURCES/runtime/$arch"
done

# requirements used only for reference now
cat > "$RESOURCES/requirements-macos.txt" <<'REQ'
mutagen>=1.47.0
REQ
cp "$RESOURCES/requirements-macos.txt" "$ROOT/requirements-macos.txt"

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
	<string>1.5.0</string>
	<key>CFBundleVersion</key>
	<string>5</string>
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
# Self-contained launcher. No Homebrew / system Python / internet required.
set -euo pipefail

APP_NAME="Album Artist Cleaner"
RUNTIME_VERSION="cpython-3.12.13+20260728"

notify() {
  local message="$1"
  /usr/bin/osascript -e "display notification \"$(printf '%s' "$message" | sed 's/"/\\"/g')\" with title \"$APP_NAME\"" \
    >/dev/null 2>&1 || true
}

abort() {
  local message="$1"
  local escaped
  escaped="$(printf '%s' "$message" | sed 's/\\/\\\\/g; s/"/\\"/g')"
  /usr/bin/osascript -e "display alert \"$APP_NAME\" message \"$escaped\" as critical" \
    >/dev/null 2>&1 || true
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

clear_quarantine() {
  local target="$1"
  if [[ -e "$target" ]]; then
    /usr/bin/xattr -dr com.apple.quarantine "$target" 2>/dev/null || true
  fi
}

runtime_ok() {
  local python_bin="$1"
  # Must be able to import mutagen and tkinter from the bundled runtime.
  "$python_bin" - <<'PY' >/dev/null 2>&1
import mutagen
import tkinter
PY
}

HERE="$(cd "$(dirname "$0")" && pwd)"
RESOURCES="$(cd "$HERE/../Resources" && pwd)"
APP_ROOT="$(cd "$HERE/../.." && pwd)"
SUPPORT="${HOME}/Library/Application Support/Album Artist Cleaner"
LOG_DIR="$SUPPORT/Logs"
mkdir -p "$SUPPORT" "$LOG_DIR"
LOG_FILE="$LOG_DIR/launch.log"

# Log, but keep stderr available for debugging if needed.
exec >>"$LOG_FILE" 2>&1
echo "---- $(date) ----"
echo "Arch: $(uname -m)"
echo "App root: $APP_ROOT"

# Gatekeeper often blocks unsigned bundled binaries until quarantine is cleared.
clear_quarantine "$APP_ROOT"
clear_quarantine "$RESOURCES/runtime"
clear_quarantine "$RESOURCES/runtime-packs"

ARCH="$(arch_key)"
BUNDLED_DIR="$RESOURCES/runtime/$ARCH"
BUNDLED_PYTHON="$BUNDLED_DIR/python/bin/python3"
PACK="$RESOURCES/runtime-packs/${ARCH}.tar.gz"
EXTRACTED_DIR="$SUPPORT/runtime/$ARCH"
EXTRACTED_PYTHON="$EXTRACTED_DIR/python/bin/python3"

PYTHON_BIN=""
PYTHON_HOME=""

prepare_extracted() {
  [[ -f "$PACK" ]] || abort "This copy of $APP_NAME is missing its built-in runtime pack.

Please re-download the complete app."
  notify "Preparing built-in runtime (first open)…"
  rm -rf "$EXTRACTED_DIR"
  mkdir -p "$EXTRACTED_DIR"
  /usr/bin/tar -xzf "$PACK" -C "$EXTRACTED_DIR" || abort "Could not prepare the built-in runtime."
  clear_quarantine "$EXTRACTED_DIR"
  # Ensure binaries are executable after extraction.
  chmod -R u+rwX "$EXTRACTED_DIR" 2>/dev/null || true
  chmod +x "$EXTRACTED_DIR/python/bin/"* 2>/dev/null || true
  find "$EXTRACTED_DIR" -name '*.so' -exec chmod +x {} + 2>/dev/null || true
  find "$EXTRACTED_DIR" -name '*.dylib' -exec chmod +x {} + 2>/dev/null || true
}

if [[ -x "$BUNDLED_PYTHON" ]]; then
  clear_quarantine "$BUNDLED_DIR"
  chmod +x "$BUNDLED_DIR/python/bin/"* 2>/dev/null || true
fi

if [[ -x "$BUNDLED_PYTHON" ]] && runtime_ok "$BUNDLED_PYTHON"; then
  PYTHON_BIN="$BUNDLED_PYTHON"
  PYTHON_HOME="$BUNDLED_DIR/python"
  echo "Using in-bundle runtime"
elif [[ -x "$EXTRACTED_PYTHON" && -f "$EXTRACTED_DIR/version" && "$(cat "$EXTRACTED_DIR/version")" == "$RUNTIME_VERSION" ]] \
  && runtime_ok "$EXTRACTED_PYTHON"; then
  PYTHON_BIN="$EXTRACTED_PYTHON"
  PYTHON_HOME="$EXTRACTED_DIR/python"
  echo "Using extracted runtime"
else
  prepare_extracted
  runtime_ok "$EXTRACTED_PYTHON" || abort "The built-in runtime could not start Tk/mutagen.

Details are in:
$LOG_FILE"
  PYTHON_BIN="$EXTRACTED_PYTHON"
  PYTHON_HOME="$EXTRACTED_DIR/python"
  notify "Ready"
fi

rm -rf "$SUPPORT/venv" "$SUPPORT/venv-python" 2>/dev/null || true

cd "$RESOURCES"
export PYTHONHOME="$PYTHON_HOME"
export PYTHONPATH="$RESOURCES"
export PYTHONNOUSERSITE=1
# Help bundled Tcl/Tk locate its files next to the runtime.
# python-build-standalone layout for Tcl/Tk 9.
if [[ -d "$PYTHON_HOME/lib/tcl9.0" ]]; then
  export TCL_LIBRARY="$PYTHON_HOME/lib/tcl9.0"
elif [[ -d "$PYTHON_HOME/lib/tcl9/9.0" ]]; then
  export TCL_LIBRARY="$PYTHON_HOME/lib/tcl9/9.0"
fi
if [[ -d "$PYTHON_HOME/lib/tk9.0" ]]; then
  export TK_LIBRARY="$PYTHON_HOME/lib/tk9.0"
fi

echo "PYTHON_BIN=$PYTHON_BIN"
echo "PYTHONHOME=$PYTHONHOME"
echo "TCL_LIBRARY=${TCL_LIBRARY:-}"
echo "TK_LIBRARY=${TK_LIBRARY:-}"

set +e
"$PYTHON_BIN" "$RESOURCES/clean_album_artists.py" --gui
STATUS=$?
set -e

if [[ $STATUS -ne 0 ]]; then
  TAIL="$(tail -n 60 "$LOG_FILE" 2>/dev/null || true)"
  abort "Album Artist Cleaner quit unexpectedly (code $STATUS).

$TAIL"
fi
exit 0
LAUNCHER

chmod +x "$MACOS/AlbumArtistCleaner"
printf 'APPL????' > "$CONTENTS/PkgInfo"

# Remove obsolete AppleScript experiment if present.
rm -f "$RESOURCES/gui.applescript"

echo "Built: $APP"
echo "Runtime packs: $(du -sh "$PACKS" | awk '{print $1}')"
echo "Full app size: $(du -sh "$APP" | awk '{print $1}')"
