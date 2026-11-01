package com.visorcraft.hy450control;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.IBinder;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

public final class OverlayService extends Service {
    private WindowManager windows;
    private LinearLayout panel;

    public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (panel == null) showPanel();
        return START_STICKY;
    }

    private void showPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(230, 20, 20, 20));
        panel.setPadding(24, 24, 24, 24);
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
        panel.getChildAt(0).requestFocus();
    }

    private void add(String title, Action action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(20);
        button.setOnClickListener(v -> action.run());
        panel.addView(button);
    }

    private void adjust(VendorIntControl control, int direction) {
        try {
            control.write(control.read() + direction);
        } catch (Exception ignored) {
        }
    }

    private void volume(int direction) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
    }

    public void onDestroy() {
        if (panel != null) windows.removeView(panel);
        panel = null;
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) { return null; }

    private interface Action {
        void run();
    }
}
