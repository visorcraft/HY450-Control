# Playground Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fullscreen Playground with remote-playable Breakout and four procedural ambient scenes while preserving every existing HY450 Control behavior.

**Architecture:** A single `PlaygroundActivity` owns menu/lifecycle/preferences and a shared `PlaygroundView` owns Canvas/frame scheduling. A pure-Java `BreakoutGame` is independently tested; four implementations of a narrow `AmbientScene` interface provide deterministic procedural drawing without assets or dependencies.

**Tech Stack:** Java 8, Android SDK 34 Canvas/Choreographer, Android build-tools 35.0.0, Bash, adb/app_process.

## Current outcome

Breakout, all four ambient engines, the fullscreen Playground, dashboard integration, regression coverage, and documentation are complete in v1.5.1/versionCode 11. Host and Android suites, shell checks, the signed/non-debuggable build, diff checks, and independent static reviews passed. Real-projector checks covered every scene, wrapping, pause and HOME/resume, Breakout controls/scoring/game over/retry, best-score process persistence, and calibration RGB text. Testing exposed a Playground immersive-call ordering crash; creating the menu before hiding system bars fixed it and the original navigation path passed on rerun. Projection, Projectivy, sleep, framework, and disabled-package baselines remained unchanged. Pure-Java tests cover the win state; a full hardware win was not attempted.

## Global Constraints

- Follow `docs/superpowers/specs/2026-11-02-playground-design.md` exactly.
- Preserve Java 8, the SDK-only build, existing schemas, and all current tests; no Gradle or third-party libraries.
- No vendor writes, network, sound, assets, idle activation, quick-overlay integration, or background playback.
- The user subsequently authorized a bug-fix version bump to 1.5.1/versionCode 11, commit/push, master integration, and the normal tag-driven release.
- Prefix shell commands with `rtk`; use an explicit adb serial in local commands and sanitize it from public docs.
- Write failing behavioral tests before production logic and observe each RED/GREEN cycle.

---

### Task 1: Pure-Java Breakout engine

**Files:**
- Create: `src/com/visorcraft/hy450control/BreakoutGame.java`
- Modify: `tests/HostTests.java`
- Modify: `tests/run-host.sh`

**Interfaces:**
- Produces: `BreakoutGame()` plus `setDirection(int)`, `centre()`, `update(float)`, `score()`, `lives()`, `bricksRemaining()`, `phase()`, `paddleX()`, `ballX()`, `ballY()`, `ballVelocityX()`, `ballVelocityY()`, and `isBrickAlive(int)`.
- `Phase` is `READY`, `RUNNING`, `PAUSED`, `WON`, or `GAME_OVER`; logical dimensions are `WIDTH = 1000` and `HEIGHT = 600`.

- [x] **Step 1: Write failing model regressions**

Add `testBreakoutGame()` to HostTests and call it from `main`. Use a helper that advances in 1/120-second increments:

```java
private static void advance(BreakoutGame game, float seconds) {
    int steps = Math.round(seconds * 120);
    for (int i = 0; i < steps; i++) game.update(1f / 120);
}
```

Cover ordinary behavior through public methods:

```java
BreakoutGame game = new BreakoutGame();
check(game.phase() == BreakoutGame.Phase.READY, "round starts ready");
check(game.score() == 0 && game.lives() == 3 && game.bricksRemaining() == 60,
        "round initial state");
game.setDirection(-1);
advance(game, 10);
check(game.paddleX() == 80f, "paddle clamps at left edge");
game.setDirection(1);
advance(game, 20);
check(game.paddleX() == 920f, "paddle clamps at right edge");
game.setDirection(0);
game.centre();
check(game.phase() == BreakoutGame.Phase.RUNNING, "centre launches");
game.centre();
check(game.phase() == BreakoutGame.Phase.PAUSED, "centre pauses");
float pausedY = game.ballY();
advance(game, 1);
check(game.ballY() == pausedY, "paused ball does not move");
game.centre();
check(game.phase() == BreakoutGame.Phase.RUNNING, "centre resumes");
```

