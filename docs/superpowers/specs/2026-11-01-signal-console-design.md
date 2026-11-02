# HY450 Control Signal Console design

## Outcome and authority

Replace the OEM-style gray-button interface with a bold visorcraft/developer/creator console while preserving the verified hardware interaction and safety model. Claude and OpenCode proposed the convergent Signal Console/Studio Deck direction; the user delegated design approval and full implementation, testing, deployment, and release to them.

## Goals and constraints

- Use scale, alignment, graphite surfaces, and one electric-cyan accent for visual impact rather than rainbow decoration.
- Preserve every hardware capability, vendor write/readback/rollback path, backup/preset schema, control ID, safety message, and D-pad interaction contract.
- SDK-only Java 8/Android 34; no Gradle, dependencies, downloaded fonts/images, or test scaffolding.
- Optimize for 1920x1080 D-pad use on the H713. Live device density is **320 dpi**; the earlier 160–240 dpi assumption was incorrect.
- Keep calibration patterns austere and fullscreen. No launcher icons, glyphs, remote remapping, or new hardware features.

## Directions considered

1. **Signal Console / Studio Deck (selected).** Graphite surfaces, one cyan accent, large type, monospace values, strong focus rails, and sectioned tiles. Best legibility, semantic-color clarity, and implementation cost.
2. **Phosphor Terminal.** Amber/green CRT styling conflicts with success/warning colors and risks becoming costume. Borrow monospace numerals only.
3. **Poster Pop / Neo-Arcade.** Multicolor saturated tiles reduce contrast, create colorblind/semantic collisions, and make dense pages noisy. Rejected; one cyan accent field supplies the punch.

## Shared UI architecture

Create `Ui.java` as the single programmatic factory for design tokens, text sizing, tiles, chips, rows, focus backgrounds, and status components. Add `res/values/colors.xml`; `Ui` reads resources with `Context.getColor()` (safe with minSdk 34) so Java and the theme share one palette. `styles.xml` uses the same colors. Existing `build.sh` automatically compiles the new Java file and resources.

Palette:

- Page `#0A0D12`; cards `#222B39`; focused card `#2B3547`; pressed `#313C50`; read-only `#181E28`; disabled `#141A23`.
- Primary text `#F4F7FB`; secondary `#A8B4C6`; tertiary `#8D98AA`; disabled `#5A6474`.
- Brand/focus `#66CCFF`; ink on accent `#0A0D12`.
- Success `#5FE3A1`; warning `#FFC24D`; error `#FF7A7A`, always paired with existing words.

Use platform `sans-serif-medium`, `sans-serif-black`, and `monospace`; no thin faces. `Ui.size()` authors against 1920x1080 design pixels and computes `screenScale = clamp(min(widthPixels / 1920f, heightPixels / 1080f), 0.8f, 2.0f)`. It converts `designPx * screenScale` to SP using unscaled `density`, not `scaledDensity`, so the system's accessibility font scaling is applied by Android exactly once at render time. The type is anchored to physical design pixels at the default font scale; the measured 320 dpi is used in that conversion rather than assumed.

At 1080p with the default font scale: dashboard title 76 px, tile title 60 px, section kicker 30 px, row label 46 px, row value 52 px monospace, TARGET value 76 px monospace, chip 38 px, status summary 46 px, status detail 40 px, hint 32 px. Spacing uses an 8 px design grid, 64 px page margins, 20 px card gaps, 16 px tile radius, and 10 px row radius.

`Ui.focusBackground()` and `Ui.rowBackground()` use the final stateful `CardDrawable`, which paints the normal/focused/pressed/disabled card and 6 px cyan leading rail directly without per-draw allocation. Disabled cards never show a rail. Background swaps do not request layout. Only dashboard tiles may animate focus scale 1.00↔1.03 over 120 ms; the dashboard row and padded root allow the scaled rail to extend into the existing margin instead of clipping it. No continuous decorative animation outside the overlay countdown, and no glow, blur, gradients, or row bounce.

