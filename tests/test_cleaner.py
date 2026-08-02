"""Tests for album artist cleaning logic and duplicate deletion."""

from __future__ import annotations

from pathlib import Path

import pytest
from mutagen.easyid3 import EasyID3
from mutagen.id3 import ID3, TALB, TIT2, TPE1, TPE2

from album_artist_cleaner.cleaner import (
    clean_album_artist,
    process_file,
    rename_incompatible_paths,
    sanitize_filename,
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


def _write_tagged_mp3(
    path: Path,
    *,
    album_artist: str,
    title: str | None = None,
    album: str | None = None,
    artist: str | None = None,
    payload: bytes | None = None,
) -> None:
    """Create a file with ID3 tags (unique bytes so content-hash differs)."""
    unique = payload if payload is not None else f"{path}\n".encode()
    path.write_bytes(unique.ljust(64, b"\0"))
    tags = ID3()
    tags.add(TPE2(encoding=3, text=[album_artist]))
    tags.add(TPE1(encoding=3, text=[artist or "Ignored Artist"]))
    tags.add(TIT2(encoding=3, text=[title or path.stem]))
    tags.add(TALB(encoding=3, text=[album or path.parent.name]))
    tags.save(path)


def test_process_file_rewrites_album_artist(tmp_path: Path):
    mp3 = tmp_path / "Artist A" / "Album" / "track.mp3"
    mp3.parent.mkdir(parents=True)
    _write_tagged_mp3(mp3, album_artist="Artist A featuring Artist B")

    result = process_file(mp3, dry_run=False)

    assert result.changed is True
    assert result.original == "Artist A featuring Artist B"
    assert result.cleaned == "Artist A"

    tags = EasyID3(mp3)
    assert tags["albumartist"] == ["Artist A"]


def test_process_file_dry_run_does_not_write(tmp_path: Path):
    mp3 = tmp_path / "track.mp3"
    _write_tagged_mp3(mp3, album_artist="Artist A feat. Artist B")

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
        _write_tagged_mp3(path, album_artist=album_artist)

    report = scan_music_folder(
        library,
        dry_run=False,
        remove_duplicates=False,
        fix_filenames=False,
    )
    stats = summarize(report)

    assert stats["total"] == 3
    assert stats["changed"] == 2
    assert stats["skipped"] == 1
    assert stats["deleted"] == 0
    assert stats["renamed"] == 0
    assert EasyID3(a)["albumartist"] == ["Drake"]
    assert EasyID3(b)["albumartist"] == ["SZA"]
    assert EasyID3(c)["albumartist"] == ["Local Natives"]


def test_process_file_skips_when_no_feature(tmp_path: Path):
    mp3 = tmp_path / "song.mp3"
    _write_tagged_mp3(mp3, album_artist="Solo Artist")

    result = process_file(mp3)
    assert result.changed is False
    assert result.skipped is True


def test_scan_music_folder_progress_callback(tmp_path: Path):
    library = tmp_path / "Music"
    paths = []
    for name in ("a.mp3", "b.mp3", "c.mp3"):
        path = library / "Artist" / "Album" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        _write_tagged_mp3(path, album_artist="Artist featuring Guest", title=name)
        paths.append(path)

    seen: list[tuple[int, int, Path]] = []

    def on_progress(current: int, total: int, path: Path) -> None:
        seen.append((current, total, path))

    report = scan_music_folder(
        library,
        dry_run=True,
        on_progress=on_progress,
        remove_duplicates=False,
        fix_filenames=False,
    )

    assert len(report.tag_results) == 3
    assert [item[0] for item in seen[:3]] == [1, 2, 3]
    assert all(item[1] == 3 for item in seen[:3])
    assert [item[2] for item in seen[:3]] == sorted(paths)


def test_deletes_duplicate_songs_by_tags(tmp_path: Path):
    library = tmp_path / "Music"
    keep = library / "Artist" / "Album" / "Song.mp3"
    dup = library / "Artist" / "Album" / "Song (1).mp3"
    other = library / "Artist" / "Album" / "Other Song.mp3"
    for path in (keep, dup, other):
        path.parent.mkdir(parents=True, exist_ok=True)

    _write_tagged_mp3(
        keep,
        album_artist="Artist",
        title="Song",
        album="Album",
        payload=b"keep-audio",
    )
    _write_tagged_mp3(
        dup,
        album_artist="Artist",
        title="Song",
        album="Album",
        payload=b"dup-audio",
    )
    _write_tagged_mp3(
        other,
        album_artist="Artist",
        title="Other Song",
        album="Album",
        payload=b"other-audio",
    )

    report = scan_music_folder(library, dry_run=False, remove_duplicates=True)
    stats = summarize(report)

    assert stats["deleted"] == 1
    assert keep.exists()
    assert other.exists()
    assert not dup.exists()
    assert report.delete_results[0].kept == keep
    assert report.delete_results[0].path == dup


def test_deletes_identical_file_copies(tmp_path: Path):
    import shutil

    library = tmp_path / "Music"
    a = library / "A" / "Alb" / "one.mp3"
    b = library / "B" / "Alb" / "two.mp3"
    for path in (a, b):
        path.parent.mkdir(parents=True, exist_ok=True)

    _write_tagged_mp3(a, album_artist="A", title="One", album="Alb", payload=b"exact-same-bytes")
    shutil.copy2(a, b)

    report = scan_music_folder(library, dry_run=False, remove_duplicates=True)
    assert summarize(report)["deleted"] == 1
    assert a.exists() != b.exists()


def test_dry_run_does_not_delete_duplicates(tmp_path: Path):
    library = tmp_path / "Music"
    a = library / "Artist" / "Album" / "Song.mp3"
    b = library / "Artist" / "Album" / "Song copy.mp3"
    for path in (a, b):
        path.parent.mkdir(parents=True, exist_ok=True)
        _write_tagged_mp3(path, album_artist="Artist", title="Song", album="Album", payload=str(path).encode())

    report = scan_music_folder(
        library,
        dry_run=True,
        remove_duplicates=True,
        fix_filenames=False,
    )
    assert summarize(report)["deleted"] == 1
    assert a.exists() and b.exists()


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("Artist`s Name", "Artist's Name"),
        ("Artist's Name", "Artist's Name"),
        ("Artist’s Name", "Artist's Name"),  # curly apostrophe
        ("Hello: World?", "Hello- World"),
        ("Track_01.mp3", "Track_01.mp3"),
        ("Song*.mp3", "Song-.mp3"),
        ('Album "Live"', "Album 'Live'"),
    ],
)
def test_sanitize_filename(raw, expected):
    assert sanitize_filename(raw) == expected


def test_renames_backtick_folders_and_files(tmp_path: Path):
    library = tmp_path / "Music"
    old_file = library / "Artist`s Name" / "Album`s End" / "track`1.mp3"
    old_file.parent.mkdir(parents=True)
    _write_tagged_mp3(old_file, album_artist="Artist's Name")

    results = rename_incompatible_paths(library, dry_run=False)
    assert len(results) >= 2

    new_file = library / "Artist's Name" / "Album's End" / "track'1.mp3"
    assert new_file.exists()
    assert not old_file.exists()


def test_scan_fixes_filenames(tmp_path: Path):
    library = tmp_path / "Music"
    old_file = library / "Gun`s N Roses" / "Appetite" / "01.mp3"
    old_file.parent.mkdir(parents=True)
    _write_tagged_mp3(old_file, album_artist="Guns N Roses featuring Nobody")

    report = scan_music_folder(
        library,
        dry_run=False,
        remove_duplicates=False,
        fix_filenames=True,
    )
    stats = summarize(report)
    assert stats["renamed"] >= 1
    new_file = library / "Gun's N Roses" / "Appetite" / "01.mp3"
    assert new_file.exists()
    assert EasyID3(new_file)["albumartist"] == ["Guns N Roses"]
