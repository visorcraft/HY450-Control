# Peer Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix every confirmed peer-review finding, replace brittle recovery coverage with behavioral tests, and complete the localized de-duplication and dead-code cleanup.

**Architecture:** Preserve the SDK-only Java 8 application and existing backup schemas. Extract a pure-Java OEM recovery state machine behind the existing Android wrapper, use JobScheduler for delayed boot launch, validate complete projection backups before writes, and centralize exception/property/access definitions without merging intentionally different restore and preset policies.

**Tech Stack:** Java 8, Android SDK 34, Android build-tools 35.0.0, Bash, adb/app_process.

## Completion outcome

All reviewed production fixes, behavioral regressions, de-duplication, dead-code removal, and documentation updates were released in v1.5.0. Host tests, shell syntax checks, the signed non-debuggable build, diff checks, and sanitization checks passed. Independent final review found no remaining Critical or Important issues after its three follow-up findings were fixed. The Android suite initially could not execute because the development unit was unreachable; connectivity later returned, and the complete Android suite passed during the v1.5.1 hardware-verification cycle.

## Global Constraints

- Preserve schema-1 backup/preset compatibility, existing UI copy except error reporting, and the 20-second boot delay.
- No Gradle, third-party libraries, live hardware writes in automated fake-data tests, version bump, commit, push, tag, release, or projector installation.
- Prefix shell commands with `rtk`; use an explicit adb serial for device commands.
- Write and observe each behavioral regression fail before its production fix; run host tests after every cycle and the full suite before completion.
- Generated output belongs under ignored `build/`, `tests/build/`, or existing ignored module output paths.

---

### Task 1: Actionable exception descriptions

**Files:**
- Modify: `tests/HostTests.java`
- Modify: `src/com/visorcraft/hy450control/Errors.java`
- Modify: `src/com/visorcraft/hy450control/ProjectionActivity.java`
- Modify: `src/com/visorcraft/hy450control/RectangleActivity.java`

**Interfaces:**
- Produces: `static String Errors.describe(Exception error)` returning the innermost reflective target's nonblank message or simple class name.

- [ ] **Step 1: Add failing exception tests**

Add direct, wrapped, nested, null-message, and blank-message cases:

```java
Exception cause = new IllegalArgumentException("Vendor rejected value");
Exception wrapped = new java.lang.reflect.InvocationTargetException(cause);
check("Vendor rejected value".equals(Errors.describe(wrapped)),
        "reflective cause must be visible");
Exception nested = new java.lang.reflect.InvocationTargetException(wrapped);
check("Vendor rejected value".equals(Errors.describe(nested)),
        "nested reflective cause must be visible");
check("IllegalStateException".equals(Errors.describe(new IllegalStateException())),
        "null message must use class name");
check("IllegalStateException".equals(Errors.describe(new IllegalStateException("  "))),
        "blank message must use class name");
```

- [ ] **Step 2: Verify RED**

Run `rtk ./tests/run-host.sh`. Expected: the wrapped-exception assertion fails with the generic wrapper text.

- [ ] **Step 3: Implement and route UI errors**

Unwrap `InvocationTargetException` while its target is non-null, then trim only to test blankness while returning the original nonblank message. Replace direct `error.getMessage()` Toast paths in both activities with `Errors.describe(error)`.

- [ ] **Step 4: Verify GREEN**

Run `rtk ./tests/run-host.sh`. Expected: `Host tests passed`.

### Task 2: Behavioral OEM recovery and restore exclusion

**Files:**
- Create: `src/com/visorcraft/hy450control/OemRecovery.java`
- Modify: `src/com/visorcraft/hy450control/OemFocus.java`
- Modify: `src/com/visorcraft/hy450control/ProjectionActivity.java`
- Modify: `tests/HostTests.java`
- Modify: `tests/run-host.sh`

