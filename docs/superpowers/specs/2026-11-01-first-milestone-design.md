# HY450 Control first-milestone design

## Scope and outcome

Deliver the approved safety and usability pack without expanding into named presets, named input selection, startup-app changes, sleep-timer changes, diagnostics, remote remapping, boot-input writes, undocumented vendor APIs, or DRM features.

The milestone comprises:

- Clamp numeric and enum adjustments at their supported limits in both the control pages and quick overlay.
- Display readable values for vendor enums and booleans, with persistent operation/readback feedback in the control pages and overlay.
- Add per-family **Restore original values**, using the existing firmware-bound snapshots and continuing past unsupported or failed controls.
- Load picture, audio, and input pages independently; make read-only rows non-focusable and skip them during adjustment navigation.
- Add overlay BACK handling, visible feedback, and automatic dismissal.
- Hide Android system bars throughout the calibration toolkit.

## Approaches considered

1. **Targeted changes to the existing SDK-only Java app (selected).** Add default adapter display/writability behavior and a shared clamp operation, then update the existing activities and service. This preserves the build, privileged installation model, and D-pad UI with minimal regression surface.
2. **A larger control-page rewrite.** A reusable row/widget hierarchy could improve future presets and input selection, but would mix later-milestone design into this safety release and require substantially more device validation.
3. **A new UI toolkit.** Compose or another UI dependency would not fit the current no-Gradle, no-runtime-dependency build and is unnecessary for these changes.

## Control model

`ControlAdapter` remains the boundary between vendor APIs and the UI. It gains:

- `isWritable()`, defaulting to true and overridden by `ReadOnlyControl`;
- `format(int)`, defaulting to the integer string;
- a clamp helper that confines a requested value to `min()`/`max()` without wrapping.

`VendorBooleanControl` formats `0`/`1` as **Off**/**On**. `VendorEnumControl` formats the selected firmware enum as a readable label by removing the known Allwinner prefix, replacing underscores with spaces, and using title case; unknown values still fail readback rather than being guessed. Numeric controls retain numeric labels.

All actual writes continue through the adapters' existing range checks and write/readback/rollback behavior. Clamp only UI-generated adjustment requests; snapshot and preset writes must not silently change stored values.

## Control pages and family loading

`MainActivity` attempts picture, audio, and input catalog construction in separate exception boundaries. A failed family gets a disabled, visibly unavailable button and does not prevent later families or unrelated shortcuts from loading.

`ControlPageActivity` rebuilds its control list from the intent's family instead of relying on the static `pending` reference, so activity recreation does not depend on a surviving process. The start API carries only the title and family.

The page contains Save, Apply, and Restore actions, a persistent status line, and rows for all discovered controls. Actions and rows occupy the scrollable area; the status line is a fixed footer outside that area so feedback remains visible while any row is adjusted. Read-only rows are visible but cannot receive focus or adjustment keys. D-pad navigation skips them. At a limit, left/right/centre leaves the value unchanged and reports that the minimum or maximum was reached.

After every successful adjustment, the page re-reads and displays the actual adapter value. The status remains visible until the next operation rather than relying on a short toast. Failures identify the control and remain visible; long failure lists are capped with a remaining-count summary so the fixed footer cannot consume the control viewport. Actions are disabled when a family cannot load, and preset saving refuses an empty control set. Preset application skips read-only controls instead of attempting a write that must fail.

## Original-value restore

The existing `BackupStore` format and firmware fingerprint check remain unchanged. Initial snapshot creation records readable, writable controls individually so one unsupported control does not discard the whole family's backup. Existing snapshots are never overwritten.

Restore reads the current family's snapshot and attempts each matching writable control independently. Read-only controls are skipped. Missing, unsupported, out-of-range, and readback-failing writable entries are reported and do not stop later entries. The operation ends with persistent restored/failed counts and per-control failure details where needed. It then refreshes every row from hardware. A missing or firmware-mismatched snapshot is an explicit error and results in no partial interpretation of the file.

## Quick overlay

The existing service and buttons remain. Backlight changes clamp to 0–255. A status view shows the current formatted value after backlight/volume operations and displays errors instead of swallowing them.

BACK closes the overlay. User interaction rearms a 15-second auto-dismiss handler; all callbacks are removed and the window is detached safely on destruction. The service is not restarted stickily after process death, avoiding an unexpected overlay.

## Calibration

`CalibrationActivity` uses Android 14 window-insets APIs to hide status and navigation bars with transient swipe behavior, including after window focus returns. Existing patterns, D-pad switching, and BACK-to-exit behavior remain unchanged.

## Verification

The project has no test suite, so no test scaffolding is added. Verification uses:

- a clean `./build.sh` APK build and signature check;
- source/static inspection for clamp behavior, writable-row navigation, independent family loading, snapshot restore continuation, overlay cleanup, and immersive calibration;
- installation on the known HY450 Max through the existing reversible Magisk module procedure;
- real UI checks for readable labels, persistent feedback, disabled read-only behavior, BACK and timeout dismissal, and fullscreen calibration;
- non-destructive hardware write/readback/restore checks, finishing with the device's original values restored;
- confirmation that unrelated accessibility, Projectivy, and Magisk modules remain intact.

Generated APKs and build outputs stay untracked. No firmware, proprietary binaries, device identifiers, or signing material enter the public repository.
