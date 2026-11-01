package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

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
            root.addView(text("Backlight unavailable: " + error.getMessage(), 18));
        }
        try {
            page(root, "Picture controls", "picture", ControlCatalog.picture(this));
            page(root, "Audio controls", "audio", ControlCatalog.audio(this));
            page(root, "Input controls", "source", ControlCatalog.source(this));
        } catch (Exception error) {
            Toast.makeText(this, "Hardware controls unavailable: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
        shortcut(root, "Automation and remote", new Intent(this, AutomationActivity.class));
        shortcut(root, "Projection and calibration", new Intent(this, ProjectionActivity.class));
        shortcut(root, "Square the image", new Intent(this, RectangleActivity.class));
        Button calibration = new Button(this);
        calibration.setText("Calibration toolkit");
        calibration.setTextSize(22);
        calibration.setOnClickListener(v -> startActivity(
                new Intent(this, CalibrationActivity.class)));
        root.addView(calibration);
        Button focus = new Button(this);
        focus.setText("OEM focus and correction");
        focus.setTextSize(22);
        focus.setOnClickListener(v -> OemFocus.launch(this));
        root.addView(focus);
        shortcut(root, "Input source", "com.softwinner.awsource", "com.softwinner.awsource.MainActivity");
        shortcut(root, "Android settings", "com.android.tv.settings", "com.android.tv.settings.MainSettings");
        scroll.addView(root);
        setContentView(scroll);
        root.getChildAt(3).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        OemFocus.onResume(this);
    }

    private void page(LinearLayout root, String title, String family, List<Control> controls) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(22);
        button.setOnClickListener(v -> ControlPageActivity.start(this, title, family, controls));
        root.addView(button);
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
                Toast.makeText(this, title + " unavailable: " + error.getMessage(),
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
