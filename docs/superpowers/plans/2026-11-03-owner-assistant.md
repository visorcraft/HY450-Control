# Owner Assistant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add read-only setup diagnostics, undoable whole-projector routines, and validated portable backup/recovery for everyday HY450 Max owners.

**Architecture:** Keep hardware access behind the existing `ControlAdapter`, `ControlBatch`, and `ProjectionBackup` boundaries. Add small capture/validation engines and private atomic stores, then connect them to D-pad activities using Android's Storage Access Framework. No UI action bypasses validation or writes during import/scanning.

**Tech Stack:** Java 8, Android SDK 34, `org.json`, Android Storage Access Framework, plain-JVM host tests, Android `app_process` tests, SDK-only APK build.

## Global Constraints

- Implement `docs/superpowers/specs/2026-11-03-owner-assistant-design.md`; Cinema Lab and Playground+ are not in this plan.
- Preserve Java 8, SDK 34, Signal Console styling, existing control behavior, and the no-third-party-runtime build.
- Add no Internet permission, telemetry, arbitrary root command, cross-firmware restore, or automatic restore/rollback.
- Capture only writable/readable values accepted by the adapter's writable domain; never clamp captured values.
- Validate an entire imported document before its first write; reject inputs over 1 MiB by reading at most 1 MiB + 1 byte.
- Use `rtk`-prefixed shell commands and commits authored as `visorcraft <noreply@visorcraft.local>`.
- Each implementation subagent must use non-interactive `mmx text chat` for a focused design or code review, evaluate the feedback rather than blindly applying it, and include the consultation result in its handoff.

---

## File Structure

- `ControlAdapter.java`: add writable-domain membership without changing clamping behavior.
- `CapabilityReport.java`: read-only scan model and allowlisted diagnostic JSON.
- `RoutineEngine.java`: fixed-family capture, validation, apply, and undo-snapshot behavior.
- `RoutineStore.java`: four routine slots and one persistent undo document.
- `BoundedDocument.java`: bounded UTF-8 stream reads and complete stream writes.
- `PortableBackup.java`: schema-1 backup capture, preview, and complete validation.
- `OwnerBackupStore.java`: atomic private pre-restore safety copy.
- `RestoreEngine.java`: validated family/projection restore with no automatic rollback.
- `OwnerAssistantActivity.java`: Owner Assistant menu, setup scan, and diagnostic export.
- `RoutinesActivity.java`: slot save/apply/undo and confirmations.
- `BackupRecoveryActivity.java`: backup export/import/preview/restore/recovery.
- `MainActivity.java`, `AndroidManifest.xml`: navigation and private activity declarations.
- `tests/HostTests.java`, `tests/DeviceTests.java`, `tests/run-host.sh`, `tests/run-device.sh`: focused regressions.

### Task 1: Writable-domain checks and capability diagnostics

**Files:**
- Modify: `src/com/visorcraft/hy450control/ControlAdapter.java`
- Create: `src/com/visorcraft/hy450control/CapabilityReport.java`
- Modify: `tests/HostTests.java`, `tests/DeviceTests.java`, `tests/run-host.sh`, `tests/run-device.sh`

**Interfaces:**
- Produces: `default boolean ControlAdapter.accepts(int value)`; `static CapabilityReport CapabilityReport.scan(Map<String,List<Control>> families)`; `JSONObject CapabilityReport.toDiagnosticJson(String appVersion, String fingerprint, int sdk, String createdUtc)`.
- `CapabilityReport.entries` is an immutable `List<Entry>` in scan order. `CapabilityReport.Entry` exposes `family`, `id`, `title`, `writable`, `readable`, `value`, and `error` for UI and diagnostics.

- [ ] **Step 1: Write failing host tests for domain membership and read-only scanning**

Add a fake adapter whose range is 0–100 and whose `read()` can return 105 or throw. Test `accepts(-1) == false`, `accepts(0) == true`, `accepts(100) == true`, and `accepts(101) == false`. Scan picture controls containing a valid value, an out-of-range value, a read failure, and a read-only control. Assert that scanning performs zero writes, preserves control order, marks the out-of-range value readable, and bounds error text to 160 characters.

