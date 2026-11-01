package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * VajzActivity clears the keystone in onDestroy. The reset lands after the
 * caller has resumed, so the saved insets are written back on the next turns
 * of the main thread.
 */
final class OemFocus {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Keystone.Snapshot pending;
    private static Runnable restore;

    private OemFocus() {}

    static void launch(Activity activity) {
        cancelRestore();
        try {
            pending = Keystone.read();
            activity.startActivity(new Intent().setComponent(
                    new ComponentName("com.hysd.vafocus", "com.hysd.vafocus.VajzActivity")));
        } catch (Exception error) {
            pending = null;
            Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    static void onResume(Activity activity) {
        Keystone.Snapshot saved = pending;
        if (saved == null) return;
        pending = null;
        cancelRestore();
        restore = () -> {
            try {
                Keystone.apply(saved);
            } catch (Exception error) {
                if (!activity.isFinishing()) {
                    Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        };
        HANDLER.post(restore);
        HANDLER.postDelayed(restore, 400);
        HANDLER.postDelayed(restore, 1200);
    }

    /** Drop a queued OEM-focus restore so a newer keystone write is kept. */
    static void cancelRestore() {
        if (restore != null) HANDLER.removeCallbacks(restore);
        restore = null;
    }
}
