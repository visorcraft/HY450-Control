# HY450 Control Owner Assistant

## Purpose

Make HY450 Control safer and more useful to everyday projector owners by turning its existing low-level controls into three guided workflows: compatibility and setup, reusable whole-projector routines, and portable backup/recovery. Add a sanitized diagnostic export for compatibility reports.

This is the first of three approved milestones. Cinema Lab and Playground+ are separate subsequent designs and are not part of this implementation. Owner Assistant targets v1.6.0 and preserves the SDK-only Java 8 build, Signal Console interface, existing control behavior, private local storage, and reversible Magisk installation.

## User experience

Add an **Owner Assistant** destination to the dashboard before the advanced OEM tools. The screen has four D-pad-friendly destinations:

1. **Setup check** — explain that the scan is read-only, then read every picture, audio, and source control. Show readable, writable, unavailable, and failed counts plus per-control errors. No setup step writes a setting.
2. **Routines** — provide Movie, Day, Night, and Custom slots. Each slot starts empty. **Save current** captures the current writable/readable controls across all three families; **Apply** restores that slot; **Undo previous apply** restores the values captured immediately before the most recent routine application. Empty slots cannot be applied. Saving an occupied slot and applying a routine require explicit confirmation.
3. **Backup & recovery** — export a fresh portable backup through Android's Storage Access Framework, import and preview a user-selected backup, and restore only after a separate confirmation. Import alone never writes projector settings. A preview identifies the backup type, creation time, firmware compatibility, included families/projection values, and any incomplete capture warnings.
4. **Diagnostic report** — export a sanitized JSON compatibility report through the Storage Access Framework. The report contains app version, Android/SDK and build fingerprint, scan results, and allowlisted control error summaries. It does not contain serial numbers, IP/MAC addresses, accounts, installed-app inventories, logcat, arbitrary system properties, or network data.

Setup completion and scan results may be remembered locally, but opening Owner Assistant never starts a scan, backup, restore, or routine automatically. There is no forced first-run wizard and no network access.

## Architecture and components

### Capability scan

Add a small `CapabilityReport` model and scanner around `ControlPageActivity.loadControls`. The scanner uses existing adapters and calls only their getters. It records each control's stable ID, family, writability, formatted value when readable, and a bounded error summary when reading fails. It does not probe unknown methods, attempt writes to discover support, or change the behavior of existing control pages.

### Routines

Add a `RoutineStore` using the same private-directory and atomic-file conventions as `PresetStore` and `JsonFileStore`. Use a versioned JSON document with four fixed slot IDs and stable control IDs grouped by family. Fixed slot names avoid requiring text entry with the remote; renaming is outside this milestone.

Capture only controls that are both writable and readable. Record unreadable/unavailable controls as warnings and omit their values. A slot with no captured values remains empty. Replacing a slot is atomic, so a failed capture or write leaves the previous slot intact.

Add a `RoutineEngine` that validates a complete routine before its first write and applies each present family through `ControlBatch`. Missing controls and read-only controls remain skipped; individual write failures follow existing continuation and capped-error behavior. Before the first write, atomically save a pre-apply snapshot of all currently readable/writable values in a separate undo document. Keep that snapshot after success, failure, process death, and reboot until a later routine apply replaces it. Undo is always user-initiated, uses the same validated batch path, reports partial failures, and never runs automatically on resume.

Routine application order is picture, audio, then source. The known firmware limitation affecting source-preset application remains an explicit per-control failure rather than being hidden. Routines do not include projection geometry, startup apps, sleep alarms, accessibility, package state, or framework settings.

### Portable backup and recovery

Define a new schema-1 `hy450-control-backup` JSON document containing:

- creation time and creating app version;
- the exact Android build fingerprint;
- current picture, audio, and source values for readable/writable controls;
- capture warnings for omitted controls;
- the existing allowlisted projection-backup values, preserving absent versus empty global values.

Use `ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT` with JSON MIME types and content URIs. Add a narrow stream/document abstraction so parsing, size limits, and I/O failures can be tested without a real document provider. Reject files larger than 1 MiB before parsing. Do not accept filesystem paths, arbitrary setting names, executable content, or additional JSON fields that imply writes outside the existing allowlists.

Validate the entire document, all control IDs and value types, adapter ranges, projection keys and values, and the exact firmware fingerprint before the first restore write. Unknown controls and unsupported schemas are errors for portable backups rather than silently ignored entries. A firmware mismatch permits inspection but disables restore. Incomplete backups remain restorable only after the preview clearly identifies omitted values and the user confirms.

Before restoring, capture a fresh private safety copy of the current portable state. Restore picture, audio, and source through `ControlBatch`, and projection through the existing validated `ProjectionBackup` path. Do not reset keystone during capture. Cancel pending OEM keystone recovery before projection restoration, as the existing projection flow does. Report applied/failed counts and preserve the safety copy for explicit recovery; never automatically roll back or restore on a later launch.

### Diagnostics

Reuse `CapabilityReport` for diagnostic output rather than maintaining a second scanner. Diagnostic generation is local and deterministic apart from timestamps. Export uses the same bounded stream helper as portable backups but cannot be imported as a backup.

## UI, accessibility, and errors

Reuse `Ui` tiles, chips, focus backgrounds, typography, semantic colors, and fixed status-feedback patterns. Every action must be reachable with the D-pad and have a visible focused state. Long compatibility or preview content belongs in scrollable, non-focusable text while actions remain reachable. BACK leaves the current screen without applying anything.

Confirmation screens state exactly what will change. Success requires zero failures; partial completion is a warning with applied/failed counts and capped details. Storage denial, cancellation, malformed JSON, oversized files, firmware mismatch, unsupported controls, and process recreation must leave live settings unchanged unless a confirmed apply/restore had already begun. No error is represented as success.

## Privacy and safety boundaries

No Internet permission, telemetry, arbitrary root shell, DRM changes, OTA behavior, or remote-control server is added. Backups and diagnostics are exported only to a destination chosen by the user. Original firmware-bound snapshots remain non-overwriting and are not replaced by routines or portable restores.

This milestone does not attempt remote-button remapping, boot-input writes, camera-based geometry, automatic wake, or cross-firmware restoration. It does not change Projectivy, accessibility services, disabled packages, Magisk modules other than installing/updating HY450 Control, or OEM framework files.

## Verification

Add focused host tests for routine selection/capture/apply/undo behavior, validation-before-write, continuation after failures, scan result modeling, and atomic store behavior. Add Android `app_process` tests using real `org.json` and fake control/value/document access for:

- portable backup round trips, absent versus empty projection globals, and legacy projection values;
- malformed, oversized, unknown-key, out-of-range, wrong-schema, and firmware-mismatched imports producing zero writes;
- incomplete-backup previews and explicit confirmation boundaries;
- routine persistence, pre-apply undo survival, and diagnostic field allowlisting;
- rendering Owner Assistant, routines, preview, and error states at the supported display size without live writes.

Run the complete host and Android suites, shell checks, APK build/signature/non-debuggable checks, and `git diff --check`. On the projector, first save the existing projection/global baseline and app state. Verify read-only scanning, all four routine slots, exact routine apply/undo, export/import/cancel paths, validation failures, process-death recovery of undo, and final restoration of the original baseline. Confirm Projectivy startup/accessibility, sleep-off state, the framework patch, and disabled packages remain unchanged.

Update README and relevant operator documentation only after behavior is verified. Release v1.6.0 only after the complete implementation and hardware verification pass; Cinema Lab and Playground+ then receive their own design, plan, and implementation cycles.
