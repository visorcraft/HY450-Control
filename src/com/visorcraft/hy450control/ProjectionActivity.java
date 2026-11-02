package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        root.addView(Ui.note(this, "D-pad moves • centre activates", HINT_PX,
                R.color.text_tertiary));

        root.addView(Ui.section(this, "PROJECTION", GAP_PX));
        LinearLayout backup = Ui.chipRow(root, GAP_PX);
        saveBackupButton = Ui.addChip(backup, "Save projection backup", v -> run(() -> {
            ProjectionBackup.save(this);
            toast("Projection backup saved");
        }), GAP_PX);
        Ui.addChip(backup, "Restore projection backup", v -> run(() -> {
            ProjectionBackup.restore(this);
            toast("Projection backup restored");
        }), GAP_PX);
        LinearLayout shaping = Ui.chipRow(root, GAP_PX);
        Ui.addChip(shaping, "Square the image",
                v -> startActivity(new Intent(this, RectangleActivity.class)), GAP_PX);
        Ui.addChip(shaping, "Calibration toolkit",
                v -> startActivity(new Intent(this, CalibrationActivity.class)), GAP_PX);

        root.addView(Ui.section(this, "OEM TOOLS", GAP_PX));
        root.addView(Ui.note(this, "OEM focus and correction: your saved keystone is restored "
                + "automatically after you leave the OEM screen.", NOTE_PX,
                R.color.text_secondary));
        root.addView(Ui.note(this, "OEM manual keystone: the OEM's keystone values are kept, with "
                + "no automatic restore.", NOTE_PX, R.color.text_secondary));
        root.addView(Ui.note(this, "Full screen (reset keystone): the current projection is backed "
                + "up, then the keystone correction is cleared.", NOTE_PX,
                R.color.text_secondary));
        LinearLayout oem = Ui.chipRow(root, GAP_PX);
        Ui.addChip(oem, "OEM focus and correction", v -> OemFocus.launch(this), GAP_PX);
        Ui.addChip(oem, "OEM manual keystone", v -> run(() -> {
            OemFocus.cancelRestore(this);
            component("com.hysd.vafocus", "com.hysd.vafocus.TchjzActivity");
        }), GAP_PX);
        LinearLayout reset = Ui.chipRow(root, GAP_PX);
        Ui.addChip(reset, "Full screen (reset keystone)", v -> run(() -> {
            OemFocus.cancelRestore(this);
            ProjectionBackup.save(this);
            Class.forName("com.hysd.vafocus.PropertiesUtils")
                    .getMethod("keystone_reset", int.class).invoke(null, 1);
            toast("Keystone reset");
        }), GAP_PX);

        scroll.addView(root);
        setContentView(scroll);
        root.post(this::focusInitial);
    }

    public void onResume() {
        super.onResume();
        OemFocus.onResume(this);
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
