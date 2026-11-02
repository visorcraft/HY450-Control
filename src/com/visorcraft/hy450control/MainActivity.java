package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final float PAGE_MARGIN_PX = 64f;
    private static final float CARD_RADIUS_PX = 16f;
    private static final float GAP_PX = 20f;
    private static final float TITLE_PX = 76f;
    private static final float DETAIL_PX = 40f;
    private static final float LABEL_PX = 46f;
    private static final float VALUE_PX = 52f;
    private static final Typeface BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private final List<Button> destinations = new ArrayList<>();
    private TextView lampValue;
    private TextView volumeValue;
    private Button pictureTile;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        int margin = Ui.px(this, PAGE_MARGIN_PX);
        root.setPadding(margin, margin, margin, margin);

        root.addView(identityBand());
        root.addView(liveBand());

        root.addView(section("TUNE"));
        LinearLayout tune = row();
        pictureTile = familyTile(tune, "Picture", "picture");
        familyTile(tune, "Audio", "audio");
        root.addView(tune);
        LinearLayout tuneSecond = row();
        familyTile(tuneSecond, "Input", "source");
        destinationTile(tuneSecond, "Projection & calibration", "Keystone and patterns",
                v -> startActivity(new Intent(this, ProjectionActivity.class)));
        root.addView(tuneSecond);

        root.addView(section("AUTOMATE"));
        LinearLayout automate = row();
        destinationTile(automate, "Automation & power", "Startup and sleep",
                v -> startActivity(new Intent(this, AutomationActivity.class)));
        destinationTile(automate, "Open quick overlay", "Quick controls overlay",
                v -> startService(new Intent(this, OverlayService.class)));
        root.addView(automate);

        root.addView(section("ADVANCED · OEM TOOLS"));
        LinearLayout tools = row();
        oemTool(tools, "OEM input source", "com.softwinner.awsource",
                "com.softwinner.awsource.MainActivity");
        oemTool(tools, "Android settings", "com.android.tv.settings",
                "com.android.tv.settings.MainSettings");
        root.addView(tools);

        scroll.addView(root);
        setContentView(scroll);
        root.post(this::focusInitial);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OemFocus.onResume(this);
        refreshLiveValues();
    }

    private View identityBand() {
        LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.VERTICAL);
        band.addView(Ui.kicker(this, "VISORCRAFT · HY450 MAX"));
        band.addView(accentTitle());
        TextView metadata = Ui.text(this, runtimeMetadata(), DETAIL_PX, R.color.text_secondary);
        LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metadataParams.topMargin = Ui.px(this, 8f);
        metadata.setLayoutParams(metadataParams);
        band.addView(metadata);
        band.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return band;
    }

    private View accentTitle() {
        TextView title = Ui.text(this, "HY450 CONTROL", TITLE_PX, R.color.ink_accent, BLACK);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setPaddingRelative(Ui.px(this, 32), Ui.px(this, 24), Ui.px(this, 32), Ui.px(this, 24));
        title.setBackground(accentField());
        title.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return title;
    }

    private GradientDrawable accentField() {
        GradientDrawable field = new GradientDrawable();
        field.setShape(GradientDrawable.RECTANGLE);
        field.setColor(getColor(R.color.accent));
        field.setCornerRadius(Ui.px(this, CARD_RADIUS_PX));
        return field;
    }

    private String runtimeMetadata() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return metrics.widthPixels + " × " + metrics.heightPixels
                + " · ANDROID " + Build.VERSION.RELEASE;
    }

    private View liveBand() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        lampValue = valueView();
        volumeValue = valueView();
        row.addView(instrument("LAMP", lampValue), cellParams(true));
        row.addView(instrument("VOLUME", volumeValue), cellParams(false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.px(this, 32f);
        row.setLayoutParams(params);
        return row;
    }

    private View instrument(String label, TextView value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPaddingRelative(Ui.px(this, 24), Ui.px(this, 16), Ui.px(this, 24), Ui.px(this, 16));
        card.setBackground(Ui.focusBackground(this));
        card.setFocusable(false);
        card.setFocusableInTouchMode(false);
        TextView name = Ui.text(this, label, LABEL_PX, R.color.text_secondary, MEDIUM);
        name.setFocusable(false);
        card.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        card.addView(value, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private TextView valueView() {
        TextView value = Ui.text(this, "—", VALUE_PX, R.color.text_primary, Typeface.MONOSPACE);
        value.setFocusable(false);
        value.setFocusableInTouchMode(false);
        return value;
    }

    private void refreshLiveValues() {
        try {
            VendorIntControl backlight = ControlCatalog.backlight();
            showValue(lampValue, String.valueOf(backlight.read()));
        } catch (Exception error) {
            showValue(lampValue, "Unavailable: " + Errors.describe(error), R.color.error);
        }
        try {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            showValue(volumeValue, audio.getStreamVolume(AudioManager.STREAM_MUSIC) + "/"
                    + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch (Exception error) {
            showValue(volumeValue, "Unavailable: " + Errors.describe(error), R.color.error);
        }
    }

    private void showValue(TextView view, String value) {
        showValue(view, value, R.color.text_primary);
    }

    private void showValue(TextView view, String value, int colorRes) {
        view.setText(value);
        view.setTextColor(getColor(colorRes));
    }

    private TextView section(String label) {
        TextView kicker = Ui.kicker(this, label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.px(this, 20f);
        params.bottomMargin = Ui.px(this, 8f);
        kicker.setLayoutParams(params);
        return kicker;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.px(this, GAP_PX);
        row.setLayoutParams(params);
        return row;
    }

    private LinearLayout.LayoutParams cellParams(boolean leading) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (leading) params.setMarginEnd(Ui.px(this, GAP_PX));
        return params;
    }

    private Button familyTile(LinearLayout row, String title, String family) {
        String subtitle;
        boolean available = true;
        try {
            List<Control> controls = ControlPageActivity.loadControls(this, family);
            subtitle = controls.size() + " controls";
        } catch (Exception error) {
            subtitle = "Unavailable: " + Errors.describe(error);
            available = false;
        }
        Button tile = Ui.tile(this, title, subtitle);
        if (available) {
            tile.setOnClickListener(v -> ControlPageActivity.start(this, title, family));
        } else {
            tile.setEnabled(false);
        }
        destinations.add(tile);
        row.addView(tile, cellParams(row.getChildCount() == 0));
        return tile;
    }

    private Button destinationTile(LinearLayout row, String title, String subtitle,
            View.OnClickListener action) {
        Button tile = Ui.tile(this, title, subtitle);
        tile.setOnClickListener(action);
        destinations.add(tile);
        row.addView(tile, cellParams(row.getChildCount() == 0));
        return tile;
    }

    private void oemTool(LinearLayout row, String title, String pkg, String activity) {
        Button chip = Ui.chip(this, title);
        chip.setOnClickListener(v -> {
            try {
                startActivity(new Intent().setComponent(new ComponentName(pkg, activity)));
            } catch (ActivityNotFoundException | SecurityException error) {
                Toast.makeText(this, title + " unavailable: " + Errors.describe(error),
                        Toast.LENGTH_LONG).show();
            }
        });
        destinations.add(chip);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (row.getChildCount() == 0) params.setMarginEnd(Ui.px(this, GAP_PX));
        row.addView(chip, params);
    }

    private void focusInitial() {
        if (pictureTile != null && pictureTile.isEnabled() && pictureTile.requestFocus()) return;
        for (Button destination : destinations) {
            if (destination.isEnabled() && destination.requestFocus()) return;
        }
    }
}
