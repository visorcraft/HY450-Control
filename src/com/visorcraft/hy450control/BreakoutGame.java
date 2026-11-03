package com.visorcraft.hy450control;

import java.util.Arrays;

final class BreakoutGame {
    static final int WIDTH = 1000;
    static final int HEIGHT = 600;
    static final int ROWS = 6;
    static final int COLUMNS = 10;
    static final int BRICK_TOP = 70;
    static final int BRICK_STRIDE = 52;
    static final int BRICK_HEIGHT = 28;
    static final int BRICK_WIDTH = WIDTH / COLUMNS;
    static final float PADDLE_WIDTH = 160f;
    static final float PADDLE_Y = 550f;
    private static final float PADDLE_SPEED = 600f;
    static final float BALL_RADIUS = 10f;
    private static final float BALL_SPEED = 480f;
    private static final float MAX_STEP = 1f / 240f;

    enum Phase { READY, RUNNING, PAUSED, WON, GAME_OVER }

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

    BreakoutGame() {
        reset();
    }

    void setDirection(int direction) {
        this.direction = Math.max(-1, Math.min(1, direction));
    }

    void centre() {
        if (phase == Phase.WON || phase == Phase.GAME_OVER) {
            reset();
        } else if (phase == Phase.READY) {
            phase = Phase.RUNNING;
        } else if (phase == Phase.RUNNING) {
            phase = Phase.PAUSED;
        } else if (phase == Phase.PAUSED) {
            phase = Phase.RUNNING;
        }
    }

    void update(float deltaSeconds) {
        if (deltaSeconds <= 0f || phase == Phase.PAUSED
                || phase == Phase.WON || phase == Phase.GAME_OVER) return;
        float delta = Math.min(deltaSeconds, 0.05f);
        paddleX += direction * PADDLE_SPEED * delta;
        paddleX = Math.max(PADDLE_WIDTH / 2f, Math.min(WIDTH - PADDLE_WIDTH / 2f, paddleX));
        if (phase == Phase.READY) {
            attachBall();
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(delta / MAX_STEP));
        float step = delta / steps;
        for (int i = 0; i < steps && phase == Phase.RUNNING; i++) updateRunning(step);
    }

    int score() { return score; }
    int lives() { return lives; }
    Phase phase() { return phase; }
    float paddleX() { return paddleX; }
    float ballX() { return ballX; }
    float ballY() { return ballY; }
    float ballVelocityX() { return ballVelocityX; }
    float ballVelocityY() { return ballVelocityY; }

    int bricksRemaining() {
        int remaining = 0;
        for (boolean brick : bricks) {
            if (brick) remaining++;
        }
        return remaining;
    }

    boolean isBrickAlive(int index) {
        return bricks[index];
    }

    private void reset() {
        Arrays.fill(bricks, true);
        phase = Phase.READY;
        direction = 0;
        score = 0;
        lives = 3;
        paddleX = WIDTH / 2f;
        ballVelocityX = 0f;
        ballVelocityY = -BALL_SPEED;
        attachBall();
    }

    private void attachBall() {
        ballX = paddleX;
        ballY = PADDLE_Y - BALL_RADIUS;
    }

    private void updateRunning(float delta) {
        float previousX = ballX;
        float previousY = ballY;
        ballX += ballVelocityX * delta;
        ballY += ballVelocityY * delta;

        if (ballX < BALL_RADIUS) {
            ballX = BALL_RADIUS;
            ballVelocityX = Math.abs(ballVelocityX);
        } else if (ballX > WIDTH - BALL_RADIUS) {
            ballX = WIDTH - BALL_RADIUS;
            ballVelocityX = -Math.abs(ballVelocityX);
        }
        if (ballY < BALL_RADIUS) {
            ballY = BALL_RADIUS;
            ballVelocityY = Math.abs(ballVelocityY);
        }

        if (ballVelocityY > 0f && previousY + BALL_RADIUS <= PADDLE_Y
                && ballY + BALL_RADIUS >= PADDLE_Y
                && ballX >= paddleX - PADDLE_WIDTH / 2f - BALL_RADIUS
                && ballX <= paddleX + PADDLE_WIDTH / 2f + BALL_RADIUS) {
            float offset = (ballX - paddleX) / (PADDLE_WIDTH / 2f);
            offset = Math.max(-1f, Math.min(1f, offset));
            ballVelocityX = BALL_SPEED * 0.85f * offset;
            ballVelocityY = -(float) Math.sqrt(BALL_SPEED * BALL_SPEED
                    - ballVelocityX * ballVelocityX);
            ballY = PADDLE_Y - BALL_RADIUS;
        }

        for (int i = 0; i < bricks.length; i++) {
            if (!bricks[i]) continue;
            float left = (i % COLUMNS) * BRICK_WIDTH;
            float top = BRICK_TOP + (i / COLUMNS) * BRICK_STRIDE;
            float right = left + BRICK_WIDTH;
            float bottom = top + BRICK_HEIGHT;
            float nearestX = Math.max(left, Math.min(right, ballX));
            float nearestY = Math.max(top, Math.min(bottom, ballY));
            float distanceX = ballX - nearestX;
            float distanceY = ballY - nearestY;
            if (distanceX * distanceX + distanceY * distanceY > BALL_RADIUS * BALL_RADIUS) {
                continue;
            }
            bricks[i] = false;
            score += 10;
            if (previousY - BALL_RADIUS >= bottom && ballY - BALL_RADIUS <= bottom) {
                ballVelocityY = Math.abs(ballVelocityY);
            } else if (previousY + BALL_RADIUS <= top && ballY + BALL_RADIUS >= top) {
                ballVelocityY = -Math.abs(ballVelocityY);
            } else if (previousX + BALL_RADIUS <= left && ballX + BALL_RADIUS >= left) {
                ballVelocityX = -Math.abs(ballVelocityX);
            } else if (previousX - BALL_RADIUS >= right && ballX - BALL_RADIUS <= right) {
                ballVelocityX = Math.abs(ballVelocityX);
            } else {
                ballVelocityY = -ballVelocityY;
            }
            if (bricksRemaining() == 0) phase = Phase.WON;
            return;
        }

        if (ballY > HEIGHT) {
            lives--;
            if (lives == 0) {
                phase = Phase.GAME_OVER;
            } else {
                phase = Phase.READY;
                attachBall();
                ballVelocityX = 0f;
                ballVelocityY = -BALL_SPEED;
            }
        }
    }
}
