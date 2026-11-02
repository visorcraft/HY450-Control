# Safety Fixes and Targeted De-duplication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Correct projection backup, keystone recovery, preset application, and release debugging; add focused regression tests; then de-duplicate storage, vendor adapters, and UI builders without changing behavior.

**Architecture:** Keep the existing SDK-only Java 8 application. Add narrow seams for fake value/property/control access, use private SharedPreferences for durable OEM-focus recovery, and extract small package-private helpers only after regression coverage exists.

**Tech Stack:** Java 8, Android SDK 34, Android build-tools 35.0.0, Bash, adb/app_process, GitHub Actions.

## Completion outcome

All seven tasks and the final whole-branch review completed at `cb20e75c116db0c77c08140081ce2f00f1842416`. Host tests, Android fake-data tests, shell syntax checks, and the signed/non-debuggable build passed. The candidate was installed through the reversible Magisk module; ordinary OEM recovery, process death followed by explicit app recreation, manual-keystone cancellation, exact original-state restoration, and the refactored UI were verified on hardware. Projectivy startup/accessibility, sleep-off state, the framework patch, and the disabled-package set remained unchanged. No version bump, tag, or release was created.

## Global Constraints

- Follow `docs/superpowers/specs/2026-11-02-safety-and-dedup-design.md` exactly.
- Preserve schema-1 backup/preset compatibility and the existing Signal Console UI.
- No Gradle, third-party libraries, hardware writes in automated fake-data tests, version bump, push, tag, or release.
- Prefix shell commands with `rtk`; use an explicit adb serial for device commands.
- Commit locally with `git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" commit ...`.
- Generated output belongs under ignored `build/`, `tests/build/`, or existing ignored module output paths.

---

### Task 1: Preserve absent projection globals

**Files:**
- Modify: `src/com/visorcraft/hy450control/ProjectionBackup.java`
- Create: `tests/DeviceTests.java`
- Create: `tests/run-device.sh`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `static JSONObject captureValues(ValueAccess access)`, `static void restoreValues(JSONObject values, ValueAccess access)`, and `static JSONObject readBackup(File source)` in ProjectionBackup.
- `ValueAccess` provides `getGlobal`, `putGlobal`, `getProperty`, and `putProperty`, each taking/returning String as appropriate and declaring `throws Exception`.

- [x] **Step 1: Write the failing device regression test**

Create a package `com.visorcraft.hy450control` test main with a map-backed ValueAccess. Its core assertions must be:

```java
Map<String, String> globals = new HashMap<>();
FakeAccess access = new FakeAccess(globals, new HashMap<>());
JSONObject values = ProjectionBackup.captureValues(access);
check(values.has("global:GsInitXYZ"), "absent key must remain present");
check(values.isNull("global:GsInitXYZ"), "absence must be JSON null");
globals.put("GsInitXYZ", "created-later");
ProjectionBackup.restoreValues(values, access);
check(!globals.containsKey("GsInitXYZ"), "restore must clear a newly created global");
globals.put("JbCamX", "");
values = ProjectionBackup.captureValues(access);
globals.put("JbCamX", "changed");
ProjectionBackup.restoreValues(values, access);
check("".equals(globals.get("JbCamX")), "empty string must survive round trip");
```

Add a schema-1 `readBackup` test using a temporary file, `Build.FINGERPRINT`, and JSON text; assert malformed JSON and firmware mismatch throw exceptions. Fake `putGlobal(key, null)` must remove the map key, while `getGlobal` returns null for absence.

- [x] **Step 2: Run the test and verify the absent-value failure**

Create `tests/run-device.sh` with `set -euo pipefail`, required `${1:?usage: run-device.sh <adb-serial>}`, SDK resolution matching build.sh, javac/d8 compilation into `tests/build/device`, and an adb push/run/cleanup trap. Run:

```bash
rtk ./tests/run-device.sh <projector-ip>:5555
```

Expected: compilation fails because `captureValues`, `restoreValues`, `readBackup`, and `ValueAccess` do not yet exist. Add `tests/build/` to `.gitignore`.

- [x] **Step 3: Implement value access and explicit JSON null handling**

Refactor ProjectionBackup so production save/restore delegates to these exact methods:

```java
interface ValueAccess {
    String getGlobal(String key) throws Exception;
    void putGlobal(String key, String value) throws Exception;
    String getProperty(String key) throws Exception;
    void putProperty(String key, String value) throws Exception;
}

static JSONObject captureValues(ValueAccess access) throws Exception {
    JSONObject values = new JSONObject();
    for (String key : GLOBAL) {
        String value = access.getGlobal(key);
        values.put("global:" + key, value == null ? JSONObject.NULL : value);
    }
    for (String key : PROPERTIES) values.put("property:" + key, access.getProperty(key));
    return values;
}
```

