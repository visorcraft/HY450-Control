package com.visorcraft.hy450control;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.util.Random;

final class AmbientScenes {
    private AmbientScenes() {}

    static AmbientScene[] create() {
        return new AmbientScene[] {new Starfield(), new Aurora(), new Fireplace(), new Ocean()};
    }

    private static final class Starfield implements AmbientScene {
        private static final int COUNT = 150;
        private final float[] x = new float[COUNT];
        private final float[] y = new float[COUNT];
        private final float[] radius = new float[COUNT];
        private final float[] speed = new float[COUNT];

        Starfield() {
            Random random = new Random(4501);
            for (int i = 0; i < COUNT; i++) {
                x[i] = random.nextFloat();
                y[i] = random.nextFloat();
                radius[i] = 0.6f + random.nextFloat() * 2.1f;
                speed[i] = 0.006f + random.nextFloat() * 0.025f;
            }
        }

        @Override
        public String title() { return "Starfield"; }

        @Override
        public void draw(Canvas canvas, Paint paint, int width, int height, float elapsed) {
            canvas.drawColor(Color.rgb(2, 5, 18));
            paint.setStyle(Paint.Style.FILL);
            float scale = Math.max(0.7f, height / 540f);
            for (int i = 0; i < COUNT; i++) {
                float py = ((y[i] + elapsed * speed[i]) % 1f) * height;
                int alpha = 125 + (int) (110 * (0.5f + 0.5f
                        * Math.sin(elapsed * 1.4f + i * 1.73f)));
                paint.setColor(i % 9 == 0 ? Color.rgb(160, 215, 255) : Color.WHITE);
                paint.setAlpha(alpha);
                canvas.drawCircle(x[i] * width, py, radius[i] * scale, paint);
            }
            paint.setAlpha(255);
        }
    }

    private static final class Aurora implements AmbientScene {
        private final int[] colours = {
            Color.rgb(60, 245, 190), Color.rgb(60, 180, 255), Color.rgb(175, 90, 255)
        };

        @Override
        public String title() { return "Aurora"; }

        @Override
        public void draw(Canvas canvas, Paint paint, int width, int height, float elapsed) {
            canvas.drawColor(Color.rgb(1, 5, 13));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            int segments = 64;
            for (int ribbon = 0; ribbon < 3; ribbon++) {
                paint.setColor(colours[ribbon]);
                paint.setAlpha(42);
                paint.setStrokeWidth(height * (0.12f + ribbon * 0.025f));
                float previousX = -width * 0.05f;
                float previousY = waveY(previousX, width, height, elapsed, ribbon);
                for (int i = 1; i <= segments; i++) {
                    float x = -width * 0.05f + i * width * 1.1f / segments;
                    float y = waveY(x, width, height, elapsed, ribbon);
                    canvas.drawLine(previousX, previousY, x, y, paint);
                    previousX = x;
                    previousY = y;
                }
            }
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
        }

        private static float waveY(float x, int width, int height, float elapsed, int ribbon) {
            float phase = x / width * 7.5f + elapsed * (0.3f + ribbon * 0.07f)
                    + ribbon * 1.8f;
            return height * (0.32f + ribbon * 0.13f)
                    + (float) Math.sin(phase) * height * 0.1f
                    + (float) Math.sin(phase * 0.47f + 1.2f) * height * 0.06f;
        }
    }

    private static final class Fireplace implements AmbientScene {
        private static final int[] FLAME_COLOURS = {
            0xFFF04B12, 0xFFFF7717, 0xFFFFAA22, 0xFFFFD54A, 0xFFFFF2B0
        };
        private static final int EMBERS = 36;
        private final float[] emberX = new float[EMBERS];
        private final float[] emberPhase = new float[EMBERS];
        private final float[] emberSpeed = new float[EMBERS];

