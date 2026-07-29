# Album Artist Cleaner

A **self-contained macOS application** — no Homebrew, Python, or Tk install required.

It scans a music library folder structured as:

```text
Artist/
  Album/
    track.mp3
```

### What it does
1. Rewrites **Album Artist** tags like `Artist A featuring Artist B` → `Artist A`
2. Deletes **duplicate songs**, keeping one preferred copy

| Before | After |
| --- | --- |
| `Artist A featuring Artist B` | `Artist A` |
| `Artist A feat. Artist B` | `Artist A` |
| `Artist A ft. Artist B` | `Artist A` |
| `Artist A (feat. Artist B)` | `Artist A` |

## Install

```bash
chmod +x scripts/build_macos_app.sh
./scripts/build_macos_app.sh
```

Then double-click **`Album Artist Cleaner.app`** (or drag it to Applications).

That’s it. The app ships with its own runtime (or downloads one automatically on first launch using only built-in macOS tools). You do **not** install Python, Homebrew, or Tk.

If macOS blocks the app: right-click → **Open** → **Open**.

### In the app
- Browse to your music folder
- Use **Dry run** to preview changes
- **Delete duplicate songs** keeps one copy and removes extras
- Watch the progress bar and activity log

## Safety tips
- Run **Dry run** first
- Back up your library before bulk edits/deletes
- Only `.mp3` files are processed

## Developer extras

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt pytest
pytest -q
```

Rebuild after code changes:

```bash
./scripts/build_macos_app.sh
```