In `restoreValues`, use `Object stored = values.get(storedKey)`. For globals, map `JSONObject.NULL` to Java null, call `putGlobal`, and compare null-safe readback. For properties, reject JSONObject.NULL and preserve string write/readback. Move schema/firmware parsing into `readBackup(File)`; keep `save(Context)` and `restore(Context)` signatures unchanged.

- [x] **Step 4: Run device tests and the standard build**

```bash
rtk ./tests/run-device.sh <projector-ip>:5555
rtk ./build.sh
```

Expected: `Device tests passed`, a successfully signed APK, and no live global/property writes from the test.

- [x] **Step 5: Commit**

```bash
rtk git add .gitignore src/com/visorcraft/hy450control/ProjectionBackup.java tests/DeviceTests.java tests/run-device.sh
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" commit -m "Preserve absent values in projection backups"
```

### Task 2: Strict keystone snapshots and durable OEM recovery

**Files:**
- Modify: `src/com/visorcraft/hy450control/Keystone.java`
- Modify: `src/com/visorcraft/hy450control/OemFocus.java`
- Modify: `src/com/visorcraft/hy450control/ProjectionActivity.java`
- Modify: `src/com/visorcraft/hy450control/RectangleActivity.java`
- Create: `tests/HostTests.java`
- Create: `tests/run-host.sh`

**Interfaces:**
- Produces: `interface PropertyReader { String get(String key) throws Exception; }`, `static Snapshot read(PropertyReader reader)`, and `boolean Snapshot.matches(Snapshot other)`.
- Changes: `OemFocus.cancelRestore(Context context)` replaces the no-argument method.

- [x] **Step 1: Write failing strict-parser tests**

Create a host main harness with `check` and `expectThrows` helpers. Use a map-backed PropertyReader containing all eight insets and both zoom keys. Test valid integer and decimal-compatible inset strings, then remove one key, set another to `bad`, and set zoom to `NaN` and `Infinity`:

```java
Keystone.Snapshot snapshot = Keystone.read(validReader);
check(snapshot.inset[0] == 12 && snapshot.zoomH == 100f, "valid snapshot");
expectThrows(() -> Keystone.read(missingReader), "missing inset");
expectThrows(() -> Keystone.read(malformedReader), "malformed inset");
expectThrows(() -> Keystone.read(nanReader), "NaN zoom");
expectThrows(() -> Keystone.read(infiniteReader), "infinite zoom");
```

`tests/run-host.sh` must resolve SDK platform 34, compile the listed production classes and HostTests into `tests/build/host`, and run with `java -cp "$PLATFORM:$CLASSES" com.visorcraft.hy450control.HostTests`.

- [x] **Step 2: Verify test failure**

```bash
rtk ./tests/run-host.sh
```

Expected: compilation fails because `read(PropertyReader)` and `matches` do not exist.

- [x] **Step 3: Implement strict reading**

Keep `static Snapshot read()` as the production entry point, delegating to a reflective PropertyReader. In `read(PropertyReader)`, read all eight DISPLAY properties and both zoom properties through the reader. Trim input, retain decimal truncation for insets, and throw on null, empty, or malformed values. Reject `Float.isNaN` and `Float.isInfinite`. Implement `Snapshot.matches` using `Arrays.equals(inset, other.inset)` and `Float.compare` for both zoom values.

- [x] **Step 4: Implement persistent recovery**

Use private preferences named `oem_focus`, keys `pending`, `inset_0` through `inset_7`, `zoom_h`, and `zoom_v`. In `launch`, cancel prior recovery, strictly read, and synchronously `commit()` the complete snapshot before `startActivity`; throw if commit returns false. On launch failure, clear recovery and show `Errors.describe(error)`.

In `onResume`, return when no marker exists. Load every key strictly, cancel old callbacks without clearing preferences, and schedule restore at 0, 400, and 1200 ms. After the third apply, require `saved.matches(Keystone.read())` before clearing the marker. Keep the marker and show an error on failure. `cancelRestore(Context)` removes callbacks, clears static Runnable state, and synchronously clears these preferences. Update all three existing cancellation call sites to pass the Activity.

- [x] **Step 5: Run focused tests and build**

```bash
rtk ./tests/run-host.sh
rtk ./tests/run-device.sh <projector-ip>:5555
rtk ./build.sh
```

Expected: all tests pass and compilation confirms every cancelRestore call has been updated.

- [x] **Step 6: Commit**

