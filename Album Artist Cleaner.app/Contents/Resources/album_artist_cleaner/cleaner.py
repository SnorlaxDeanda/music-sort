"""Core logic for cleaning Album Artist tags that include featured artists."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from collections.abc import Callable, Iterable, Iterator
from typing import Optional

from mutagen.easyid3 import EasyID3
from mutagen.id3 import ID3, ID3NoHeaderError

ProgressCallback = Callable[[int, int, Path], None]

# Matches common "featuring" forms used in album/artist credits.
# Examples matched:
#   featuring / feat / feat. / ft / ft. / w/ / with
FEATURE_PATTERN = re.compile(
    r"""
    \s*                                  # optional whitespace before separator
    (?:
        [\(\[]\s*                        # optional opening paren/bracket
    )?
    (?:
        (?:
            featuring
            | feat\.?
            | ft\.?
            | with
        )\b
        | w/                             # "w/" has no word boundary after "/"
    )
    """,
    re.IGNORECASE | re.VERBOSE,
)

# Trailing junk left after stripping a parenthetical feature credit.
_TRAILING_PARENS = re.compile(r"\s*[\(\[\{]\s*[\)\]\}]\s*$")
_MULTI_SPACE = re.compile(r"\s{2,}")

AUDIO_EXTENSIONS = {".mp3"}


@dataclass(frozen=True)
class FileResult:
    """Outcome of processing a single audio file."""

    path: Path
    original: str | None
    cleaned: str | None
    changed: bool
    skipped: bool
    error: str | None = None


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


def _iter_mp3_files(root: Path) -> Iterator[Path]:
    for path in sorted(root.rglob("*")):
        if path.is_file() and path.suffix.lower() in AUDIO_EXTENSIONS:
            yield path


def list_mp3_files(root: Path | str) -> list[Path]:
    """Return sorted MP3 paths under a music folder."""
    root_path = Path(root).expanduser().resolve()
    if not root_path.is_dir():
        raise NotADirectoryError(f"Not a directory: {root_path}")
    return list(_iter_mp3_files(root_path))


def _read_album_artist(path: Path) -> str | None:
    try:
        audio = EasyID3(path)
    except ID3NoHeaderError:
        return None
    except Exception:
        # Fall back to raw ID3 for odd files.
        try:
            tags = ID3(path)
        except Exception:
            return None
        frame = tags.get("TPE2")
        if frame is None:
            return None
        return str(frame)

    values = audio.get("albumartist")
    if not values:
        return None
    return values[0]


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


def scan_music_folder(
    root: Path | str,
    *,
    dry_run: bool = False,
    on_progress: Optional[ProgressCallback] = None,
) -> list[FileResult]:
    """
    Scan a music library folder (artist > album > mp3) and clean Album Artist tags.

    Only MP3 files are processed. Nested depth is not enforced so slight
    variations in folder layout still work.

    If ``on_progress`` is provided it is called as
    ``on_progress(current_index, total, path)`` after each file is processed
    (``current_index`` is 1-based).
    """
    files = list_mp3_files(root)
    total = len(files)
    results: list[FileResult] = []
    for index, path in enumerate(files, start=1):
        results.append(process_file(path, dry_run=dry_run))
        if on_progress is not None:
            on_progress(index, total, path)
    return results


def summarize(results: Iterable[FileResult]) -> dict[str, int]:
    """Count changed / skipped / errored files."""
    changed = skipped = errors = 0
    for result in results:
        if result.error:
            errors += 1
        elif result.changed:
            changed += 1
        else:
            skipped += 1
    return {"changed": changed, "skipped": skipped, "errors": errors, "total": changed + skipped + errors}


# Ensure EasyID3 recognizes albumartist (TPE2).
if "albumartist" not in EasyID3.valid_keys:
    EasyID3.RegisterTextKey("albumartist", "TPE2")
