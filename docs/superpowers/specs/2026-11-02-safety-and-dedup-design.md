# HY450 Control safety fixes and targeted de-duplication

## Purpose

Correct the projection-backup, keystone-recovery, and preset-application defects found in peer review; disable debugging in production APKs; add focused regression tests; and then reduce duplication in storage, vendor adapters, and secondary-screen UI construction. Preserve the current SDK-only Java 8 build, Signal Console appearance, device compatibility, and backup-first safety model.

This change does not redesign the UI, change the version, publish a release, or address unrelated review suggestions.

## Correctness design

### Projection backup

Keep projection backup schema 1 so existing backups remain readable. When a `Settings.Global` value is absent, store `JSONObject.NULL` instead of passing Java `null` to `JSONObject.put`, which would omit the key. Empty strings and non-empty strings remain unchanged.

During restore, distinguish an explicit null from a string. Restore an absent global by deleting its row through the `Settings.Global` content URI and verify that readback is null. Restore empty and non-empty strings with `Settings.Global.putString` as before. System-property behavior remains unchanged.

Introduce a narrow value-access seam around global/property reads and writes so backup and restore logic can be exercised with fake values on Android without changing real settings. Production callers retain firmware validation and private-file storage.

### Keystone snapshots and OEM-focus recovery

Use strict parsing for the eight display insets and two zoom values in `Keystone.read()`. Empty, missing, malformed, or non-finite values fail the snapshot instead of becoming zero or 100. Existing decimal-compatible integer parsing may be retained, but conversion failures must be reported. Screen-size fallback for scaling writes is unrelated and remains unchanged.

Before launching OEM focus, synchronously save the exact snapshot and a pending-restore marker in private `SharedPreferences`. Persistence must complete before `startActivity`; a launch failure clears the pending state. Remove reliance on process-static snapshot survival.

On return to MainActivity or ProjectionActivity, load any pending snapshot and retain the existing immediate, 400 ms, and 1200 ms restore passes to accommodate the OEM exit race. After the final pass, strictly read and compare the display insets and zoom values. Clear the pending marker only when they match. Keep the marker after failure so a later resume can retry, and surface the error.

Explicit cancellation from manual keystone, Square the image, full-screen reset, or a new OEM-focus launch must cancel callbacks and clear both persisted and in-memory recovery state. No unrelated settings are stored in these preferences.

### Preset application

Apply every matching writable preset entry independently. Skip read-only controls and missing IDs as before. Continue after unsupported values, vendor failures, and readback mismatches. Refresh all control rows afterward.

Report the number applied and failed, with the existing capped per-control detail format. Report success only when there are no failures. Saving behavior and stored preset schema remain unchanged. Original-value restore semantics remain unchanged.

### Production debugging

Set `android:debuggable="false"` explicitly in the manifest. Extend APK metadata verification to reject output containing `application-debuggable`, so both local release verification and CI can detect a regression.

## Regression-test design

Add a small test harness under `tests/` without Gradle or third-party libraries. Generated test output goes under `tests/build/`, which is ignored.

Host-side plain-JVM tests compile against Android platform 34 and cover:

- Integer, boolean, and enum adapter success, bounds rejection, initial-read failure, setter failure, readback mismatch with rollback, and rollback failure using fake vendor managers.
- Batch application continuing after a middle-control failure, skipping read-only and missing entries, and returning accurate applied/failed counts and details.
- Strict keystone parsing through an injectable property-read seam, including valid decimal-compatible inset values, missing values, malformed values, and non-finite zoom.
- Shared atomic file-helper round trips, replacement, missing-file failures, and read/write failures using temporary directories.

Device-side `app_process` tests use Android's real `org.json` implementation and fake value access to cover:

- Projection round trips for absent, empty, and non-empty globals.
- Clearing a global that was created after an absent-value backup.
- Existing schema-1 backup compatibility.
- Empty, populated, and malformed JSON handling without hardware writes.

The harness must use temporary private test paths and fake stores, never live projection properties or globals. CI runs host tests and the non-debuggable APK assertion. Device-side tests are run through an explicit adb serial.

## De-duplication design

Refactor only after the corrected behavior has passing regression coverage.

### Storage

Add one package-private text-file helper for complete reads and same-directory temporary-write, `fsync`, close, and rename. BackupStore, PresetStore, and ProjectionBackup retain their own filenames, safe-name rules, JSON schemas, and firmware checks. BackupStore also rejects an existing snapshot inside its own write method rather than relying solely on the Activity's pre-check.

### Vendor adapters

Extract the shared bounds-check, original-read, raw-write, readback, rollback, and mismatch sequence into a small package-private base class. Integer, boolean, and enum adapters implement only their type-specific reads and writes. Preserve public constructor signatures needed by callers, range behavior, enum labels, and exception reporting.

Remove `ControlAdapter.isAvailable()` and its unused implementations. Add one backlight adapter factory used by ControlCatalog, MainActivity, and OverlayService so method names and bounds cannot drift.

### UI builders

Move the duplicated section, note, chip-row, and add-chip factories from AutomationActivity and ProjectionActivity into `Ui`. Reuse them from MainActivity and ControlPageActivity where parameters already match. Do not alter spacing, focus behavior, wording, colors, or navigation.

## Verification

Run the focused host tests, device-side fake-data tests, all shell syntax checks, the standard `./build.sh`, APK signature/metadata verification, and `git diff --check`. Confirm no generated outputs are tracked.

On the projector, first verify installation and save/read original projection state. Exercise OEM-focus return normally and after killing the HY450 Control process while the OEM activity is open; confirm exact keystone recovery and pending-marker cleanup. Verify explicit cancellation does not later restore stale state. Apply a preset with a deliberate fake failure in tests rather than risking an invalid hardware input. Confirm Projectivy startup/accessibility and existing framework patches remain untouched.

Documentation that describes backup, preset, testing, or release-debugging behavior must be updated to match the final implementation. The design does not require a version bump, tag, or GitHub release.

## Implementation outcome

Implemented and reviewed through `cb20e75c116db0c77c08140081ce2f00f1842416`. Host tests, Android fake-data tests, shell checks, and the signed/non-debuggable build passed. On hardware, ordinary OEM recovery, process death followed by explicit app recreation, and immediate manual-keystone cancellation all restored or preserved exact projection values and cleared pending recovery. Final restoration matched the complete original baseline; Projectivy startup/accessibility, sleep-off state, and the framework patch remained unchanged.