```bash
rtk git add src/com/visorcraft/hy450control/Keystone.java src/com/visorcraft/hy450control/OemFocus.java src/com/visorcraft/hy450control/ProjectionActivity.java src/com/visorcraft/hy450control/RectangleActivity.java tests/HostTests.java tests/run-host.sh
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" commit -m "Make OEM keystone recovery strict and durable"
```

### Task 3: Continue preset application after individual failures

**Files:**
- Create: `src/com/visorcraft/hy450control/ControlBatch.java`
- Modify: `src/com/visorcraft/hy450control/ControlPageActivity.java:415-426`
- Modify: `tests/HostTests.java`
- Modify: `tests/run-host.sh`

**Interfaces:**
- Produces: `ControlBatch.Values` with `boolean has(String id)` and `int getInt(String id) throws Exception`.
- Produces: `static ControlBatch.Result apply(List<Control> controls, ControlBatch.Values values)`; Result exposes `int applied` and `List<String> failures`.

- [x] **Step 1: Add the failing batch test**

Create three fake controls (`first`, `middle`, `last`) plus one read-only control. Make `middle.write` throw `Out of range`. Use map-backed Values containing all writable IDs and assert:

```java
ControlBatch.Result result = ControlBatch.apply(controls, values);
check(result.applied == 2, "later controls must still apply");
check(result.failures.size() == 1, "one failure must be reported");
check(result.failures.get(0).startsWith("Middle: "), "failure must identify control");
check(first.lastWritten == 10 && last.lastWritten == 30, "surrounding writes must land");
check(readOnly.writeCalls == 0, "read-only control must be skipped");
```

Also assert a missing stored ID is skipped without failure.

- [x] **Step 2: Verify failure, then implement ControlBatch**

Run `rtk ./tests/run-host.sh`; expected compilation failure for ControlBatch. Implement `apply` as an independent per-control loop: skip non-writable/missing IDs, call write inside its own try/catch, increment applied on success, and add `control.title + ": " + Errors.describe(error)` on failure. Result constructs an immutable copy of failures.

- [x] **Step 3: Use ControlBatch from the Activity**

Replace `applyPreset` with JSON adaptation and result reporting:

```java
JSONObject stored = presets.read(family).getJSONObject("values");
ControlBatch.Result result = ControlBatch.apply(controls, new ControlBatch.Values() {
    public boolean has(String id) { return stored.has(id); }
    public int getInt(String id) throws Exception { return stored.getInt(id); }
});
StringBuilder message = new StringBuilder("Applied ").append(result.applied).append(" values");
if (!result.failures.isEmpty()) {
    message.append(" • ").append(result.failures.size()).append(" not applied");
    message.append("\n").append(detail(result.failures));
}
setStatus(message.toString(), result.failures.isEmpty() ? StatusKind.SUCCESS : StatusKind.WARNING);
refresh();
```

Retain the outer catch for unreadable preset JSON and use `Apply unavailable: ...` so it is distinct from per-control partial results.

- [x] **Step 4: Run tests/build and commit**

```bash
rtk ./tests/run-host.sh
rtk ./tests/run-device.sh <projector-ip>:5555
rtk ./build.sh
rtk git add src/com/visorcraft/hy450control/ControlBatch.java src/com/visorcraft/hy450control/ControlPageActivity.java tests/HostTests.java tests/run-host.sh
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" commit -m "Report partial preset application accurately"
```

Expected: all tests pass, including the middle-write failure case.

### Task 4: Disable and enforce non-debuggable APKs

**Files:**
- Modify: `AndroidManifest.xml:14`
- Modify: `build.sh:37`
- Modify: `.github/workflows/release.yml:74-93`

- [x] **Step 1: Add the failing assertion**

In workflow APK verification add:

```python
assert "application-debuggable" not in output, output
```

In build.sh, immediately after apksigner verification, add:

```bash
if "$BT/aapt2" dump badging hy450-control.apk | grep -q 'application-debuggable'; then
  echo "release APK must not be debuggable" >&2
  exit 1
fi
```

Run `rtk ./build.sh`; expected failure `release APK must not be debuggable` against the unchanged manifest.

- [x] **Step 2: Disable debugging and verify**

Change the manifest attribute to `android:debuggable="false"`. Run:

```bash
rtk ./build.sh
rtk ./tests/run-host.sh
```

Expected: build and tests pass; badging has no `application-debuggable` line.

- [x] **Step 3: Commit**

```bash
rtk git add AndroidManifest.xml build.sh .github/workflows/release.yml
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" commit -m "Disable debugging in production APKs"
```

