# First Safety and Usability Milestone Implementation Plan

**Status:** Complete; released as v1.2.0 at `5e44a6c` and superseded visually by the Signal Console release.

> **For agentic workers:** This completed plan is retained for implementation history; every task checkbox is marked complete.

**Goal:** Make HY450 Control safer and clearer with clamped writes, readable values and persistent feedback, original-value restoration, resilient family loading, a dismissible overlay, and fullscreen calibration.

**Architecture:** Retain the existing SDK-only Java 8 app and vendor adapter boundary. Add small default adapter methods, rebuild control pages by family, and make focused changes to the existing activities and overlay service.

**Tech Stack:** Java 8, Android SDK 34, Allwinner vendor reflection APIs, Android WindowInsets, Bash SDK/APK build, adb/Magisk hardware verification.

## Global Constraints

- Do not add named presets, named input selection, startup-app changes, sleep-timer changes, diagnostics, remote remapping, boot-input writes, undocumented vendor APIs, arbitrary root commands, or DRM features.
- Do not add dependencies, Gradle, tests, or test scaffolding; this project has no test suite.
- Clamp only UI-generated adjustments. Snapshot and preset writes retain adapter range validation and write/readback/rollback behavior.
- Preserve the existing backup schema and firmware fingerprint check; never overwrite an existing snapshot.
- Continue past per-control restore failures, but never partially interpret a missing, malformed, or firmware-mismatched snapshot.
- Generated APKs/build outputs and proprietary binaries remain untracked.
- Prefix host shell commands with `rtk`; public commits use `visorcraft <noreply@visorcraft.local>`; do not push without explicit authorization.
- Each implementation task is executed through the Claude CLI by a fresh Kimi coder subagent, followed by independent spec and quality review.

---

### Task 1: Control adapter behavior

**Files:**
- Modify: `src/com/visorcraft/hy450control/ControlAdapter.java`
- Modify: `src/com/visorcraft/hy450control/VendorBooleanControl.java`
- Modify: `src/com/visorcraft/hy450control/VendorEnumControl.java`
- Modify: `src/com/visorcraft/hy450control/ReadOnlyControl.java`

**Interfaces:**
- Consumes: existing `ControlAdapter.read()`, `write()`, `min()`, and `max()` contracts.
- Produces: `default boolean isWritable()`, `default String format(int value)`, and `default int clamp(int value)`; readable enum/boolean values; non-writable read-only controls.

- [x] **Step 1: Add the default adapter behavior**

Use Java 8 default methods so numeric adapters need no formatting changes:

```java
default boolean isWritable() { return true; }

default String format(int value) { return String.valueOf(value); }

default int clamp(int value) {
    return Math.max(min(), Math.min(max(), value));
}
```

- [x] **Step 2: Implement boolean and read-only overrides**

Add to `VendorBooleanControl`:

```java
@Override
public String format(int value) {
    if (value == 0) return "Off";
    if (value == 1) return "On";
    throw new IllegalArgumentException("Invalid boolean value");
}
```

Add to `ReadOnlyControl`:

```java
@Override
public boolean isWritable() { return false; }
```

- [x] **Step 3: Implement readable enum formatting**

Add a range-checked `format(int)` override to `VendorEnumControl`. Obtain the stable name with `((Enum<?>) values[value]).name()`, remove a leading `E_AW_`, replace underscores with spaces, lowercase the remainder, and capitalize each word. Keep numeric indices as the stored/written representation. Use this implementation:

```java
@Override
public String format(int value) {
    if (value < min() || value > max()) throw new IllegalArgumentException("Out of range");
    String name = ((Enum<?>) values[value]).name();
    if (name.startsWith("E_AW_")) name = name.substring(5);
    String[] words = name.toLowerCase(java.util.Locale.ROOT).split("_");
    StringBuilder label = new StringBuilder();
    for (String word : words) {
        if (word.isEmpty()) continue;
        if (label.length() > 0) label.append(' ');
        label.append(Character.toUpperCase(word.charAt(0)));
        if (word.length() > 1) label.append(word.substring(1));
    }
    return label.toString();
}
```

- [x] **Step 4: Build and inspect**

Run: `rtk ./build.sh`
Expected: Java compilation, D8, APK signing, and `apksigner verify` all succeed; final output is `built: hy450-control.apk`.

Run: `rtk git diff --check`
Expected: no whitespace errors.

- [x] **Step 5: Commit**

```bash
rtk git add src/com/visorcraft/hy450control/ControlAdapter.java \
  src/com/visorcraft/hy450control/VendorBooleanControl.java \
  src/com/visorcraft/hy450control/VendorEnumControl.java \
  src/com/visorcraft/hy450control/ReadOnlyControl.java
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" \
  commit -m "Add readable and clamped control behavior"
```

