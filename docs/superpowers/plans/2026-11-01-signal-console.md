# Signal Console UI Redesign Implementation Plan

**Status:** Complete; device-verified and released as v1.3.0 at `fa2d2fb`.

> **For agentic workers:** This completed plan is retained for implementation history; every task checkbox is marked complete.

**Goal:** Replace HY450 Control's OEM-style interface with the approved bold, projector-first Signal Console while preserving verified hardware behavior.

**Architecture:** Keep the SDK-only Java 8 programmatic UI. Centralize tokens and widget construction in `Ui.java`, then restyle each screen without changing vendor adapters, persisted schemas, or control IDs. Use cached display strings to remove full-family reads from the adjustment hot path.

**Tech Stack:** Java 8, Android SDK 34, a custom stateful `CardDrawable`, `GradientDrawable` for read-only cards, platform fonts, custom `View` drawing, Bash APK build, adb/Magisk validation, GitHub Actions releases.

## Global Constraints

- Follow `docs/superpowers/specs/2026-11-01-signal-console-design.md` exactly, including palette, 1080p scale formula, focus behavior, and out-of-scope list.
- No Gradle, dependencies, downloaded assets, new hardware features, or test scaffolding; the project has no test suite.
- Preserve vendor write/readback/rollback, backup/preset schemas, control IDs, safety copy, startup/sleep behavior, calibration geometry, and release signing.
- `MainActivity` derives resolution/Android metadata dynamically; `VISORCRAFT · HY450 MAX` is a product constant. Availability and reason text come from `ControlPageActivity.loadControls`/`ControlCatalog`: `Unavailable: <Errors.describe(error)>`.
- Input family tile, OEM input source shortcut, and overlay Input source are distinct destinations; do not miswire them.
- TARGET retains the last selected row while chips are focused; with no prior row it shows the family and `Select a control`; failed loading shows `Controls unavailable`. Pinned bands are pinned, not fixed-height: they wrap content and may grow with system font scale.
- Generated APKs/build outputs/proprietary binaries stay untracked. Prefix host shell commands with `rtk`; use the visorcraft commit identity; do not push until the release step.
- Claude implements each task through fresh subagents; OpenCode independently reviews each task. A broad dual-model review precedes device deployment.

---

### Task 1: Signal Console design system

**Files:**
- Create: `res/values/colors.xml`
- Create: `src/com/visorcraft/hy450control/Ui.java`
- Modify: `res/values/styles.xml`

**Interfaces:**
- Produces: shared colors, `Ui.scale(Context)`, `Ui.px(Context, float)`, `Ui.size(TextView, float)`, `Ui.text(...)`, `Ui.kicker(...)`, `Ui.tile(...)`, `Ui.chip(...)`, `Ui.focusBackground(Context)`, and `Ui.Row` with public `label`/`value` fields and `setValue(String)`.

- [x] **Step 1: Define palette and theme**

Create every color from the spec with stable resource names: `page`, `card`, `card_focused`, `card_pressed`, `readonly`, `disabled`, `text_primary`, `text_secondary`, `text_tertiary`, `text_disabled`, `accent`, `ink_accent`, `success`, `warning`, and `error`. Update `styles.xml`:

```xml
<style name="AppTheme" parent="android:style/Theme.Material.NoActionBar">
    <item name="android:windowBackground">@color/page</item>
    <item name="android:colorAccent">@color/accent</item>
    <item name="android:fontFamily">sans</item>
</style>
```

- [x] **Step 2: Implement density-correct sizing**

Use this exact scale logic; do not use `scaledDensity` for the conversion:

```java
static float scale(Context context) {
    DisplayMetrics m = context.getResources().getDisplayMetrics();
    float scale = Math.min(m.widthPixels / 1920f, m.heightPixels / 1080f);
    return Math.max(0.8f, Math.min(2.0f, scale));
}

static int px(Context context, float designPx) {
    return Math.round(designPx * scale(context));
}

static void size(TextView view, float designPx) {
    DisplayMetrics m = view.getResources().getDisplayMetrics();
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP,
            designPx * scale(view.getContext()) / m.density);
}
```

- [x] **Step 3: Implement shared widgets and states**

Read colors with `context.getColor(...)`. Use platform `sans-serif-medium`, `sans-serif-black`, and `Typeface.MONOSPACE`. `Ui.text` accepts text, design-pixel size, color resource, and optional typeface. `Ui.kicker` produces uppercase tertiary section text with modest letter spacing. `Ui.tile` and `Ui.chip` produce focusable Buttons with the shared background; only tiles animate focus scale 1.00↔1.03 over 120 ms.

