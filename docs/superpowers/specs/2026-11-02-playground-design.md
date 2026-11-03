# Playground Design

## Goal

Add a dependency-free Playground to HY450 Control with a remote-playable Breakout game and four fullscreen ambient scenes: Starfield, Aurora, Fireplace, and Ocean waves. The feature must be fun and visually polished without changing projector hardware settings or adding network, media, DRM, or streaming behavior.

## Scope

- Add one Playground destination to the existing dashboard.
- Provide a fullscreen menu for Breakout and all four ambient scenes.
- Support every interaction with the existing D-pad, centre button, and BACK button.
- Persist only the Breakout best score in app-private preferences.
- Pause animation when the activity is backgrounded.
- Verify the UI on the development projector through the existing reversible Magisk module.

Out of scope: additional games, scene customization, sound, multiplayer, accounts, network access, automatic idle/screensaver activation, quick-overlay integration, background playback, and changes to backlight, picture, input, keystone, zoom, or other vendor values.

## Experience

### Dashboard and menu

Add a **PLAY** section below **AUTOMATE**. Its **Playground** tile has the subtitle **Breakout + ambient scenes** and opens `PlaygroundActivity`.

The activity opens a fullscreen menu with large D-pad-focusable destinations in this order:

1. Breakout
2. Starfield
3. Aurora
4. Fireplace
5. Ocean waves

The existing graphite/cyan visual language, focus scaling, readable typography, and fullscreen system-bar behavior apply. BACK from the menu returns to the dashboard.

### Breakout

- LEFT and RIGHT move the paddle; holding a direction moves it continuously.
- CENTRE launches the ball when attached, pauses/resumes an active rally, and starts a new round after win or game over.
- A round starts with three lives and a score of zero.
- The playfield contains six rows of ten bricks. Each brick is worth ten points and is removed by one hit.
- The ball begins attached to the paddle. Paddle impact position controls the rebound angle so the player can aim.
- Side and top walls reflect the ball. The paddle and bricks reflect it without allowing repeated score from the same removed brick.
- Passing the bottom edge costs one life and reattaches the ball while lives remain. At zero lives the round is game over.
- Clearing all sixty bricks produces the win state.
- The HUD shows score, best score, lives, and contextual control hints.
- The best score is updated whenever the current score exceeds the stored best and is retained across process death.
- BACK returns to the Playground menu. Starting another round resets current score and lives but not the best score.

### Ambient scenes

- LEFT and RIGHT move to the previous/next scene in the menu order, wrapping at either end.
- CENTRE pauses or resumes animation.
- BACK returns to the Playground menu.
- Starfield renders layered, gently drifting stars.
- Aurora renders slow, flowing coloured light ribbons over a dark field.
- Fireplace renders warm flames and rising embers without strobing.
- Ocean waves renders layered moonlit waves at a calm pace.
- Scene changes require no assets, network, sound, or hardware adjustment.

## Architecture

Use one shared fullscreen shell with independent game and scene engines.

### `PlaygroundActivity`

Owns the menu, mode selection, key events, BACK navigation, fullscreen behavior, and private `playground` preferences with the `breakout_best` integer. It stops animation when backgrounded and restores the current mode paused when the same activity resumes. Process recreation returns to the menu; only the best score survives.

### `PlaygroundView`

Owns Canvas rendering and `Choreographer` frame callbacks. It translates key state into Breakout input, supplies elapsed time to ambient scenes, draws HUD/control hints, and stops callbacks whenever the view is detached or the activity is paused. Breakout renders at the display's frame callbacks; ambient animation is capped at 30 frames per second. Frame gaps are clamped before reaching the game engine.

### `BreakoutGame`

A pure-Java model with no Android imports. It exposes explicit methods for directional input, centre-button actions, fixed time updates, and immutable/read-only state needed by the renderer. It uses a fixed logical playfield scaled by `PlaygroundView`, keeps paddle movement within bounds, resolves wall/paddle/brick/bottom collisions deterministically, and prevents a removed brick from scoring twice.

### `AmbientScene` and implementations

A narrow scene interface provides a name and deterministic Canvas drawing for a supplied size and elapsed animation time. Seeded star, ember, and wave geometry is created during initialization rather than allocated every frame. Time accumulation belongs to the view so pause and scene switching behave consistently.

## Data flow

1. Dashboard starts `PlaygroundActivity`.
2. The activity shows the menu and reads the best score only when needed by Breakout.
3. Selecting a mode attaches the shared view with either a new `BreakoutGame` or the selected `AmbientScene`.
4. Key events update game input or invoke pause/restart/scene actions.
5. Frame callbacks update the model or animation clock and draw the current state.
6. A new high score is written with `SharedPreferences.apply()`; gameplay continues if disk persistence fails.
7. BACK detaches the active view and returns to the menu; activity pause stops all frame work.

## Lifecycle and error handling

- No vendor adapter or system-property API is called from Playground code.
- Missing or malformed best-score preferences fall back to zero without blocking play.
- `onPause` and view detachment cancel frame callbacks. Ordinary resume preserves the mode but requires CENTRE to resume motion.
- Clamped frame deltas prevent a background/resume jump from skipping collision handling.
- Zero-sized views do not update or draw until dimensions are available.
- No flashing effects, arbitrary root commands, external files, or third-party dependencies are introduced.

## Verification

### Automated

Add plain-JVM host regressions for:

- Paddle bounds and continuous directional input
- Ball launch, pause, resume, and restart
- Side/top wall reflection and paddle rebound direction
- Brick removal, exact scoring, and no duplicate scoring
- Life loss, ball reattachment, game over, and win
- Best-score-compatible score transitions without Android dependencies

Run the complete existing host suite, Android fake-data suite, shell syntax checks, signed/non-debuggable build, and diff checks. Preserve all existing backup, recovery, automation, and adapter tests.

### Development projector

Before installation, follow the private archive's backup-first device rules and record current projection and application state. Install the candidate through the existing reversible Magisk module without changing the release version or publishing a release.

Verify at 1920×1080:

- Dashboard and menu D-pad reachability, focus visibility, and BACK navigation
- Breakout launch, paddle movement, aiming, scoring, life loss, pause/resume, game over, restart, and best-score persistence after process recreation
- All four ambient scenes, scene wrapping, centre pause/resume, and BACK exit
- Animation stopping while backgrounded and remaining paused on return
- No changes to the complete projection baseline, Projectivy startup/accessibility, sleep-off state, framework patch, or disabled-package set

Restore the original projection values if any verification unexpectedly changes them. Document any unverified path explicitly rather than claiming success.

## Outcome

Released as **v1.5.1/versionCode 11**. Host and Android suites, shell checks, build, and diff checks passed. Real-projector verification covered dashboard/menu navigation, every ambient scene, RIGHT wraparound, byte-identical pause and HOME/resume frames, Breakout paddle/scoring/pause/life loss/game over/retry, best-score persistence through force-stop, and readable RGB calibration text. Pure-Java tests cover the win state and difficult collision arrangements; a full hardware win was not attempted. An initial Playground immersive-call ordering crash was fixed and retested. Projection properties, Projectivy startup/accessibility, sleep-off state, framework patch, and disabled-package set remained unchanged.
