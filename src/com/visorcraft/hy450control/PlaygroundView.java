package com.visorcraft.hy450control;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.View;

final class PlaygroundView extends View implements Choreographer.FrameCallback {
    interface ScoreListener {
        void bestChanged(int best);
    }

    private static final int[] BRICK_COLOURS = {
        Color.rgb(242, 92, 110), Color.rgb(245, 158, 66), Color.rgb(240, 211, 75),
        Color.rgb(77, 205, 143), Color.rgb(64, 174, 235), Color.rgb(157, 113, 235)
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BreakoutGame game;
    private final AmbientScene[] scenes;
    private final ScoreListener scoreListener;
    private final BestScore best;
    private int sceneIndex;
    private boolean hostPaused;
    private boolean userPaused;
    private boolean attached;
    private boolean framePosted;
    private boolean leftHeld;
    private boolean rightHeld;
    private long lastFrameNanos;
    private float sceneElapsed;
    private float ambientAccumulator;

    private PlaygroundView(Context context, BreakoutGame game, int best,
            ScoreListener scoreListener) {
        super(context);
        this.game = game;
        this.best = new BestScore(best);
        this.scoreListener = scoreListener;
        scenes = null;
        setFocusable(true);
    }

    private PlaygroundView(Context context, int sceneIndex) {
        super(context);
        game = null;
        best = null;
        scoreListener = null;
        scenes = AmbientScenes.create();
        this.sceneIndex = sceneIndex;
        setFocusable(true);
    }

    static PlaygroundView breakout(Context context, BreakoutGame game, int best,
            ScoreListener listener) {
        return new PlaygroundView(context, game, best, listener);
    }

    static PlaygroundView ambient(Context context, int sceneIndex) {
        return new PlaygroundView(context, sceneIndex);
    }

    boolean isAmbient() {
        return scenes != null;
    }

    void direction(int direction, boolean pressed) {
        if (isAmbient()) return;
        if (direction < 0) leftHeld = pressed;
        if (direction > 0) rightHeld = pressed;
        game.setDirection(rightHeld ? 1 : leftHeld ? -1 : 0);
        schedule();
    }

    void centre() {
        if (isAmbient()) {
            userPaused = !userPaused;
            lastFrameNanos = 0L;
        } else {
            game.centre();
        }
        invalidate();
        if (active()) {
            schedule();
        } else {
            cancelFrame();
        }
    }

    void nextScene(int direction) {
        if (!isAmbient()) return;
        sceneIndex = (sceneIndex + direction + scenes.length) % scenes.length;
        sceneElapsed = 0f;
        ambientAccumulator = 0f;
        lastFrameNanos = 0L;
        invalidate();
        schedule();
    }

    void setHostPaused(boolean paused) {
        hostPaused = paused;
        if (paused) {
            if (!isAmbient()) {
                leftHeld = false;
                rightHeld = false;
                game.setDirection(0);
                if (game.phase() == BreakoutGame.Phase.RUNNING) game.centre();
            } else {
                userPaused = true;
            }
            cancelFrame();
        } else {
            lastFrameNanos = 0L;
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        lastFrameNanos = 0L;
        if (width == 0 || height == 0) {
            cancelFrame();
        } else {
            schedule();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        cancelFrame();
        super.onDetachedFromWindow();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        framePosted = false;
        if (!active()) return;
        float delta = lastFrameNanos == 0L ? 0f
                : Math.min(0.05f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = frameTimeNanos;
        if (isAmbient()) {
            sceneElapsed += delta;
            ambientAccumulator += delta;
            if (ambientAccumulator >= 1f / 30f) {
                ambientAccumulator = 0f;
                invalidate();
            }
        } else {
            game.update(delta);
            if (best.update(game.score())) {
                scoreListener.bestChanged(best.get());
            }
            invalidate();
        }
        schedule();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        if (isAmbient()) {
            drawAmbient(canvas);
        } else {
            drawBreakout(canvas);
        }
    }

    private void drawBreakout(Canvas canvas) {
        canvas.drawColor(Color.rgb(5, 9, 16));
        float scale = Math.min(getWidth() / (float) BreakoutGame.WIDTH,
                getHeight() / (float) BreakoutGame.HEIGHT);
        float offsetX = (getWidth() - BreakoutGame.WIDTH * scale) / 2f;
        float offsetY = (getHeight() - BreakoutGame.HEIGHT * scale) / 2f;
        int save = canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        for (int i = 0; i < BreakoutGame.ROWS * BreakoutGame.COLUMNS; i++) {
            if (!game.isBrickAlive(i)) continue;
            float left = (i % BreakoutGame.COLUMNS) * BreakoutGame.BRICK_WIDTH;
            float top = BreakoutGame.BRICK_TOP
                    + (i / BreakoutGame.COLUMNS) * BreakoutGame.BRICK_STRIDE;
            paint.setColor(BRICK_COLOURS[i / BreakoutGame.COLUMNS]);
            canvas.drawRect(left + 3f, top + 3f,
                    left + BreakoutGame.BRICK_WIDTH - 3f,
                    top + BreakoutGame.BRICK_HEIGHT - 3f, paint);
        }
        paint.setColor(Color.rgb(65, 225, 225));
        canvas.drawRoundRect(game.paddleX() - BreakoutGame.PADDLE_WIDTH / 2f,
                BreakoutGame.PADDLE_Y - 9f,
                game.paddleX() + BreakoutGame.PADDLE_WIDTH / 2f,
                BreakoutGame.PADDLE_Y + 9f, 12f, 12f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(game.ballX(), game.ballY(), BreakoutGame.BALL_RADIUS, paint);
        canvas.restoreToCount(save);

        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(Math.max(22f, getHeight() * 0.035f));
        paint.setColor(Color.WHITE);
        canvas.drawText("SCORE " + game.score() + "   BEST " + best.get()
                + "   LIVES " + game.lives(), 28f, 42f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(18f, getHeight() * 0.026f));
        paint.setColor(Color.rgb(170, 195, 205));
        canvas.drawText(breakoutHint(), getWidth() / 2f, getHeight() - 24f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
    }

    private String breakoutHint() {
        switch (game.phase()) {
            case READY: return "CENTRE launch  •  LEFT/RIGHT move  •  BACK menu";
            case RUNNING: return "CENTRE pause  •  BACK menu";
            case PAUSED: return "PAUSED  •  CENTRE resume  •  BACK menu";
            case WON: return "YOU WIN!  •  CENTRE new round  •  BACK menu";
            default: return "GAME OVER  •  CENTRE retry  •  BACK menu";
        }
    }

    private void drawAmbient(Canvas canvas) {
        scenes[sceneIndex].draw(canvas, paint, getWidth(), getHeight(), sceneElapsed);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(Math.max(26f, getHeight() * 0.045f));
        paint.setColor(Color.WHITE);
        paint.setAlpha(230);
        canvas.drawText(scenes[sceneIndex].title(), 30f, 52f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(17f, getHeight() * 0.025f));
        String hint = userPaused
                ? "PAUSED  •  CENTRE resume  •  LEFT/RIGHT scenes  •  BACK menu"
                : "LEFT/RIGHT scenes  •  CENTRE pause  •  BACK menu";
        canvas.drawText(hint, getWidth() / 2f, getHeight() - 24f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setAlpha(255);
    }

    private boolean active() {
        if (!attached || hostPaused || getWidth() == 0 || getHeight() == 0) return false;
        if (isAmbient()) return !userPaused;
        return game.phase() == BreakoutGame.Phase.READY
                || game.phase() == BreakoutGame.Phase.RUNNING;
    }

    private void schedule() {
        if (!active() || framePosted) return;
        framePosted = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void cancelFrame() {
        if (!framePosted) return;
        Choreographer.getInstance().removeFrameCallback(this);
        framePosted = false;
    }
}
