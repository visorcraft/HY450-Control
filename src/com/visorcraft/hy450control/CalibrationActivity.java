package com.visorcraft.hy450control;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

public final class CalibrationActivity extends Activity {
    private CalibrationView view;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        view = new CalibrationView(this);
        setContentView(view);
        hideSystemBars();
    }

    public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            view.next(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return true;
        }
        return super.onKeyDown(code, event);
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller == null) return;
        controller.hide(WindowInsets.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
}
