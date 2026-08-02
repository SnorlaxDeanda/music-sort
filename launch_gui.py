"""Bootstrap the GUI and surface startup failures with a macOS alert."""

from __future__ import annotations

import os
import sys
import traceback


def _alert(message: str) -> None:
    import subprocess

    text = message[-1200:].replace("\\", "\\\\").replace('"', '\\"')
    subprocess.run(
        [
            "/usr/bin/osascript",
            "-e",
            f'display alert "Album Artist Cleaner" message "{text}" as critical',
        ],
        check=False,
    )


def main() -> int:
    os.environ.setdefault("TK_SILENCE_DEPRECATION", "1")
    try:
        from album_artist_cleaner.gui import run_gui

        return run_gui()
    except Exception:  # noqa: BLE001
        details = traceback.format_exc()
        print(details, file=sys.stderr)
        _alert(
            "The app failed to start.\n\n"
            f"{details[-900:]}\n\n"
            "Log: ~/Library/Application Support/Album Artist Cleaner/Logs/launch.log"
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
