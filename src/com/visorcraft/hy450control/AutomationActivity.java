package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
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
    private static final float PAGE_MARGIN_PX = 64f;
    private static final float GAP_PX = 20f;
    private static final float HINT_PX = 32f;
    private static final float NOTE_PX = 40f;
    private static final float STATUS_PX = 40f;

    private SharedPreferences prefs;
    private TextView status;
    private Ui.Row startupRow;
    private Ui.Row sleepRow;
    private Button openOverlayButton;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("automation", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int margin = Ui.px(this, PAGE_MARGIN_PX);
        root.setPadding(margin, margin, margin, margin);

        root.addView(Ui.kicker(this, "VISORCRAFT · HY450 MAX"));
        root.addView(note("D-pad moves • centre activates", HINT_PX,
                R.color.text_tertiary));
        status = Ui.text(this, "", STATUS_PX, R.color.text_secondary, Typeface.MONOSPACE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Ui.px(this, 8f);
        statusParams.bottomMargin = Ui.px(this, 8f);
        status.setLayoutParams(statusParams);
        root.addView(status);

        root.addView(section("OVERLAY"));
        LinearLayout overlay = chipRow(root);
        openOverlayButton = addChip(overlay, "Open quick overlay",
                v -> startService(new Intent(this, OverlayService.class)));
        addChip(overlay, "Close overlay",
                v -> stopService(new Intent(this, OverlayService.class)));

        root.addView(section("AUTOMATION"));
        startupRow = addRow(root, "Startup app", v -> cycle("startup", STARTUPS.length, index -> {
            prefs.edit().putString("startup_package", STARTUPS[index]).apply();
            refresh();
        }));
        sleepRow = addRow(root, "Sleep timer", v -> cycle("sleep", SLEEP_VALUES.length, index -> {
            int minutes = SLEEP_VALUES[index];
            prefs.edit().putInt("sleep_minutes", minutes).apply();
            SleepTimer.set(this, minutes);
            refresh();
        }));

        root.addView(section("REMOTE SHORTCUTS"));
        root.addView(note("Remote shortcuts — not available on this firmware", NOTE_PX,
                R.color.warning));
        root.addView(note("The OEM consumes Menu and Guide before apps, so the overlay "
                + "shortcut cannot be remapped here.", NOTE_PX, R.color.text_secondary));
        LinearLayout remote = chipRow(root);
        Button remoteKey = addChip(remote, "Remote shortcut key · " + remoteKeyName(), null);
        remoteKey.setEnabled(false);
        addChip(remote, "Open accessibility settings", v -> openAccessibilitySettings());

        scroll.addView(root);
        setContentView(scroll);
        refresh();
        root.post(this::focusInitial);
    }

    private TextView section(String label) {
        TextView kicker = Ui.kicker(this, label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.px(this, GAP_PX);
        params.bottomMargin = Ui.px(this, 8f);
        kicker.setLayoutParams(params);
        return kicker;
    }

    private TextView note(String value, float designPx, int colorRes) {
        TextView view = Ui.text(this, value, designPx, colorRes);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.px(this, 8f);
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayout chipRow(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.px(this, GAP_PX);
        row.setLayoutParams(params);
        root.addView(row);
        return row;
    }

    private Button addChip(LinearLayout row, String title, View.OnClickListener action) {
        Button button = Ui.chip(this, title);
        if (action != null) button.setOnClickListener(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Ui.px(this, GAP_PX));
        row.addView(button, params);
        return button;
    }

    private Ui.Row addRow(LinearLayout root, String label, View.OnClickListener action) {
        Ui.Row row = new Ui.Row(this);
        row.label.setText(label);
        row.label.setOnClickListener(action);
        row.setOnClickListener(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.px(this, GAP_PX);
        row.setLayoutParams(params);
        root.addView(row);
        return row;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException error) {
            toast("Accessibility settings unavailable: " + Errors.describe(error));
        }
    }

    private String remoteKeyName() {
        int remote = prefs.getInt("remote_key", KeyEvent.KEYCODE_MENU);
        for (int i = 0; i < REMOTE_KEYS.length; i++) {
            if (REMOTE_KEYS[i] == remote) return REMOTE_NAMES[i];
        }
        return REMOTE_NAMES[0];
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
        startupRow.setValue(STARTUP_NAMES[startupIndex]);
        sleepRow.setValue(sleep == 0 ? "off" : sleep + " min");
    }

    private void focusInitial() {
        if (openOverlayButton != null) openOverlayButton.requestFocus();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private interface Action {
        void run(int index);
    }
}
