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
    private static OemRecovery recovery;

    interface Action {
        void run() throws Exception;
    }

    private OemFocus() {}

    static void launch(Activity activity) {
        try {
            recovery(activity).prepare();
            activity.startActivity(new Intent().setComponent(
                    new ComponentName("com.hysd.vafocus", "com.hysd.vafocus.VajzActivity")));
        } catch (Exception error) {
            try {
                cancelRestore(activity);
            } catch (Exception clearError) {
                error = clearError;
            }
            Toast.makeText(activity, Errors.describe(error), Toast.LENGTH_LONG).show();
        }
    }

    static void onResume(Activity activity) {
        recovery(activity).resume(error -> showError(activity, error));
    }

    /** Cancel queued and persisted recovery so a newer keystone write is kept. */
    static void cancelRestore(Context context) throws Exception {
        recovery(context).cancel();
    }

    static void cancelAndRun(Context context, Action action) throws Exception {
        OemRecovery.cancelAndRun(recovery(context), action::run);
    }

    private static OemRecovery recovery(Context context) {
        if (recovery == null) {
            Context application = context.getApplicationContext();
            recovery = new OemRecovery(new PreferencesStore(application), new HandlerScheduler(),
                    new KeystoneValues());
        }
        return recovery;
    }

    private static void showError(Activity activity, Exception error) {
        if (!activity.isFinishing()) {
            Toast.makeText(activity, Errors.describe(error), Toast.LENGTH_LONG).show();
        }
    }

    private static final class HandlerScheduler implements OemRecovery.Scheduler {
        @Override
        public void post(Runnable runnable) {
            HANDLER.post(runnable);
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMillis) {
            HANDLER.postDelayed(runnable, delayMillis);
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            HANDLER.removeCallbacks(runnable);
        }
    }

    private static final class KeystoneValues implements OemRecovery.Values {
        @Override
        public Keystone.Snapshot read() throws Exception {
            return Keystone.read();
        }

        @Override
        public void apply(Keystone.Snapshot snapshot) throws Exception {
            Keystone.apply(snapshot);
        }
    }

    private static final class PreferencesStore implements OemRecovery.Store {
        private final SharedPreferences preferences;

        PreferencesStore(Context context) {
            preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        }

        @Override
        public boolean isPending() {
            return preferences.getBoolean(PENDING, false);
        }

        @Override
        public Keystone.Snapshot read() throws Exception {
            int[] inset = new int[8];
            for (int i = 0; i < inset.length; i++) {
                String key = insetKey(i);
                requireSaved(key);
                inset[i] = preferences.getInt(key, 0);
            }
            requireSaved(ZOOM_H);
            requireSaved(ZOOM_V);
            float zoomH = preferences.getFloat(ZOOM_H, 0f);
            float zoomV = preferences.getFloat(ZOOM_V, 0f);
            if (Float.isNaN(zoomH) || Float.isInfinite(zoomH)
                    || Float.isNaN(zoomV) || Float.isInfinite(zoomV)) {
                throw new IllegalStateException("Non-finite saved keystone zoom");
            }
            return new Keystone.Snapshot(inset, zoomH, zoomV);
        }

        @Override
        public boolean save(Keystone.Snapshot snapshot) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(PENDING, true);
            for (int i = 0; i < snapshot.inset.length; i++) {
                editor.putInt(insetKey(i), snapshot.inset[i]);
            }
            editor.putFloat(ZOOM_H, snapshot.zoomH);
            editor.putFloat(ZOOM_V, snapshot.zoomV);
            return editor.commit();
        }

        @Override
        public boolean clear() {
            return preferences.edit().clear().commit();
        }

        private void requireSaved(String key) {
            if (!preferences.contains(key)) {
                throw new IllegalStateException("Missing saved keystone value: " + key);
            }
        }

        private static String insetKey(int index) {
            return "inset_" + index;
        }
    }
}
