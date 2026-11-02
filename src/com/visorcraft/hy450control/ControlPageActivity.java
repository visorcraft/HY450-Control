package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public final class ControlPageActivity extends Activity {
    private static final int MAX_DETAILS = 3;
    private static final float PAGE_MARGIN_PX = 64f;
    private static final float ROW_HEIGHT_PX = 112f;
    private static final float ROW_MARGIN_PX = 20f;
    private static final float TARGET_VALUE_PX = 76f;
    private static final float TARGET_VALUE_MAX_PX = 800f;
    private static final float TARGET_LABEL_PX = 46f;
    private static final float MARKER_PX = 30f;
    private static final float HINT_PX = 32f;
    private static final float STATUS_SUMMARY_PX = 46f;
    private static final float STATUS_DETAIL_PX = 40f;
    private static final float DOT_PX = 16f;
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private enum StatusKind {
        NEUTRAL, SUCCESS, WARNING, ERROR
    }

    private final List<Control> controls = new ArrayList<>();
    private final List<Ui.Row> rows = new ArrayList<>();
    private final List<String> displayedValues = new ArrayList<>();
    private String family;
    private String familyLabel;
    private String loadError;
    private int index;
    private int target = -1;
    private PresetStore presets;
    private Button saveButton;
    private Button applyButton;
    private Button restoreButton;
    private TextView targetLabel;
    private TextView targetValue;
    private TextView status;
    private TextView statusDetail;
    private GradientDrawable statusDot;
    private View errorSurface;

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

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        family = getIntent().getStringExtra("family");
        familyLabel = getIntent().getStringExtra("title");
        try {
            controls.addAll(loadControls(this, family));
        } catch (Exception error) {
            loadError = Errors.describe(error);
        }
        presets = new PresetStore(this);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(header());
        outer.addView(chipRail());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int margin = Ui.px(this, PAGE_MARGIN_PX);
        root.setPadding(margin, Ui.px(this, 24f), margin, Ui.px(this, 32f));
        for (int i = 0; i < controls.size(); i++) {
            Control control = controls.get(i);
            Ui.Row row = new Ui.Row(this);
            row.label.setText(control.title);
            row.setMinimumHeight(Ui.px(this, ROW_HEIGHT_PX));
            row.setReadOnly(!control.adapter.isWritable());
            final int position = i;
            row.label.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) return;
                index = position;
                target = position;
                updateTarget(position);
            });
            root.addView(row, rowParams());
            rows.add(row);
            displayedValues.add("");
        }
        if (loadError != null || controls.isEmpty()) {
            saveButton.setEnabled(false);
            applyButton.setEnabled(false);
            restoreButton.setEnabled(false);
            errorSurface = errorCard();
            root.addView(errorSurface, rowParams());
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.addView(root);
        outer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        outer.addView(statusBand());
        setContentView(outer);
        if (loadError == null) {
            backupOriginals();
        } else {
            setStatus("Controls unavailable: " + loadError, StatusKind.ERROR);
        }
        refresh();
        root.post(this::focusInitial);
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int gap = Ui.px(this, ROW_MARGIN_PX);
        params.setMargins(gap, gap, gap, 0);
        return params;
    }

    private View header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.px(this, PAGE_MARGIN_PX), Ui.px(this, 24f),
                Ui.px(this, PAGE_MARGIN_PX), Ui.px(this, 16f));
        header.addView(Ui.kicker(this, familyLabel));
        header.addView(Ui.text(this, "D-pad up/down selects • left/right adjusts • centre steps forward",
                HINT_PX, R.color.text_tertiary));
        header.addView(targetBand());
        return header;
    }

    private View targetBand() {
        LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView marker = Ui.text(this, "TARGET", MARKER_PX, R.color.accent, MEDIUM);
        marker.setLetterSpacing(0.12f);
        column.addView(marker);
        targetLabel = Ui.text(this, familyLabel, TARGET_LABEL_PX, R.color.text_primary, MEDIUM);
        column.addView(targetLabel);
        band.addView(column, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        targetValue = Ui.text(this, "Select a control", TARGET_VALUE_PX, R.color.accent,
                Typeface.MONOSPACE);
        targetValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        targetValue.setMaxWidth(Ui.px(this, TARGET_VALUE_MAX_PX));
        band.addView(targetValue, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return band;
    }

    private View chipRail() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setFocusable(false);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(Ui.px(this, PAGE_MARGIN_PX), 0, Ui.px(this, PAGE_MARGIN_PX),
                Ui.px(this, 16f));
        rail.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        saveButton = Ui.addChip(rail, "Save preset", v -> savePreset(), ROW_MARGIN_PX);
        applyButton = Ui.addChip(rail, "Apply preset", v -> applyPreset(), ROW_MARGIN_PX);
        restoreButton = Ui.addChip(rail, "Restore originals", v -> restoreOriginals(), ROW_MARGIN_PX);
        scroll.addView(rail);
        return scroll;
    }

    private View errorCard() {
        String message = loadError != null ? "Controls unavailable: " + loadError
                : "No controls in this family";
        TextView card = Ui.text(this, message, STATUS_DETAIL_PX, R.color.error);
        card.setBackground(Ui.focusBackground(this));
        card.setPaddingRelative(Ui.px(this, 24f), Ui.px(this, 24f),
                Ui.px(this, 24f), Ui.px(this, 24f));
        card.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        card.setMinHeight(Ui.px(this, ROW_HEIGHT_PX));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        return card;
    }

    private View statusBand() {
        LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.CENTER_VERTICAL);
        band.setPadding(Ui.px(this, PAGE_MARGIN_PX), Ui.px(this, 16f),
                Ui.px(this, PAGE_MARGIN_PX), Ui.px(this, 16f));
        band.setBackground(Ui.focusBackground(this));

        statusDot = new GradientDrawable();
        statusDot.setShape(GradientDrawable.OVAL);
        statusDot.setColor(getColor(R.color.accent));
        View dot = new View(this);
        dot.setBackground(statusDot);
        dot.setFocusable(false);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                Ui.px(this, DOT_PX), Ui.px(this, DOT_PX));
        dotParams.setMarginEnd(Ui.px(this, ROW_MARGIN_PX));
        band.addView(dot, dotParams);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        status = Ui.text(this, "Original values are backed up automatically",
                STATUS_SUMMARY_PX, R.color.text_primary, MEDIUM);
        status.setFocusable(false);
        statusDetail = Ui.text(this, "", STATUS_DETAIL_PX, R.color.text_secondary);
        statusDetail.setFocusable(false);
        column.addView(status);
        column.addView(statusDetail);
        band.addView(column, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return band;
    }

    private void focusInitial() {
        for (int i = 0; i < rows.size(); i++) {
            if (controls.get(i).adapter.isWritable() && rows.get(i).label.requestFocus()) return;
        }
        if (errorSurface != null && errorSurface.requestFocus()) return;
        if (saveButton.isEnabled()) saveButton.requestFocus();
    }

    private void backupOriginals() {
        Map<String, Integer> values = new HashMap<>();
        List<String> failures = new ArrayList<>();
        try {
            BackupStore backups = new BackupStore(this);
            if (backups.hasSnapshot(family)) {
                setStatus("Original values backed up", StatusKind.SUCCESS);
                return;
            }
            for (Control control : controls) {
                if (!control.adapter.isWritable()) continue;
                try {
                    values.put(control.id, control.adapter.read());
                } catch (Exception error) {
                    failures.add(control.title + ": " + Errors.describe(error));
                }
            }
            if (values.isEmpty()) {
                String message = "No original values could be read • no snapshot was created";
                if (!failures.isEmpty()) message = message + "\n" + detail(failures);
                setStatus(message, StatusKind.WARNING);
                return;
            }
            backups.snapshot(family, values);
        } catch (Exception error) {
            failures.add("Snapshot: " + Errors.describe(error));
        }
        if (!failures.isEmpty()) {
            setStatus("Backup incomplete • " + failures.size() + " not backed up"
                    + "\n" + detail(failures), StatusKind.WARNING);
        } else {
            setStatus("Original values backed up", StatusKind.SUCCESS);
        }
    }

    private void refresh() {
        for (int i = 0; i < controls.size(); i++) {
            Control control = controls.get(i);
            String text;
            try {
                text = control.adapter.format(control.adapter.read());
            } catch (Exception error) {
                text = "Unavailable: " + Errors.describe(error);
            }
            displayedValues.set(i, text);
            rows.get(i).setValue(text);
        }
        updateTarget(target);
    }

    private void updateTarget(int position) {
        if (loadError != null) {
            targetLabel.setText("Controls unavailable");
            targetValue.setText("—");
            return;
        }
        if (position < 0 || position >= displayedValues.size()) {
            targetLabel.setText(familyLabel);
            targetValue.setText(controls.isEmpty() ? "No controls in this family"
                    : "Select a control");
            return;
        }
        targetLabel.setText(controls.get(position).title);
        targetValue.setText(displayedValues.get(position));
    }

    private void adjust(int direction) {
        Control control = controls.get(index);
        if (!control.adapter.isWritable()) return;
        try {
            int current = control.adapter.read();
            int next = control.adapter.clamp(current + direction);
            if (next == current) {
                String limit = direction < 0 ? "Minimum" : "Maximum";
                String text = control.adapter.format(current);
                setStatus(limit + " reached • " + control.title + ": " + text, StatusKind.WARNING);
                cacheValue(index, text);
            } else {
                control.adapter.write(next);
                int actual = control.adapter.read();
                String text = control.adapter.format(actual);
                cacheValue(index, text);
                setStatus(control.title + ": " + text + " • read back OK", StatusKind.SUCCESS);
            }
        } catch (Exception error) {
            setStatus(control.title + " failed: " + Errors.describe(error), StatusKind.ERROR);
            refreshRow(index);
        }
    }

    private void cacheValue(int position, String text) {
        displayedValues.set(position, text);
        rows.get(position).setValue(text);
        updateTarget(target);
    }

    private void refreshRow(int position) {
        Control control = controls.get(position);
        String text;
        try {
            text = control.adapter.format(control.adapter.read());
        } catch (Exception error) {
            text = "Unavailable: " + Errors.describe(error);
        }
        cacheValue(position, text);
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
                    failures.add(control.title + ": " + Errors.describe(error));
                }
            }
            StringBuilder message = new StringBuilder("Restored " + restored + " values");
            if (!failures.isEmpty()) {
                message.append(" • ").append(failures.size()).append(" not restored");
                message.append("\n").append(detail(failures));
            }
            setStatus(message.toString(), failures.isEmpty() ? StatusKind.SUCCESS : StatusKind.WARNING);
        } catch (Exception error) {
            setStatus("Restore unavailable: " + Errors.describe(error), StatusKind.ERROR);
        }
        refresh();
    }

    private void savePreset() {
        try {
            Map<String, Integer> values = new HashMap<>();
            for (Control control : controls) values.put(control.id, control.adapter.read());
            if (values.isEmpty()) {
                setStatus("Nothing to save", StatusKind.NEUTRAL);
                return;
            }
            presets.save(family, values);
            setStatus("Preset saved", StatusKind.SUCCESS);
        } catch (Exception error) {
            setStatus("Save failed: " + Errors.describe(error), StatusKind.ERROR);
        }
    }

    private void applyPreset() {
        try {
            JSONObject stored = presets.read(family).getJSONObject("values");
            ControlBatch.Result result = ControlBatch.apply(controls, new ControlBatch.Values() {
                public boolean has(String id) { return stored.has(id); }
                public int getInt(String id) throws Exception { return stored.getInt(id); }
            });
            StringBuilder message = new StringBuilder("Applied ").append(result.applied).append(" values");
            if (!result.failures.isEmpty()) {
                message.append(" • ").append(result.failures.size()).append(" not applied");
                message.append("\n").append(detail(result.failures));
            }
            setStatus(message.toString(), result.failures.isEmpty() ? StatusKind.SUCCESS : StatusKind.WARNING);
        } catch (Exception error) {
            setStatus("Apply unavailable: " + Errors.describe(error), StatusKind.ERROR);
        }
        refresh();
    }

    private void setStatus(String message, StatusKind kind) {
        int split = message.indexOf('\n');
        if (split < 0) {
            status.setText(message);
            statusDetail.setText("");
        } else {
            status.setText(message.substring(0, split));
            statusDetail.setText(message.substring(split + 1));
        }
        statusDot.setColor(getColor(colorFor(kind)));
    }

    private int colorFor(StatusKind kind) {
        switch (kind) {
            case SUCCESS:
                return R.color.success;
            case WARNING:
                return R.color.warning;
            case ERROR:
                return R.color.error;
            default:
                return R.color.accent;
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        if (rows.isEmpty()) return super.onKeyDown(code, event);
        Ui.Row row = rows.get(index);
        if (row.label.isFocused() && controls.get(index).adapter.isWritable()) {
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

    private String detail(List<String> failures) {
        StringBuilder text = new StringBuilder();
        int shown = Math.min(failures.size(), MAX_DETAILS);
        for (int i = 0; i < shown; i++) {
            if (i > 0) text.append("\n");
            text.append(failures.get(i));
        }
        if (failures.size() > shown) {
            text.append("\n...and ").append(failures.size() - shown).append(" more");
        }
        return text.toString();
    }
}