Use fresh games and bounded `advance` loops to verify side/top reflection, a brick score of exactly 10 and one removal, and loss of all three lives into GAME_OVER. For hard-to-arrange paddle-angle, last-brick, and exact collision boundaries, use test-side reflection helpers to arrange private model state; do not add production test-only setters. After GAME_OVER/WON, `centre()` must return to READY with zero score, three lives, and sixty bricks. Verify a removed brick cannot score twice.

- [x] **Step 2: Verify RED**

Add `src/com/visorcraft/hy450control/BreakoutGame.java` to `tests/run-host.sh`, then run `rtk ./tests/run-host.sh`. Expected: compilation fails because BreakoutGame is absent.

- [x] **Step 3: Implement the deterministic model**

Use these constants and state shape:

```java
static final int WIDTH = 1000;
static final int HEIGHT = 600;
private static final int ROWS = 6;
private static final int COLUMNS = 10;
private static final float PADDLE_WIDTH = 160f;
private static final float PADDLE_Y = 550f;
private static final float PADDLE_SPEED = 600f;
private static final float BALL_RADIUS = 10f;
private static final float BALL_SPEED = 480f;
private final boolean[] bricks = new boolean[ROWS * COLUMNS];
private Phase phase = Phase.READY;
private int direction;
private int score;
private int lives = 3;
private float paddleX = WIDTH / 2f;
private float ballX;
private float ballY;
private float ballVelocityX;
private float ballVelocityY;
```

Initialize/reset by filling bricks with true, attaching the ball at `(paddleX, PADDLE_Y - BALL_RADIUS)`, and setting vertical upward velocity. `centre()` launches READY, toggles RUNNING/PAUSED, and resets WON/GAME_OVER to READY. `update` clamps each supplied delta to 0.05 and subdivides movement into steps no larger than 1/240 second. READY moves the attached ball with the paddle; PAUSED/WON/GAME_OVER do no physics.

Resolve side/top walls, one paddle rebound, circle-versus-brick collision, and bottom loss inside each substep. A removed brick sets its slot false, increments score by ten, and reverses the appropriate ball axis once. Paddle rebound uses normalized offset:

```java
float offset = (ballX - paddleX) / (PADDLE_WIDTH / 2f);
offset = Math.max(-1f, Math.min(1f, offset));
ballVelocityX = BALL_SPEED * 0.85f * offset;
ballVelocityY = -(float) Math.sqrt(BALL_SPEED * BALL_SPEED
        - ballVelocityX * ballVelocityX);
ballY = PADDLE_Y - BALL_RADIUS;
```

Bottom loss decrements lives once and enters READY or GAME_OVER. Clearing the last brick enters WON. Getters expose current values without exposing the brick array.

- [x] **Step 4: Verify GREEN**

Run `rtk ./tests/run-host.sh`. Expected: all new Breakout and existing host tests pass.

### Task 2: Procedural ambient engines

**Files:**
- Create: `src/com/visorcraft/hy450control/AmbientScene.java`
- Create: `src/com/visorcraft/hy450control/AmbientScenes.java`
- Modify: `tests/DeviceTests.java`
- Modify: `tests/run-device.sh`

**Interfaces:**
- Produces: `String AmbientScene.title()` and `void draw(Canvas, Paint, int, int, float)`.
- Produces: `static AmbientScene[] AmbientScenes.create()` in menu order.

- [x] **Step 1: Write failing Android rendering tests**

Add a device test that creates each scene, draws 320×180 RGB bitmaps at elapsed 0 and 1 seconds, and asserts animation and nonblank output:

