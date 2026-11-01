package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public final class ControlPageActivity extends Activity {
    private static List<Control> pending;

    private final List<Control> controls = new ArrayList<>();
    private final List<TextView> rows = new ArrayList<>();
    private String family;
    private int index;
    private PresetStore presets;
    private Button saveButton;
    private Button applyButton;

    public static void start(Context context, String title, String family, List<Control> controls) {
        pending = controls;
        Intent intent = new Intent(context, ControlPageActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("family", family);
        context.startActivity(intent);
    }

    public void onCreate(Bundle state) {
        super.onCreate(state);
        controls.addAll(pending);
        family = getIntent().getStringExtra("family");
        presets = new PresetStore(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 32);
        TextView title = new TextView(this);
        title.setText(getIntent().getStringExtra("title"));
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        root.addView(title);
        TextView help = new TextView(this);
        help.setText("D-pad up/down selects • left/right adjusts • centre steps forward");
        help.setTextColor(Color.LTGRAY);
        root.addView(help);
        saveButton = addAction(root, "Save current as preset", v -> savePreset());
        applyButton = addAction(root, "Apply saved preset", v -> applyPreset());
        for (Control control : controls) {
            TextView row = new TextView(this);
            row.setTextSize(22);
            row.setTextColor(Color.WHITE);
            row.setPadding(24, 20, 24, 20);
            row.setFocusable(true);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            root.addView(row);
            rows.add(row);
        }
        scroll.addView(root);
        setContentView(scroll);
        backupOriginals();
        refresh();
    }

    private Button addAction(LinearLayout root, String title, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(20);
        button.setOnClickListener(action);
        root.addView(button);
        return button;
    }

    private void backupOriginals() {
        try {
            BackupStore backups = new BackupStore(this);
            if (!backups.hasSnapshot(family)) {
                Map<String, Integer> values = new HashMap<>();
                for (Control control : controls) {
                    try {
                        values.put(control.id, control.adapter.read());
                    } catch (Exception error) {
                        throw new IllegalStateException(control.id + ": " + error.getMessage(), error);
                    }
                }
                backups.snapshot(family, values);
            }
        } catch (Exception error) {
            toast("Backup failed: " + error.getMessage());
        }
    }

    private void refresh() {
        for (int i = 0; i < controls.size(); i++) {
            Control control = controls.get(i);
            try {
                rows.get(i).setText(control.title + "\n" + control.adapter.read());
            } catch (Exception error) {
                rows.get(i).setText(control.title + "\nUnavailable: " + error.getMessage());
            }
        }
        if (!rows.isEmpty()) rows.get(index).requestFocus();
    }

    private void adjust(int direction) {
        Control control = controls.get(index);
        try {
            int current = control.adapter.read();
            int next = current + direction;
            if (next < control.adapter.min()) next = control.adapter.max();
            if (next > control.adapter.max()) next = control.adapter.min();
            control.adapter.write(next);
            toast(control.title + ": " + next);
        } catch (Exception error) {
            toast(control.title + " failed: " + error.getMessage());
        }
        refresh();
    }

    private void savePreset() {
        try {
            Map<String, Integer> values = new HashMap<>();
            for (Control control : controls) values.put(control.id, control.adapter.read());
            presets.save(family, values);
            toast("Preset saved");
        } catch (Exception error) {
            toast("Save failed: " + error.getMessage());
        }
    }

    private void applyPreset() {
        try {
            JSONObject stored = presets.read(family).getJSONObject("values");
            for (Control control : controls) {
                if (stored.has(control.id)) control.adapter.write(stored.getInt(control.id));
            }
            toast("Preset applied");
        } catch (Exception error) {
            toast("Apply failed: " + error.getMessage());
        }
        refresh();
    }

    public boolean onKeyDown(int code, KeyEvent event) {
        if (rows.isEmpty()) return super.onKeyDown(code, event);
        boolean rowFocused = rows.get(index).isFocused();
        if (code == KeyEvent.KEYCODE_DPAD_UP && rowFocused && index == 0) {
            applyButton.requestFocus();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP && applyButton.isFocused()) {
            saveButton.requestFocus();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && applyButton.isFocused()) {
            rows.get(0).requestFocus();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && saveButton.isFocused()) {
            applyButton.requestFocus();
            return true;
        }
        if (rowFocused && code == KeyEvent.KEYCODE_DPAD_UP && index > 0) {
            index--;
            refresh();
            return true;
        }
        if (rowFocused && code == KeyEvent.KEYCODE_DPAD_DOWN && index < rows.size() - 1) {
            index++;
            refresh();
            return true;
        }
        if (rowFocused && code == KeyEvent.KEYCODE_DPAD_LEFT) {
            adjust(-1);
            return true;
        }
        if (rowFocused && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            adjust(1);
            return true;
        }
        if (rowFocused && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
            adjust(1);
            return true;
        }
        return super.onKeyDown(code, event);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
