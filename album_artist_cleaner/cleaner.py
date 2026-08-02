"""Core logic for cleaning Album Artist tags and removing duplicate songs."""

from __future__ import annotations

import hashlib
import re
from collections import defaultdict
from collections.abc import Callable, Iterable, Iterator
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from mutagen.easyid3 import EasyID3
from mutagen.id3 import ID3, ID3NoHeaderError

ProgressCallback = Callable[[int, int, Path], None]

# Matches common "featuring" forms used in album/artist credits.
FEATURE_PATTERN = re.compile(
    r"""
    \s*
    (?:
        [\(\[]\s*
    )?
    (?:
        (?:
            featuring
            | feat\.?
            | ft\.?
            | with
        )\b
        | w/
    )
    """,
    re.IGNORECASE | re.VERBOSE,
)

_TRAILING_PARENS = re.compile(r"\s*[\(\[\{]\s*[\)\]\}]\s*$")
_MULTI_SPACE = re.compile(r"\s{2,}")
_COPY_SUFFIX = re.compile(
    r"""
    (?:
        \s*\(\d+\)          # track (1)
        | \s+copy(?:\s*\d+)? 
        | \s+-\s+copy
        | \s+duplicate
    )
    $
    """,
    re.IGNORECASE | re.VERBOSE,
)

AUDIO_EXTENSIONS = {".mp3"}


def _is_ignored_name(name: str) -> bool:
    """Ignore macOS AppleDouble / resource-fork files like '._track.mp3'."""
    return name.startswith("._")


def _is_ignored_path(path: Path) -> bool:
    """True if this path or any parent component should be skipped."""
    return any(_is_ignored_name(part) for part in path.parts)

# Characters that often become "_" (or are rejected) on Linux/Windows/Samba transfers.
# Quote-like marks are normalized to a plain ASCII apostrophe.
_APOSTROPHE_CHARS = str.maketrans(
    {
        "`": "'",  # backtick
        "´": "'",  # acute accent
        "‘": "'",  # left single quotation mark
        "’": "'",  # right single quotation mark
        "‛": "'",  # single high-reversed-9 quotation mark
        "ʼ": "'",  # modifier letter apostrophe
        "ʻ": "'",  # modifier letter turned comma
        "′": "'",  # prime
        "ꞌ": "'",  # latin small letter saltillo
    }
)

# Other characters commonly rewritten to "_" by restrictive filesystems/tools.
_UNSAFE_CHARS = str.maketrans(
    {
        '"': "'",
        "“": "'",
        "”": "'",
        ":": "-",
        "*": "-",
        "?": "",
        "<": "",
        ">": "",
        "|": "-",
        "\\": "-",
        "/": "-",
        "#": "",
        "%": "",
    }
)

_MULTI_DASH = re.compile(r"-{2,}")
_MULTI_UNDERSCORE = re.compile(r"_{2,}")


@dataclass(frozen=True)
class FileResult:
    """Outcome of processing Album Artist on a single audio file."""

    path: Path
    original: str | None
    cleaned: str | None
    changed: bool
    skipped: bool
    error: str | None = None


@dataclass(frozen=True)
class DeleteResult:
    """Outcome of deleting a duplicate song file."""

    path: Path
    kept: Path
    reason: str
    deleted: bool
    error: str | None = None


@dataclass(frozen=True)
class RenameResult:
    """Outcome of renaming a path for cross-platform compatibility."""

    original: Path
    renamed: Path
    changed: bool
    error: str | None = None


@dataclass
class ScanReport:
    """Combined results from a library scan."""

    tag_results: list[FileResult] = field(default_factory=list)
    delete_results: list[DeleteResult] = field(default_factory=list)
    rename_results: list[RenameResult] = field(default_factory=list)

    @property
    def files_scanned(self) -> int:
        return len(self.tag_results)