```java
AmbientScene[] scenes = AmbientScenes.create();
String[] expected = {"Starfield", "Aurora", "Fireplace", "Ocean waves"};
check(scenes.length == expected.length, "four scenes in menu order");
for (int i = 0; i < scenes.length; i++) {
    check(expected[i].equals(scenes[i].title()), "scene title/order");
    android.graphics.Bitmap first = android.graphics.Bitmap.createBitmap(
            320, 180, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Bitmap second = android.graphics.Bitmap.createBitmap(
            320, 180, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Paint paint = new android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG);
    scenes[i].draw(new android.graphics.Canvas(first), paint, 320, 180, 0f);
    scenes[i].draw(new android.graphics.Canvas(second), paint, 320, 180, 1f);
    check(!first.sameAs(second), expected[i] + " must animate");
    check(!first.sameAs(blank), expected[i] + " must draw nonblank output");
    first.recycle();
    second.recycle();
}
```

Create `blank` once with the same dimensions/config and recycle it in a finally block. Run through the existing explicit-device harness without live vendor writes.

- [x] **Step 2: Verify RED**

Add AmbientScene/AmbientScenes to `tests/run-device.sh` and run `rtk ./tests/run-device.sh 192.168.1.x:5555`. Expected: compilation fails because the scene classes are absent.

- [x] **Step 3: Implement the four drawing styles**

`AmbientScenes.create()` returns exactly four independent scenes named Starfield, Aurora, Fireplace, and Ocean waves. Each constructor seeds geometry with a fixed scene-specific seed. Precompute star positions/radii, ember seeds, and wave phases; `draw` performs only mathematical animation and Canvas calls using the supplied Paint.

```java
// Starfield: dark navy clear; 3 depth layers drift downward at different speeds.
// Aurora: black clear; 3 translucent cyan/green/violet sine ribbons with phase drift.
// Fireplace: near-black clear; layered warm flame shapes and rising embers.
// Ocean waves: midnight sky/moon plus 4 overlapping sine-wave layers with phase drift.
```

Set/reset Paint style, color, alpha, and stroke width inside each scene so state cannot leak into the next scene. Do not use flashing or random values inside `draw`. Clear each supplied Canvas before drawing so reused Paint state and previous frames cannot affect output.

- [x] **Step 4: Verify GREEN**

Run `rtk ./tests/run-device.sh 192.168.1.x:5555 && rtk ./tests/run-host.sh && rtk ./build.sh`. Expected: all automated suites and the APK build pass.

### Task 3: Fullscreen Playground shell and both modes

**Files:**
- Create: `src/com/visorcraft/hy450control/PlaygroundActivity.java`
- Create: `src/com/visorcraft/hy450control/PlaygroundView.java`
- Modify: `src/com/visorcraft/hy450control/MainActivity.java`
- Modify: `AndroidManifest.xml`

**Interfaces:**
- Consumes: every BreakoutGame method from Task 1 and AmbientScene/AmbientScenes from Task 2.
- Produces: `PlaygroundView.breakout(Context, BreakoutGame, int, ScoreListener)`, `PlaygroundView.ambient(Context, int)`, `direction(int, boolean)`, `centre()`, `nextScene(int)`, `setHostPaused(boolean)`, and `boolean isAmbient()`.
- `ScoreListener.bestChanged(int)` persists a new best score.

- [x] **Step 1: Add the dashboard and manifest shell**

Add a PLAY section immediately after AUTOMATE:

```java
root.addView(Ui.section(this, "PLAY", GAP_PX));
LinearLayout play = row();
destinationTile(play, "Playground", "Breakout + ambient scenes",
        v -> startActivity(new Intent(this, PlaygroundActivity.class)));
root.addView(play);
```

Declare `<activity android:name=".PlaygroundActivity" android:exported="false" />`. Create an Activity whose menu lists Breakout, Starfield, Aurora, Fireplace, and Ocean waves with `Ui.tile`, hides system bars using the same pattern as CalibrationActivity, and finishes on BACK from the menu. Selecting Breakout calls `showBreakout()`; selecting an ambient index stores it and calls `showAmbient(index)`.

- [x] **Step 2: Implement view scheduling and shared key behavior**