## Dashboard

`MainActivity` becomes a scrollable, sectioned console:

1. **Identity band:** `VISORCRAFT · HY450 MAX` kicker, a large cyan accent field containing `HY450 CONTROL` in near-black ink, and metadata derived at runtime from display metrics and `Build.VERSION.RELEASE` (for example, `1920 × 1080 · ANDROID 14`), never hardcoded device claims.
2. **Live instrument strip:** `LAMP <value>` read through the same vendor backlight adapter used by Picture controls, and `VOLUME <current>/<max>` from AudioManager. Remove the ambiguous dashboard SCREEN_BRIGHTNESS readout. `MainActivity.onResume` re-reads both values after initial creation and whenever returning from another activity or overlay. Delete `CapabilityRegistry.java` and `SettingsIntControl.java` only after source search confirms they have no remaining code references; family availability comes from `ControlPageActivity.loadControls`, not `CapabilityRegistry`. Backup/preset JSON stores only family/control IDs and integers, never adapter class names; these classes were used only for the dashboard value, while family restore always constructs vendor adapters through `ControlPageActivity.loadControls`/`ControlCatalog`. Therefore persisted values cannot reach the deleted settings-adapter path.
3. **TUNE:** two-column tiles for Picture, Audio, **Input family**, and Projection & calibration. Tiles show large titles and concise metadata, not fake snapshot status. Unavailable families remain visibly disabled with a reason.
4. **AUTOMATE:** two secondary tiles for Automation & power and Open quick overlay. The overlay action calls the existing internal service directly.
5. **ADVANCED · OEM TOOLS:** visually demoted **OEM input source** and Android settings shortcuts. OEM focus/correction and Square the image move exclusively into Projection & calibration, removing duplicate dashboard actions.

Explicit view references replace fragile child-index focus. Every destination must be reachable in at most 12 D-pad presses; grid navigation is verified on-device.

## Control pages

`ControlPageActivity` retains single-column rows and LEFT/RIGHT/CENTRE adjustment. Two-column controls are forbidden because horizontal keys already adjust values.

Page anatomy:

1. Fixed family kicker and a **TARGET** band containing the static marker `TARGET`, the focused control's label, and its large cyan value. The band is display-only and never steals focus.
2. Fixed horizontal chip rail: Save preset, Apply preset, Restore originals. UP from the first writable row reaches the rail; DOWN from the rail reaches the first writable row. On chips, horizontal keys perform native focus movement; on rows, they adjust. If a family has no writable rows, the enabled rail retains initial focus and read-only rows remain unfocusable.
3. Scrollable single-column cards with left-aligned labels and right-aligned monospace values. Read-only rows use `#181E28` with tertiary text `#8D98AA` and remain unfocusable; disabled-text `#5A6474` is reserved for genuinely inactive controls. Existing unavailable/readback wording remains visible.
4. Fixed status strip with a semantic dot and existing persistent summary/details. Counts and safety words are never truncated; details remain capped at three plus a remaining count, with enough height for all four detail lines.

Cache each row's displayed value string. Focus changes update TARGET from that cache with no vendor call. `adjust()` refreshes only the affected row, TARGET, and status after write/readback; it no longer rereads every family control per keypress. Full refresh remains for initial load, restore, and preset apply. Failed/empty families get a focusable error surface so the page never has zero focused views.

## Overlay

`OverlayService` uses the same card/focus system with a 560 px panel and readable status. Add a thin custom-drawn countdown bar driven by a new periodic runnable on the existing Handler every 250 ms; it must not change LayoutParams or allocate per-frame objects. Any interaction rearms it to 15 seconds. BACK and Close still dismiss.

“Input source” removes the timeout and countdown callbacks and detaches the overlay panel before it dispatches `startActivity(NEW_TASK)`, and calls `stopSelf()` afterwards, so the overlay is never composed above the OEM screen and the OEM screen receives D-pad focus. A missing or blocked OEM activity is reported with a visible Toast. `onDestroy` removes both timeout and countdown callbacks and detaches the panel safely.