def clean_album_artist(value: str | None) -> str | None:
    """
    Return album artist with featured collaborators removed.

    "Artist A featuring Artist B" -> "Artist A"
    "Artist A (feat. Artist B)"   -> "Artist A"
    Values without a featuring credit are returned unchanged.
    """
    if value is None:
        return None

    text = value.strip()
    if not text:
        return text

    match = FEATURE_PATTERN.search(text)
    if not match:
        return text

    cleaned = text[: match.start()].strip(" -–—,/&")
    cleaned = _TRAILING_PARENS.sub("", cleaned)
    cleaned = _MULTI_SPACE.sub(" ", cleaned).strip()
    return cleaned or text


def _normalize_text(value: str | None) -> str:
    if not value:
        return ""
    text = value.casefold().strip()
    text = _MULTI_SPACE.sub(" ", text)
    return text


def _normalize_title(value: str | None, fallback_stem: str) -> str:
    raw = value if value else fallback_stem
    text = _normalize_text(raw)
    text = _COPY_SUFFIX.sub("", text).strip()
    return text


def _iter_mp3_files(root: Path) -> Iterator[Path]:
    for path in sorted(root.rglob("*")):
        if _is_ignored_path(path):
            continue
        if path.is_file() and path.suffix.lower() in AUDIO_EXTENSIONS:
            yield path


def list_mp3_files(root: Path | str) -> list[Path]:
    """Return sorted MP3 paths under a music folder."""
    root_path = Path(root).expanduser().resolve()
    if not root_path.is_dir():
        raise NotADirectoryError(f"Not a directory: {root_path}")
    return list(_iter_mp3_files(root_path))


def sanitize_filename(name: str) -> str:
    """
    Normalize a single path component for cross-platform use.

    Backticks and curly/smart quotes become a plain apostrophe (').
    Other characters that often become "_" on Linux/Windows/Samba are
    replaced with safer ASCII alternatives.
    """
    if not name:
        return name

    stem = name
    suffix = ""
    # Preserve a normal file extension like .mp3 when present.
    if "." in name and not name.startswith("."):
        path_name = Path(name)
        if path_name.suffix:
            stem = path_name.stem
            suffix = path_name.suffix

    cleaned = stem.translate(_APOSTROPHE_CHARS).translate(_UNSAFE_CHARS)
    cleaned = cleaned.replace("\0", "")
    cleaned = _MULTI_SPACE.sub(" ", cleaned)
    cleaned = _MULTI_DASH.sub("-", cleaned)
    cleaned = _MULTI_UNDERSCORE.sub("_", cleaned)
    cleaned = cleaned.strip(" .")
    if not cleaned:
        cleaned = "unnamed"
    return f"{cleaned}{suffix}"


def _unique_target(target: Path) -> Path:
    """Avoid clobbering an existing path by adding ' (2)', ' (3)', …"""
    if not target.exists():
        return target
    stem = target.stem
    suffix = target.suffix
    parent = target.parent
    index = 2
    while True:
        candidate = parent / f"{stem} ({index}){suffix}"
        if not candidate.exists():
            return candidate
        index += 1


def rename_incompatible_paths(
    root: Path | str,
    *,
    dry_run: bool = False,
    on_progress: Optional[ProgressCallback] = None,
    progress_offset: int = 0,
    progress_total: int | None = None,
) -> list[RenameResult]:
    """
    Rename files/folders under root whose names contain incompatible characters.

    Processes deepest paths first so children are renamed before parents.
    """
    root_path = Path(root).expanduser().resolve()
    if not root_path.is_dir():
        raise NotADirectoryError(f"Not a directory: {root_path}")

    # Collect every file and directory under root (not root itself).
    # Skip macOS AppleDouble files/folders that start with "._".
    entries = [
        path
        for path in root_path.rglob("*")
        if path.exists() and not _is_ignored_path(path)
    ]
    # Deepest first.
    entries.sort(key=lambda path: len(path.parts), reverse=True)

    results: list[RenameResult] = []
    total = progress_total if progress_total is not None else progress_offset + max(len(entries), 1)

    for index, path in enumerate(entries, start=1):
        original_name = path.name
        new_name = sanitize_filename(original_name)
        if new_name == original_name:
            if on_progress is not None:
                on_progress(progress_offset + index, total, path)
            continue

        target = _unique_target(path.with_name(new_name))
        try:
            if not dry_run:
                path.rename(target)
            results.append(
                RenameResult(
                    original=path,
                    renamed=target,
                    changed=True,
                )
            )
        except Exception as exc:  # noqa: BLE001
            results.append(
                RenameResult(
                    original=path,
                    renamed=target,
                    changed=False,
                    error=str(exc),
                )
            )
        if on_progress is not None:
            on_progress(progress_offset + index, total, path)

    return results


