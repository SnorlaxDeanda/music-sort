# Album Artist Cleaner

A **macOS application** that scans a music library folder structured as:

```text
Artist/
  Album/
    track.mp3
```

For each MP3, if the **Album Artist** ID3 tag looks like a featuring credit, it is rewritten to the primary artist only.

It also **deletes duplicate songs**, keeping one preferred copy. Duplicates are detected when:

- the same song identity matches (album artist/artist + album + title), including names like `Song (1).mp3` / `Song copy.mp3`
- or two files are byte-for-byte identical

| Before | After |
| --- | --- |
| `Artist A featuring Artist B` | `Artist A` |
| `Artist A feat. Artist B` | `Artist A` |
| `Artist A ft. Artist B` | `Artist A` |
| `Artist A (feat. Artist B)` | `Artist A` |
| `Artist A with Artist B` | `Artist A` |

Tags without a featuring-style credit are left alone. The track **Artist** tag is not modified.

## Install (macOS app)

1. Build (or refresh) the app bundle from this repo:

```bash
chmod +x scripts/build_macos_app.sh
./scripts/build_macos_app.sh
```

2. In Finder, open **`Album Artist Cleaner.app`**.
3. Optional: drag it into **Applications**.

First launch installs a private Python environment under  
`~/Library/Application Support/Album Artist Cleaner/`.

**Requirement:** Python 3.10+ (from [python.org](https://www.python.org/downloads/) or Homebrew).  
The app uses a **native macOS Cocoa UI** and does **not** need Tk/`python-tk`.

If macOS blocks the app: right-click → **Open** → **Open**.

### In the app

- Browse to your music folder
- Optionally enable **Dry run** to preview tag changes and deletions
- Keep **Delete duplicate songs** checked to remove extras (one copy kept)
- Click **Scan & Clean**
- Watch the **progress bar** and activity log

## Command line (optional)

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Preview only
python clean_album_artists.py "/path/to/Music" --dry-run

# Apply changes
python clean_album_artists.py "/path/to/Music"

# Open the GUI without the .app wrapper
python clean_album_artists.py --gui
```

## Safety tips

- Run with **Dry run** first.
- Keep a backup of your library (or work on a copy) before rewriting tags or deleting duplicates.
- Only `.mp3` files are processed.
- Duplicate deletion keeps the preferred file (better tags / non-copy name / larger size).

## Tests

```bash
pip install -r requirements.txt pytest
pytest -q
```

## Rebuild the app after code changes

```bash
./scripts/build_macos_app.sh
```