---

### Task 2: Resilient control pages and original-value restore

**Files:**
- Modify: `src/com/visorcraft/hy450control/MainActivity.java`
- Modify: `src/com/visorcraft/hy450control/ControlPageActivity.java`
- Reference: `src/com/visorcraft/hy450control/BackupStore.java`
- Reference: `src/com/visorcraft/hy450control/ControlCatalog.java`

**Interfaces:**
- Consumes: `ControlAdapter.isWritable()`, `format(int)`, and `clamp(int)` from Task 1; existing `BackupStore.hasSnapshot()`, `snapshot()`, and `readSnapshot()`.
- Produces: `static List<Control> loadControls(Context context, String family)` and `static void start(Context context, String title, String family)` in `ControlPageActivity`.

- [x] **Step 1: Remove static control transfer and rebuild by family**

Delete `private static List<Control> pending`. Implement:

```java
static List<Control> loadControls(Context context, String family) throws Exception {
    if ("picture".equals(family)) return ControlCatalog.picture(context);
    if ("audio".equals(family)) return ControlCatalog.audio(context);
    if ("source".equals(family)) return ControlCatalog.source(context);
    throw new IllegalArgumentException("Unknown control family");
}

public static void start(Context context, String title, String family) {
    Intent intent = new Intent(context, ControlPageActivity.class);
    intent.putExtra("title", title);
    intent.putExtra("family", family);
    context.startActivity(intent);
}
```

In `onCreate`, assign `family` first and rebuild with `controls.addAll(loadControls(this, family))` inside a local try/catch. Preserve the intent title and family extras. Retain any load exception message in a local `String loadError`, finish constructing the page with an empty control list, show `Controls unavailable: <message>` in the persistent status, and skip automatic backup in that case; `onCreate` cannot propagate the checked exception.

- [x] **Step 2: Load each dashboard family independently**

Replace the single try/catch around all three `page(...)` calls with three calls to a helper. The helper creates the button first, validates only its own family with `ControlPageActivity.loadControls(this, family)`, and disables only that button on failure:

```java
private void controlPage(LinearLayout root, String title, String family) {
    Button button = new Button(this);
    button.setText(title);
    button.setTextSize(22);
    try {
        ControlPageActivity.loadControls(this, family);
        button.setOnClickListener(v -> ControlPageActivity.start(this, title, family));
    } catch (Exception error) {
        button.setText(title + "\nUnavailable: " + error.getMessage());
        button.setEnabled(false);
    }
    root.addView(button);
}
```

Call it for `("Picture controls", "picture")`, `("Audio controls", "audio")`, and `("Input controls", "source")`. Remove the obsolete `page(..., List<Control>)` method and unused `java.util.List` import. Ensure initial focus uses an existing enabled view rather than assuming child index 3 is suitable.

- [x] **Step 3: Add persistent status, restore action, and readable rows**

Add fields:

```java
private TextView status;
private Button restoreButton;
```

Add `restoreButton = addAction(root, "Restore original values", v -> restoreOriginals());` after the preset actions. Add a non-focusable status `TextView` with initial text `Original values are backed up automatically`, size 18, light-gray color, and enough padding for D-pad use. Keep actions and rows in the existing `ScrollView`, but place that scroll view and the status in a vertical outer `LinearLayout`: give the scroll view height `0` and weight `1`, then add the status after it as a fixed footer. Call `setContentView` on the outer layout so status feedback cannot scroll off-screen.

For each row, use `row.setFocusable(control.adapter.isWritable())`. Attach a focus listener that updates `index` to that row when focused. Display values as:

```java
rows.get(i).setText(control.title + "\n" + control.adapter.format(control.adapter.read()));
```

After layout, focus the first writable row if one exists; otherwise focus `saveButton`. Do not request row focus again on every refresh.

- [x] **Step 4: Back up writable controls individually**

Replace the all-or-nothing backup loop with per-control reads that skip `!control.adapter.isWritable()` and collect read failures. If at least one value was read and no snapshot exists, write one snapshot containing those values. Never replace an existing snapshot. Show a persistent warning listing backup failures; if no values could be saved, explicitly state that no snapshot was created.

- [x] **Step 5: Clamp adjustments and retain feedback**

Use this operation shape for left/right/centre adjustment:

