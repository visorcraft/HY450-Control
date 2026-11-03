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
        root.addView(Ui.note(this, "D-pad moves • centre activates", HINT_PX,
                R.color.text_tertiary));
        status = Ui.text(this, "", STATUS_PX, R.color.text_secondary, Typeface.MONOSPACE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Ui.px(this, 8f);
        statusParams.bottomMargin = Ui.px(this, 8f);
        status.setLayoutParams(statusParams);
        root.addView(status);

        root.addView(Ui.section(this, "OVERLAY", GAP_PX));
        LinearLayout overlay = Ui.chipRow(root, GAP_PX);
        openOverlayButton = Ui.addChip(overlay, "Open quick overlay",
                v -> startService(new Intent(this, OverlayService.class)), GAP_PX);
        Ui.addChip(overlay, "Close overlay",
                v -> stopService(new Intent(this, OverlayService.class)), GAP_PX);

        root.addView(Ui.section(this, "AUTOMATION", GAP_PX));
        startupRow = addRow(root, "Startup app", v -> cycle("startup", STARTUPS.length, index -> {
            prefs.edit().putString("startup_package", STARTUPS[index]).apply();
            refresh();
        }));
        sleepRow = addRow(root, "Sleep timer", v -> cycle("sleep", SLEEP_VALUES.length, index -> {
            int minutes = SLEEP_VALUES[index];
            SleepTimer.set(this, minutes);
            prefs.edit().putInt("sleep_minutes", minutes).apply();
            refresh();
        }));

        root.addView(Ui.section(this, "REMOTE SHORTCUTS", GAP_PX));
        root.addView(Ui.note(this, "Remote shortcuts — not available on this firmware", NOTE_PX,
                R.color.warning));
        root.addView(Ui.note(this, "The OEM consumes Menu and Guide before apps, so the overlay "
                + "shortcut cannot be remapped here.", NOTE_PX, R.color.text_secondary));
        LinearLayout remote = Ui.chipRow(root, GAP_PX);
        Button remoteKey = Ui.addChip(remote, "Remote shortcut key · " + remoteKeyName(), null,
                GAP_PX);
        remoteKey.setEnabled(false);
        Ui.addChip(remote, "Open accessibility settings", v -> openAccessibilitySettings(), GAP_PX);

        scroll.addView(root);
        setContentView(scroll);
        refresh();
        root.post(this::focusInitial);
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

    private void cycle(String key, int count, AutomationSelection.Action action) {
        try {
            int index = AutomationSelection.cycle(
                    prefs.getInt("cycle_" + key, 0), count, action);
            prefs.edit().putInt("cycle_" + key, index).apply();
        } catch (Exception error) {
            refresh();
            status.setText("Setting unavailable: " + Errors.describe(error));
        }
    }

    private void refresh() {
        String startup = prefs.getString("startup_package", "");
        int startupIndex = 0;
        for (int i = 0; i < STARTUPS.length; i++) {
            if (STARTUPS[i].equals(startup)) startupIndex = i;
        }
        int sleep = prefs.getInt("sleep_minutes", 0);
        status.setText("Startup: " + STARTUP_NAMES[startupIndex]
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
}