```java
Map<String, List<Control>> families = new LinkedHashMap<>();
families.put("picture", Arrays.asList(valid, outOfRange, broken, readOnly));
CapabilityReport report = CapabilityReport.scan(families);
check(report.entries.get(0).readable, "valid value must be readable");
check("105".equals(report.entries.get(1).value), "scan must report actual vendor value");
check(report.entries.get(2).error.length() <= 160, "scan errors must be bounded");
check(fake.writeCalls == 0, "capability scan must not write");
```

- [ ] **Step 2: Add failing Android diagnostic-allowlist tests**

Serialize a report and assert that its `type` is `hy450-control-diagnostic`, `schema` is 1, and the JSON contains app/firmware/SDK/timestamp/control entries but no keys named `serial`, `ip`, `mac`, `accounts`, `packages`, `logcat`, or `properties`.

```java
JSONObject json = report.toDiagnosticJson("1.6.0", "test-fingerprint", 34, "2026-11-03T00:00:00Z");
check(json.getString("type").equals("hy450-control-diagnostic"), "diagnostic type");
check(json.getInt("schema") == 1, "diagnostic schema");
String encoded = json.toString();
check(!encoded.contains("serial") && !encoded.contains("logcat"), "diagnostic allowlist");
```

- [ ] **Step 3: Run the tests and confirm the new symbols are missing**

Run: `rtk ./tests/run-host.sh`
Expected: FAIL during compilation because `accepts` and `CapabilityReport` do not exist. Do not treat an unrelated existing failure as the expected failure.

- [ ] **Step 4: Implement the minimal domain and report model**

Add the default method without changing `clamp`:

```java
default boolean accepts(int value) {
    return value >= min() && value <= max();
}
```

Implement immutable `CapabilityReport.Entry` values and a scanner that calls only `control.adapter.read()`, `format()`, and `isWritable()`. Catch read/format failures per control, use `Errors.describe`, truncate errors to 160 UTF-16 code units, and continue. Build diagnostic JSON only from explicit fields; do not serialize arbitrary exception or system maps.

- [ ] **Step 5: Run both suites and commit**

Run: `rtk ./tests/run-host.sh`
Expected: `Host tests passed` and capture-metadata checks pass.
Run: `rtk ./tests/run-device.sh 192.168.1.x:5555`
Expected: `Device tests passed`.
Commit: `rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" commit -am "Add capability diagnostics"` after staging the new source file.

### Task 2: Routine capture, application, persistence, and undo

**Files:**
- Create: `src/com/visorcraft/hy450control/RoutineEngine.java`, `src/com/visorcraft/hy450control/RoutineStore.java`
- Modify: `tests/HostTests.java`, `tests/DeviceTests.java`, `tests/run-host.sh`, `tests/run-device.sh`

**Interfaces:**
- Consumes: `ControlAdapter.accepts`, `ControlBatch.apply`.
- Produces: `static Capture RoutineEngine.capture(Map<String,List<Control>> families)`; `static void RoutineEngine.validate(Map<String,List<Control>> families, Map<String,Integer> values)`; `static Result RoutineEngine.apply(Map<String,List<Control>> families, Map<String,Integer> values)`. `Capture` exposes immutable `values`/`warnings`; `Result` exposes `applied`/`failures`.
- Produces: `RoutineStore(File directory)` and `RoutineStore(Context context)`; `saveSlot(String slot, Capture capture)`, `readSlot(String slot)`, `saveUndo(Capture capture)`, and `readUndo()`. Read methods return `Capture`; an absent slot or undo returns `null`.
- Values use keys `family + ":" + control.id`. Family order is exactly `picture`, `audio`, `source`; slot IDs are exactly `movie`, `day`, `night`, `custom`.

- [ ] **Step 1: Write failing host tests for capture and validation-before-write**

Use one family with values `10`, `105`, and a throwing getter. Assert capture stores only `picture:first`, emits two warnings, and never clamps 105 to 100. Add an invalid stored value and assert `apply` throws before incrementing any fake write counter.

```java
RoutineEngine.Capture capture = RoutineEngine.capture(families);
check(capture.values.get("picture:first") == 10, "valid routine value");
check(!capture.values.containsKey("picture:outside"), "out-of-range capture must be omitted");
check(capture.warnings.size() == 2, "capture omissions must be visible");
Map<String, Integer> invalid = new HashMap<>(capture.values);
invalid.put("picture:second", 105);
expectThrows(() -> RoutineEngine.apply(families, invalid), "invalid routine");
check(fake.writeCalls == 0, "validation must precede every routine write");
```

