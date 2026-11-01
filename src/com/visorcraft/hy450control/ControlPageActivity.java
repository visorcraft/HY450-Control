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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public final class ControlPageActivity extends Activity {
    private final List<Control> controls = new ArrayList<>();
    private final List<TextView> rows = new ArrayList<>();
    private String family;
    private int index;
    private PresetStore presets;
    private Button saveButton;
    private Button applyButton;
    private Button restoreButton;
    private TextView status;

    static List<Control> loadControls(Context context, String family) throws Exception {
        if ("picture".equals(family)) return ControlCatalog.picture(context);
        if ("audio".equals(family)) return ControlCatalog.audio(context);
        if ("source".equals(family)) return ControlCatalog.source(context);
        throw new IllegalArgumentException("Unknown control family");
    }

    public static void start(Context context, String title, String family) {
        Intent intent = new Intent(context, ControlPageActivity.class);
        intent.putExtra("title", title);
        intent.putExtra("family", family);
        context.startActivity(intent);
    }

    public void onCreate(Bundle state) {
        super.onCreate(state);
        family = getIntent().getStringExtra("family");
        String loadError = null;
        try {
            controls.addAll(loadControls(this, family));
        } catch (Exception error) {
            loadError = error.getMessage();
        }
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
        restoreButton = addAction(root, "Restore original values", v -> restoreOriginals());
        for (int i = 0; i < controls.size(); i++) {
            Control control = controls.get(i);
            TextView row = new TextView(this);
            row.setTextSize(22);
            row.setTextColor(Color.WHITE);
            row.setPadding(24, 20, 24, 20);
            row.setFocusable(control.adapter.isWritable());
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            final int position = i;
            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) index = position;
            });
            root.addView(row);
            rows.add(row);
        }
        scroll.addView(root);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        status = new TextView(this);
        status.setText("Original values are backed up automatically");
        status.setTextSize(18);
        status.setTextColor(Color.LTGRAY);
        status.setPadding(24, 20, 24, 20);
        status.setFocusable(false);
        outer.addView(status);
        setContentView(outer);
        if (loadError == null) {
            backupOriginals();
        } else {
            status.setText("Controls unavailable: " + loadError);
        }
        refresh();
        root.post(this::focusFirstRow);
    }

    private Button addAction(LinearLayout root, String title, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(20);
        button.setOnClickListener(action);
        root.addView(button);
        return button;
    }

    private void focusFirstRow() {
        for (int i = 0; i < rows.size(); i++) {
            if (controls.get(i).adapter.isWritable()) {
                index = i;
                rows.get(i).requestFocus();
                return;
            }
        }
        saveButton.requestFocus();
    }

    private void backupOriginals() {
        Map<String, Integer> values = new HashMap<>();
        List<String> failures = new ArrayList<>();
        for (Control control : controls) {
            if (!control.adapter.isWritable()) continue;
            try {
                values.put(control.id, control.adapter.read());
            } catch (Exception error) {
                failures.add(control.title + ": " + error.getMessage());
            }
        }
        if (values.isEmpty()) {
            String message = "No original values could be read • no snapshot was created";
            if (!failures.isEmpty()) message = message + "\n" + join(failures);
            status.setText(message);
            return;
        }
        boolean written = false;
        try {
            BackupStore backups = new BackupStore(this);
            if (!backups.hasSnapshot(family)) {
                backups.snapshot(family, values);
                written = true;
            }
        } catch (Exception error) {
            failures.add("Snapshot: " + error.getMessage());
        }
        if (!failures.isEmpty()) {
            status.setText("Backup incomplete • " + failures.size() + " not backed up"
                    + "\n" + join(failures));
        } else if (written) {
            status.setText("Original values backed up");
        }
    }

    private void refresh() {
        for (int i = 0; i < controls.size(); i++) {
            Control control = controls.get(i);
            try {
                rows.get(i).setText(control.title + "\n" + control.adapter.format(control.adapter.read()));
            } catch (Exception error) {
                rows.get(i).setText(control.title + "\nUnavailable: " + error.getMessage());
            }
        }
    }

    private void adjust(int direction) {
        Control control = controls.get(index);
        if (!control.adapter.isWritable()) return;
        try {
            int current = control.adapter.read();
            int next = control.adapter.clamp(current + direction);
            if (next == current) {
                String limit = direction < 0 ? "Minimum" : "Maximum";
                status.setText(limit + " reached • " + control.title + ": "
                        + control.adapter.format(current));
            } else {
                control.adapter.write(next);
                int actual = control.adapter.read();
                status.setText(control.title + ": " + control.adapter.format(actual)
                        + " • read back OK");
            }
        } catch (Exception error) {
            status.setText(control.title + " failed: " + error.getMessage());
        }
        refresh();
    }

    private void restoreOriginals() {
        int restored = 0;
        List<String> failures = new ArrayList<>();
        try {
            JSONObject stored = new BackupStore(this).readSnapshot(family)
                    .getJSONObject("values");
            for (Control control : controls) {
                if (!control.adapter.isWritable()) continue;
                if (!stored.has(control.id)) {
                    failures.add(control.title + ": no saved value");
                    continue;
                }
                try {
                    control.adapter.write(stored.getInt(control.id));
                    restored++;
                } catch (Exception error) {
                    failures.add(control.title + ": " + error.getMessage());
                }
            }
            StringBuilder message = new StringBuilder("Restored " + restored + " values");
            if (!failures.isEmpty()) {
                message.append(" • ").append(failures.size()).append(" not restored");
                for (String failure : failures) message.append("\n").append(failure);
            }
            status.setText(message.toString());
        } catch (Exception error) {
            status.setText("Restore unavailable: " + error.getMessage());
        }
        refresh();
    }

    private void savePreset() {
        try {
            Map<String, Integer> values = new HashMap<>();
            for (Control control : controls) values.put(control.id, control.adapter.read());
            presets.save(family, values);
            status.setText("Preset saved");
        } catch (Exception error) {
            status.setText("Save failed: " + error.getMessage());
        }
    }

    private void applyPreset() {
        try {
            JSONObject stored = presets.read(family).getJSONObject("values");
            for (Control control : controls) {
                if (stored.has(control.id)) control.adapter.write(stored.getInt(control.id));
            }
            status.setText("Preset applied");
        } catch (Exception error) {
            status.setText("Apply failed: " + error.getMessage());
        }
        refresh();
    }

    public boolean onKeyDown(int code, KeyEvent event) {
        if (rows.isEmpty()) return super.onKeyDown(code, event);
        boolean rowFocused = rows.get(index).isFocused();
        if (rowFocused && controls.get(index).adapter.isWritable()) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                adjust(-1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                adjust(1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                adjust(1);
                return true;
            }
        }
        return super.onKeyDown(code, event);
    }

    private String join(List<String> values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            if (text.length() > 0) text.append("\n");
            text.append(value);
        }
        return text.toString();
    }
}
