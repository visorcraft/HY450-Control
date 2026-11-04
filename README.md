<h1 align="center">HY450 Control</h1>

<p align="center">
  <b>Tune it. Back it up. Make it yours.</b>
  <br />
  A privileged control panel for the <b>HY450 Max</b> Android projector (Allwinner H713).
  <br />
  Picture &amp; audio controls · projection backup · calibration tools · automation · Playground.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%2014%20(SDK%2034)-3DDC84?logo=android&logoColor=white" alt="Platform: Android 14" />
  <img src="https://img.shields.io/badge/SoC-Allwinner%20H713-orange" alt="SoC: Allwinner H713" />
  <img src="https://img.shields.io/badge/root-Magisk-00A86B" alt="Root: Magisk" />
  <img src="https://img.shields.io/badge/language-Java%208-red" alt="Language: Java 8" />
  <img src="https://img.shields.io/badge/license-GPL--3.0--only-blue" alt="License: GPL-3.0-only" />
</p>

---

Developed and verified end-to-end on a retail HY450 Max, including real
hardware writes with readback and restoration, projection backup/restore,
boot automation, sleep/wake, and systemless removal. This repository contains
the complete original source and diagnostic utilities - **no prebuilt APKs,
firmware, or vendor binaries**.

> **Warning / disclaimer:** For projectors you own. This app uses privileged
> Android permissions and vendor APIs to change live hardware settings. Save
> your original values before experimenting; an incompatible setting can make
> the picture or audio unusable until restored. No warranty is provided; see
> LICENSE. The authors are not affiliated with the device vendor. Do not accept
> an OEM OTA with custom Magisk modules active. See [LEGAL.md](LEGAL.md) for
> the repository's source-only and lawful-use boundaries.

---

## Compatibility

Verified on the retail development unit (Android 14 build
`Projector_20260821.111618`):

| Component | Requirement |
|---|---|
| **Device** | HY450 Max (Magcubic/Hotack family) |
| **SoC** | Allwinner H713 (`sun50iw15p1`, platform `hermes`) |
| **Operating System** | Android 14 (SDK 34) |
| **Userspace ABI** | 32-bit ARM (`armeabi-v7a`) |
| **Root** | Magisk with `su` access for adb shell |
| **Vendor API** | OEM `com.softwinner.tv` framework library |
| **Installation** | Systemless Magisk `priv-app` module |

> **Not the HY300.** This targets the newer H713 projector generation, not the
> Android 11 / kernel 4.9 HY300 family. Closely related H713 units may expose
> different enums or firmware behavior: begin with read-only checks, back up
> original values, and do not assume that every control is compatible.

---

## Quick start

