"""Clean Album Artist ID3 tags by removing featured artists."""

from .cleaner import (
    FEATURE_PATTERN,
    clean_album_artist,
    process_file,
    scan_music_folder,
)

__all__ = [
    "FEATURE_PATTERN",
    "clean_album_artist",
    "process_file",
    "scan_music_folder",
]

__version__ = "1.0.0"