Build normal/focused/pressed/disabled states with the final custom stateful `CardDrawable`, which paints the card and leading 6 px accent rail directly. Resolve base colors in this order: disabled, pressed, focused, default. Require positive `android.R.attr.state_enabled` before showing a rail; Android represents disabled state sets by omitting it. Disable child/padding clipping at the dashboard row and padded root so tile scaling cannot clip the leading rail.

`Ui.Row` is a horizontal, non-focusable-container `LinearLayout` with a weighted label and right-aligned monospace value. The Activity, not the container, controls focus. Provide:

```java
static final class Row extends LinearLayout {
    final TextView label;
    final TextView value;
    void setValue(String formatted) { value.setText(formatted); }
}
```

Give labels 46 px and values 52 px at default 1080p scale. Keep value text readable with primary or tertiary color; never use disabled color for read-only values.

- [x] **Step 4: Build and commit**

Run `rtk ./build.sh && rtk git diff --check`. Expected: resources/Java compile, APK signature verification, no whitespace errors. Commit only the three files with subject `Add the Signal Console design system` using the visorcraft identity.

---

### Task 2: Dashboard redesign

**Files:**
- Modify: `src/com/visorcraft/hy450control/MainActivity.java`
- Delete: `src/com/visorcraft/hy450control/CapabilityRegistry.java`
- Delete: `src/com/visorcraft/hy450control/SettingsIntControl.java`

**Interfaces:**
- Consumes: Task 1 `Ui` factories and existing `ControlPageActivity.loadControls`.
- Produces: sectioned dashboard with live LAMP/VOLUME, four TUNE tiles, two AUTOMATE tiles, and demoted OEM tools.

- [x] **Step 1: Build identity and live bands**

Use a vertical ScrollView with 64 px page margins. Add the product kicker, a cyan accent field with near-black `HY450 CONTROL`, and runtime-derived `<width> × <height> · ANDROID <release>`. Add non-focusable LAMP and VOLUME readouts using 52 px monospace values.

Store `TextView lampValue` and `volumeValue`. In `onResume`, retain `OemFocus.onResume(this)` and call `refreshLiveValues()`: read LAMP via `ControlCatalog.picture(this).get(0).adapter.read()` and volume via `AudioManager.STREAM_MUSIC`. Show `Unavailable` with the existing error wording on failure; never read `Settings.System.SCREEN_BRIGHTNESS`.

- [x] **Step 2: Add sectioned destinations**

Create horizontal two-tile rows with equal weights and 20 px gaps. TUNE order: Picture, Audio, Input family, Projection & calibration. Metadata may be concise (`15 controls`, `Live tuning`) but must not imply unverified backup state. Family availability is checked independently with `ControlPageActivity.loadControls`; failed tiles are disabled and show `Unavailable: <reason>`.

AUTOMATE: Automation & power opens `AutomationActivity`; Open quick overlay calls `startService(new Intent(this, OverlayService.class))`. ADVANCED · OEM TOOLS: OEM input source opens `com.softwinner.awsource/.MainActivity`; Android settings opens `com.android.tv.settings/.MainSettings`, retaining unavailable-activity handling.

Remove dashboard Square the image, Calibration toolkit, and OEM focus/correction buttons; those destinations remain in `ProjectionActivity`. Do not remove `ProjectionActivity` itself. Explicitly focus the Picture tile on initial display, with a safe fallback to the first enabled tile if Picture is unavailable.

- [x] **Step 3: Remove dead adapters and verify**

After source search confirms no remaining uses, delete `CapabilityRegistry.java` and `SettingsIntControl.java`. Do not change backup/preset JSON or family adapter construction.

Run `rtk ./build.sh && rtk git diff --check`. Inspect the built resources for the new palette. Commit the modified/deleted files with subject `Build the Signal Console dashboard`.

---

### Task 3: Console control pages and hot-path optimization

**Files:**
- Modify: `src/com/visorcraft/hy450control/ControlPageActivity.java`

**Interfaces:**
- Consumes: `Ui.Row`, `Ui.chip`, colors, and sizing.
- Produces: pinned TARGET and action bands, cached row values, prominent status strip, and O(1) per-adjustment rendering/vendor work.

- [x] **Step 1: Replace row views with console cards**

Change `List<TextView> rows` to `List<Ui.Row> rows` and add `List<String> displayedValues`. Each row shows title left and formatted value right, has 112 px minimum height and 20 px margins, and uses shared focus backgrounds. Writable rows are focusable; read-only rows use `readonly` background and `text_tertiary`, never receive focus.

- [x] **Step 2: Add pinned TARGET and chip bands**

Place a fixed header above the ScrollView: family kicker plus static TARGET marker, `targetLabel`, and 76 px cyan monospace `targetValue`. Add a fixed horizontal rail containing Save preset, Apply preset, Restore originals. Chips remain enabled only when controls loaded successfully, matching current empty-family protection.

