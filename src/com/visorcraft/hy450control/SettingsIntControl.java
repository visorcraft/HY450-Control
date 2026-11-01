package com.visorcraft.hy450control;

import android.content.Context;
import android.provider.Settings;
import java.io.IOException;

public final class SettingsIntControl implements ControlAdapter {
    private final Context context;
    private final String key;
    private final int min;
    private final int max;

    public SettingsIntControl(Context context, String key, int min, int max) {
        this.context = context.getApplicationContext();
        this.key = key;
        this.min = min;
        this.max = max;
    }

    public boolean isAvailable() { return true; }
    public int min() { return min; }
    public int max() { return max; }

    public int read() throws Settings.SettingNotFoundException {
        return Settings.System.getInt(context.getContentResolver(), key);
    }

    public void write(int value) throws Exception {
        if (value < min || value > max) throw new IllegalArgumentException("Out of range");
        int original = read();
        if (!Settings.System.putInt(context.getContentResolver(), key, value)) {
            throw new IOException("Setting write rejected");
        }
        if (read() != value) {
            Settings.System.putInt(context.getContentResolver(), key, original);
            throw new IOException("Setting readback mismatch");
        }
    }
}