**Interfaces:**
- `OemRecovery.Store`: `boolean isPending()`, `Keystone.Snapshot read() throws Exception`, `boolean save(Keystone.Snapshot snapshot)`, and `boolean clear()`.
- `OemRecovery.Scheduler`: `post`, `postDelayed`, and `removeCallbacks` for one Runnable.
- `OemRecovery.Values`: `Keystone.Snapshot read() throws Exception` and `void apply(Keystone.Snapshot snapshot) throws Exception`.
- Produces: `prepare()`, `resume(ErrorReporter)`, and `cancel()`; existing `OemFocus.launch/onResume/cancelRestore` signatures remain unchanged.

- [ ] **Step 1: Add failing recovery state-machine tests**

Use map-backed fake Store/Values and a fake scheduler that records delayed Runnables. Cover:

```java
recovery.prepare();
check(store.pending && scheduler != null, "prepare must persist a snapshot");
recovery.resume(errors::add);
check(scheduler.count == 3, "resume must schedule three passes");
scheduler.run(0);
scheduler.run(1);
scheduler.run(2);
check(values.applied == 3 && !store.pending, "verified recovery must clear pending");
```

Independently verify failed final readback preserves pending, failed clear re-saves pending, `cancel()` removes every queued callback and clears persisted state, malformed persisted state reports an error without writes, and prepare/cancel commit failures throw. A projection-restore sequencing fake must require cancellation to finish before its first value write.

- [ ] **Step 2: Verify RED**

Run `rtk ./tests/run-host.sh`. Expected: compilation fails because `OemRecovery` does not exist.

- [ ] **Step 3: Implement the core and Android adapters**

Move the existing three-pass algorithm into OemRecovery without changing delays. Keep pending state on apply/readback/clear failure and retry `save` after a failed final clear. `cancel()` must remove callbacks before synchronously clearing. OemFocus adapts SharedPreferences, Handler, Keystone, and Activity error Toasts to these interfaces; it retains application-context ownership and no Activity in static fields.

- [ ] **Step 4: Exclude stale recovery from projection restore**

At the start of the existing guarded restore action, call:

```java
OemFocus.cancelRestore(this);
ProjectionBackup.restore(this);
```

Cancellation failure must prevent backup writes. Remove the old source-text assertion and retain behavioral coverage of cancellation and sequencing.

- [ ] **Step 5: Verify GREEN**

Run `rtk ./tests/run-host.sh`. Expected: `Host tests passed`, including process-recreation-equivalent pending reads, failed final readback, commit/clear failures, and callback cancellation.

### Task 3: Validate projection JSON and share projection definitions

**Files:**
- Modify: `src/com/visorcraft/hy450control/ProjectionBackup.java`
- Modify: `src/com/visorcraft/hy450control/Keystone.java`
- Modify: `tests/DeviceTests.java`

**Interfaces:**
- Produces: `static void ProjectionBackup.validateValues(JSONObject values)` allowing exactly the existing five global and ten property keys with String values, except JSON null for globals.
- Produces: package-private shared keystone property definitions used by both Keystone and ProjectionBackup.

- [ ] **Step 1: Add failing pre-write validation tests**

For unknown global/property keys, malformed prefixes, null properties, numeric values, and a valid early key followed by an invalid key, assert an exception and zero writes:

```java
JSONObject invalid = validProjectionValues();
invalid.put("property:persist.display.keystone_ltx", 12);
expectThrows(() -> ProjectionBackup.restoreValues(invalid, access),
        "numeric property must be rejected");
check(access.writes == 0, "validation must finish before writes");
```

Keep absent-global, empty-string, and legacy partial schema-1 fixtures valid.

- [ ] **Step 2: Verify RED**

Run `rtk ./tests/run-device.sh <projector-ip>:5555` if the explicit development unit is reachable. Expected: invalid entries reach writes or produce the wrong failure. If offline, run the same tests after the remaining host cycles and record the initial RED as blocked rather than claiming it passed.

- [ ] **Step 3: Validate first and de-duplicate**

Iterate every entry for exact key/type membership before any put. Reuse one context-backed `ValueAccess` for save/restore. Share Keystone's display/zoom keys through package-private accessors or arrays without exposing mutable public state. Remove the unused `Keystone.parseFloat` helper.

