package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(64, 48, 64, 48);
        TextView title = text("HY450 Control", 36);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        TextView info = text(metrics.widthPixels + " x " + metrics.heightPixels
                + "  •  Android " + android.os.Build.VERSION.RELEASE, 22);
        root.addView(title);
        root.addView(info);
        try {
            Control backlight = CapabilityRegistry.all(this).get(0);
            root.addView(text(backlight.title + ": " + backlight.adapter.read(), 22));
        } catch (Exception error) {
            root.addView(text("Backlight unavailable: " + Errors.describe(error), 18));
        }
        controlPage(root, "Picture controls", "picture");
        controlPage(root, "Audio controls", "audio");
        controlPage(root, "Input controls", "source");
        shortcut(root, "Automation and remote", new Intent(this, AutomationActivity.class));
        shortcut(root, "Projection and calibration", new Intent(this, ProjectionActivity.class));
        Button calibration = new Button(this);
        calibration.setText("Calibration toolkit");
        calibration.setTextSize(22);
        calibration.setOnClickListener(v -> startActivity(
                new Intent(this, CalibrationActivity.class)));
        root.addView(calibration);
        shortcut(root, "OEM focus and correction", "com.hysd.vafocus", "com.hysd.vafocus.VajzActivity");
        shortcut(root, "Input source", "com.softwinner.awsource", "com.softwinner.awsource.MainActivity");
        shortcut(root, "Android settings", "com.android.tv.settings", "com.android.tv.settings.MainSettings");
        scroll.addView(root);
        setContentView(scroll);
        focusFirst(root);
    }

    private void controlPage(LinearLayout root, String title, String family) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(22);
        try {
            ControlPageActivity.loadControls(this, family);
            button.setOnClickListener(v -> ControlPageActivity.start(this, title, family));
        } catch (Exception error) {
            button.setText(title + "\nUnavailable: " + Errors.describe(error));
            button.setEnabled(false);
        }
        root.addView(button);
    }

    private void focusFirst(LinearLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child.isEnabled() && child.isFocusable()) {
                child.requestFocus();
                return;
            }
        }
    }

    private void shortcut(LinearLayout root, String title, Intent intent) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(22);
        button.setOnClickListener(v -> startActivity(intent));
        root.addView(button);
    }

    private void shortcut(LinearLayout root, String title, String pkg, String activity) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(22);
        button.setOnClickListener(v -> {
            try {
                startActivity(new Intent().setComponent(new ComponentName(pkg, activity)));
            } catch (ActivityNotFoundException | SecurityException error) {
                Toast.makeText(this, title + " unavailable: " + Errors.describe(error),
                        Toast.LENGTH_LONG).show();
            }
        });
        root.addView(button);
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setPadding(24, 16, 24, 16);
        return view;
    }
}
