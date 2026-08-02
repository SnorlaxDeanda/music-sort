"""Command-line interface for Album Artist cleaner."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .cleaner import ScanReport, scan_music_folder, summarize


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="album-artist-cleaner",
        description=(
            "Scan a music folder (artist > album > mp3), fix incompatible "
            "filenames, rewrite Album Artist featuring tags, and delete duplicates."
        ),
    )
    parser.add_argument(
        "folder",
        nargs="?",
        help="Root music folder to scan. Omit to open the macOS folder picker GUI.",
    )
    parser.add_argument(
        "--dry-run",
        "-n",
        action="store_true",
        help="Show what would change/delete/rename without modifying files.",
    )
    parser.add_argument(
        "--quiet",
        "-q",
        action="store_true",
        help="Only print a summary (and errors).",
    )
    parser.add_argument(
        "--gui",
        action="store_true",
        help="Open the graphical folder picker interface.",
    )
    parser.add_argument(
        "--keep-duplicates",
        action="store_true",
        help="Do not delete duplicate songs.",
    )
    parser.add_argument(
        "--keep-filenames",
        action="store_true",
        help="Do not rename folders/files with incompatible special characters.",
    )
    parser.add_argument(
        "--json-progress",
        action="store_true",
        help="Emit JSON lines for progress/events (used by the macOS app UI).",
    )
    return parser


def print_results(report: ScanReport, *, quiet: bool = False, dry_run: bool = False) -> int:
    for result in report.rename_results:
        if result.error:
            print(f"ERROR  {result.original}: {result.error}", file=sys.stderr)
            continue
        if quiet:
            continue
        prefix = "WOULD RENAME" if dry_run else "RENAMED"
        print(f"{prefix}  {result.original.name} -> {result.renamed.name}")
        print(f"  {result.original} -> {result.renamed}")

    for result in report.tag_results:
        if result.error:
            print(f"ERROR  {result.path}: {result.error}", file=sys.stderr)
            continue
        if not result.changed or quiet:
            continue
        prefix = "WOULD CHANGE" if dry_run else "CHANGED"
        print(f"{prefix}  {result.path}")
        print(f'  "{result.original}" -> "{result.cleaned}"')

    for result in report.delete_results:
        if result.error:
            print(f"ERROR  {result.path}: {result.error}", file=sys.stderr)
            continue
        if quiet:
            continue
        prefix = "WOULD DELETE" if dry_run else "DELETED"
        print(f"{prefix}  {result.path}")
        print(f"  duplicate of {result.kept}")

    stats = summarize(report)
    mode = "Dry run" if dry_run else "Done"
    print(
        f"{mode}: {stats['changed']} changed, "
        f"{stats['renamed']} renamed, "
        f"{stats['deleted']} deleted, "
        f"{stats['skipped']} unchanged, "
        f"{stats['errors']} errors "
        f"({stats['total']} files)"
    )
    return 1 if stats["errors"] else 0


def emit(event: dict) -> None:
    sys.stdout.write(json.dumps(event, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def run_json_progress(
    folder: Path,
    *,
    dry_run: bool,
    remove_duplicates: bool,
    fix_filenames: bool,
) -> int:
    def on_progress(current: int, total: int, path: Path) -> None:
        emit(
            {
                "type": "progress",
                "current": current,
                "total": total,
                "path": str(path),
                "name": path.name,
            }
        )

    try:
        report = scan_music_folder(
            folder,
            dry_run=dry_run,
            on_progress=on_progress,
            remove_duplicates=remove_duplicates,
            fix_filenames=fix_filenames,
        )
    except NotADirectoryError as exc:
        emit({"type": "error", "message": str(exc)})
        return 2
    except Exception as exc:  # noqa: BLE001
        emit({"type": "error", "message": str(exc)})
        return 1

    events: list[dict] = []
    for result in report.rename_results:
        if result.error:
            events.append(
                {"kind": "error", "path": str(result.original), "message": result.error}
            )
        elif result.changed:
            events.append(
                {
                    "kind": "rename",
                    "path": str(result.original),
                    "renamed": str(result.renamed),
                }
            )

    for result in report.tag_results:
        if result.error:
            events.append({"kind": "error", "path": str(result.path), "message": result.error})
        elif result.changed:
            events.append(
                {
                    "kind": "change",
                    "path": str(result.path),
                    "original": result.original,
                    "cleaned": result.cleaned,
                }
            )

    for result in report.delete_results:
        if result.error:
            events.append({"kind": "error", "path": str(result.path), "message": result.error})
        else:
            events.append(
                {
                    "kind": "delete",
                    "path": str(result.path),
                    "kept": str(result.kept),
                }
            )

    stats = summarize(report)
    emit(
        {
            "type": "done",
            "dry_run": dry_run,
            "stats": stats,
            "events": events,
        }
    )
    return 1 if stats["errors"] else 0


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    remove_duplicates = not args.keep_duplicates
    fix_filenames = not args.keep_filenames

    if args.json_progress:
        if not args.folder:
            emit({"type": "error", "message": "folder is required with --json-progress"})
            return 2
        return run_json_progress(
            Path(args.folder),
            dry_run=args.dry_run,
            remove_duplicates=remove_duplicates,
            fix_filenames=fix_filenames,
        )

    if args.gui or not args.folder:
        from .gui import run_gui

        return run_gui(
            initial_folder=args.folder,
            dry_run=args.dry_run,
            remove_duplicates=remove_duplicates,
            fix_filenames=fix_filenames,
        )

    folder = Path(args.folder)
    try:
        report = scan_music_folder(
            folder,
            dry_run=args.dry_run,
            remove_duplicates=remove_duplicates,
            fix_filenames=fix_filenames,
        )
    except NotADirectoryError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    return print_results(report, quiet=args.quiet, dry_run=args.dry_run)


if __name__ == "__main__":
    raise SystemExit(main())
