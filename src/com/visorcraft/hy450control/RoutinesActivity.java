package com.visorcraft.hy450control;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RoutinesActivity extends Activity {
    private static final String[] SLOTS = {"movie", "day", "night", "custom"};
    private static final String[] LABELS = {"Movie", "Day", "Night", "Custom"};
    private static final int MAX_WARNINGS = 3;
    private static final float PAGE_MARGIN_PX = 64f;
    private static final float GAP_PX = 20f;
    private static final float HINT_PX = 32f;
    private static final float NOTE_PX = 40f;

    static final class UiState {
        final boolean applyEnabled;
        final boolean undoEnabled;

        UiState(boolean applyEnabled, boolean undoEnabled) {
            this.applyEnabled = applyEnabled;
            this.undoEnabled = undoEnabled;
        }
    }

    private final Button[] slotButtons = new Button[SLOTS.length];
    private RoutineStore store;
    private Map<String, List<Control>> families;
    private String selected = SLOTS[0];
    private Button saveButton;
    private Button applyButton;
    private Button undoButton;
    private TextView status;
    private TextView values;

    static UiState uiState(RoutineEngine.Capture slot, RoutineEngine.Capture undo) {
        return new UiState(slot != null, undo != null);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new RoutineStore(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        int margin = Ui.px(this, PAGE_MARGIN_PX);
        root.setPadding(margin, margin, margin, margin);

        root.addView(Ui.kicker(this, "OWNER ASSISTANT · ROUTINES"));
        root.addView(Ui.note(this, "D-pad moves • centre selects • BACK cancels without changes",
                HINT_PX, R.color.text_tertiary));
        root.addView(Ui.section(this, "ROUTINE SLOTS", GAP_PX));
        for (int i = 0; i < SLOTS.length; i++) {
            final String slot = SLOTS[i];
            Button button = Ui.chip(this, LABELS[i]);
            button.setOnClickListener(v -> select(slot));
            slotButtons[i] = button;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = Ui.px(this, GAP_PX);
            root.addView(button, params);
        }

        LinearLayout actions = Ui.chipRow(root, GAP_PX);
        saveButton = Ui.addChip(actions, "Save selected", v -> confirmSave(), GAP_PX);
        applyButton = Ui.addChip(actions, "Apply selected", v -> confirmApply(), GAP_PX);
        undoButton = Ui.addChip(actions, "Undo last apply", v -> confirmUndo(), GAP_PX);
        status = Ui.note(this, "", NOTE_PX, R.color.text_secondary);
        root.addView(status);
        values = Ui.note(this, "Current values refresh after apply or undo",
                HINT_PX, R.color.text_secondary);
        root.addView(values);

        scroll.addView(root);
        setContentView(scroll);
        refreshSlots();
        showSelectedStatus();
        root.post(this::focusInitial);
    }

    private void select(String slot) {
        selected = slot;
        refreshSlots();
        showSelectedStatus();
    }

    private void confirmSave() {
        final RoutineEngine.Capture existing;
        try {
            existing = store.readSlot(selected);
        } catch (Exception error) {
            showError("Routine slot unavailable", error);
            return;
        }
        String title = "Save " + label(selected) + " routine?";
        String message = existing == null
                ? "Capture the current picture, audio, and input values into this slot."
                : "This overwrites the populated " + label(selected) + " slot.";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(existing == null ? "Save" : "Overwrite",
                        (dialog, which) -> saveSelected())
                .show();
    }

    private void saveSelected() {
        try {
            RoutineEngine.Capture capture = RoutineEngine.capture(families());
            store.saveSlot(selected, capture);
            status.setText(label(selected) + " saved • " + capture.values.size()
                    + " values • " + capture.warnings.size() + " omissions");
            refreshSlots();
        } catch (Exception error) {
            showError("Save failed", error);
        }
    }

    private void confirmApply() {
        try {
            final RoutineEngine.Capture routine = store.readSlot(selected);
            if (routine == null) {
                refreshSlots();
                return;
            }
            RoutineEngine.validate(families(), routine.values);
            final RoutineEngine.Capture undo = RoutineEngine.capture(families());
            new AlertDialog.Builder(this)
                    .setTitle("Apply " + label(selected) + " routine?")
                    .setMessage("Current values are saved for Undo before any apply write.\n"
                            + undoText(undo))
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Apply", (dialog, which) -> apply(routine, undo))
                    .show();
        } catch (Exception error) {
            showError("Apply unavailable", error);
        }
    }

    private void apply(RoutineEngine.Capture routine, RoutineEngine.Capture undo) {
        try {
            store.saveUndo(undo);
            RoutineEngine.Result result = RoutineEngine.apply(families(), routine.values);
            showResult("Applied", result.applied, result.failures);
        } catch (Exception error) {
            showError("Apply failed", error);
        } finally {
            refreshSlots();
            refreshValues();
        }
    }

    private void confirmUndo() {
        try {
            final RoutineEngine.Capture undo = store.readUndo();
            if (undo == null) {
                refreshSlots();
                return;
            }
            RoutineEngine.validate(families(), undo.values);
            new AlertDialog.Builder(this)
                    .setTitle("Undo last routine apply?")
                    .setMessage("Restore the values captured before the last apply.\n"
                            + undoText(undo))
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Undo", (dialog, which) -> undo(undo))
                    .show();
        } catch (Exception error) {
            showError("Undo unavailable", error);
        }
    }

    private void undo(RoutineEngine.Capture undo) {
        try {
            RoutineEngine.Result result = RoutineEngine.apply(families(), undo.values);
            showResult("Undo applied", result.applied, result.failures);
        } catch (Exception error) {
            showError("Undo failed", error);
        } finally {
            refreshSlots();
            refreshValues();
        }
    }

    private void refreshSlots() {
        RoutineEngine.Capture selectedCapture = null;
        RoutineEngine.Capture undo = null;
        String problem = null;
        for (int i = 0; i < SLOTS.length; i++) {
            try {
                RoutineEngine.Capture capture = store.readSlot(SLOTS[i]);
                if (SLOTS[i].equals(selected)) selectedCapture = capture;
                slotButtons[i].setText(LABELS[i] + (capture == null ? " · Empty"
                        : " · " + capture.values.size() + " values"));
            } catch (Exception error) {
                problem = Errors.describe(error);
                slotButtons[i].setText(LABELS[i] + " · Unavailable");
            }
        }
        try {
            undo = store.readUndo();
        } catch (Exception error) {
            problem = Errors.describe(error);
        }
        UiState state = uiState(selectedCapture, undo);
        applyButton.setEnabled(state.applyEnabled);
        undoButton.setEnabled(state.undoEnabled);
        if (problem != null) status.setText("Routine storage unavailable: " + problem);
        keepFocusEnabled();
    }

    private void showSelectedStatus() {
        try {
            RoutineEngine.Capture capture = store.readSlot(selected);
            status.setText(label(selected) + " selected"
                    + (capture == null ? " · Empty" : " · Populated"));
        } catch (Exception ignored) {
            // refreshSlots already reports storage failures.
        }
    }

    private void refreshValues() {
        try {
            RoutineEngine.Capture current = RoutineEngine.capture(families());
            StringBuilder text = new StringBuilder("Current values: ")
                    .append(current.values.size()).append(" captured")
                    .append(" • ").append(current.warnings.size()).append(" omissions");
            int shown = 0;
            for (Map.Entry<String, Integer> entry : current.values.entrySet()) {
                if (shown++ == MAX_WARNINGS) break;
                text.append("\n").append(entry.getKey()).append(" = ").append(entry.getValue());
            }
            values.setText(text.toString());
        } catch (Exception error) {
            values.setText("Current values unavailable: " + Errors.describe(error));
        }
    }

    private Map<String, List<Control>> families() throws Exception {
        if (families == null) {
            families = new LinkedHashMap<>();
            families.put("picture", ControlPageActivity.loadControls(this, "picture"));
            families.put("audio", ControlPageActivity.loadControls(this, "audio"));
            families.put("source", ControlPageActivity.loadControls(this, "source"));
        }
        return families;
    }

    private void showResult(String verb, int applied, List<String> failures) {
        StringBuilder message = new StringBuilder();
        if (failures.isEmpty()) message.append(verb).append(" successfully • ");
        else message.append(verb).append(" with failures • ");
        message.append(applied).append(" values");
        if (!failures.isEmpty()) {
            message.append(" • ").append(failures.size()).append(" failed");
            int shown = Math.min(failures.size(), MAX_WARNINGS);
            for (int i = 0; i < shown; i++) message.append("\n").append(failures.get(i));
        }
        status.setText(message.toString());
    }

    private void showError(String prefix, Exception error) {
        status.setText(prefix + ": " + Errors.describe(error));
    }

    private static String undoText(RoutineEngine.Capture undo) {
        StringBuilder text = new StringBuilder("Undo capture: ")
                .append(undo.values.size()).append(" values • ")
                .append(undo.warnings.size()).append(" omissions");
        int shown = Math.min(undo.warnings.size(), MAX_WARNINGS);
        for (int i = 0; i < shown; i++) text.append("\n• ").append(undo.warnings.get(i));
        if (undo.warnings.size() > shown) {
            text.append("\n...and ").append(undo.warnings.size() - shown).append(" more");
        }
        return text.toString();
    }

    private String label(String slot) {
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i].equals(slot)) return LABELS[i];
        return slot;
    }

    private void focusInitial() {
        for (Button button : slotButtons) {
            if (button.isEnabled() && button.requestFocus()) return;
        }
        if (saveButton.isEnabled()) saveButton.requestFocus();
    }

    private void keepFocusEnabled() {
        View focused = getCurrentFocus();
        if (focused == null || focused.isEnabled()) return;
        if (slotButtons[0].requestFocus()) return;
        if (saveButton.isEnabled()) saveButton.requestFocus();
    }
}