`updateTarget(int position)` reads only `displayedValues`; it performs no vendor call. TARGET retains the last row when a chip has focus. Before any row: family label + `Select a control`. On load failure: `Controls unavailable` and a focusable error surface. If all rows are read-only, keep initial focus on the enabled rail.

- [x] **Step 3: Add prominent semantic status strip**

Keep the status outside the ScrollView and non-focusable. Add a colored dot plus the existing status TextView; summary/detail text remains intact and may wrap with font scaling. Implement `setStatus(String message, StatusKind kind)` where `StatusKind` is `NEUTRAL`, `SUCCESS`, `WARNING`, or `ERROR`; map to accent/success/warning/error. Replace direct `status.setText` calls with the appropriate helper while preserving every message and capped-detail behavior.

- [x] **Step 4: Cache values and optimize adjustment**

During full `refresh()`, read each control once, store the formatted string or existing `Unavailable: ...` text in `displayedValues`, update the row, and update TARGET. Preserve read-only and error wording.

During successful `adjust()`, keep the existing read → clamp → write → adapter readback flow. Update only the affected row from the `actual` value already read, update its cached string, TARGET, and status. Do not call full `refresh()`. On failure, refresh only the affected row and status. Restore/preset/initial load still use full refresh. No new per-key animation or unrelated vendor calls.

- [x] **Step 5: Build and commit**

Run `rtk ./build.sh && rtk git diff --check`. Verify no `List<TextView>` assumptions remain and no `refresh()` call remains at the end of `adjust()`. Commit with subject `Turn control pages into responsive consoles`.

---

### Task 4: Overlay visual system and countdown lifecycle

**Files:**
- Create: `src/com/visorcraft/hy450control/CountdownBar.java`
- Modify: `src/com/visorcraft/hy450control/OverlayService.java`

**Interfaces:**
- Consumes: `Ui`, existing Handler/timeout, backlight/volume/input actions.
- Produces: `CountdownBar.setFraction(float)` drawing without per-tick allocation; overlay lifecycle cleanup for both timeout and ticker.

- [x] **Step 1: Implement allocation-free countdown drawing**

`CountdownBar` extends `View`, owns a `Paint` and fraction 0–1, and draws a track plus accent fill in `onDraw`. `setFraction(float)` clamps and calls `invalidate()` only when the value changes. Do not allocate objects or alter LayoutParams in `onDraw`/`setFraction`.

- [x] **Step 2: Restyle overlay and add ticker**

Use a 560 px-wide graphite panel with shared focus chips, readable status, and a thin countdown bar. Add `long deadline` and a periodic Runnable on the existing Handler. `armTimeout()` sets `deadline = SystemClock.uptimeMillis() + 15000`, removes/reposts both timeout and ticker, and updates the fraction from remaining time every 250 ms. BACK and every button continue to rearm.

- [x] **Step 3: Fix external-input focus and cleanup**

Move Input source into a helper that removes the timeout and ticker callbacks and detaches the overlay panel before it calls `startActivity(NEW_TASK)`, then calls `stopSelf()` afterwards; a missing or blocked OEM activity is reported with a visible Toast and the service still stops. `onDestroy` removes both Runnables, detaches only an attached panel, and nulls view references. Preserve `START_NOT_STICKY`, all vendor write/readback behavior, and existing status text.

- [x] **Step 4: Build and commit**

Run `rtk ./build.sh && rtk git diff --check`. Commit with subject `Add the live console overlay and countdown`.

---

### Task 5: Automation, projection, and remote honesty

**Files:**
- Modify: `src/com/visorcraft/hy450control/AutomationActivity.java`
- Modify: `src/com/visorcraft/hy450control/ProjectionActivity.java`
- Modify: `src/com/visorcraft/hy450control/RemoteAccessibilityService.java`
- Modify: `res/values/strings.xml`

**Interfaces:**
- Consumes: Task 1 UI system and existing startup/sleep/projection actions.
- Produces: consistent secondary screens, safe accessibility-settings deep link, and demoted OEM tools.

- [x] **Step 1: Restyle Automation without changing automation behavior**

Add OVERLAY (Open quick overlay, Close overlay), AUTOMATION (Startup app, Sleep timer), and REMOTE SHORTCUTS sections using shared cards/chips. Preserve preference keys, cycling, `SleepTimer.set`, startup package list, and current status text. Store an explicit `openOverlayButton` and focus it initially; no `getChildAt` focus.

- [x] **Step 2: Replace accessibility string surgery**

Delete `toggleAccessibility()` and all writes to `ENABLED_ACCESSIBILITY_SERVICES`/`ACCESSIBILITY_ENABLED`. Add a focusable `Open accessibility settings` action launching `Settings.ACTION_ACCESSIBILITY_SETTINGS`, catching `ActivityNotFoundException` with a visible Toast. The unsupported remote-key control is disabled and warning-labelled; the settings link remains enabled. Opening Automation must not modify Projectivy or any accessibility setting. Update `remote_description` to state that the experimental overlay shortcut is unavailable on this firmware. Change `Log.e` to `Log.d` in `RemoteAccessibilityService`.

