"""Native macOS Cocoa GUI (no Tk required)."""

from __future__ import annotations

import threading
import traceback
from pathlib import Path
from typing import Any

import objc
from AppKit import (
    NSAlert,
    NSApplication,
    NSApplicationActivationPolicyRegular,
    NSBackingStoreBuffered,
    NSBezelStyleRounded,
    NSButton,
    NSButtonTypeSwitch,
    NSMakeRect,
    NSObject,
    NSOpenPanel,
    NSProgressIndicator,
    NSScrollView,
    NSTextField,
    NSTextView,
    NSWindow,
    NSWindowStyleMaskClosable,
    NSWindowStyleMaskMiniaturizable,
    NSWindowStyleMaskResizable,
    NSWindowStyleMaskTitled,
)
from PyObjCTools import AppHelper

from .cleaner import ScanReport, scan_music_folder, summarize


class AppController(NSObject):
    folderField = objc.ivar()
    dryRunButton = objc.ivar()
    duplicatesButton = objc.ivar()
    progress = objc.ivar()
    progressLabel = objc.ivar()
    logView = objc.ivar()
    statusLabel = objc.ivar()
    scanButton = objc.ivar()
    browseButton = objc.ivar()
    window = objc.ivar()
    busy = objc.ivar()

    def initWithOptions_(self, options: dict[str, Any]):
        self = objc.super(AppController, self).init()
        if self is None:
            return None
        self.busy = False
        self._initial_folder = options.get("initial_folder") or ""
        self._initial_dry_run = bool(options.get("dry_run"))
        self._initial_remove_duplicates = bool(options.get("remove_duplicates", True))
        self._build_ui()
        return self

    def _build_ui(self) -> None:
        style = (
            NSWindowStyleMaskTitled
            | NSWindowStyleMaskClosable
            | NSWindowStyleMaskMiniaturizable
            | NSWindowStyleMaskResizable
        )
        window = NSWindow.alloc().initWithContentRect_styleMask_backing_defer_(
            NSMakeRect(0, 0, 760, 620),
            style,
            NSBackingStoreBuffered,
            False,
        )
        window.setTitle_("Album Artist Cleaner")
        window.center()
        content = window.contentView()

        title = NSTextField.labelWithString_("Album Artist Cleaner")
        title.setFont_(title.font().withSize_(22))
        title.setFrame_(NSMakeRect(20, 560, 720, 30))
        content.addSubview_(title)

        subtitle = NSTextField.wrappingLabelWithString_(
            "Choose a music library folder (artist → album → mp3). "
            "Cleans Album Artist featuring credits and deletes duplicate songs."
        )
        subtitle.setFrame_(NSMakeRect(20, 510, 720, 44))
        content.addSubview_(subtitle)

        folder_label = NSTextField.labelWithString_("Music folder:")
        folder_label.setFrame_(NSMakeRect(20, 470, 100, 24))
        content.addSubview_(folder_label)

        self.folderField = NSTextField.alloc().initWithFrame_(NSMakeRect(120, 470, 500, 24))
        self.folderField.setStringValue_(self._initial_folder)
        self.folderField.setPlaceholderString_("Select a folder…")
        content.addSubview_(self.folderField)

        self.browseButton = NSButton.alloc().initWithFrame_(NSMakeRect(630, 466, 110, 30))
        self.browseButton.setTitle_("Browse…")
        self.browseButton.setBezelStyle_(NSBezelStyleRounded)
        self.browseButton.setTarget_(self)
        self.browseButton.setAction_("browse:")
        content.addSubview_(self.browseButton)

        self.dryRunButton = NSButton.alloc().initWithFrame_(NSMakeRect(20, 430, 520, 24))
        self.dryRunButton.setButtonType_(NSButtonTypeSwitch)
        self.dryRunButton.setTitle_("Dry run (preview only — do not write tags or delete files)")
        self.dryRunButton.setState_(1 if self._initial_dry_run else 0)
        content.addSubview_(self.dryRunButton)

        self.duplicatesButton = NSButton.alloc().initWithFrame_(NSMakeRect(20, 400, 420, 24))
        self.duplicatesButton.setButtonType_(NSButtonTypeSwitch)
        self.duplicatesButton.setTitle_("Delete duplicate songs (keep one copy)")
        self.duplicatesButton.setState_(1 if self._initial_remove_duplicates else 0)
        content.addSubview_(self.duplicatesButton)

        self.scanButton = NSButton.alloc().initWithFrame_(NSMakeRect(20, 355, 130, 32))
        self.scanButton.setTitle_("Scan & Clean")
        self.scanButton.setBezelStyle_(NSBezelStyleRounded)
        self.scanButton.setTarget_(self)
        self.scanButton.setAction_("scan:")
        content.addSubview_(self.scanButton)

        progress_box_label = NSTextField.labelWithString_("Progress")
        progress_box_label.setFrame_(NSMakeRect(20, 320, 100, 20))
        content.addSubview_(progress_box_label)

        self.progress = NSProgressIndicator.alloc().initWithFrame_(NSMakeRect(20, 295, 720, 16))
        self.progress.setIndeterminate_(False)
        self.progress.setMinValue_(0.0)
        self.progress.setMaxValue_(100.0)
        self.progress.setDoubleValue_(0.0)
        content.addSubview_(self.progress)

        self.progressLabel = NSTextField.labelWithString_("Idle")
        self.progressLabel.setFrame_(NSMakeRect(20, 270, 720, 20))
        content.addSubview_(self.progressLabel)

        activity_label = NSTextField.labelWithString_("Activity")
        activity_label.setFrame_(NSMakeRect(20, 240, 100, 20))
        content.addSubview_(activity_label)

        scroll = NSScrollView.alloc().initWithFrame_(NSMakeRect(20, 50, 720, 185))
        scroll.setHasVerticalScroller_(True)
        scroll.setBorderType_(2)  # NSBezelBorder
        self.logView = NSTextView.alloc().initWithFrame_(scroll.bounds())
        self.logView.setEditable_(False)
        self.logView.setAutoresizingMask_(18)  # width + height
        scroll.setDocumentView_(self.logView)
        content.addSubview_(scroll)

        self.statusLabel = NSTextField.labelWithString_("Ready")
        self.statusLabel.setFrame_(NSMakeRect(20, 18, 720, 20))
        content.addSubview_(self.statusLabel)

        self.window = window
        window.setDelegate_(self)
        window.makeKeyAndOrderFront_(None)

    def windowWillClose_(self, _notification) -> None:
        NSApplication.sharedApplication().terminate_(None)

    def browse_(self, _sender) -> None:
        if self.busy:
            return
        panel = NSOpenPanel.openPanel()
        panel.setCanChooseFiles_(False)
        panel.setCanChooseDirectories_(True)
        panel.setAllowsMultipleSelection_(False)
        panel.setMessage_("Choose your music folder")
        if panel.runModal():
            urls = panel.URLs()
            if urls:
                self.folderField.setStringValue_(urls[0].path())

    def scan_(self, _sender) -> None:
        if self.busy:
            return

        folder = str(self.folderField.stringValue() or "").strip()
        if not folder:
            self._alert("Missing folder", "Choose a music folder first.")
            return

        path = Path(folder)
        if not path.is_dir():
            self._alert("Invalid folder", f"Not a directory:\n{path}")
            return

        dry_run = bool(self.dryRunButton.state())
        remove_duplicates = bool(self.duplicatesButton.state())

        if remove_duplicates and not dry_run:
            if not self._confirm(
                "Delete duplicates?",
                "Duplicate songs will be permanently deleted (one copy kept).\n\nContinue?",
            ):
                return

        self._clear_log()
        self.progress.setDoubleValue_(0.0)
        self.progressLabel.setStringValue_("Scanning for MP3 files…")
        self.statusLabel.setStringValue_("Working…")
        self._set_busy(True)

        def worker() -> None:
            try:
                def on_progress(current: int, total: int, file_path: Path) -> None:
                    AppHelper.callAfter(self._update_progress, current, total, file_path)

                report = scan_music_folder(
                    path,
                    dry_run=dry_run,
                    on_progress=on_progress,
                    remove_duplicates=remove_duplicates,
                )
                AppHelper.callAfter(self._finish, report, dry_run)
            except Exception as exc:  # noqa: BLE001
                AppHelper.callAfter(self._fail, str(exc), traceback.format_exc())

        threading.Thread(target=worker, daemon=True).start()

    def _alert(self, title: str, message: str) -> None:
        alert = NSAlert.alloc().init()
        alert.setMessageText_(title)
        alert.setInformativeText_(message)
        alert.addButtonWithTitle_("OK")
        alert.runModal()

    def _confirm(self, title: str, message: str) -> bool:
        alert = NSAlert.alloc().init()
        alert.setMessageText_(title)
        alert.setInformativeText_(message)
        alert.addButtonWithTitle_("Continue")
        alert.addButtonWithTitle_("Cancel")
        return alert.runModal() == 1000  # NSAlertFirstButtonReturn

    def _set_busy(self, busy: bool) -> None:
        self.busy = busy
        enabled = not busy
        self.scanButton.setEnabled_(enabled)
        self.browseButton.setEnabled_(enabled)
        self.folderField.setEditable_(enabled)
        self.dryRunButton.setEnabled_(enabled)
        self.duplicatesButton.setEnabled_(enabled)

    def _clear_log(self) -> None:
        self.logView.setString_("")

    def _append_log(self, text: str) -> None:
        current = str(self.logView.string() or "")
        self.logView.setString_(current + text + "\n")
        self.logView.scrollRangeToVisible_((len(self.logView.string()), 0))

    def _update_progress(self, current: int, total: int, path: Path) -> None:
        if total <= 0:
            self.progress.setDoubleValue_(100.0)
            self.progressLabel.setStringValue_("No MP3 files found")
            return
        percent = (current / total) * 100.0
        self.progress.setDoubleValue_(percent)
        self.progressLabel.setStringValue_(f"Processing {current} of {total} — {path.name}")

    def _fail(self, message: str, details: str) -> None:
        self.progressLabel.setStringValue_("Failed")
        self.statusLabel.setStringValue_("Failed")
        self._append_log(f"ERROR: {message}")
        if details:
            self._append_log(details)
        self._set_busy(False)
        self._alert("Scan failed", message)

    def _finish(self, report: ScanReport, dry_run: bool) -> None:
        total = report.files_scanned
        self.progress.setDoubleValue_(100.0)

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
        if total == 0:
            self.progressLabel.setStringValue_("No MP3 files found")
        else:
            self.progressLabel.setStringValue_(
                f"Finished — {total} file{'s' if total != 1 else ''}"
            )
        self.statusLabel.setStringValue_(summary)
        self._set_busy(False)


def run_cocoa_gui(
    *,
    initial_folder: str | None = None,
    dry_run: bool = False,
    remove_duplicates: bool = True,
) -> int:
    app = NSApplication.sharedApplication()
    app.setActivationPolicy_(NSApplicationActivationPolicyRegular)
    controller = AppController.alloc().initWithOptions_(
        {
            "initial_folder": initial_folder,
            "dry_run": dry_run,
            "remove_duplicates": remove_duplicates,
        }
    )
    # Keep a Python reference so the controller is not collected.
    app._album_artist_cleaner_controller = controller  # type: ignore[attr-defined]
    app.activateIgnoringOtherApps_(True)
    AppHelper.runEventLoop()
    return 0
