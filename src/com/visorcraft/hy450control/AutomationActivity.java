package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class AutomationActivity extends Activity {
    private static final int[] REMOTE_KEYS = {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_TV
    };
    private static final String[] REMOTE_NAMES = {"Menu", "Guide", "TV"};
    private static final String[] STARTUPS = {
            "", "com.google.android.youtube.tv", "com.spocky.projengmenu",
            "com.visorcraft.hy450control"
    };
    private static final String[] STARTUP_NAMES = {"Disabled", "YouTube TV", "Projectivy", "HY450 Control"};
    private static final int[] SLEEP_VALUES = {0, 15, 30, 60, 120};
    private SharedPreferences prefs;
    private TextView status;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("automation", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 32);
        TextView title = new TextView(this);
        title.setText("Automation and remote");
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        root.addView(title);
        status = new TextView(this);
        status.setTextSize(18);
        status.setTextColor(Color.LTGRAY);
        root.addView(status);
        add(root, "Open quick overlay", v -> startService(new Intent(this, OverlayService.class)));
        add(root, "Enable/disable remote shortcut service", v -> toggleAccessibility());
        add(root, "Remote shortcut key", v -> cycle("remote", REMOTE_KEYS.length, index -> {
            prefs.edit().putInt("remote_key", REMOTE_KEYS[index]).apply();
            refresh();
        }));
        add(root, "Startup app", v -> cycle("startup", STARTUPS.length, index -> {
            prefs.edit().putString("startup_package", STARTUPS[index]).apply();
            refresh();
        }));
        add(root, "Sleep timer", v -> cycle("sleep", SLEEP_VALUES.length, index -> {
            int minutes = SLEEP_VALUES[index];
            prefs.edit().putInt("sleep_minutes", minutes).apply();
            SleepTimer.set(this, minutes);
            refresh();
        }));
        add(root, "Close overlay", v -> stopService(new Intent(this, OverlayService.class)));
        scroll.addView(root);
        setContentView(scroll);
        root.getChildAt(2).requestFocus();
        refresh();
    }

    private void add(LinearLayout root, String title, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(21);
        button.setOnClickListener(action);
        root.addView(button);
    }

    private void toggleAccessibility() {
        ComponentName component = new ComponentName(this, RemoteAccessibilityService.class);
        String flattened = component.flattenToString();
        String current = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (current == null) current = "";
        boolean enabled = current.contains(flattened);
        String updated;
        if (enabled) {
            updated = current.replace(flattened, "").replace("::", ":")
                    .replaceAll("^:|:$", "");
        } else {
            updated = current.isEmpty() ? flattened : current + ":" + flattened;
        }
        Settings.Secure.putString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
        Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        toast(enabled ? "Remote shortcuts disabled" : "Remote shortcuts enabled");
    }

    private void cycle(String key, int count, Action action) {
        int index = prefs.getInt("cycle_" + key, 0);
        index = (index + 1) % count;
        prefs.edit().putInt("cycle_" + key, index).apply();
        action.run(index);
    }

    private void refresh() {
        int remote = prefs.getInt("remote_key", KeyEvent.KEYCODE_MENU);
        int remoteIndex = 0;
        for (int i = 0; i < REMOTE_KEYS.length; i++) {
            if (REMOTE_KEYS[i] == remote) remoteIndex = i;
        }
        String startup = prefs.getString("startup_package", "");
        int startupIndex = 0;
        for (int i = 0; i < STARTUPS.length; i++) {
            if (STARTUPS[i].equals(startup)) startupIndex = i;
        }
        int sleep = prefs.getInt("sleep_minutes", 0);
        status.setText("Shortcut: " + REMOTE_NAMES[remoteIndex]
                + "  •  Startup: " + STARTUP_NAMES[startupIndex]
                + "  •  Sleep: " + (sleep == 0 ? "off" : sleep + " min"));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private interface Action {
        void run(int index);
    }
}
