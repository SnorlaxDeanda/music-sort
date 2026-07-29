"""Simple macOS-friendly GUI for the Album Artist cleaner."""

from __future__ import annotations

import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from .cleaner import scan_music_folder, summarize


class AlbumArtistCleanerApp:
    def __init__(self, root: tk.Tk, *, initial_folder: str | None = None, dry_run: bool = False):
        self.root = root
        self.root.title("Album Artist Cleaner")
        self.root.minsize(640, 420)
        self.folder_var = tk.StringVar(value=initial_folder or "")
        self.dry_run_var = tk.BooleanVar(value=dry_run)
        self._build()

    def _build(self) -> None:
        pad = {"padx": 12, "pady": 8}
        frame = ttk.Frame(self.root, padding=16)
        frame.pack(fill=tk.BOTH, expand=True)

        title = ttk.Label(
            frame,
            text="Album Artist Cleaner",
            font=("Helvetica", 18, "bold"),
        )
        title.pack(anchor=tk.W)

        subtitle = ttk.Label(
            frame,
            text=(
                "Scans artist > album > mp3 folders and rewrites Album Artist tags "
                "like “Artist A featuring Artist B” to “Artist A”."
            ),
            wraplength=600,
        )
        subtitle.pack(anchor=tk.W, pady=(4, 12))

        picker = ttk.Frame(frame)
        picker.pack(fill=tk.X, **pad)
        ttk.Label(picker, text="Music folder:").pack(side=tk.LEFT)
        entry = ttk.Entry(picker, textvariable=self.folder_var)
        entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(8, 8))
        ttk.Button(picker, text="Browse…", command=self._browse).pack(side=tk.LEFT)

        options = ttk.Frame(frame)
        options.pack(fill=tk.X, **pad)
        ttk.Checkbutton(
            options,
            text="Dry run (preview only — do not write tags)",
            variable=self.dry_run_var,
        ).pack(side=tk.LEFT)

        actions = ttk.Frame(frame)
        actions.pack(fill=tk.X, **pad)
        ttk.Button(actions, text="Scan & Clean", command=self._run).pack(side=tk.LEFT)
        ttk.Button(actions, text="Quit", command=self.root.destroy).pack(side=tk.RIGHT)

        self.log = tk.Text(frame, height=18, wrap=tk.WORD)
        self.log.pack(fill=tk.BOTH, expand=True, pady=(8, 0))
        self.log.configure(state=tk.DISABLED)

        self.status = ttk.Label(frame, text="Ready")
        self.status.pack(anchor=tk.W, pady=(8, 0))

    def _browse(self) -> None:
        chosen = filedialog.askdirectory(title="Choose music folder")
        if chosen:
            self.folder_var.set(chosen)

    def _append_log(self, text: str) -> None:
        self.log.configure(state=tk.NORMAL)
        self.log.insert(tk.END, text + "\n")
        self.log.see(tk.END)
        self.log.configure(state=tk.DISABLED)

    def _clear_log(self) -> None:
        self.log.configure(state=tk.NORMAL)
        self.log.delete("1.0", tk.END)
        self.log.configure(state=tk.DISABLED)

    def _run(self) -> None:
        folder = self.folder_var.get().strip()
        if not folder:
            messagebox.showwarning("Missing folder", "Choose a music folder first.")
            return

        path = Path(folder)
        if not path.is_dir():
            messagebox.showerror("Invalid folder", f"Not a directory:\n{path}")
            return

        dry_run = self.dry_run_var.get()
        self._clear_log()
        self.status.configure(text="Scanning…")
        self.root.update_idletasks()

        try:
            results = scan_music_folder(path, dry_run=dry_run)
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror("Scan failed", str(exc))
            self.status.configure(text="Failed")
            return

        for result in results:
            if result.error:
                self._append_log(f"ERROR  {result.path}: {result.error}")
            elif result.changed:
                prefix = "WOULD CHANGE" if dry_run else "CHANGED"
                self._append_log(f'{prefix}  {result.path}')
                self._append_log(f'  "{result.original}" -> "{result.cleaned}"')

        stats = summarize(results)
        mode = "Dry run" if dry_run else "Done"
        summary = (
            f"{mode}: {stats['changed']} changed, "
            f"{stats['skipped']} unchanged, "
            f"{stats['errors']} errors "
            f"({stats['total']} files)"
        )
        self._append_log("")
        self._append_log(summary)
        self.status.configure(text=summary)


def run_gui(*, initial_folder: str | None = None, dry_run: bool = False) -> int:
    root = tk.Tk()
    # Prefer a light native look on macOS when available.
    try:
        root.tk.call("tk", "scaling", 1.25)
    except tk.TclError:
        pass
    AlbumArtistCleanerApp(root, initial_folder=initial_folder, dry_run=dry_run)
    root.mainloop()
    return 0
