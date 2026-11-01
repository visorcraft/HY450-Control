package com.visorcraft.hy450control;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class OverlayService extends Service {
    private static final long TIMEOUT_MS = 15000L;
    private static final long TICK_MS = 250L;
    private static final float PANEL_WIDTH_PX = 560f;
    private static final float PANEL_PADDING_PX = 24f;
    private static final float GAP_PX = 12f;
    private static final float STATUS_GAP_PX = 8f;
    private static final float STATUS_PX = 38f;
    private static final float OFFSET_PX = 48f;

    private WindowManager windows;
    private LinearLayout panel;
    private TextView status;
    private CountdownBar countdown;
    private Button focusChip;
    private Handler handler;
    private long deadline;
    private final Runnable timeout = () -> stopSelf();
    private final Runnable ticker = this::tick;

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
        deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(ticker);
        handler.postDelayed(timeout, TIMEOUT_MS);
        handler.postDelayed(ticker, TICK_MS);
        updateCountdown();
    }

    private void tick() {
        if (countdown == null) return;
        updateCountdown();
        handler.postDelayed(ticker, TICK_MS);
    }

    private void updateCountdown() {
        if (countdown == null) return;
        long remaining = deadline - SystemClock.uptimeMillis();
        countdown.setFraction(remaining <= 0 ? 0f : remaining / (float) TIMEOUT_MS);
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
        panel.setBackground(Ui.focusBackground(this));
        int pad = Ui.px(this, PANEL_PADDING_PX);
        panel.setPadding(pad, pad, pad, pad);

        TextView kicker = Ui.kicker(this, "Quick overlay");
        kicker.setFocusable(false);
        panel.addView(kicker);

        status = Ui.text(this, "Ready", STATUS_PX, R.color.text_primary);
        status.setFocusable(false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Ui.px(this, STATUS_GAP_PX);
        panel.addView(status, statusParams);

        countdown = new CountdownBar(this);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = Ui.px(this, GAP_PX);
        barParams.bottomMargin = Ui.px(this, GAP_PX);
        panel.addView(countdown, barParams);

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
        add("Input source", this::openInputSource);
        add("Close", this::stopSelf);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Ui.px(this, PANEL_WIDTH_PX),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Ui.px(this, OFFSET_PX);
        params.y = Ui.px(this, OFFSET_PX);
        windows.addView(panel, params);
        if (focusChip != null) focusChip.requestFocus();
    }

    private void add(String title, Action action) {
        Button button = Ui.chip(this, title);
        button.setMinimumHeight(0);
        button.setOnClickListener(v -> {
            armTimeout();
            action.run();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.px(this, GAP_PX);
        panel.addView(button, params);
        if (focusChip == null) focusChip = button;
    }

    private void openInputSource() {
        handler.removeCallbacks(timeout);
        handler.removeCallbacks(ticker);
        detachPanel();
        try {
            startActivity(new Intent()
                    .setComponent(new ComponentName(
                            "com.softwinner.awsource", "com.softwinner.awsource.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, "Input source unavailable: " + Errors.describe(error),
                    Toast.LENGTH_LONG).show();
        }
        stopSelf();
    }

    private void detachPanel() {
        if (panel == null) return;
        if (panel.isAttachedToWindow()) windows.removeView(panel);
        panel = null;
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
        handler.removeCallbacks(ticker);
        detachPanel();
        status = null;
        countdown = null;
        focusChip = null;
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) { return null; }

    private interface Action {
        void run();
    }
}
