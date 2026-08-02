# Album Artist Cleaner

A **fully self-contained macOS application**.  
No Homebrew. No Python install. No build step. No internet required to run.

## Use it

1. Open **`Album Artist Cleaner.app`**
2. If macOS blocks it the first time: right-click → **Open** → **Open**
3. Optional: drag it into **Applications**

That’s it. The first open may take a moment while it prepares the built-in runtime already inside the app.

### What it does

Scans a music library folder:

```text
Artist/
  Album/
    track.mp3
```

1. Rewrites **Album Artist** tags like `Artist A featuring Artist B` → `Artist A`
2. Fixes **special characters in folder/file names** that often become `_` on Linux  
   (e.g. backtick `` ` `` and curly quotes → plain `'`)
3. Deletes **duplicate songs**, keeping one preferred copy

### In the app

- Browse to your music folder
- Use **Dry run** to preview changes
- **Fix special characters** normalizes names like ``Artist`s`` → `Artist's`
- **Delete duplicate songs** keeps one copy and removes extras
- Watch the progress bar and activity log

## If the app closes right after you allow it in Privacy & Security

Reset the cached runtime and reopen:

```bash
rm -rf "$HOME/Library/Application Support/Album Artist Cleaner"
xattr -dr com.apple.quarantine "/path/to/Album Artist Cleaner.app"
open "/path/to/Album Artist Cleaner.app"
```

If it still fails, check:

```bash
open -e "$HOME/Library/Application Support/Album Artist Cleaner/Logs/launch.log"
```

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
./scripts/build_macos_app.sh
```