        Fireplace() {
            Random random = new Random(4502);
            for (int i = 0; i < EMBERS; i++) {
                emberX[i] = random.nextFloat();
                emberPhase[i] = random.nextFloat();
                emberSpeed[i] = 0.08f + random.nextFloat() * 0.18f;
            }
        }

        @Override
        public String title() { return "Fireplace"; }

        @Override
        public void draw(Canvas canvas, Paint paint, int width, int height, float elapsed) {
            canvas.drawColor(Color.rgb(15, 5, 3));
            paint.setStyle(Paint.Style.FILL);
            float centre = width * 0.5f;
            float base = height * 0.9f;
            paint.setColor(Color.rgb(80, 28, 10));
            canvas.drawRect(width * 0.2f, base, width * 0.8f, height, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(height * 0.065f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(75, 34, 18));
            canvas.drawLine(width * 0.32f, base, width * 0.68f, base - height * 0.04f, paint);
            canvas.drawLine(width * 0.32f, base - height * 0.04f, width * 0.68f, base, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int layer = 0; layer < 5; layer++) {
                float sway = (float) Math.sin(elapsed * (2.1f + layer * 0.17f) + layer)
                        * width * 0.015f;
                float flameHeight = height * (0.54f - layer * 0.07f)
                        * (0.94f + 0.06f * (float) Math.sin(elapsed * 3.2f + layer));
                float flameWidth = width * (0.25f - layer * 0.032f);
                paint.setColor(FLAME_COLOURS[layer]);
                paint.setAlpha(215);
                canvas.drawOval(centre + sway - flameWidth / 2f, base - flameHeight,
                        centre + sway + flameWidth / 2f, base + height * 0.02f, paint);
            }
            for (int i = 0; i < EMBERS; i++) {
                float progress = (emberPhase[i] + elapsed * emberSpeed[i]) % 1f;
                float x = width * (0.3f + emberX[i] * 0.4f)
                        + (float) Math.sin(progress * 8f + i) * width * 0.025f;
                float y = base - progress * height * 0.75f;
                paint.setColor(Color.rgb(255, 150, 45));
                paint.setAlpha((int) (220 * (1f - progress)));
                canvas.drawCircle(x, y, 2f + (i % 3), paint);
            }
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setAlpha(255);
        }
    }

    private static final class Ocean implements AmbientScene {
        private final int[] colours = {
            Color.rgb(12, 55, 90), Color.rgb(14, 74, 112),
            Color.rgb(18, 94, 132), Color.rgb(24, 112, 145)
        };

        @Override
        public String title() { return "Ocean waves"; }

        @Override
        public void draw(Canvas canvas, Paint paint, int width, int height, float elapsed) {
            canvas.drawColor(Color.rgb(3, 12, 29));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(205, 225, 230));
            canvas.drawCircle(width * 0.76f, height * 0.2f, height * 0.075f, paint);
            paint.setColor(Color.rgb(3, 12, 29));
            canvas.drawCircle(width * 0.79f, height * 0.175f, height * 0.07f, paint);
            paint.setStyle(Paint.Style.STROKE);
            int segments = 72;
            for (int layer = 0; layer < 4; layer++) {
                paint.setColor(colours[layer]);
                paint.setAlpha(220);
                paint.setStrokeWidth(height * (0.09f + layer * 0.025f));
                float baseline = height * (0.53f + layer * 0.13f);
                float previousX = -width * 0.04f;
                float previousY = baseline;
                for (int i = 1; i <= segments; i++) {
                    float x = -width * 0.04f + i * width * 1.08f / segments;
                    float phase = x / width * (8f + layer) + elapsed * (0.65f + layer * 0.1f)
                            + layer * 1.4f;
                    float y = baseline + (float) Math.sin(phase) * height * 0.035f
                            + (float) Math.sin(phase * 0.41f) * height * 0.018f;
                    canvas.drawLine(previousX, previousY, x, y, paint);
                    previousX = x;
                    previousY = y;
                }
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
        }
    }
}