- [ ] **Step 4: Verify GREEN**

Run host and device suites. Expected: all existing round trips plus every invalid-input case pass with zero writes on rejection.

### Task 4: Reliable boot and atomic sleep selection

**Files:**
- Create: `src/com/visorcraft/hy450control/BootLaunchService.java`
- Create: `src/com/visorcraft/hy450control/AutomationSelection.java`
- Modify: `src/com/visorcraft/hy450control/BootReceiver.java`
- Modify: `src/com/visorcraft/hy450control/AutomationActivity.java`
- Modify: `AndroidManifest.xml`
- Modify: `tests/DeviceTests.java`
- Modify: `tests/HostTests.java`
- Modify: `tests/run-device.sh`
- Modify: `tests/run-host.sh`

**Interfaces:**
- BootReceiver schedules job ID 1001 for BootLaunchService with `setMinimumLatency(20_000L)`.
- `AutomationSelection.cycle(int current, int count, Action action)` invokes the action and returns the next index only after success.

- [ ] **Step 1: Add failing lifecycle and selection tests**

On Android, use a MockContext and fake JobScheduler to assert BOOT_COMPLETED schedules the correct component/delay and unrelated actions do not. On host, assert selection failure propagates and returns no committed next index:

```java
expectThrows(() -> AutomationSelection.cycle(0, 3, index -> {
    throw new SecurityException("Exact alarms denied");
}), "scheduler failure must propagate");
int selected = AutomationSelection.cycle(0, 3, index -> { });
check(selected == 1, "successful selection returns the next index");
```

- [ ] **Step 2: Verify RED**

Run both suites. Expected: missing BootLaunchService/AutomationSelection compilation failures and no scheduled-job behavior.

- [ ] **Step 3: Implement JobScheduler delivery**

BootReceiver schedules the fixed job and logs scheduling failure. BootLaunchService reads the existing allowlisted preference, resolves the launch intent, logs null/failure, launches when available, and returns false because work is synchronous. Register the non-exported job service with `android.permission.BIND_JOB_SERVICE`. Remove Handler callback usage.

- [ ] **Step 4: Make sleep selection failure-safe**

AutomationActivity catches action failures, leaves the cycle preference unchanged until `AutomationSelection.cycle` succeeds, schedules SleepTimer before saving `sleep_minutes`, refreshes after success/failure, and displays `Errors.describe(error)` instead of crashing.

- [ ] **Step 5: Verify GREEN**

Run both suites and `rtk ./build.sh`. Expected: tests pass and manifest/component linkage compiles into a signed non-debuggable APK.

### Task 5: Final cleanup, documentation, and verification

**Files:**
- Modify: `src/com/visorcraft/hy450control/Ui.java`
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-11-02-peer-review-fixes.md`

- [ ] **Step 1: Remove dead code and update behavior documentation**

Delete the unused single-argument `Ui.tile` overload. Update README safety/testing descriptions only where behavior changed: complete pre-write backup validation, JobScheduler boot delivery, failure-safe sleep selection, and behavioral recovery coverage. Do not change the current release version or claim this working tree is installed on hardware.

- [ ] **Step 2: Run complete verification**

```bash
rtk ./tests/run-host.sh
rtk ./tests/run-device.sh <projector-ip>:5555
rtk bash -n build.sh install.sh uninstall.sh capture/build.sh capture/capture.sh probe/build.sh tests/run-host.sh tests/run-device.sh
rtk ./build.sh
rtk git diff --check
rtk git status --short --branch
```

Expected: both suites and build pass; APK remains signed/non-debuggable; no generated files are tracked or unexpectedly untracked.

- [ ] **Step 3: Request a final read-only peer review**

Review the complete diff against all six original findings and cleanup requests. Fix any confirmed Critical/Important regression, rerun affected tests, and report exactly what was verified. Do not commit, push, tag, release, or install the candidate without a separate user request.