`PlaygroundView` extends View and implements `Choreographer.FrameCallback`. It owns a Paint, mode, host/user pause flags, last-frame time, and elapsed scene time. `setHostPaused(true)` cancels callbacks and pauses the game/animation; `setHostPaused(false)` leaves user-visible motion paused until CENTRE. `onDetachedFromWindow` cancels callbacks. `doFrame` clamps game delta to 0.05, calls `game.update`, invalidates, and posts the next frame only while active. Ambient frames are capped at 30 fps and accumulate elapsed seconds only while unpaused.

Activity key handling must be:

```java
if (view == null) return super.onKeyDown(code, event);
if (code == KeyEvent.KEYCODE_BACK) { showMenu(); return true; }
if (code == KeyEvent.KEYCODE_DPAD_CENTER && event.getRepeatCount() == 0) {
    view.centre();
    return true;
}
if ((code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)
        && !view.isAmbient()) {
    view.direction(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1, true);
    return true;
}
```

On key up, clear the matching Breakout direction. Ambient LEFT/RIGHT calls `nextScene` only on the initial key down.

- [x] **Step 3: Draw both modes and persist best score**

Scale Breakout's logical 1000×600 field uniformly while preserving aspect ratio. Draw dark background, cyan paddle, white ball, six colour-banded brick rows, and a HUD with score/best/lives plus READY/PAUSED/WON/GAME_OVER hints. Avoid per-frame object allocation except Canvas strings.

Ambient drawing calls the selected scene, then draws its title and **LEFT/RIGHT scenes • CENTRE pause • BACK menu**. `nextScene(direction)` wraps with `(index + direction + scenes.length) % scenes.length`, resets elapsed time to zero, and invalidates.

Activity preference handling is:

```java
private int bestScore() {
    try {
        return getSharedPreferences("playground", MODE_PRIVATE)
                .getInt("breakout_best", 0);
    } catch (ClassCastException error) {
        return 0;
    }
}

private void saveBest(int best) {
    getSharedPreferences("playground", MODE_PRIVATE)
            .edit().putInt("breakout_best", best).apply();
}
```

The view tracks the greater of stored/current best and invokes `ScoreListener.bestChanged(best)` only when the value increases.

- [x] **Step 4: Compile the integrated Playground**

Run `rtk ./tests/run-host.sh && rtk ./tests/run-device.sh 192.168.1.x:5555 && rtk ./build.sh`. Expected: all automated suites and the signed/non-debuggable APK build pass with both modes reachable from the dashboard.

### Task 4: Documentation, review, and projector verification

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-11-02-playground.md`
- Modify private operator notes only if device state changes, never in the public repository

- [x] **Step 1: Document Playground and the release candidate**

Add a README bullet describing Breakout controls/persistent best score and all four scenes. State that Playground is manual, fullscreen, dependency-free, and does not alter hardware settings. Use release 1.5.1/versionCode 11 and distinguish the locally signed installed candidate from the stable-key GitHub release.

- [x] **Step 2: Run final automated checks and independent review**

```bash
rtk ./tests/run-host.sh
rtk ./tests/run-device.sh 192.168.1.x:5555
rtk bash -n build.sh install.sh uninstall.sh capture/build.sh capture/capture.sh probe/build.sh tests/run-host.sh tests/run-device.sh
rtk ./build.sh
rtk git diff --check
rtk git status --short --branch
```

Request a read-only review of the complete diff against the spec. Fix confirmed Critical/Important findings and rerun affected tests.

- [x] **Step 3: Verify on the development projector**

Read `<private-hy450-archive>/AGENTS.md` and update required private notes before installation. Record projection/application baselines, install through the reversible Magisk module, and reboot. Exercise dashboard/menu navigation, every Breakout state, high-score persistence after process recreation, all scene transitions/pause/BACK paths, and background/resume behavior at 1920×1080. Compare projection, Projectivy, sleep, framework, and disabled-package state afterward.

- [x] **Step 4: Record verification and release outcome**

Record verified and unverified paths in the plan and final response. The user separately authorized the 1.5.1 bug-fix bump, commit/push, master integration, and normal tag-driven GitHub release.
