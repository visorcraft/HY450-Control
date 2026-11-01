package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class ProjectionActivity extends Activity {
    private static final float PAGE_MARGIN_PX = 64f;
    private static final float GAP_PX = 20f;
    private static final float HINT_PX = 32f;
    private static final float NOTE_PX = 40f;
    private Button saveBackupButton;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int margin = Ui.px(this, PAGE_MARGIN_PX);
        root.setPadding(margin, margin, margin, margin);

        root.addView(Ui.kicker(this, "VISORCRAFT · HY450 MAX"));
        root.addView(note("D-pad moves • centre activates", HINT_PX,
                R.color.text_tertiary));

        root.addView(section("PROJECTION"));
        LinearLayout backup = chipRow(root);
        saveBackupButton = addChip(backup, "Save projection backup", v -> run(() -> {
            ProjectionBackup.save(this);
            toast("Projection backup saved");
        }));
        addChip(backup, "Restore projection backup", v -> run(() -> {
            ProjectionBackup.restore(this);
            toast("Projection backup restored");
        }));
        LinearLayout shaping = chipRow(root);
        addChip(shaping, "Square the image",
                v -> startActivity(new Intent(this, RectangleActivity.class)));
        addChip(shaping, "Calibration toolkit",
                v -> startActivity(new Intent(this, CalibrationActivity.class)));

        root.addView(section("OEM TOOLS"));
        root.addView(note("OEM focus and correction: your saved keystone is restored "
                + "automatically after you leave the OEM screen.", NOTE_PX,
                R.color.text_secondary));
        root.addView(note("OEM manual keystone: the OEM's keystone values are kept, with "
                + "no automatic restore.", NOTE_PX, R.color.text_secondary));
        root.addView(note("Full screen (reset keystone): the current projection is backed "
                + "up, then the keystone correction is cleared.", NOTE_PX,
                R.color.text_secondary));
        LinearLayout oem = chipRow(root);
        addChip(oem, "OEM focus and correction", v -> OemFocus.launch(this));
        addChip(oem, "OEM manual keystone", v -> {
            OemFocus.cancelRestore();
            component("com.hysd.vafocus", "com.hysd.vafocus.TchjzActivity");
        });
        LinearLayout reset = chipRow(root);
        addChip(reset, "Full screen (reset keystone)", v -> run(() -> {
            OemFocus.cancelRestore();
            ProjectionBackup.save(this);
            Class.forName("com.hysd.vafocus.PropertiesUtils")
                    .getMethod("keystone_reset", int.class).invoke(null, 1);
            toast("Keystone reset");
        }));

        scroll.addView(root);
        setContentView(scroll);
        root.post(this::focusInitial);
    }

    public void onResume() {
        super.onResume();
        OemFocus.onResume(this);
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
        button.setOnClickListener(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Ui.px(this, GAP_PX));
        row.addView(button, params);
        return button;
    }

    private void focusInitial() {
        if (saveBackupButton != null) saveBackupButton.requestFocus();
    }

    private void component(String pkg, String activity) {
        try {
            startActivity(new Intent().setComponent(new ComponentName(pkg, activity)));
        } catch (Exception error) {
            toast(error.getMessage());
        }
    }

    private void run(Action action) {
        try {
            action.run();
        } catch (Exception error) {
            toast(error.getMessage());
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private interface Action {
        void run() throws Exception;
    }
}