Add a three-control apply test where the middle write fails; assert later controls run and the result has two applied/one failure.

- [ ] **Step 2: Implement `RoutineEngine` and run host tests**

Implement capture with `isWritable()`, `read()`, and `accepts()`. Implement `validate` by resolving every key against the supplied family/control maps, rejecting unknown, read-only, missing, or out-of-domain entries before returning. Implement apply only after validation, delegating each present family to `ControlBatch` in fixed order and aggregating counts/failures.

Run: `rtk ./tests/run-host.sh`
Expected: `Host tests passed`.

- [ ] **Step 3: Write failing Android store round-trip tests**

Test all four slots, rejection of a fifth slot, atomic replacement, malformed JSON, and independent undo persistence. Verify that saving a slot does not modify undo and that saving undo does not modify a slot.

```java
RoutineStore store = new RoutineStore(testDirectory);
store.saveSlot("movie", capture);
store.saveUndo(capture);
check(store.readSlot("movie").values.equals(capture.values), "slot round trip");
check(store.readUndo().values.equals(capture.values), "undo round trip");
expectThrows(() -> store.saveSlot("other", capture), "unknown slot");
```

- [ ] **Step 4: Implement the schema-1 atomic store**

Store `routines.json` with a `slots` object and `routine-undo.json` separately under a caller-provided private directory. Include schema, values, and warnings. Reuse `JsonFileStore.write`; read back with strict slot/schema/type checks and reject unknown keys. Constructor overloads must accept `Context` for production and `File` for tests.

- [ ] **Step 5: Run both suites and commit**

Run both standard suites with the explicit projector serial. Expected: both pass. Stage the two new files and test runners, then commit with message `Add undoable routine engine`.

### Task 3: Bounded documents and portable-backup validation

**Files:**
- Create: `src/com/visorcraft/hy450control/BoundedDocument.java`, `src/com/visorcraft/hy450control/PortableBackup.java`
- Modify: `src/com/visorcraft/hy450control/ProjectionBackup.java`, `tests/HostTests.java`, `tests/DeviceTests.java`, both test runners

**Interfaces:**
- Produces: `static byte[] BoundedDocument.read(InputStream input)` and `static void BoundedDocument.write(OutputStream output, byte[] value)`.
- Produces: `static JSONObject PortableBackup.capture(Map<String,List<Control>> families, ProjectionBackup.ValueAccess access, String appVersion, String fingerprint, String createdUtc)`; `static Preview PortableBackup.preview(JSONObject root, String fingerprint)`; and `static Validated PortableBackup.validate(JSONObject root, Map<String,List<Control>> families, String fingerprint)`. `Preview` exposes `summary`, `firmwareMatches`, and `warnings`; `Validated` exposes `values`, `projection`, `warnings`, `createdUtc`, and `appVersion`.
- Backup root fields are exactly `type`, `schema`, `createdUtc`, `appVersion`, `firmware`, `families`, `warnings`, and `projection`; `type` is `hy450-control-backup` and `schema` is 1. `families` contains family objects mapping unqualified control IDs to integers, `warnings` is an array of strings, and `projection` is the existing projection values object.

- [ ] **Step 1: Write failing bounded-stream host tests**

Use streams of exactly 1 MiB and 1 MiB + 1 byte. Assert the first round-trips UTF-8 and the second throws before JSON parsing. Use a stream whose reported available length is zero to prove the limit does not trust metadata.

```java
byte[] maximum = new byte[1024 * 1024];
check(BoundedDocument.read(new ByteArrayInputStream(maximum)).length == maximum.length,
        "exact maximum must be accepted");
expectThrows(() -> BoundedDocument.read(new ByteArrayInputStream(new byte[1024 * 1024 + 1])),
        "oversized document");
```

- [ ] **Step 2: Implement `BoundedDocument` and run host tests**

Read chunks into a `ByteArrayOutputStream` only while total size is at most 1 MiB; throw `IllegalStateException("Backup exceeds 1 MiB")` on the first excess byte. Close is owned by the caller. `write` writes all bytes and flushes without closing the provider stream.

- [ ] **Step 3: Write failing Android backup tests**

Cover a complete round trip; absent/empty/non-empty projection globals; wrong type/schema/firmware; unknown family/control; non-integer values; out-of-domain values; malformed projection keys; and oversized input. For every invalid document, assert fake adapter/property/global write counts remain zero. Include a vendor getter returning 105 and assert capture omits it with a warning rather than producing an invalid backup.

