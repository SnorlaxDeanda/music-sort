"""Tests for album artist cleaning logic and MP3 rewriting."""

from __future__ import annotations

from pathlib import Path

import pytest
from mutagen.easyid3 import EasyID3
from mutagen.id3 import ID3, TIT2, TPE1, TPE2

from album_artist_cleaner.cleaner import (
    clean_album_artist,
    process_file,
    scan_music_folder,
    summarize,
)


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("Artist A featuring Artist B", "Artist A"),
        ("Artist A Featuring Artist B", "Artist A"),
        ("Artist A feat. Artist B", "Artist A"),
        ("Artist A feat Artist B", "Artist A"),
        ("Artist A Feat. Artist B & Artist C", "Artist A"),
        ("Artist A ft. Artist B", "Artist A"),
        ("Artist A ft Artist B", "Artist A"),
        ("Artist A (feat. Artist B)", "Artist A"),
        ("Artist A [featuring Artist B]", "Artist A"),
        ("Artist A with Artist B", "Artist A"),
        ("Artist A w/ Artist B", "Artist A"),
        ("Just Artist", "Just Artist"),
        ("Artist A and Artist B", "Artist A and Artist B"),
        ("  Artist A featuring Artist B  ", "Artist A"),
        ("", ""),
        (None, None),
    ],
)
def test_clean_album_artist(raw, expected):
    assert clean_album_artist(raw) == expected


def _write_tagged_mp3(path: Path, album_artist: str) -> None:
    """Create a file with ID3 tags (no real audio needed for mutagen tag I/O)."""
    path.write_bytes(b"\x00" * 64)
    tags = ID3()
    tags.add(TPE2(encoding=3, text=[album_artist]))
    tags.add(TPE1(encoding=3, text=["Ignored Artist"]))
    tags.add(TIT2(encoding=3, text=[path.stem]))
    tags.save(path)


def test_process_file_rewrites_album_artist(tmp_path: Path):
    mp3 = tmp_path / "Artist A" / "Album" / "track.mp3"
    mp3.parent.mkdir(parents=True)
    _write_tagged_mp3(mp3, "Artist A featuring Artist B")

    result = process_file(mp3, dry_run=False)

    assert result.changed is True
    assert result.original == "Artist A featuring Artist B"
    assert result.cleaned == "Artist A"

    tags = EasyID3(mp3)
    assert tags["albumartist"] == ["Artist A"]


def test_process_file_dry_run_does_not_write(tmp_path: Path):
    mp3 = tmp_path / "track.mp3"
    _write_tagged_mp3(mp3, "Artist A feat. Artist B")

    result = process_file(mp3, dry_run=True)
    assert result.changed is True
    assert EasyID3(mp3)["albumartist"] == ["Artist A feat. Artist B"]


def test_scan_music_folder(tmp_path: Path):
    library = tmp_path / "Music"
    a = library / "Drake" / "Album One" / "01.mp3"
    b = library / "SZA" / "Album Two" / "02.mp3"
    c = library / "Local Natives" / "Album Three" / "03.mp3"
    for path, album_artist in (
        (a, "Drake featuring 21 Savage"),
        (b, "SZA ft. Travis Scott"),
        (c, "Local Natives"),
    ):
        path.parent.mkdir(parents=True, exist_ok=True)
        _write_tagged_mp3(path, album_artist)

    results = scan_music_folder(library, dry_run=False)
    stats = summarize(results)

    assert stats["total"] == 3
    assert stats["changed"] == 2
    assert stats["skipped"] == 1
    assert EasyID3(a)["albumartist"] == ["Drake"]
    assert EasyID3(b)["albumartist"] == ["SZA"]
    assert EasyID3(c)["albumartist"] == ["Local Natives"]


def test_process_file_skips_when_no_feature(tmp_path: Path):
    mp3 = tmp_path / "song.mp3"
    _write_tagged_mp3(mp3, "Solo Artist")

    result = process_file(mp3)
    assert result.changed is False
    assert result.skipped is True


def test_scan_music_folder_progress_callback(tmp_path: Path):
    library = tmp_path / "Music"
    paths = []
    for name in ("a.mp3", "b.mp3", "c.mp3"):
        path = library / "Artist" / "Album" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        _write_tagged_mp3(path, "Artist featuring Guest")
        paths.append(path)

    seen: list[tuple[int, int, Path]] = []

    def on_progress(current: int, total: int, path: Path) -> None:
        seen.append((current, total, path))

    results = scan_music_folder(library, dry_run=True, on_progress=on_progress)

    assert len(results) == 3
    assert [item[0] for item in seen] == [1, 2, 3]
    assert all(item[1] == 3 for item in seen)
    assert [item[2] for item in seen] == sorted(paths)
