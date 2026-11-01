package com.visorcraft.hy450control;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public final class CalibrationView extends View {
    public static final int PATTERN_COUNT = 6;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int pattern;

    public CalibrationView(Context context) {
        super(context);
        setFocusable(true);
    }

    public void next(int direction) {
        pattern = (pattern + direction + PATTERN_COUNT) % PATTERN_COUNT;
        invalidate();
    }

    public String title() {
        switch (pattern) {
            case 0: return "Focus grid";
            case 1: return "Sharpness";
            case 2: return "RGB";
            case 3: return "Alignment";
            case 4: return "Greyscale";
            default: return "Solid colours";
        }
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawColor(Color.BLACK);
        paint.setStrokeWidth(2);
        switch (pattern) {
            case 0: grid(canvas, w, h); break;
            case 1: sharpness(canvas, w, h); break;
            case 2: rgb(canvas, w, h); break;
            case 3: alignment(canvas, w, h); break;
            case 4: greyscale(canvas, w, h); break;
            default: solids(canvas, w, h); break;
        }
        paint.setTextSize(32);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.WHITE);
        canvas.drawText(title() + "  •  D-pad left/right  •  Back to exit", 32, h - 32, paint);
    }

    private void grid(Canvas c, int w, int h) {
        paint.setColor(Color.WHITE);
        for (int x = 0; x <= w; x += w / 16) c.drawLine(x, 0, x, h, paint);
        for (int y = 0; y <= h; y += h / 9) c.drawLine(0, y, w, y, paint);
        paint.setColor(Color.RED);
        c.drawLine(w / 2, 0, w / 2, h, paint);
        c.drawLine(0, h / 2, w, h / 2, paint);
    }

    private void sharpness(Canvas c, int w, int h) {
        for (int spacing = 2; spacing <= 16; spacing *= 2) {
            int left = (spacing == 2 ? 0 : spacing == 4 ? w / 4 : spacing == 8 ? w / 2 : 3 * w / 4);
            paint.setColor(Color.WHITE);
            for (int x = left; x < left + w / 4; x += spacing) c.drawLine(x, 0, x, h * 3 / 4, paint);
        }
    }

    private void rgb(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.RED); c.drawRect(0, 0, w / 3, h, paint);
        paint.setColor(Color.GREEN); c.drawRect(w / 3, 0, 2 * w / 3, h, paint);
        paint.setColor(Color.BLUE); c.drawRect(2 * w / 3, 0, w, h, paint);
        paint.setStyle(Paint.Style.STROKE);
    }

    private void alignment(Canvas c, int w, int h) {
        paint.setColor(Color.GREEN);
        c.drawRect(8, 8, w - 8, h - 8, paint);
        c.drawLine(0, 0, w, h, paint);
        c.drawLine(w, 0, 0, h, paint);
        c.drawCircle(w / 2, h / 2, Math.min(w, h) / 3, paint);
    }

    private void greyscale(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 16; i++) {
            int level = i * 255 / 15;
            paint.setColor(Color.rgb(level, level, level));
            c.drawRect(i * w / 16, 0, (i + 1) * w / 16, h, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
    }

    private void solids(Canvas c, int w, int h) {
        int[] colours = {Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE};
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < colours.length; i++) {
            paint.setColor(colours[i]);
            c.drawRect(i * w / colours.length, 0, (i + 1) * w / colours.length, h, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
    }
}
