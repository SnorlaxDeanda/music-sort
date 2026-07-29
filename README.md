# Album Artist Cleaner

macOS-friendly tool that scans a music library folder structured as:

```text
Artist/
  Album/
    track.mp3
```

For each MP3, if the **Album Artist** ID3 tag looks like a featuring credit, it is rewritten to the primary artist only.

| Before | After |
| --- | --- |
| `Artist A featuring Artist B` | `Artist A` |
| `Artist A feat. Artist B` | `Artist A` |
| `Artist A ft. Artist B` | `Artist A` |
| `Artist A (feat. Artist B)` | `Artist A` |
| `Artist A with Artist B` | `Artist A` |

Tags without a featuring-style credit are left alone. The track **Artist** tag is not modified.

## Requirements

- macOS (or any OS with Python 3.10+)
- Python 3

## Quick start (macOS)

1. Open Terminal in this folder.
2. Make the launcher executable once:

```bash
chmod +x run_album_artist_cleaner.command
```

3. Double-click `run_album_artist_cleaner.command` in Finder, **or** run:

```bash
./run_album_artist_cleaner.command
```

The first launch creates a local virtualenv and installs `mutagen`. A simple window opens so you can pick your music folder.

Check **Dry run** first to preview changes without writing tags.

## Command line

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Preview only
python clean_album_artists.py "/path/to/Music" --dry-run

# Apply changes
python clean_album_artists.py "/path/to/Music"

# Open GUI
python clean_album_artists.py --gui
```

## Safety tips

- Run with `--dry-run` / the Dry run checkbox first.
- Keep a backup of your library (or work on a copy) before rewriting tags in bulk.
- Only `.mp3` files are processed.

## Tests

```bash
pip install -r requirements.txt pytest
pytest -q
```