- [x] **Step 3: Restyle Projection and preserve all entry points**

Add visorcraft sections for Save/Restore projection backup, Square the image (`RectangleActivity`), and Calibration toolkit. Add a demoted OEM TOOLS section for OEM focus/correction, OEM manual keystone, and Full screen (reset keystone), with text explaining reset behavior and automatic keystone restoration after OEM focus exits. Preserve every action, `OemFocus.onResume`, and error handling. Focus Save projection backup explicitly.

- [x] **Step 4: Build and commit**

Run `rtk ./build.sh && rtk git diff --check`. Commit with subject `Unify secondary screens and clarify remote limitations`.

---

### Task 6: Dual review, device validation, and documentation

**Files:**
- Modify: `README.md`
- Modify: private archive `NOTES.md` and `MANAGEMENT.md` before device-state changes
- Verify: all changed source

**Interfaces:**
- Consumes: complete Tasks 1–5.
- Produces: reviewed, device-verified Signal Console build and accurate records; no release yet.

- [x] **Step 1: Run clean build and broad reviews**

Run `rtk ./build.sh && rtk git diff --check && rtk git status --short`. Generate the complete branch diff. Have Claude and OpenCode independently review for spec compliance, focus/lifecycle, rendering performance, behavior regressions, and scope. Fix Critical/Important findings through one coordinated fix wave and re-review.

- [x] **Step 2: Record baseline and deploy**

Record current app version/hash, accessibility services, framework hash, lamp/gamma values, startup/sleep state, and current module backup. Update private NOTES/MANAGEMENT before installation. Install with `rtk ./install.sh <projector-ip>:5555`, verify module hash, reboot, and verify active APK hash/version.

- [x] **Step 3: Verify visuals and navigation**

Capture and inspect every redesigned screen at 1920x1080: dashboard, each family page, Automation, Projection, and overlay. Verify identity, palette, readable values, no clipping, scalable pinned bands, and disabled/explained states. Scripted D-pad audit covers every tile/chip/row, ≤12 presses to every dashboard destination, chip-rail UP/DOWN and LEFT/RIGHT behavior, read-only skip, no zero-focus error page, BACK, and focus return.

- [x] **Step 4: Verify hardware and lifecycle**

Safely repeat backlight 100→101→100, gamma maximum/restore, original-value restore, preset read-only skip, and overlay 100→101→100. Check countdown visible/reset/15-second dismissal, BACK, and Input source opening OEM UI without overlay focus. Confirm calibration fullscreen/exit. Do not invoke Square the image merely for visual testing because it writes keystone.

Confirm Projectivy/accessibility list and framework hash unchanged, preserve the live startup/sleep settings (Projectivy startup and sleep off on the development unit), and find no AndroidRuntime crash in the app. Use `dumpsys gfxinfo` plus repeated navigation to investigate visible jank; do not invent performance measurements the platform does not expose.

- [x] **Step 5: Update documentation**

Update README feature descriptions and verified-hardware bullets only for checks actually performed. Record screenshots/findings, installed build hash, remaining limitations, and undo path in private NOTES/MANAGEMENT. Commit documentation with subject `Document the Signal Console experience`.

---

### Task 7: Version and GitHub release

**Files:**
- Modify: `module/module.prop`
- Modify: `README.md` Quick start version and APK filename
- Modify: private release records

**Interfaces:**
- Consumes: device-verified Task 6 build on `master`.
- Produces: public v1.3.0/versionCode 8 signed GitHub Release.

- [x] **Step 1: Bump and verify**

Set `version=1.3.0` and `versionCode=8`. Update Quick start to v1.3.0/version code 8 and `hy450-control-1.3.0.apk`. Run clean build, `aapt2 dump badging`, signature verification, and diff checks. Commit with subject `Release HY450 Control 1.3.0`.

- [x] **Step 2: Push and wait for master CI**

Push `master` to origin, identify the new master workflow, and wait for it to pass. Do not tag before master CI succeeds.

- [x] **Step 3: Tag and publish**

Create annotated tag `v1.3.0` at the release commit and push it. Wait for the tag build and Publish GitHub release jobs to pass.

- [x] **Step 4: Verify published assets**

Download `hy450-control-1.3.0.apk` and `sha256sums.txt` into ignored build output. Run checksum verification, `apksigner verify --print-certs`, and `aapt2 dump badging`. Confirm release is public/non-draft and tag points at the release commit. Record URL/hash/run IDs in private NOTES/MANAGEMENT and report the deployed device version separately from the public release.
