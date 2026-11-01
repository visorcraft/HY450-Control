<h1 align="center">HY450 Control</h1>

<p align="center">
  <b>Tune it. Back it up. Make it yours.</b>
  <br />
  A privileged control panel for the <b>HY450 Max</b> Android projector (Allwinner H713).
  <br />
  Picture &amp; audio controls · projection backup · calibration tools · presets &amp; automation.
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

The current signed release is **v1.1.3** (version code **6**). Download
`hy450-control-1.1.3.apk` and `sha256sums.txt` from the
[latest GitHub release](https://github.com/visorcraft/HY450-Control/releases/latest)
into the same directory, then verify the download:

```bash
sha256sum -c sha256sums.txt
```

In a source checkout, place the downloaded APK in the Magisk module:

```bash
mkdir -p module/system/priv-app/HY450Control
cp /path/to/hy450-control-1.1.3.apk \
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
with a different key.

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

- **Picture controls** - backlight, brightness, contrast, hue, saturation, sharpness, picture mode, colour temperature, gamma, noise reduction, dynamic backlight, aspect ratio, HDMI video/PC mode, and video range.
- **Audio controls** - audio mode, seven-band EQ, balance, output delay, output mode, surround, and automatic volume limiting.
- **Input controls** - switch the active source, freeze the current input, control idle blue-screen behavior, and inspect the firmware's boot default without exposing it to D-pad adjustment.
- **Readable, bounded controls** - On/Off and vendor-enum labels, clamped adjustments that never wrap, and persistent readback/limit/error feedback in a fixed status footer. Picture, audio, and input families load independently.
- **Original-value restore** - restore a family's firmware-bound snapshot while continuing past unsupported controls; existing snapshots are never overwritten.
- **Projection backup** - save and restore projection settings as JSON without resetting keystone during backup.
- **Fullscreen calibration toolkit** - D-pad-friendly focus grid, sharpness, RGB, alignment, greyscale, and solid-colour patterns with system bars hidden. **Square the image** sets the keystone from the tilt sensor, using insets measured on the wall for this ceiling mount. Opening OEM focus and correction puts the previous keystone back when that screen exits, because the OEM screen clears it on the way out.
- **Quick overlay** - backlight, volume, input selection, visible readback, BACK dismissal, and a 15-second inactivity timeout without opening the full control panel.
- **Named presets** - save and reapply picture, audio, and input settings while skipping read-only entries during application.
- **Automation** - choose an app to launch after boot and set a sleep timer. Both are opt-in and can be switched off.

## Safety model

- Vendor values are read through the projector's own Allwinner APIs.
- UI-generated numeric and enum adjustments clamp at their supported limits; snapshot and preset writes retain adapter range validation.
- Controls read back their values, roll back mismatches where supported, and surface failures instead of hiding them.
- Original-value snapshots are firmware-bound and never overwritten; restore validates the snapshot before attempting independent control writes.
- Backup and preset data stays in the app's private storage.
- Installation is a reversible Magisk overlay, not a raw partition write.
- There is no arbitrary root-command interface and no DRM circumvention.

## Current limitations

- **Remote-button remapping is unsupported.** The accessibility service can
  bind, but the OEM consumes Menu/Guide before delivery. A lower-level input
  monitor also did not receive a matching physical button event and was
  removed. Do not treat the remote page as a working remapper.
- **Boot-default input is read-only.** The firmware exposes a getter but no
  compatible setter.
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
| `src/com/visorcraft/hy450control/` | Complete Java app: dashboard, hardware adapters, backup/preset stores, overlay, automation, and calibration UI. |
| `res/` + `AndroidManifest.xml` | App resources, component declarations, and privileged permission requests. |
| `module/` | Magisk metadata and priv-app permission allowlist. The generated APK is excluded. |
| `build.sh` | SDK-only Java 8/APK build and debug signing; no Gradle or third-party runtime libraries. |
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

The development-unit checks included:

- Backlight write/readback/restore (`100 → 101 → 100`).
- Original-value restoration of all 15 picture values, including backlight.
- Gamma clamping at its maximum without wrapping (`3 → 4 → 5`, another step stayed at `5`), followed by restoration to `3`.
- Readable enum and On/Off values, boolean limit handling, and D-pad navigation that skips the read-only boot-default row.
- Partial audio backup/restore continuing past unsupported controls, with restored/failed counts and capped error details.
- EQ-band and input-freeze writes followed by restoration.
- Projection JSON backup/restore without changing keystone.
- Quick-overlay D-pad write/readback (`100 → 101 → 100`), BACK dismissal with focus restored, and 15-second automatic dismissal.
- All six calibration patterns rendered fullscreen, pattern navigation, and BACK exit.
- Startup-app launch after a real reboot, then automation disabled.
- Sleep receiver putting the projector to sleep and POWER waking it.
- Preset save/apply restoring the original backlight value.
- Module removal and reinstallation, with Projectivy accessibility and the
  existing framework fix left intact.

## Release automation

GitHub Actions builds and verifies the APK on every push to `master` and every
pull request. A pushed `v*` tag additionally creates a signed GitHub Release
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
