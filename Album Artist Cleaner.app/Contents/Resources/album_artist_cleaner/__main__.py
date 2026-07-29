"""Allow `python -m album_artist_cleaner`."""

from .cli import main

if __name__ == "__main__":
    raise SystemExit(main())
