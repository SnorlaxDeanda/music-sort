# Album Artist Cleaner

A **fully self-contained macOS application**.  
No Homebrew. No Python install. No Tk. No build step. No internet required to run.

## Use it

1. Open **`Album Artist Cleaner.app`**
2. If macOS blocks it: right-click → **Open** → **Open**
3. Optional: drag it into **Applications**

That’s it.

The first open may take a moment while it prepares the built-in runtime already shipped inside the app.

### What it does

Scans a music library folder:

```text
Artist/
  Album/
    track.mp3
```

1. Rewrites **Album Artist** tags like `Artist A featuring Artist B` → `Artist A`
2. Deletes **duplicate songs**, keeping one preferred copy

| Before | After |
| --- | --- |
| `Artist A featuring Artist B` | `Artist A` |
| `Artist A feat. Artist B` | `Artist A` |
| `Artist A ft. Artist B` | `Artist A` |
| `Artist A (feat. Artist B)` | `Artist A` |

### In the app

- Browse to your music folder
- Use **Dry run** to preview changes
- **Delete duplicate songs** keeps one copy and removes extras
- Watch the progress bar and activity log

## Safety tips

- Run **Dry run** first
- Back up your library before bulk edits/deletes
- Only `.mp3` files are processed

## For developers

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt pytest
pytest -q
```

To refresh the bundled app after changing source:

```bash
./scripts/build_macos_app.sh
```