The current signed release is **v1.5.1** (version code **11**). Download
`hy450-control-1.5.1.apk` and `sha256sums.txt` from the
[latest GitHub release](https://github.com/visorcraft/HY450-Control/releases/latest)
into the same directory, then verify the download:

```bash
sha256sum -c sha256sums.txt
```

The v1.5.1 APK SHA-256 is
`3b8d1c5493f3f760f2bbc48aa424b2e793f9ed74589c00c2594039416077d521`.

In a source checkout, place the downloaded APK in the Magisk module:

```bash
mkdir -p module/system/priv-app/HY450Control
cp /path/to/hy450-control-1.5.1.apk \
  module/system/priv-app/HY450Control/HY450Control.apk
```

An ordinary `adb install` is not sufficient: the app must be installed through
this module to receive its priv-app permissions.

### Build from source (alternative)

Prerequisites:

- A compatible, Magisk-rooted projector with adb access
- A computer with a JDK, `zip`, and Android adb
- Android SDK platform **34** and build-tools **35.0.0**
- `ANDROID_HOME` set, or an SDK at `$HOME/Android/Sdk`
- An Android debug keystore at `$HOME/.android/debug.keystore`

```bash
git clone https://github.com/visorcraft/HY450-Control.git
cd HY450-Control
./build.sh
```

The build reads `version` and `versionCode` from `module/module.prop`, creates
a debug-signed `hy450-control.apk`, and places a copy in the Magisk module.
Both are generated outputs and are intentionally not tracked. Set
`ANDROID_KEYSTORE`, `ANDROID_KEYSTORE_PASS`, and `ANDROID_KEY_ALIAS` to sign
with a different key. Debug-signed describes only the signing identity: the
manifest explicitly sets `android:debuggable="false"`, so local and release
builds are non-debuggable.

### Regression tests

Run the host suite (plain-JVM behavior tests plus hostile capture-metadata cases):

```bash
./tests/run-host.sh
```

Run the Android rendering and fake-data suite against an explicit device serial:

```bash
adb connect <projector-ip>:5555
./tests/run-device.sh <projector-ip>:5555
```

The device suite renders all four ambient scenes and uses temporary private
paths, fake value access, and a fake job scheduler; it does not write live
projection settings or schedule real jobs. Owner Assistant coverage includes
atomic routine/safety stores, confirmation boundaries, portable validation and
zero-write rejection, diagnostic allowlisting, and process-recreated state. The
host suite also covers picture-mode ordering and final whole-batch readback for
linked firmware controls.

### Install

```bash
adb connect <projector-ip>:5555
./install.sh <projector-ip>:5555
adb -s <projector-ip>:5555 reboot
```

After the reboot, open **HY450 Control** from the launcher. Installation uses
an explicit adb device serial so a second Android device cannot be targeted by
accident.

### Remove

```bash
./uninstall.sh <projector-ip>:5555
adb -s <projector-ip>:5555 reboot
```

Removal deletes only the `hy450control` Magisk module. It does not write a
partition, remove another module, or reset the projector.

---

## What you get

- **Signal Console interface** - a graphite-and-cyan visorcraft dashboard with live lamp/volume instruments, bold family tiles, D-pad focus rails, and demoted OEM tools. Control pages add a pinned TARGET band, aligned monospace values, fixed preset/restore actions, and semantic status feedback.
- **Picture controls** - backlight, brightness, contrast, hue, saturation, sharpness, picture mode, colour temperature, gamma, noise reduction, dynamic backlight, aspect ratio, HDMI video/PC mode, and video range.
- **Audio controls** - audio mode, seven-band EQ, balance, output delay, output mode, surround, and automatic volume limiting.
- **Input controls** - switch the active source, freeze the current input, control idle blue-screen behavior, and inspect the firmware's boot default without exposing it to D-pad adjustment.
- **Readable, bounded controls** - On/Off and vendor-enum labels, clamped adjustments that never wrap, and persistent readback/limit/error feedback in a fixed status footer. Picture, audio, and input families load independently; adjustments refresh only the affected row.
- **Original-value restore** - restore a family's firmware-bound snapshot while continuing past unsupported controls; existing snapshots are never overwritten.
- **Projection backup** - save and restore projection settings as JSON without resetting keystone during backup. Values absent when the backup was taken are explicitly cleared on restore, while empty strings remain empty. Every key and value is validated before the first write, and restoring cancels stale OEM keystone recovery first.
- **Fullscreen calibration toolkit** - D-pad-friendly focus grid, sharpness, RGB, alignment, greyscale, and solid-colour patterns with system bars hidden. **Square the image** sets the keystone from the tilt sensor, using insets measured on the wall for this ceiling mount. Opening OEM focus and correction saves the exact keystone and zoom before launch and restores them after exit. Pending recovery survives process death and remains pending after a failed strict readback so a later resume can retry; explicit cancellation clears it.
- **Quick overlay** - backlight, volume, input selection, visible readback, BACK dismissal, and a visible 15-second countdown without opening the full control panel. Launching the OEM input source dismisses the overlay first.
- **Family presets** - save and reapply picture, audio, and input settings while skipping read-only controls and missing entries. Application continues after individual failures and reports applied/failed counts with capped per-control errors; success requires zero failures.
- **Automation** - choose an app to launch after boot and set a sleep timer. Boot launch uses Android's job scheduler so the delayed work survives receiver completion; sleep selections are saved only after the alarm is scheduled successfully. Both are opt-in and can be switched off. Accessibility configuration uses Android's system settings instead of rewriting the enabled-service list.
- **Playground** - manual fullscreen Breakout with 60 bricks, three lives, scoring, and a persistent best score, plus Starfield, Aurora, Fireplace, and Ocean waves. LEFT/RIGHT moves the paddle or changes scenes; CENTRE launches, pauses, or retries; BACK returns to the menu. Everything renders locally with Canvas and no assets, network access, or hardware-setting changes; animation pauses in the background.
- **Owner Assistant** - an explicit read-only setup scan across picture, audio, and input; Movie, Day, Night, and Custom routines with pre-apply undo that survives process death; and portable backup/recovery through user-selected documents. Imports are previewed in memory and fully validated before writes, firmware mismatches cannot be restored, and every confirmed restore saves a fresh pre-restore safety copy. A separate diagnostic export contains only the allowlisted compatibility report.

## Safety model

- Vendor values are read through the projector's own Allwinner APIs.
- UI-generated numeric and enum adjustments clamp at their supported limits; snapshot and preset writes retain adapter range validation.
- Controls read back their values, roll back mismatches where supported, and surface failures instead of hiding them. Routine and portable batches also verify final whole-family state so a later linked write cannot silently undo an earlier one.
- Original-value snapshots are firmware-bound and never overwritten; restore validates the snapshot before attempting independent control writes.
- Portable documents are capped at 1 MiB and restricted to known control/projection IDs, integer values within adapter domains, schema 1, and the exact firmware fingerprint. Restore saves a new safety copy before writing and never rolls back automatically.
- Internal backup and preset data stays in the app's private storage. Portable backups and diagnostics are written only to documents selected by the user; diagnostics exclude serial/IP/MAC addresses, accounts, package inventories, logs, arbitrary properties, and network data.
- Installation is a reversible Magisk overlay, not a raw partition write.
- There is no arbitrary root-command interface and no DRM circumvention.

## Current limitations

- **Remote-button remapping is unsupported.** The accessibility service can
  bind, but the OEM consumes Menu/Guide before delivery. A lower-level input
  monitor also did not receive a matching physical button event and was
  removed. Do not treat the remote page as a working remapper.
- **Boot-default input is read-only.** The firmware exposes a getter but no
  compatible setter.
- **Input preset application can fail on this firmware.** Saving succeeds, but
  the reported current-input value can be outside the writable adapter range,
  causing `Out of range`; the original-input snapshot remains restorable.
- **Audio mode and EQ restoration are partial on the verified firmware.** The
  audio-mode setter and all seven EQ-band setters reject writes; routines and
  portable recovery report these failures instead of claiming full audio success.
- **Some imported picture combinations are not attainable.** Picture-mode writes
  reset backlight/basic values, and backlight/colour-temperature writes change
  picture mode. Picture mode is applied last and final whole-family readback
  reports any remaining conflict rather than silently returning success.
- **Process death removes the app's activity stack.** If BACK from OEM focus
  returns to another app after HY450 Control was killed, reopen HY450 Control
  to run the durable pending recovery; the marker remains until that resume.
- **Factory distortion recognition is not claimed as fixed.** The normal
  ceiling autofocus/calibration path is separate from that factory diagnostic.
- **The built-in camera cannot see a wall trapezoid.** It looks along the
  beam, so Square the image does not use it. The correction is the wall
  measurement for this mount, scaled if the projector tilts.
- **Streaming and DRM are out of scope.** This app does not unlock streaming
  services, certifications, codecs, or content protection.

---

## Source and tools

| Path | Purpose |
|---|---|
| `src/com/visorcraft/hy450control/` | Complete Java app: dashboard, hardware adapters, backup/preset stores, overlay, automation, calibration, and Playground. |
| `res/` + `AndroidManifest.xml` | App resources, component declarations, and privileged permission requests. |
| `module/` | Magisk metadata and priv-app permission allowlist. The generated APK is excluded. |
| `build.sh` | SDK-only Java 8/APK build and debug signing; no Gradle or third-party runtime libraries. |
| `tests/` | Plain-JVM host and Android `app_process` rendering/fake-data regression suites with explicit-device adb runner. |
| `install.sh` / `uninstall.sh` | Explicit-device Magisk module installation and removal. |
| `capture/` | Original ARMv7 V4L2 camera diagnostic and frame-capture scripts. |
| `probe/TvProbe.java` | Small read-only-by-default Allwinner display API probe used for hardware validation. |

### Optional camera capture utility

The camera utility requires Android NDK **27**, an ARMv7-compatible device, and
`ffmpeg` on the host for PNG conversion:

```bash
cd capture
./build.sh
./capture.sh <projector-ip>:5555 /dev/video0 frame.png
```

Only `/dev/video0` and `/dev/video1` are accepted. Temporary device-side files
are removed after capture.

### Optional Allwinner API probe

```bash
cd probe
./build.sh
adb -s <projector-ip>:5555 push build/tvprobe.jar /data/local/tmp/
adb -s <projector-ip>:5555 shell 'su -c "CLASSPATH=/data/local/tmp/tvprobe.jar app_process /system/bin TvProbe"'
```

With no write arguments, the probe prints backlight, picture mode, colour
temperature, gamma, and dynamic-backlight values.

---

## Verified on hardware

The 1.6.0/versionCode 12 Owner Assistant candidate was installed through the
reversible Magisk module and verified at 1920×1080. The locally signed candidate
APK SHA-256 is `86a49600b2929693283ac47dbf91d2df53da5617644395ed06502314b8855301`.
This is a verified source candidate, not a published GitHub release.

The 1.6.0 checks included:

- Explicit setup scan of 33 controls (29 readable, 32 writable, one unavailable, four failed), with all 27 captured family values and 15 projection values unchanged afterward.
- Movie, Day, Night, and Custom routine save/apply/undo using an attainable custom-mode/brightness change. Picture state returned exactly on every apply and undo; the pre-apply undo document was byte-identical and usable after force-stop/recreation.
- Portable export, import, SAF cancellation, malformed and firmware-mismatched imports with Restore disabled and zero live changes, fresh pre-restore safety capture, and explicit recovery without overwriting the safety document. Final export matched every original family/projection value.
- Final review regressions and hardware checks covered prepared safety omissions/failure barriers, occupied-slot protection from empty captures, derived preview counts/missing IDs, bounded read-only warnings, independent unavailable/failed counts, projection-only result counts, and truncating a 20,000-byte provider document to valid shorter JSON.
- A real schema-1 diagnostic export with 33 controls and only the allowlisted metadata/control fields.
- D-pad navigation and usable Owner Assistant, routine, and recovery actions at 100% and 130% font scale, followed by restoration to 100%.
- Hardware tracing exposed linked picture-mode/backlight/colour-temperature side effects. Picture mode is now deferred and final whole-family readback rejects conflicting outcomes; regression and device suites passed after the fix. The eight unsupported audio-mode/EQ writes remained visible failures.
- Projectivy home/startup/accessibility, sleep-off state, the 12-package disabled set, the framework patch, and the complete original projection baseline remained intact. Temporary test state and documents were removed without resetting unrelated app data.

The v1.5.1/versionCode 11 candidate was installed through the reversible Magisk
module and verified at 1920×1080. The locally signed candidate APK SHA-256 is
`25f019cfb647397428245a34e6b624c65af9ff2f0776e9c3201cf01d614c9b2b`; the
stable-key GitHub release APK is a distinct artifact.

The 1.5.1 checks included:

- Dashboard D-pad navigation to Playground, all four ambient scenes, scene wrapping, BACK paths, and byte-identical paused frames across both a timed pause and HOME/resume.
- Breakout paddle control, scoring, pause, three life losses, game over, and retry. Best score 40 survived force-stop/recreation; win-state and collision edge cases passed the pure-Java regressions.
- Readable filled calibration help text on the RGB pattern, pattern wrapping, and BACK exit. An immersive-startup ordering issue found during Playground testing was fixed and the same navigation path was rerun successfully.
- Exact preservation of all 32 projection properties, every other global setting except the expected two-reboot `boot_count` increment, all 12 disabled packages, Projectivy startup/accessibility, sleep-off state, and the framework patch. No new crash appeared after the corrected candidate launched.

The post-release safety and de-duplication source through `cb20e75` was also
installed and verified on the development unit. This earlier local candidate is
distinct from the signed v1.3.0 release artifact, although both report version
1.3.0/versionCode 8.

The development-unit checks also included:

- Backlight write/readback/restore (`100 → 101 → 100`).
- Original-value restoration of all 15 picture values, including backlight.
- Gamma clamping at its maximum without wrapping (`3 → 4 → 5`, another step stayed at `5`), followed by restoration to `3`.
- Readable enum and On/Off values, boolean limit handling, and D-pad navigation that skips the read-only boot-default row.
- Partial audio backup/restore continuing past unsupported controls, with restored/failed counts and capped error details.
- EQ-band and input-freeze writes followed by restoration.
- Projection JSON backup/restore without changing keystone.
- Host and Android fake-data regression suites passed, covering absent/empty/non-empty globals, legacy schema-1 backups, snapshot non-overwrite, strict keystone parsing, preset continuation, and adapter rollback.
- Ordinary OEM-focus recovery, process death followed by explicit app recreation, and immediate manual-keystone cancellation restored or preserved every inset and zoom value and cleared pending recovery. Final projection restoration matched the complete original baseline.
- The installed post-release candidate reported 1.3.0/versionCode 8 without the `DEBUGGABLE` package flag; Projectivy startup/accessibility, sleep-off state, the framework patch, and disabled-package set remained unchanged.
- Quick-overlay D-pad write/readback (`100 → 101 → 100`), BACK dismissal with focus restored, countdown rearm, 15-second automatic dismissal, and OEM input-source launch with the overlay already detached.
- All six calibration patterns rendered fullscreen, pattern navigation, and BACK exit.
- Signal Console screens inspected at 1920×1080, including focused/disabled rails and wrapped long values; every dashboard destination was reachable in at most four D-pad presses.
- Dashboard and Picture controls remained usable at 130% font scale, which was restored to 100% afterward.
- Startup-app launch after a real reboot, then automation disabled after that historical test; the current development unit's Projectivy startup choice is preserved.
- Sleep receiver putting the projector to sleep and POWER waking it.
- Preset save/apply restoring the original backlight value.
- Module removal and reinstallation, with Projectivy accessibility and the
  existing framework fix left intact.

## Release automation

GitHub Actions runs the host regression tests, then builds and verifies the
non-debuggable APK on every push to `master` and every pull request. A pushed
`v*` tag additionally creates a signed GitHub Release
with the versioned APK and its SHA-256 checksum. The release job rejects a tag
that does not match `module/module.prop`.

To publish a new version:

1. Update `version` and `versionCode` together in `module/module.prop`.
2. Commit and push `master`, then wait for its build to pass.
3. Create and push an annotated `v<version>` tag.

Release signing uses the stable `HY450_KEYSTORE_B64` repository secret so users
receive the same signer across releases. The key itself is never committed.

## Credits / related work

- [HY450 Max root &amp; debloat toolkit](https://github.com/visorcraft/root-debloat-hy450-max-h713-toolkit) -
  rooting, recovery, accessibility, and device-integration research that made
  this app possible.
- Allwinner and Magcubic/Hotack are referenced nominatively for hardware
  compatibility. No vendor application code, framework jar, or firmware is
  redistributed here.

## Contribute

Tested on one retail HY450 Max. Reports from other firmware batches and closely
related H713 projectors are welcome - especially missing or incompatible vendor
APIs. Please keep investigations backup-first and begin with read-only probes.
Do not attach firmware dumps, proprietary jars, personal backups, serial
numbers, or other device identifiers to issues or pull requests.

---

## License

Licensed under the **GNU General Public License v3.0 only (GPL-3.0-only)** -
see [`LICENSE`](LICENSE) for the full text.

© 2026 visorcraft. This repository contains original source and documentation.
It ships no firmware dumps, device-pulled binaries, OEM framework jars, or
prebuilt APKs. Not affiliated with or endorsed by Magcubic, Hotack, Allwinner,
Google, or any other vendor mentioned.