def _read_tags(path: Path) -> dict[str, str | None]:
    """Read common EasyID3 fields; missing keys become None."""
    try:
        audio = EasyID3(path)
    except ID3NoHeaderError:
        return {"albumartist": None, "artist": None, "album": None, "title": None}
    except Exception:
        try:
            tags = ID3(path)
        except Exception:
            return {"albumartist": None, "artist": None, "album": None, "title": None}
        return {
            "albumartist": str(tags["TPE2"]) if "TPE2" in tags else None,
            "artist": str(tags["TPE1"]) if "TPE1" in tags else None,
            "album": str(tags["TALB"]) if "TALB" in tags else None,
            "title": str(tags["TIT2"]) if "TIT2" in tags else None,
        }

    def first(key: str) -> str | None:
        values = audio.get(key)
        if not values:
            return None
        return values[0]

    return {
        "albumartist": first("albumartist"),
        "artist": first("artist"),
        "album": first("album"),
        "title": first("title"),
    }


def _read_album_artist(path: Path) -> str | None:
    return _read_tags(path)["albumartist"]


def _write_album_artist(path: Path, value: str) -> None:
    try:
        audio = EasyID3(path)
    except ID3NoHeaderError:
        tags = ID3()
        tags.save(path)
        audio = EasyID3(path)

    audio["albumartist"] = value
    audio.save(path)


def process_file(path: Path, *, dry_run: bool = False) -> FileResult:
    """Read, optionally rewrite, and report Album Artist for one MP3."""
    try:
        original = _read_album_artist(path)
        cleaned = clean_album_artist(original)

        if original is None or cleaned is None or cleaned == original:
            return FileResult(
                path=path,
                original=original,
                cleaned=cleaned,
                changed=False,
                skipped=True,
            )

        if not dry_run:
            _write_album_artist(path, cleaned)

        return FileResult(
            path=path,
            original=original,
            cleaned=cleaned,
            changed=True,
            skipped=False,
        )
    except Exception as exc:  # noqa: BLE001 - surface per-file errors to the UI/CLI
        return FileResult(
            path=path,
            original=None,
            cleaned=None,
            changed=False,
            skipped=True,
            error=str(exc),
        )


