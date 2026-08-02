#!/usr/bin/env bash
# Double-clickable / Terminal launcher for macOS.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required. Install Python from https://www.python.org/downloads/" >&2
  exit 1
fi

if [[ ! -d .venv ]]; then
  python3 -m venv .venv
  .venv/bin/pip install --upgrade pip
  .venv/bin/pip install -r requirements.txt
fi

# No args -> open GUI. Pass a folder (and optional flags) for CLI mode.
exec .venv/bin/python clean_album_artists.py "$@"
