package com.visorcraft.hy450control;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public final class PlaygroundActivity extends Activity {
    private PlaygroundView view;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        showMenu();
        Ui.hideSystemBars(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.hideSystemBars(this);
        if (view != null) view.setHostPaused(false);
    }

    @Override
    protected void onPause() {
        if (view != null) view.setHostPaused(true);
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        if (view == null) return super.onKeyDown(code, event);
        if (code == KeyEvent.KEYCODE_BACK) {
            showMenu();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER && event.getRepeatCount() == 0) {
            view.centre();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int direction = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
            if (view.isAmbient()) {
                if (event.getRepeatCount() == 0) view.nextScene(direction);
            } else {
                view.direction(direction, true);
            }
            return true;
        }
        return super.onKeyDown(code, event);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent event) {
        if (view != null && !view.isAmbient()
                && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            view.direction(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1, false);
            return true;
        }
        return super.onKeyUp(code, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.hideSystemBars(this);
    }

    private void showMenu() {
        if (view != null) view.setHostPaused(true);
        view = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int margin = Ui.px(this, 64f);
        root.setPadding(margin, margin, margin, margin);
        root.addView(Ui.kicker(this, "VISORCRAFT · PLAYGROUND"));
        root.addView(Ui.note(this, "D-pad moves • centre selects • BACK exits", 32f,
                R.color.text_tertiary));
        root.addView(Ui.section(this, "ARCADE + AMBIENT", 20f));
        Button first = destination(root, "Breakout", "Three lives • persistent best score",
                this::showBreakout);
        String[] scenes = {"Starfield", "Aurora", "Fireplace", "Ocean waves"};
        String[] subtitles = {
            "Layered deep-space drift", "Flowing ribbons of colour",
            "Warm flames and rising embers", "Slow moonlit waves"
        };
        for (int i = 0; i < scenes.length; i++) {
            final int index = i;
            destination(root, scenes[i], subtitles[i], () -> showAmbient(index));
        }
        scroll.addView(root);
        setContentView(scroll);
        root.post(first::requestFocus);
    }

    private Button destination(LinearLayout root, String title, String subtitle, Runnable action) {
        Button button = Ui.tile(this, title, subtitle);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.px(this, 20f);
        root.addView(button, params);
        return button;
    }

    private void showBreakout() {
        view = PlaygroundView.breakout(this, new BreakoutGame(), bestScore(), this::saveBest);
        setContentView(view);
    }

    private void showAmbient(int index) {
        view = PlaygroundView.ambient(this, index);
        setContentView(view);
    }

    private int bestScore() {
        try {
            return getSharedPreferences("playground", MODE_PRIVATE)
                    .getInt("breakout_best", 0);
        } catch (ClassCastException error) {
            return 0;
        }
    }

    private void saveBest(int best) {
        getSharedPreferences("playground", MODE_PRIVATE)
                .edit().putInt("breakout_best", best).apply();
    }
}
