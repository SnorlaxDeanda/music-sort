"""Command-line interface for Album Artist cleaner."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .cleaner import scan_music_folder, summarize


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="album-artist-cleaner",
        description=(
            "Scan a music folder (artist > album > mp3) and rewrite Album Artist "
            "tags that include featured artists, e.g. "
            "'Artist A featuring Artist B' -> 'Artist A'."
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
        help="Show what would change without writing tags.",
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
    return parser


def print_results(results, *, quiet: bool = False, dry_run: bool = False) -> int:
    for result in results:
        if result.error:
            print(f"ERROR  {result.path}: {result.error}", file=sys.stderr)
            continue
        if not result.changed:
            continue
        if quiet:
            continue
        prefix = "WOULD CHANGE" if dry_run else "CHANGED"
        print(f'{prefix}  {result.path}')
        print(f'  "{result.original}" -> "{result.cleaned}"')

    stats = summarize(results)
    mode = "Dry run" if dry_run else "Done"
    print(
        f"{mode}: {stats['changed']} changed, "
        f"{stats['skipped']} unchanged, "
        f"{stats['errors']} errors "
        f"({stats['total']} files)"
    )
    return 1 if stats["errors"] else 0


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    if args.gui or not args.folder:
        from .gui import run_gui

        return run_gui(initial_folder=args.folder, dry_run=args.dry_run)

    folder = Path(args.folder)
    try:
        results = scan_music_folder(folder, dry_run=args.dry_run)
    except NotADirectoryError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    return print_results(results, quiet=args.quiet, dry_run=args.dry_run)


if __name__ == "__main__":
    raise SystemExit(main())