### Task 5: De-duplicate atomic storage

**Files:**
- Create: `src/com/visorcraft/hy450control/JsonFileStore.java`
- Modify: `src/com/visorcraft/hy450control/BackupStore.java`
- Modify: `src/com/visorcraft/hy450control/PresetStore.java`
- Modify: `src/com/visorcraft/hy450control/ProjectionBackup.java`
- Modify: `tests/HostTests.java`
- Modify: `tests/run-host.sh`

**Interfaces:**
- Produces: `static String JsonFileStore.read(File source)` and `static void JsonFileStore.write(File target, File temporary, String value)`, both `throws Exception`.

- [x] **Step 1: Write failing file-helper tests**

Using a unique directory under `java.io.tmpdir`, test missing-file failure, UTF-8 round trip, replacement of existing content, and failure when the target parent is a regular file. Delete recursively in `finally`. Add a device-side BackupStore non-overwrite test using the package-private `BackupStore(File directory)` constructor: save one schema-1 snapshot in a temporary directory, then assert a second snapshot with the same family throws and the original JSON remains unchanged.

- [x] **Step 2: Verify failure and implement helper**

Run host tests; expected compilation failure for JsonFileStore. Move the existing complete-read loop into `read`, retaining truncated-read detection. Move temporary write, `output.getFD().sync()`, close, and `renameTo(target)` into `write`; throw `IllegalStateException("File rename failed")` on rename failure.

- [x] **Step 3: Migrate all three stores**

Replace duplicated I/O with JsonFileStore while preserving JSON and firmware logic. Add a package-private `BackupStore(File directory)` constructor, retain the Context constructor, and reject `target.isFile()` at the start of `snapshot` with `IllegalStateException("Original snapshot already exists")`. ProjectionBackup `readBackup` uses JsonFileStore.read and save uses JsonFileStore.write.

- [x] **Step 4: Run all tests/build and commit**

```bash
rtk ./tests/run-host.sh
rtk ./tests/run-device.sh <projector-ip>:5555
rtk ./build.sh
rtk git add src/com/visorcraft/hy450control/JsonFileStore.java src/com/visorcraft/hy450control/BackupStore.java src/com/visorcraft/hy450control/PresetStore.java src/com/visorcraft/hy450control/ProjectionBackup.java tests/HostTests.java tests/run-host.sh
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" commit -m "Share atomic JSON file storage"
```

Expected: all existing and new tests pass with no schema changes.

### Task 6: De-duplicate vendor adapters and backlight creation

**Files:**
- Create: `src/com/visorcraft/hy450control/VendorControl.java`
- Modify: `src/com/visorcraft/hy450control/ControlAdapter.java`
- Modify: `src/com/visorcraft/hy450control/VendorIntControl.java`
- Modify: `src/com/visorcraft/hy450control/VendorBooleanControl.java`
- Modify: `src/com/visorcraft/hy450control/VendorEnumControl.java`
- Modify: `src/com/visorcraft/hy450control/ReadOnlyControl.java`
- Modify: `src/com/visorcraft/hy450control/ControlCatalog.java`
- Modify: `src/com/visorcraft/hy450control/MainActivity.java`
- Modify: `src/com/visorcraft/hy450control/OverlayService.java`
- Modify: `tests/HostTests.java`
- Modify: `tests/run-host.sh`

**Interfaces:**
- Produces: abstract `VendorControl` implementing ControlAdapter with final `write(int)` and abstract `rawWrite(int)`.
- Produces: `static VendorIntControl ControlCatalog.backlight()`.

- [x] **Step 1: Write comprehensive failing adapter tests**

For each int, boolean, and enum fake manager, cover successful writes, bounds rejection with zero setter calls, initial getter failure, setter failure, readback mismatch restoring the original, and rollback failure surfacing an exception. Integer tests must cover both parameter and no-parameter signatures. A representative fake assertion is:

```java
FakeIntManager manager = new FakeIntManager(5);
manager.mismatch = true;
VendorIntControl control = new VendorIntControl(manager, "getValue", "setValue", null, 0, 10);
expectThrows(() -> control.write(7), "readback mismatch");
check(manager.value == 5, "mismatch must roll back");
check(manager.setCalls == 2, "write plus rollback");
```

- [x] **Step 2: Implement the common write sequence**

Create VendorControl holding manager/getter/setter and implement:

```java
public final void write(int value) throws Exception {
    if (value < min() || value > max()) throw new IllegalArgumentException("Out of range");
    int original = read();
    rawWrite(value);
    if (read() != value) {
        rawWrite(original);
        throw new IllegalStateException("Readback mismatch");
    }
}
```

