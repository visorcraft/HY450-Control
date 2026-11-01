package com.visorcraft.hy450control;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Thin countdown bar for the overlay. Draws a graphite track with an accent
 * fill for the remaining fraction. The fraction is clamped and only redraws
 * when it actually changes, so the periodic tick costs no allocation.
 */
public final class CountdownBar extends View {
    private static final float BAR_HEIGHT_PX = 12f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;

    public CountdownBar(Context context) {
        super(context);
        track.setColor(context.getColor(R.color.card_pressed));
        fill.setColor(context.getColor(R.color.accent));
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    public void setFraction(float value) {
        float clamped = value < 0f ? 0f : Math.min(value, 1f);
        if (clamped == fraction) return;
        fraction = clamped;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = Ui.px(getContext(), BAR_HEIGHT_PX);
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(Math.max(height, getSuggestedMinimumHeight()), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        float radius = height / 2f;
        canvas.drawRoundRect(0f, 0f, width, height, radius, radius, track);
        float filled = width * fraction;
        if (filled <= 0f) return;
        canvas.drawRoundRect(0f, 0f, filled, height, radius, radius, fill);
    }
}