## Automation and projection

`AutomationActivity` receives the same section/card treatment and explicit focus references. An **OVERLAY** section retains Open quick overlay and Close overlay even though Open is also available from the dashboard. **AUTOMATION** keeps Startup app and Sleep timer primary. The remote group is visually demoted and warning-labelled **Remote shortcuts — not available on this firmware**, explaining that the OEM consumes Menu/Guide before apps. It is not presented as a remapper.

Replace direct `ENABLED_ACCESSIBILITY_SERVICES` string surgery and forced `ACCESSIBILITY_ENABLED` writes with a focusable link to `Settings.ACTION_ACCESSIBILITY_SETTINGS`, catching `ActivityNotFoundException`. The warning group is not one uniformly disabled container: the settings link remains usable, while unsupported key-selection controls are disabled. Opening the screen must not alter Projectivy's accessibility entry. Update `remote_description` to describe only the experimental overlay shortcut and firmware limitation. Change routine key logging from error to debug level.

`ProjectionActivity` puts visorcraft backup/restore, **Square the image (`RectangleActivity`)**, and Calibration first, preserving RectangleActivity's launch path. OEM focus/correction, OEM manual keystone, and Full screen/reset move into a demoted OEM section with explicit reset/keystone-restore explanations. Existing OemFocus resume behavior remains unchanged.

## Out of scope

No changes to calibration pattern geometry/colors, RectangleActivity behavior, vendor APIs, control IDs, backup/preset formats, startup/sleep behavior, boot input, remote remapping implementation, DRM, dependencies, or release signing. No new visual assets or launcher icon.

## Error handling and performance

All existing vendor exceptions, range checks, rollback behavior, and persistent status copy remain authoritative. New intents catch unavailable-activity failures. Unavailable families and disabled actions remain explained rather than disappearing. Display caches contain strings only and never become a substitute for hardware readback after writes.

Per-adjustment work is O(1) in views and vendor calls. No per-frame layout changes or continuous decorative animation outside the overlay countdown are allowed. Countdown drawing allocates no objects per tick.

## Verification

- Clean SDK build, signature, APK metadata, diff checks, and no generated files in Git.
- Independent Claude and OpenCode code reviews, including focus/lifecycle and unintended behavior changes.
- Real-device screenshot inspection of every redesigned screen at 1920x1080.
- Scripted D-pad audit: all tiles/chips/rows reachable, read-only skipped, error pages retain focus, BACK works, and no focus traps.
- Safe hardware checks: backlight 100→101→100, gamma limit/restore, original-value restore, preset read-only skip, overlay BACK/15-second countdown/dismiss, overlay input-source focus, and calibration exit. Restore original values.
- Confirm the accessibility service list and Projectivy remain unchanged, and preserve the live startup/sleep settings. On the development unit that means Projectivy startup remains selected and sleep remains off; older disabled-startup assumptions were incorrect.
- Check rendering with `dumpsys gfxinfo` and repeated navigation; investigate visible jank or excessive allocation rather than assuming performance.
- Deploy through the reversible Magisk module and verify on-device APK hash before/after reboot.
- Only after device verification, bump the minor version, update docs/private records, commit/push master, tag, and verify the signed GitHub Release.

## Final outcome

Released as **v1.3.0/versionCode 8** at commit `fa2d2fb`. Real-device verification covered every redesigned screen, focused and disabled rail pixels, all eight dashboard destinations in at most four D-pad presses, chip-rail traversal, 130% font scaling, backlight/gamma restoration, overlay countdown and OEM handoff, calibration, and preservation of Projectivy startup/accessibility and the framework patch. The published APK checksum, release signature, package/version, CI jobs, and tag target were verified. Input preset save works, but applying it can fail `Out of range` on the firmware's current-input value; this limitation is documented rather than hidden.
