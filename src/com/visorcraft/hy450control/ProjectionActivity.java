package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class ProjectionActivity extends Activity {
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 32);
        TextView title = new TextView(this);
        title.setText("Projection and calibration");
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        root.addView(title);
        add(root, "Save projection backup", v -> run(() -> {
            ProjectionBackup.save(this);
            toast("Projection backup saved");
        }));
        add(root, "Restore projection backup", v -> run(() -> {
            ProjectionBackup.restore(this);
            toast("Projection backup restored");
        }));
        add(root, "OEM focus and correction", v -> component("com.hysd.vafocus", "com.hysd.vafocus.VajzActivity"));
        add(root, "OEM manual keystone", v -> component("com.hysd.vafocus", "com.hysd.vafocus.TchjzActivity"));
        add(root, "Full screen (reset keystone)", v -> run(() -> {
            ProjectionBackup.save(this);
            Class.forName("com.hysd.vafocus.PropertiesUtils")
                    .getMethod("keystone_reset", int.class).invoke(null, 1);
            toast("Keystone reset");
        }));
        add(root, "Calibration toolkit", v -> startActivity(new Intent(this, CalibrationActivity.class)));
        scroll.addView(root);
        setContentView(scroll);
        root.getChildAt(1).requestFocus();
    }

    private void add(LinearLayout root, String title, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(21);
        button.setOnClickListener(action);
        root.addView(button);
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