def _file_fingerprint(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def _song_identity_key(path: Path, tags: dict[str, str | None]) -> str | None:
    """
    Build a duplicate key for "same song" matching.

    Uses cleaned album artist (or artist) + album + title.
    Falls back to filename when title is missing.
    Returns None when there is not enough identity information.
    """
    album_artist = clean_album_artist(tags.get("albumartist")) or tags.get("artist")
    album = tags.get("album")
    title = _normalize_title(tags.get("title"), path.stem)

    artist_key = _normalize_text(album_artist)
    album_key = _normalize_text(album)
    if not title:
        return None
    if not artist_key and not album_key:
        # Filename-only duplicates in the same folder still count.
        return f"path:{path.parent.resolve()}|{title}"
    return f"{artist_key}|{album_key}|{title}"


def _keep_score(path: Path, tags: dict[str, str | None]) -> tuple:
    """Higher score wins when choosing which duplicate to keep."""
    has_title = 1 if tags.get("title") else 0
    has_album = 1 if tags.get("album") else 0
    has_albumartist = 1 if tags.get("albumartist") else 0
    albumartist = tags.get("albumartist") or ""
    featuring_penalty = 0 if clean_album_artist(albumartist) == (albumartist or None) or not albumartist else -1
    copy_penalty = -1 if _COPY_SUFFIX.search(path.stem) else 0
    try:
        size = path.stat().st_size
    except OSError:
        size = 0
    # Prefer richer tags, non-copy names, larger files, then shorter path.
    return (has_title + has_album + has_albumartist + featuring_penalty + copy_penalty, size, -len(str(path)))


def find_duplicate_groups(files: Iterable[Path]) -> list[tuple[str, list[Path]]]:
    """
    Group duplicate songs by tag identity and by identical file content.

    Returns groups with at least two surviving paths.
    """
    identity_groups: dict[str, list[Path]] = defaultdict(list)
    hash_groups: dict[str, list[Path]] = defaultdict(list)
    tag_cache: dict[Path, dict[str, str | None]] = {}

    for path in files:
        tags = _read_tags(path)
        tag_cache[path] = tags
        key = _song_identity_key(path, tags)
        if key is not None:
            identity_groups[key].append(path)
        try:
            hash_groups[_file_fingerprint(path)].append(path)
        except OSError:
            continue

    merged: dict[frozenset[Path], str] = {}

    def add_group(paths: list[Path], reason: str) -> None:
        unique = sorted({p.resolve() for p in paths})
        if len(unique) < 2:
            return
        key = frozenset(unique)
        # Prefer a descriptive reason if already present.
        merged[key] = merged.get(key, reason)

    for key, paths in identity_groups.items():
        add_group(paths, f"same song ({key})")

    for digest, paths in hash_groups.items():
        add_group(paths, f"identical file ({digest[:12]})")

    # Expand overlapping groups so A~B and B~C become one set.
    # Simple union-find over path sets.
    parents: dict[Path, Path] = {}

    def find(item: Path) -> Path:
        parents.setdefault(item, item)
        while parents[item] != item:
            parents[item] = parents[parents[item]]
            item = parents[item]
        return item

    def union(a: Path, b: Path) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parents[rb] = ra

    reason_for: dict[Path, str] = {}
    for group, reason in merged.items():
        members = list(group)
        for member in members:
            reason_for[member] = reason
        for other in members[1:]:
            union(members[0], other)

    clusters: dict[Path, list[Path]] = defaultdict(list)
    for path in parents:
        clusters[find(path)].append(path)

    result: list[tuple[str, list[Path]]] = []
    for paths in clusters.values():
        if len(paths) < 2:
            continue
        ordered = sorted(paths)
        reason = reason_for.get(ordered[0], "duplicate song")
        result.append((reason, ordered))
    return result


def choose_file_to_keep(paths: list[Path]) -> Path:
    """Pick the preferred file to keep from a duplicate group."""
    scored = []
    for path in paths:
        tags = _read_tags(path)
        scored.append((_keep_score(path, tags), path))
    scored.sort(reverse=True)
    return scored[0][1]


def delete_duplicate_songs(
    files: Iterable[Path],
    *,
    dry_run: bool = False,
    on_progress: Optional[ProgressCallback] = None,
    progress_offset: int = 0,
    progress_total: int | None = None,
) -> list[DeleteResult]:
    """Delete duplicate song files, keeping one preferred copy per group."""
    file_list = list(files)
    groups = find_duplicate_groups(file_list)
    results: list[DeleteResult] = []
    deleted_paths: set[Path] = set()

    work_items: list[tuple[Path, Path, str]] = []
    for reason, paths in groups:
        keep = choose_file_to_keep(paths)
        for path in paths:
            if path.resolve() == keep.resolve():
                continue
            work_items.append((path, keep, reason))

    total = progress_total if progress_total is not None else progress_offset + len(work_items)
    for index, (path, keep, reason) in enumerate(work_items, start=1):
        if path.resolve() in deleted_paths:
            continue
        try:
            if not dry_run:
                path.unlink()
            deleted_paths.add(path.resolve())
            results.append(
                DeleteResult(
                    path=path,
                    kept=keep,
                    reason=reason,
                    deleted=not dry_run,
                )
            )
        except Exception as exc:  # noqa: BLE001
            results.append(
                DeleteResult(
                    path=path,
                    kept=keep,
                    reason=reason,
                    deleted=False,
                    error=str(exc),
                )
            )
        if on_progress is not None:
            on_progress(progress_offset + index, total, path)

    return results


def scan_music_folder(
    root: Path | str,
    *,
    dry_run: bool = False,
    on_progress: Optional[ProgressCallback] = None,
    remove_duplicates: bool = True,
    fix_filenames: bool = True,
) -> ScanReport:
    """
    Scan a music library folder (artist > album > mp3):

    1. Rename folders/files with incompatible special characters
    2. Rewrite Album Artist tags that include featuring credits
    3. Delete duplicate songs (same tags/title identity or identical files)

    If ``on_progress`` is provided it is called as
    ``on_progress(current_index, total, path)`` during work (1-based).
    """
    root_path = Path(root).expanduser().resolve()
    report = ScanReport()

    rename_entries = (
        [path for path in root_path.rglob("*") if not _is_ignored_path(path)]
        if fix_filenames
        else []
    )
    # Pre-count mp3s for progress; recount after renames for real work.
    preliminary_files = list_mp3_files(root_path)
    delete_budget = len(preliminary_files) if remove_duplicates else 0
    overall_total = max(len(rename_entries) + len(preliminary_files) + delete_budget, 1)
    progress_cursor = 0

    if fix_filenames:
        report.rename_results = rename_incompatible_paths(
            root_path,
            dry_run=dry_run,
            on_progress=on_progress,
            progress_offset=0,
            progress_total=overall_total,
        )
        progress_cursor = len(rename_entries)

    files = list_mp3_files(root_path)
    tag_total = len(files)

    for index, path in enumerate(files, start=1):
        report.tag_results.append(process_file(path, dry_run=dry_run))
        if on_progress is not None:
            on_progress(progress_cursor + index, overall_total, path)

    if not remove_duplicates:
        if on_progress is not None:
            on_progress(overall_total, overall_total, root_path)
        return report

    surviving = [path for path in files if path.exists()]
    report.delete_results = delete_duplicate_songs(
        surviving,
        dry_run=dry_run,
        on_progress=on_progress,
        progress_offset=progress_cursor + tag_total,
        progress_total=overall_total,
    )

    if on_progress is not None:
        on_progress(overall_total, overall_total, root_path)

    return report


def summarize(report: ScanReport | Iterable[FileResult]) -> dict[str, int]:
    """Count changed / skipped / errored / deleted / renamed files."""
    if isinstance(report, ScanReport):
        tag_results = report.tag_results
        delete_results = report.delete_results
        rename_results = report.rename_results
    else:
        tag_results = list(report)
        delete_results = []
        rename_results = []

    changed = skipped = errors = 0
    for result in tag_results:
        if result.error:
            errors += 1
        elif result.changed:
            changed += 1
        else:
            skipped += 1

    deleted = sum(1 for item in delete_results if item.error is None)
    delete_errors = sum(1 for item in delete_results if item.error is not None)
    renamed = sum(1 for item in rename_results if item.changed and item.error is None)
    rename_errors = sum(1 for item in rename_results if item.error is not None)
    return {
        "changed": changed,
        "skipped": skipped,
        "errors": errors + delete_errors + rename_errors,
        "deleted": deleted,
        "renamed": renamed,
        "total": len(tag_results),
    }


# Ensure EasyID3 recognizes albumartist (TPE2).
if "albumartist" not in EasyID3.valid_keys:
    EasyID3.RegisterTextKey("albumartist", "TPE2")
