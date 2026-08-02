"""macOS application GUI for the Album Artist cleaner."""

from __future__ import annotations

import queue
import sys
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from typing import Any

from . import __version__
from .cleaner import ScanReport, scan_music_folder, summarize


class AlbumArtistCleanerApp:
    def __init__(
        self,
        root: tk.Tk,
        *,
        initial_folder: str | None = None,
        dry_run: bool = False,
        remove_duplicates: bool = True,
    ):
        self.root = root
        self.root.title("Album Artist Cleaner")
        self.root.minsize(720, 540)
        self.folder_var = tk.StringVar(value=initial_folder or "")
        self.dry_run_var = tk.BooleanVar(value=dry_run)
        self.remove_duplicates_var = tk.BooleanVar(value=remove_duplicates)
        self.progress_var = tk.DoubleVar(value=0.0)
        self.progress_text_var = tk.StringVar(value="Idle")
        self._busy = False
        self._event_queue: queue.Queue[tuple[str, Any]] = queue.Queue()
        self._configure_window()
        self._build_menus()
        self._build()
        self.root.after(50, self._poll_events)
        self.root.protocol("WM_DELETE_WINDOW", self._quit)

    def _configure_window(self) -> None:
        try:
            self.root.createcommand("tk::mac::Quit", self._quit)
            self.root.createcommand("tk::mac::ShowPreferences", self._focus_options)
            self.root.createcommand("tk::mac::ShowHelp", self._show_about)
        except tk.TclError:
            pass

    def _build_menus(self) -> None:
        menubar = tk.Menu(self.root)

        file_menu = tk.Menu(menubar, tearoff=0)
        file_menu.add_command(label="Open Music Folder…", accelerator="Command-O", command=self._browse)
        file_menu.add_separator()
        file_menu.add_command(label="Scan & Clean", accelerator="Command-R", command=self._run)
        file_menu.add_separator()
        file_menu.add_command(label="Quit", accelerator="Command-Q", command=self._quit)
        menubar.add_cascade(label="File", menu=file_menu)

        help_menu = tk.Menu(menubar, tearoff=0)
        help_menu.add_command(label="About Album Artist Cleaner", command=self._show_about)
        menubar.add_cascade(label="Help", menu=help_menu)

        self.root.config(menu=menubar)
        self.root.bind_all("<Command-o>", lambda _event: self._browse())
        self.root.bind_all("<Command-r>", lambda _event: self._run())
        self.root.bind_all("<Command-q>", lambda _event: self._quit())

    def _build(self) -> None:
        pad = {"padx": 12, "pady": 8}
        frame = ttk.Frame(self.root, padding=20)
        frame.pack(fill=tk.BOTH, expand=True)

        title = ttk.Label(frame, text="Album Artist Cleaner", font=("Helvetica", 22, "bold"))
        title.pack(anchor=tk.W)

        subtitle = ttk.Label(
            frame,
            text=(
                "Choose a music library folder (artist → album → mp3). "
                "Cleans Album Artist featuring credits and deletes duplicate songs."
            ),
            wraplength=660,
        )
        subtitle.pack(anchor=tk.W, pady=(6, 16))

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
            text="Dry run (preview only — do not write tags or delete files)",
            variable=self.dry_run_var,
        )
        self.dry_run_check.pack(anchor=tk.W)

        self.duplicates_check = ttk.Checkbutton(
            options,
            text="Delete duplicate songs (keep one copy)",
            variable=self.remove_duplicates_var,
        )
        self.duplicates_check.pack(anchor=tk.W, pady=(4, 0))

        actions = ttk.Frame(frame)
        actions.pack(fill=tk.X, **pad)
        self.run_button = ttk.Button(actions, text="Scan & Clean", command=self._run)
        self.run_button.pack(side=tk.LEFT)
        self.quit_button = ttk.Button(actions, text="Quit", command=self._quit)
        self.quit_button.pack(side=tk.RIGHT)

        progress_frame = ttk.LabelFrame(frame, text="Progress", padding=10)
        progress_frame.pack(fill=tk.X, pady=(8, 0))
        self.progress = ttk.Progressbar(
            progress_frame,
            mode="determinate",
            maximum=100,
            variable=self.progress_var,
            length=400,
        )
        self.progress.pack(fill=tk.X)
        ttk.Label(progress_frame, textvariable=self.progress_text_var).pack(anchor=tk.W, pady=(6, 0))

        log_frame = ttk.LabelFrame(frame, text="Activity", padding=8)
        log_frame.pack(fill=tk.BOTH, expand=True, pady=(12, 0))
        self.log = tk.Text(log_frame, height=14, wrap=tk.WORD)
        self.log.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar = ttk.Scrollbar(log_frame, orient=tk.VERTICAL, command=self.log.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.log.configure(yscrollcommand=scrollbar.set, state=tk.DISABLED)

        self.status = ttk.Label(frame, text="Ready")
        self.status.pack(anchor=tk.W, pady=(10, 0))

    def _focus_options(self) -> None:
        self.dry_run_check.focus_set()

    def _show_about(self) -> None:
        messagebox.showinfo(
            "About Album Artist Cleaner",
            (
                f"Album Artist Cleaner {__version__}\n\n"
                "Cleans Album Artist featuring credits and deletes duplicate songs."
            ),
        )

    def _quit(self) -> None:
        if self._busy:
            if not messagebox.askyesno("Quit", "A scan is still running. Quit anyway?"):
                return
        self.root.destroy()

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
        self.duplicates_check.configure(state=state)

    def _set_progress(self, current: int, total: int, path: Path | None = None) -> None:
        if total <= 0:
            self.progress_var.set(100.0)
            self.progress_text_var.set("No MP3 files found")
            return
        self.progress_var.set((current / total) * 100.0)
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
        remove_duplicates = self.remove_duplicates_var.get()
        if remove_duplicates and not dry_run:
            if not messagebox.askyesno(
                "Delete duplicates?",
                "Duplicate songs will be permanently deleted (one copy kept).\n\nContinue?",
            ):
                return

        self._clear_log()
        self.progress_var.set(0.0)
        self.progress_text_var.set("Scanning for MP3 files…")
        self.status.configure(text="Working…")
        self._set_busy(True)
        threading.Thread(
            target=self._worker,
            args=(path, dry_run, remove_duplicates),
            daemon=True,
        ).start()

    def _worker(self, path: Path, dry_run: bool, remove_duplicates: bool) -> None:
        try:
            def on_progress(current: int, total: int, file_path: Path) -> None:
                self._event_queue.put(("progress", (current, total, file_path)))

            report = scan_music_folder(
                path,
                dry_run=dry_run,
                on_progress=on_progress,
                remove_duplicates=remove_duplicates,
            )
            self._event_queue.put(("done", (report, dry_run)))
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
                    report, dry_run = payload
                    self._finish(report, dry_run=dry_run)
                elif kind == "error":
                    messagebox.showerror("Scan failed", payload)
                    self.progress_text_var.set("Failed")
                    self.status.configure(text="Failed")
                    self._set_busy(False)
        except queue.Empty:
            pass
        self.root.after(50, self._poll_events)

    def _finish(self, report: ScanReport, *, dry_run: bool) -> None:
        total = report.files_scanned
        self._set_progress(max(total, 1), max(total, 1))

        for result in report.tag_results:
            if result.error:
                self._append_log(f"ERROR  {result.path}: {result.error}")
            elif result.changed:
                prefix = "WOULD CHANGE" if dry_run else "CHANGED"
                self._append_log(f"{prefix}  {result.path}")
                self._append_log(f'  "{result.original}" -> "{result.cleaned}"')

        for result in report.delete_results:
            if result.error:
                self._append_log(f"ERROR  {result.path}: {result.error}")
            else:
                prefix = "WOULD DELETE" if dry_run else "DELETED"
                self._append_log(f"{prefix}  {result.path}")
                self._append_log(f"  duplicate of {result.kept}")

        stats = summarize(report)
        mode = "Dry run" if dry_run else "Done"
        summary = (
            f"{mode}: {stats['changed']} changed, "
            f"{stats['deleted']} deleted, "
            f"{stats['skipped']} unchanged, "
            f"{stats['errors']} errors "
            f"({stats['total']} files)"
        )
        self._append_log("")
        self._append_log(summary)
        self.progress_text_var.set(
            "No MP3 files found" if total == 0 else f"Finished — {total} file{'s' if total != 1 else ''}"
        )
        self.status.configure(text=summary)
        self._set_busy(False)


def run_gui(
    *,
    initial_folder: str | None = None,
    dry_run: bool = False,
    remove_duplicates: bool = True,
) -> int:
    # Bundled runtime includes Tcl/Tk — use Tk directly (no system Tk needed).
    root = tk.Tk()
    root.title("Album Artist Cleaner")
    try:
        root.tk.call("tk", "scaling", 1.25)
    except tk.TclError:
        pass

    AlbumArtistCleanerApp(
        root,
        initial_folder=initial_folder,
        dry_run=dry_run,
        remove_duplicates=remove_duplicates,
    )

    if sys.platform == "darwin":
        try:
            root.createcommand(
                "tkAboutDialog",
                lambda: messagebox.showinfo(
                    "About Album Artist Cleaner",
                    (
                        f"Album Artist Cleaner {__version__}\n\n"
                        "Cleans Album Artist featuring credits and deletes duplicate songs."
                    ),
                ),
            )
        except tk.TclError:
            pass

    try:
        root.lift()
        root.attributes("-topmost", True)
        root.after(300, lambda: root.attributes("-topmost", False))
        root.focus_force()
    except tk.TclError:
        pass

    root.mainloop()
    return 0