```java
PortableBackup.Validated valid = PortableBackup.validate(root, families, "test-fingerprint");
check(valid.values.get("picture:backlight") == 100, "portable value");
check(valid.projection.isNull("global:GsInitXYZ"), "absent global must survive");
JSONObject mismatch = new JSONObject(root.toString());
mismatch.put("firmware", "other-firmware");
expectThrows(() -> PortableBackup.validate(mismatch, families, "test-firmware"),
        "firmware mismatch");
check(access.writes == 0, "validation must not write projection state");
```

- [ ] **Step 4: Implement capture, preview, and strict validation**

Capture routines through `RoutineEngine.capture`, projection through `ProjectionBackup.captureValues`, and explicit metadata arguments. `preview` returns type/schema/time/firmware compatibility/warnings without calling an adapter write. `validate` checks the exact root allowlist, all nested shapes, known control IDs, `accepts(value)`, and `ProjectionBackup.validateValues` before constructing `Validated`. Add a package-visible `ProjectionBackup.restoreValues(Context, JSONObject)` wrapper around the existing Android access path for Task 4; do not change schema-1 projection behavior.

- [ ] **Step 5: Run both suites and commit**

Expected: all old and new tests pass. Commit message: `Add validated portable backup documents`.

### Task 4: Pre-restore safety copies and restore execution

**Files:**
- Create: `src/com/visorcraft/hy450control/OwnerBackupStore.java`, `src/com/visorcraft/hy450control/RestoreEngine.java`
- Modify: `tests/DeviceTests.java`, `tests/run-device.sh`

**Interfaces:**
- Consumes: `PortableBackup.validate`, `RoutineEngine.apply`, and `ProjectionBackup.restoreValues`.
- Produces: `OwnerBackupStore(Context context)` and `OwnerBackupStore(File directory)`; `void saveSafety(JSONObject root)` and `JSONObject readSafety()`. An absent safety copy returns `null`.
- Produces: `static Result RestoreEngine.restore(JSONObject root, Map<String,List<Control>> families, String fingerprint, ProjectionRestore projectionRestore)`. `Result` exposes `applied` and immutable `failures`.
- `ProjectionRestore.restore(JSONObject projection) throws Exception` lets production cancel OEM recovery immediately before projection writes and lets tests count calls.

- [ ] **Step 1: Write failing safety-copy and restore tests**

Verify safety JSON survives store recreation and a failed second save leaves the first file intact. Verify a family failure continues to later families, projection restore runs after source, a projection exception is reported, and no automatic second restore occurs. Pass a malformed root and a wrong fingerprint to `RestoreEngine.restore` and assert both invoke zero family or projection writes.

```java
RestoreEngine.Result result = RestoreEngine.restore(root, families, "test-fingerprint", projection -> {
    order.add("projection");
    ProjectionBackup.restoreValues(projection, access);
});
check(order.equals(Arrays.asList("picture", "audio", "source", "projection")),
        "restore order");
check(result.failures.size() == 1, "partial failure must be visible");
```

- [ ] **Step 2: Implement private safety storage and restore orchestration**

`OwnerBackupStore` stores one schema-validated `pre-restore-backup.json` atomically. `RestoreEngine.restore` first calls `PortableBackup.validate`, then `RoutineEngine.apply`, then invokes `ProjectionRestore.restore` once with the validated projection object. Return applied count plus bounded family/projection failures; never retry or roll back automatically.

- [ ] **Step 3: Run the Android suite and commit**

Run: `rtk ./tests/run-device.sh 192.168.1.x:5555`
Expected: `Device tests passed`.
Run the host suite to prove no regression, then commit with message `Add portable restore recovery engine`.

### Task 5: D-pad Owner Assistant UI and Storage Access Framework

**Files:**
- Create: `src/com/visorcraft/hy450control/OwnerAssistantActivity.java`, `src/com/visorcraft/hy450control/RoutinesActivity.java`, `src/com/visorcraft/hy450control/BackupRecoveryActivity.java`
- Modify: `src/com/visorcraft/hy450control/MainActivity.java`, `AndroidManifest.xml`, `src/com/visorcraft/hy450control/Ui.java` only if a shared builder is reused by at least two screens
- Test: `tests/DeviceTests.java` for non-hardware preview/confirmation state and rendering helpers