```java
private void adjust(int direction) {
    Control control = controls.get(index);
    if (!control.adapter.isWritable()) return;
    try {
        int current = control.adapter.read();
        int next = control.adapter.clamp(current + direction);
        if (next == current) {
            String limit = direction < 0 ? "Minimum" : "Maximum";
            status.setText(limit + " reached • " + control.title + ": "
                    + control.adapter.format(current));
        } else {
            control.adapter.write(next);
            int actual = control.adapter.read();
            status.setText(control.title + ": " + control.adapter.format(actual)
                    + " • read back OK");
        }
    } catch (Exception error) {
        status.setText(control.title + " failed: " + error.getMessage());
    }
    refresh();
}
```

Remove wrapping assignments and adjustment toasts. Keep refresh in `finally`-equivalent control flow so actual values are shown after failures as well.

- [x] **Step 6: Let native D-pad navigation skip read-only rows**

Delete manual UP/DOWN index navigation. Handle only LEFT, RIGHT, CENTRE, and ENTER when the currently focused row is writable; delegate other keys with `return super.onKeyDown(code, event)`. Native focus navigation then moves among buttons and writable rows without focusing read-only rows.

- [x] **Step 7: Implement fault-tolerant original-value restore**

Read and validate `BackupStore.readSnapshot(family)` before any writes. Iterate the current controls; skip read-only controls, report writable controls absent from the snapshot as skipped, and catch each write failure individually. Continue after range, vendor, and readback errors. Use this structure:

```java
private void restoreOriginals() {
    int restored = 0;
    List<String> failures = new ArrayList<>();
    try {
        JSONObject stored = new BackupStore(this).readSnapshot(family)
                .getJSONObject("values");
        for (Control control : controls) {
            if (!control.adapter.isWritable()) continue;
            if (!stored.has(control.id)) {
                failures.add(control.title + ": no saved value");
                continue;
            }
            try {
                control.adapter.write(stored.getInt(control.id));
                restored++;
            } catch (Exception error) {
                failures.add(control.title + ": " + error.getMessage());
            }
        }
        StringBuilder message = new StringBuilder("Restored " + restored + " values");
        if (!failures.isEmpty()) {
            message.append(" • ").append(failures.size()).append(" not restored");
            for (String failure : failures) message.append("\n").append(failure);
        }
        status.setText(message.toString());
    } catch (Exception error) {
        status.setText("Restore unavailable: " + error.getMessage());
    }
    refresh();
}
```

Do not clamp snapshot values and do not modify `BackupStore` or its schema.

- [x] **Step 8: Update preset feedback and build**

Route preset save/apply/failure messages through the persistent status instead of short toasts. Keep preset storage unchanged, but skip read-only controls during preset application so they cannot cause a false failure or abort later writes; this is an explicit user-approved refinement of the original unchanged-preset constraint. Disable all three actions when the family cannot load or has no controls, refuse to save an empty value map, and cap detailed failure lists with a remaining-count summary so the fixed footer cannot consume the viewport. Remove the obsolete `Toast` import and `toast(...)` method only after all uses are gone.

Run: `rtk ./build.sh && rtk git diff --check`
Expected: build and APK verification succeed; no whitespace errors.

- [x] **Step 9: Commit**

```bash
rtk git add src/com/visorcraft/hy450control/MainActivity.java \
  src/com/visorcraft/hy450control/ControlPageActivity.java
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" \
  commit -m "Add resilient controls and original-value restore"
```

---

### Task 3: Quick-overlay safety and lifecycle

**Files:**
- Modify: `src/com/visorcraft/hy450control/OverlayService.java`

**Interfaces:**
- Consumes: `VendorIntControl.clamp(int)` and `format(int)` from Task 1.
- Produces: visible overlay feedback, BACK dismissal, 15-second inactivity dismissal, and safe handler/window cleanup.

- [x] **Step 1: Add status and timeout state**

Add a main-thread `Handler`, a 15,000 ms timeout constant, a `TextView status`, and a `Runnable timeout` that calls `stopSelf()`. `armTimeout()` removes callbacks and posts the runnable for 15 seconds. Return `START_NOT_STICKY` from `onStartCommand` and arm the timeout on every start.

- [x] **Step 2: Handle BACK and interaction at the panel boundary**

Construct the panel as an anonymous `LinearLayout` overriding `dispatchKeyEvent(KeyEvent)`:

```java
@Override
public boolean dispatchKeyEvent(KeyEvent event) {
    armTimeout();
    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
        stopSelf();
        return true;
    }
    return super.dispatchKeyEvent(event);
}
```

Add a light-gray, non-focusable status view before the buttons. Every button click rearms the timeout before running its action.

- [x] **Step 3: Clamp and report backlight operations**

Replace `control.write(control.read() + direction)` with current/clamped logic equivalent to:

```java
int current = control.read();
int next = control.clamp(current + direction);
if (next == current) {
    status.setText((direction < 0 ? "Minimum" : "Maximum")
            + " • Backlight: " + control.format(current));
} else {
    control.write(next);
    status.setText("Backlight: " + control.format(control.read()) + " • read back OK");
}
```

Catch failures and put `Backlight failed: <message>` in the status; do not silently ignore them.

- [x] **Step 4: Report volume and clean up**

After `adjustStreamVolume`, show `Volume: <current>/<max>` using `getStreamVolume` and `getStreamMaxVolume`. Catch volume failures into the same status view.

In `onDestroy`, remove timeout callbacks, detach the panel only when non-null and attached to a window, null the fields, and call `super.onDestroy()`.

- [x] **Step 5: Build and commit**

Run: `rtk ./build.sh && rtk git diff --check`
Expected: build and APK verification succeed; no whitespace errors.

```bash
rtk git add src/com/visorcraft/hy450control/OverlayService.java
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" \
  commit -m "Make the quick overlay bounded and dismissible"
```

---

### Task 4: Fullscreen calibration

**Files:**
- Modify: `src/com/visorcraft/hy450control/CalibrationActivity.java`

**Interfaces:**
- Consumes: Android 34 `WindowInsets` and `WindowInsetsController` APIs.
- Produces: immersive calibration with existing pattern and BACK behavior unchanged.

- [x] **Step 1: Hide system bars**

Add imports for `android.view.WindowInsets` and `android.view.WindowInsetsController`. Add:

```java
private void hideSystemBars() {
    getWindow().setDecorFitsSystemWindows(false);
    WindowInsetsController controller = getWindow().getInsetsController();
    if (controller == null) return;
    controller.hide(WindowInsets.Type.systemBars());
    controller.setSystemBarsBehavior(
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
}
```

Call `hideSystemBars()` after `setContentView(view)` and again from `onWindowFocusChanged(boolean)` whenever focus is gained. Keep existing D-pad and BACK dispatch unchanged.

- [x] **Step 2: Build and commit**

Run: `rtk ./build.sh && rtk git diff --check`
Expected: build and APK verification succeed; no whitespace errors.

```bash
rtk git add src/com/visorcraft/hy450control/CalibrationActivity.java
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" \
  commit -m "Show calibration patterns fullscreen"
```

---

### Task 5: Documentation and end-to-end verification

**Files:**
- Modify: `README.md`
- Modify: private archive `MANAGEMENT.md` and/or `NOTES.md` before device-state changes, as required by the private archive guidance.
- Verify: all source changed in Tasks 1–4.

**Interfaces:**
- Consumes: the complete implementation from Tasks 1–4 and the existing `install.sh` reversible Magisk installation procedure.
- Produces: accurate public usage/verification records and private device-state notes; no release or version change unless separately requested.

- [x] **Step 1: Run a clean source build**

Run: `rtk ./build.sh && rtk git diff --check && rtk git status --short`
Expected: build/signing succeeds, no whitespace errors, and generated APK/build outputs do not appear as untracked files.

- [x] **Step 2: Verify dashboard and control-page behavior on the projector**

Connect explicitly to the known device and install using the existing module script; reboot only as required by that script. Confirm all three available family buttons remain usable, readable boolean/enum labels appear, read-only boot default cannot be focused, limit adjustments do not wrap, and persistent status matches actual readback. Exercise a supported control by changing one step, reading it back, then restoring its exact original value.

- [x] **Step 3: Verify original-value restore**

Record the current values first. Change one supported value, invoke **Restore original values**, and confirm the pre-change snapshot value returns. Confirm a missing/unsupported entry is reported without preventing other restores. Leave all hardware settings at their original values.

- [x] **Step 4: Verify overlay and calibration**

Open the overlay; confirm visible feedback, clamped backlight behavior without changing the final original value, BACK dismissal, and automatic dismissal after approximately 15 seconds. Open calibration and confirm status/navigation bars are hidden, left/right changes patterns, and BACK exits.

- [x] **Step 5: Check unrelated device state and update documentation**

Confirm Projectivy accessibility and existing Magisk modules remain intact. Update README feature/safety/verified-on-hardware bullets only with checks actually performed. Record the installed build hash and verification results in the private archive before any public claim. Do not push either repository and do not create a release under this task.

- [x] **Step 6: Commit accurate documentation**

```bash
rtk git add README.md
rtk git -c user.name="visorcraft" -c user.email="noreply@visorcraft.local" \
  commit -m "Document first-milestone controls and verification"
```

Run final `rtk git status --short --branch`; expected: clean `master` apart from no generated outputs, with all commits local and unpushed.