Subclasses retain constructors, read/format/min/max, and implement only rawWrite. Remove isAvailable from the interface and all adapters. Add `ControlCatalog.backlight()` returning `new VendorIntControl(Vendor.display(), "getBacklight", "setBacklight", null, 0, 255)` and use it in picture(), MainActivity, and OverlayService.

- [x] **Step 3: Run all tests/build and commit**

```bash
rtk ./tests/run-host.sh
rtk ./tests/run-device.sh <projector-ip>:5555
rtk ./build.sh
rtk git add src/com/visorcraft/hy450control/ControlAdapter.java src/com/visorcraft/hy450control/VendorControl.java src/com/visorcraft/hy450control/VendorIntControl.java src/com/visorcraft/hy450control/VendorBooleanControl.java src/com/visorcraft/hy450control/VendorEnumControl.java src/com/visorcraft/hy450control/ReadOnlyControl.java src/com/visorcraft/hy450control/ControlCatalog.java src/com/visorcraft/hy450control/MainActivity.java src/com/visorcraft/hy450control/OverlayService.java tests/HostTests.java tests/run-host.sh
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" commit -m "Consolidate vendor write verification"
```

Expected: adapter and all previous tests pass; behavior remains unchanged.

### Task 7: De-duplicate UI builders, document, and verify end to end

**Files:**
- Modify: `src/com/visorcraft/hy450control/Ui.java`
- Modify: `src/com/visorcraft/hy450control/AutomationActivity.java`
- Modify: `src/com/visorcraft/hy450control/ProjectionActivity.java`
- Modify: `src/com/visorcraft/hy450control/MainActivity.java`
- Modify: `src/com/visorcraft/hy450control/ControlPageActivity.java`
- Modify: `README.md`
- Modify: `.github/workflows/release.yml`
- Modify: `docs/superpowers/specs/2026-11-02-safety-and-dedup-design.md` only if final behavior differs

**Interfaces:**
- Produces: `Ui.section(Context, String, float gapPx)`, `Ui.note(Context, String, float textPx, int colorRes)`, `Ui.chipRow(LinearLayout root, float gapPx)`, and `Ui.addChip(LinearLayout row, String title, View.OnClickListener action, float gapPx)`.

- [x] **Step 1: Extract UI factories without visual changes**

Move the existing method bodies into Ui, replacing `this` with `context` and existing GAP_PX with the explicit gap parameter. Ui.addChip must attach a null action only when non-null, preserving the disabled remote-key behavior. Replace private duplicate methods and update call sites. Do not change dimensions or ordering.

- [x] **Step 2: Update tests, CI, and documentation**

Add a `Run host regression tests` workflow step before Build APK whose command is `./tests/run-host.sh` (GitHub runners do not use the workspace `rtk` prefix). README must describe family presets with applied/failed reporting, explicit absent-value projection restoration, durable OEM recovery, non-debuggable builds, and exact host/device test commands. Keep current version 1.3.0 and existing limitations.

- [x] **Step 3: Run full local/device fake-data verification**

```bash
rtk ./tests/run-host.sh
rtk ./tests/run-device.sh <projector-ip>:5555
rtk bash -n build.sh install.sh uninstall.sh capture/build.sh capture/capture.sh probe/build.sh tests/run-host.sh tests/run-device.sh
rtk ./build.sh
rtk git diff --check
```

Expected: all tests and build pass, APK is signed and non-debuggable, and no generated files appear in `git status`.

- [x] **Step 4: Verify real recovery with backups**

Read current private `AGENTS.md` before device work and re-verify live state. Update the private operator/forensics notes before changing installation state, and never copy private identifiers into this public repository. Install the candidate through the reversible Magisk module and reboot. Record strict keystone values and a projection backup before opening OEM focus. Test ordinary return, process death while OEM focus is open, and explicit cancellation; verify exact values and no stale pending recovery. Do not test invalid input presets on hardware. Confirm Projectivy accessibility/startup and the framework services.jar hash remain unchanged.

- [x] **Step 5: Commit implementation and report**

```bash
rtk git add src/com/visorcraft/hy450control/Ui.java src/com/visorcraft/hy450control/AutomationActivity.java src/com/visorcraft/hy450control/ProjectionActivity.java src/com/visorcraft/hy450control/MainActivity.java src/com/visorcraft/hy450control/ControlPageActivity.java README.md .github/workflows/release.yml
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" commit -m "Share console UI builders and document safety tests"
rtk git status --short --branch
```

Expected: clean master with local commits ahead of origin, no push/tag/release. Report test evidence, device verification, remaining limitations, and commit IDs.