**Interfaces:**
- Consumes: all public/package interfaces from Tasks 1–4.
- No new hardware interface is introduced in Activities.

- [ ] **Step 1: Add failing UI-state tests**

Test that an empty slot disables Apply, an invalid/mismatched import disables Restore, BACK/cancel leaves fake write counters at zero, and a persisted undo/safety document enables the corresponding recovery action after recreation. Render preview and error helpers at 1920×1080 using the device suite's non-live context; assert text is non-empty and no hardware fake was called.

- [ ] **Step 2: Build Owner Assistant navigation and scan/diagnostic flows**

Add the dashboard tile before Advanced/OEM tools and declare all three activities `android:exported="false"`. Owner Assistant shows Setup check, Routines, Backup & recovery, and Diagnostic report. Scan only after explicit activation, display counts/details, and export diagnostics using `ACTION_CREATE_DOCUMENT` with MIME `application/json`. Process `onActivityResult` cancellation without writes and close streams in `finally`/try-with-resources.

- [ ] **Step 3: Build routine flows**

Show Movie/Day/Night/Custom slots and empty/populated status. Confirm overwrite and apply with `AlertDialog`; show undo-capture warnings in the confirmation. Save through `RoutineStore`, save undo before the first apply write, and keep Undo available after recreation. Refresh visible values after apply/undo and use success only for zero failures.

- [ ] **Step 4: Build backup/recovery flows**

Use request codes local to the activity for create/open document. Export a freshly captured backup; import only into memory and show `PortableBackup.preview`. Before confirmed restore, capture and atomically save a safety copy, show omissions, then call `RestoreEngine` with:

```java
projection -> OemFocus.cancelAndRun(this,
        () -> ProjectionBackup.restoreValues(this, projection))
```

Recover pre-restore state only after preview/confirmation and never replace the safety copy during recovery. Import, SAF cancellation, BACK, malformed files, and firmware mismatch must not invoke a write.

- [ ] **Step 5: Build and exercise UI on hardware**

Run: `rtk ./build.sh`
Expected: APK builds, signature verifies, and no `application-debuggable` flag.
Install with `rtk ./install.sh 192.168.1.x:5555` and reboot only after the normal module readback checks. Verify every destination and confirmation with the D-pad, including font scale 100% and 130%, then restore 100%. Test SAF cancel and real export/import using a user-chosen temporary document. No test may accept an OEM OTA or alter unrelated settings.

- [ ] **Step 6: Commit the verified UI**

Commit message: `Add Owner Assistant workflows`.

### Task 6: Full verification, documentation, and v1.6.0 release preparation

**Files:**
- Modify: `README.md`, `module/module.prop`, relevant private operator docs only after public behavior is verified
- Modify: `docs/superpowers/specs/2026-11-03-owner-assistant-design.md` with an implementation-outcome section

- [ ] **Step 1: Run complete automated verification**

Run: `rtk ./tests/run-host.sh`, `rtk ./tests/run-device.sh 192.168.1.x:5555`, `rtk bash -n build.sh install.sh uninstall.sh tests/run-host.sh tests/run-device.sh`, `rtk ./build.sh`, and `rtk git diff --check`.
Expected: every command exits zero; both test suites print their pass messages.

- [ ] **Step 2: Verify hardware safety end to end**

Back up the complete projection/global baseline and current app state under the private archive with a new `hy450control-1.6.0-validation` directory. Exercise read-only scanning, all four routine slots, apply/undo, process-death undo, backup export/import/cancel, invalid/firmware-mismatched imports with zero changes, pre-restore recovery, and final baseline restoration. Verify Projectivy startup/accessibility, sleep-off state, framework patch hash, and disabled-package set remain unchanged.

- [ ] **Step 3: Update documentation and version together**

Document setup scan, routines/undo, portable backup validation/recovery, diagnostic privacy, new tests, and hardware evidence. Set `version=1.6.0` and `versionCode=12`; do not claim checks that were not run. Update private operator docs before any public sanitized mirror, and never commit private backups or device identifiers.

- [ ] **Step 4: Request final review before publishing**

Run `rtk git status --short` and ensure only intended source/docs changes are present. Commit message: `Release Owner Assistant 1.6.0`. Obtain the user's explicit push/tag/release approval before pushing, tagging `v1.6.0`, or creating a GitHub release. Then start separate brainstorming/design cycles for Cinema Lab and Playground+.
