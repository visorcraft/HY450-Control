package com.visorcraft.hy450control;

import android.graphics.Canvas;
import android.graphics.Paint;

interface AmbientScene {
    String title();
    void draw(Canvas canvas, Paint paint, int width, int height, float elapsedSeconds);
}
