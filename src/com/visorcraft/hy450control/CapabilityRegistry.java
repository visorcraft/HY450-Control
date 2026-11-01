package com.visorcraft.hy450control;

import android.content.Context;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

public final class CapabilityRegistry {
    public static List<Control> all(Context context) {
        List<Control> controls = new ArrayList<>();
        controls.add(new Control("display.backlight", "Backlight",
                new SettingsIntControl(context, Settings.System.SCREEN_BRIGHTNESS, 0, 255)));
        return controls;
    }

    private CapabilityRegistry() {}
}
