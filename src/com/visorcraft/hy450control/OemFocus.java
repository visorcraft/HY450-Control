package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * VajzActivity clears the keystone in onDestroy. The reset lands after the
 * caller has resumed, so the saved insets are written back on the next turns
 * of the main thread.
 */
final class OemFocus {
    private static final String PREFERENCES = "oem_focus";
    private static final String PENDING = "pending";
    private static final String ZOOM_H = "zoom_h";
    private static final String ZOOM_V = "zoom_v";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable restore;

    private OemFocus() {}

    static void launch(Activity activity) {
        try {
            cancelRestore(activity);
            Keystone.Snapshot saved = Keystone.read();
            SharedPreferences.Editor editor = preferences(activity).edit();
            editor.putBoolean(PENDING, true);
            for (int i = 0; i < saved.inset.length; i++) {
                editor.putInt(insetKey(i), saved.inset[i]);
            }
            editor.putFloat(ZOOM_H, saved.zoomH);
            editor.putFloat(ZOOM_V, saved.zoomV);
            if (!editor.commit()) throw new IllegalStateException("Keystone recovery save failed");
            activity.startActivity(new Intent().setComponent(
                    new ComponentName("com.hysd.vafocus", "com.hysd.vafocus.VajzActivity")));
        } catch (Exception error) {
            cancelRestore(activity);
            Toast.makeText(activity, Errors.describe(error), Toast.LENGTH_LONG).show();
        }
    }

    static void onResume(Activity activity) {
        SharedPreferences preferences = preferences(activity);
        if (!preferences.getBoolean(PENDING, false)) return;
        final Keystone.Snapshot saved;
        try {
            saved = read(preferences);
        } catch (Exception error) {
            cancelCallbacks();
            showError(activity, error);
            return;
        }
        cancelCallbacks();
        final int[] passes = {0};
        restore = () -> {
            int pass = ++passes[0];
            try {
                Keystone.apply(saved);
                if (pass == 3) {
                    if (!saved.matches(Keystone.read())) {
                        throw new IllegalStateException("Keystone readback mismatch");
                    }
                    preferences(activity).edit().clear().commit();
                }
            } catch (Exception error) {
                showError(activity, error);
            }
        };
        HANDLER.post(restore);
        HANDLER.postDelayed(restore, 400);
        HANDLER.postDelayed(restore, 1200);
    }

    /** Cancel queued and persisted recovery so a newer keystone write is kept. */
    static void cancelRestore(Context context) {
        cancelCallbacks();
        preferences(context).edit().clear().commit();
    }

    private static Keystone.Snapshot read(SharedPreferences preferences) throws Exception {
        int[] inset = new int[8];
        for (int i = 0; i < inset.length; i++) {
            String key = insetKey(i);
            if (!preferences.contains(key)) {
                throw new IllegalStateException("Missing saved keystone value: " + key);
            }
            inset[i] = preferences.getInt(key, 0);
        }
        requireSaved(preferences, ZOOM_H);
        requireSaved(preferences, ZOOM_V);
        return new Keystone.Snapshot(inset,
                preferences.getFloat(ZOOM_H, 0f), preferences.getFloat(ZOOM_V, 0f));
    }

    private static void requireSaved(SharedPreferences preferences, String key) {
        if (!preferences.contains(key)) {
            throw new IllegalStateException("Missing saved keystone value: " + key);
        }
    }

    private static String insetKey(int index) {
        return "inset_" + index;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static void cancelCallbacks() {
        if (restore != null) HANDLER.removeCallbacks(restore);
        restore = null;
    }

    private static void showError(Activity activity, Exception error) {
        if (!activity.isFinishing()) {
            Toast.makeText(activity, Errors.describe(error), Toast.LENGTH_LONG).show();
        }
    }
}
