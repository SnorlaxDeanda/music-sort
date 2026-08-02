"""Clean Album Artist ID3 tags by removing featured artists."""

from .cleaner import (
    FEATURE_PATTERN,
    ScanReport,
    clean_album_artist,
    list_mp3_files,
    process_file,
    scan_music_folder,
)

__all__ = [
    "FEATURE_PATTERN",
    "ScanReport",
    "clean_album_artist",
    "list_mp3_files",
    "process_file",
    "scan_music_folder",
]

__version__ = "1.5.1"
