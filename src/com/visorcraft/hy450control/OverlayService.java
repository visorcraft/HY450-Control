package com.visorcraft.hy450control;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class OverlayService extends Service {
    private static final long TIMEOUT_MS = 15000L;

    private WindowManager windows;
    private LinearLayout panel;
    private TextView status;
    private Handler handler;
    private final Runnable timeout = () -> stopSelf();

    public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (panel == null) showPanel();
        armTimeout();
        return START_NOT_STICKY;
    }

    private void armTimeout() {
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, TIMEOUT_MS);
    }

    private void showPanel() {
        panel = new LinearLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                armTimeout();
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    stopSelf();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(230, 20, 20, 20));
        panel.setPadding(24, 24, 24, 24);
        status = new TextView(this);
        status.setText("Ready");
        status.setTextSize(18);
        status.setTextColor(Color.LTGRAY);
        status.setFocusable(false);
        status.setPadding(24, 16, 24, 16);
        panel.addView(status);
        VendorIntControl backlight;
        try {
            backlight = new VendorIntControl(Vendor.display(), "getBacklight", "setBacklight", null, 0, 255);
        } catch (Exception error) {
            stopSelf();
            return;
        }
        add("Backlight +", () -> adjust(backlight, 1));
        add("Backlight -", () -> adjust(backlight, -1));
        add("Volume +", () -> volume(1));
        add("Volume -", () -> volume(-1));
        add("Input source", () -> startActivity(new Intent()
                .setComponent(new android.content.ComponentName(
                        "com.softwinner.awsource", "com.softwinner.awsource.MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        add("Close", this::stopSelf);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        windows.addView(panel, params);
        focusFirstButton();
    }

    private void add(String title, Action action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(20);
        button.setOnClickListener(v -> {
            armTimeout();
            action.run();
        });
        panel.addView(button);
    }

    private void focusFirstButton() {
        for (int i = 0; i < panel.getChildCount(); i++) {
            View child = panel.getChildAt(i);
            if (child instanceof Button) {
                child.requestFocus();
                return;
            }
        }
    }

    private void adjust(VendorIntControl control, int direction) {
        try {
            int current = control.read();
            int next = control.clamp(current + direction);
            if (next == current) {
                status.setText((direction < 0 ? "Minimum" : "Maximum")
                        + " • Backlight: " + control.format(current));
            } else {
                control.write(next);
                status.setText("Backlight: " + control.format(control.read()) + " • read back OK");
            }
        } catch (Exception error) {
            status.setText("Backlight failed: " + Errors.describe(error));
        }
    }

    private void volume(int direction) {
        try {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
            status.setText("Volume: " + audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    + "/" + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch (Exception error) {
            status.setText("Volume failed: " + Errors.describe(error));
        }
    }

    public void onDestroy() {
        handler.removeCallbacks(timeout);
        if (panel != null) {
            if (panel.isAttachedToWindow()) windows.removeView(panel);
            panel = null;
        }
        status = null;
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) { return null; }

    private interface Action {
        void run();
    }
}
