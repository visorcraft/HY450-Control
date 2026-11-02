package com.visorcraft.hy450control;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/** Applies the wall-measured keystone for the current tilt. */
public final class RectangleActivity extends Activity {
    public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            OemFocus.cancelRestore(this);
            Keystone.Snapshot current = Keystone.read();
            int[] inset = WallRectangle.insets(WallRectangle.pose());
            Keystone.apply(new Keystone.Snapshot(inset, current.zoomH, current.zoomV));
            StringBuilder text = new StringBuilder("Image squared");
            for (int value : inset) text.append(' ').append(value);
            Log.i("Rectangle", text.toString());
            Toast.makeText(this, "Image squared", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            String message = error.getMessage() == null ? "Square the image failed" : error.getMessage();
            Log.e("Rectangle", message, error);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
