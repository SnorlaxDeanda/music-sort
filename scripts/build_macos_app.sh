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
cp "$ROOT/launch_gui.py" "$RESOURCES/launch_gui.py"
cp "$ROOT/requirements-macos.txt" "$RESOURCES/requirements-macos.txt"
find "$RESOURCES/album_artist_cleaner" -type d -name '__pycache__' -prune -exec rm -rf {} +
find "$RESOURCES" -type f -name '*.pyc' -delete

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
	<string>1.5.1</string>
	<key>CFBundleVersion</key>
	<string>6</string>
	<key>LSApplicationCategoryType</key>
	<string>public.app-category.music</string>
	<key>LSMinimumSystemVersion</key>
	<string>11.0</string>
	<key>NSHighResolutionCapable</key>
	<true/>
</dict>
</plist>
PLIST

cp "$ROOT/scripts/macos_launcher.sh" "$MACOS/AlbumArtistCleaner"
chmod +x "$MACOS/AlbumArtistCleaner"
printf 'APPL????' > "$CONTENTS/PkgInfo"

# Remove obsolete AppleScript experiment if present.
rm -f "$RESOURCES/gui.applescript"

echo "Built: $APP"
echo "Runtime packs: $(du -sh "$PACKS" | awk '{print $1}')"
echo "Full app size: $(du -sh "$APP" | awk '{print $1}')"
