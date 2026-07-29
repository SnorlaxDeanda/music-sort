"""Simple macOS-friendly GUI for the Album Artist cleaner."""

from __future__ import annotations

import queue
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from typing import Any

from .cleaner import FileResult, scan_music_folder, summarize


class AlbumArtistCleanerApp:
    def __init__(self, root: tk.Tk, *, initial_folder: str | None = None, dry_run: bool = False):
        self.root = root
        self.root.title("Album Artist Cleaner")
        self.root.minsize(680, 480)
        self.folder_var = tk.StringVar(value=initial_folder or "")
        self.dry_run_var = tk.BooleanVar(value=dry_run)
        self.progress_var = tk.DoubleVar(value=0.0)
        self.progress_text_var = tk.StringVar(value="Idle")
        self._busy = False
        self._event_queue: queue.Queue[tuple[str, Any]] = queue.Queue()
        self._build()
        self.root.after(50, self._poll_events)

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
            wraplength=640,
        )
        subtitle.pack(anchor=tk.W, pady=(4, 12))

        picker = ttk.Frame(frame)
        picker.pack(fill=tk.X, **pad)
        ttk.Label(picker, text="Music folder:").pack(side=tk.LEFT)
        self.folder_entry = ttk.Entry(picker, textvariable=self.folder_var)
        self.folder_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(8, 8))
        self.browse_button = ttk.Button(picker, text="Browse…", command=self._browse)
        self.browse_button.pack(side=tk.LEFT)

        options = ttk.Frame(frame)
        options.pack(fill=tk.X, **pad)
        self.dry_run_check = ttk.Checkbutton(
            options,
            text="Dry run (preview only — do not write tags)",
            variable=self.dry_run_var,
        )
        self.dry_run_check.pack(side=tk.LEFT)

        actions = ttk.Frame(frame)
        actions.pack(fill=tk.X, **pad)
        self.run_button = ttk.Button(actions, text="Scan & Clean", command=self._run)
        self.run_button.pack(side=tk.LEFT)
        self.quit_button = ttk.Button(actions, text="Quit", command=self.root.destroy)
        self.quit_button.pack(side=tk.RIGHT)

        progress_frame = ttk.Frame(frame)
        progress_frame.pack(fill=tk.X, pady=(4, 0))
        ttk.Label(progress_frame, text="Progress").pack(anchor=tk.W)
        self.progress = ttk.Progressbar(
            progress_frame,
            mode="determinate",
            maximum=100,
            variable=self.progress_var,
        )
        self.progress.pack(fill=tk.X, pady=(4, 0))
        ttk.Label(progress_frame, textvariable=self.progress_text_var).pack(anchor=tk.W, pady=(4, 0))

        self.log = tk.Text(frame, height=16, wrap=tk.WORD)
        self.log.pack(fill=tk.BOTH, expand=True, pady=(12, 0))
        self.log.configure(state=tk.DISABLED)

        self.status = ttk.Label(frame, text="Ready")
        self.status.pack(anchor=tk.W, pady=(8, 0))

    def _browse(self) -> None:
        if self._busy:
            return
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

    def _set_busy(self, busy: bool) -> None:
        self._busy = busy
        state = tk.DISABLED if busy else tk.NORMAL
        self.run_button.configure(state=state)
        self.browse_button.configure(state=state)
        self.folder_entry.configure(state=state)
        self.dry_run_check.configure(state=state)

    def _set_progress(self, current: int, total: int, path: Path | None = None) -> None:
        if total <= 0:
            self.progress_var.set(100.0)
            self.progress_text_var.set("No MP3 files found")
            return

        percent = (current / total) * 100.0
        self.progress_var.set(percent)
        name = path.name if path is not None else ""
        suffix = f" — {name}" if name else ""
        self.progress_text_var.set(f"Processing {current} of {total}{suffix}")

    def _run(self) -> None:
        if self._busy:
            return

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
        self.progress_var.set(0.0)
        self.progress_text_var.set("Scanning for MP3 files…")
        self.status.configure(text="Working…")
        self._set_busy(True)

        thread = threading.Thread(
            target=self._worker,
            args=(path, dry_run),
            daemon=True,
        )
        thread.start()

    def _worker(self, path: Path, dry_run: bool) -> None:
        try:
            results: list[FileResult] = []

            def on_progress(current: int, total: int, file_path: Path) -> None:
                self._event_queue.put(("progress", (current, total, file_path)))

            results = scan_music_folder(path, dry_run=dry_run, on_progress=on_progress)
            self._event_queue.put(("done", (results, dry_run)))
        except Exception as exc:  # noqa: BLE001
            self._event_queue.put(("error", str(exc)))

    def _poll_events(self) -> None:
        try:
            while True:
                kind, payload = self._event_queue.get_nowait()
                if kind == "progress":
                    current, total, file_path = payload
                    self._set_progress(current, total, file_path)
                elif kind == "done":
                    results, dry_run = payload
                    self._finish(results, dry_run=dry_run)
                elif kind == "error":
                    messagebox.showerror("Scan failed", payload)
                    self.progress_text_var.set("Failed")
                    self.status.configure(text="Failed")
                    self._set_busy(False)
        except queue.Empty:
            pass
        self.root.after(50, self._poll_events)

    def _finish(self, results: list[FileResult], *, dry_run: bool) -> None:
        total = len(results)
        self._set_progress(total, total)

        for result in results:
            if result.error:
                self._append_log(f"ERROR  {result.path}: {result.error}")
            elif result.changed:
                prefix = "WOULD CHANGE" if dry_run else "CHANGED"
                self._append_log(f"{prefix}  {result.path}")
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
        if total == 0:
            self.progress_text_var.set("No MP3 files found")
        else:
            self.progress_text_var.set(f"Finished — {total} file{'s' if total != 1 else ''}")
        self.status.configure(text=summary)
        self._set_busy(False)


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
