package com.visorcraft.hy450control;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * Signal Console design system. Single factory for palette tokens,
 * density-correct type sizing, focusable tiles and chips, and control rows.
 */
public final class Ui {
    private static final float TILE_RADIUS_PX = 16f;
    private static final float ROW_RADIUS_PX = 10f;
    private static final float RAIL_PX = 6f;

    private static final float TILE_TITLE_PX = 60f;
    private static final float KICKER_PX = 30f;
    private static final float ROW_LABEL_PX = 46f;
    private static final float ROW_VALUE_PX = 52f;
    private static final float ROW_VALUE_MAX_PX = 700f;
    private static final float CHIP_PX = 38f;

    private static final float TILE_SUBTITLE_SCALE = 0.55f;
    private static final float KICKER_SPACING = 0.12f;
    private static final float FOCUS_SCALE = 1.03f;
    private static final long FOCUS_SCALE_MS = 120L;

    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);

    private Ui() {
    }

    public static float scale(Context context) {
        DisplayMetrics m = context.getResources().getDisplayMetrics();
        float scale = Math.min(m.widthPixels / 1920f, m.heightPixels / 1080f);
        return Math.max(0.8f, Math.min(2.0f, scale));
    }

    public static int px(Context context, float designPx) {
        return Math.round(designPx * scale(context));
    }

    public static void size(TextView view, float designPx) {
        DisplayMetrics m = view.getResources().getDisplayMetrics();
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                designPx * scale(view.getContext()) / m.density);
    }

    public static TextView text(Context context, CharSequence value, float designPx, int colorRes) {
        return text(context, value, designPx, colorRes, null);
    }

    public static TextView text(Context context, CharSequence value, float designPx, int colorRes,
            Typeface typeface) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        size(view, designPx);
        view.setTextColor(context.getColor(colorRes));
        if (typeface != null) view.setTypeface(typeface);
        return view;
    }

    public static TextView kicker(Context context, CharSequence value) {
        TextView view = text(context, value == null ? "" : value.toString().toUpperCase(Locale.US),
                KICKER_PX, R.color.text_tertiary, MEDIUM);
        view.setLetterSpacing(KICKER_SPACING);
        return view;
    }

    public static Button tile(Context context, CharSequence title) {
        return tile(context, title, null);
    }

    public static Button tile(Context context, CharSequence title, CharSequence subtitle) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setTypeface(BLACK);
        button.setTextColor(enabledText(context));
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPaddingRelative(px(context, 32), px(context, 24), px(context, 32), px(context, 24));
        button.setBackground(focusBackground(context));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setText(subtitle == null ? title : tileText(context, title, subtitle));
        size(button, TILE_TITLE_PX);
        flatten(button);
        button.setOnFocusChangeListener((v, hasFocus) -> v.animate()
                .scaleX(hasFocus ? FOCUS_SCALE : 1.0f)
                .scaleY(hasFocus ? FOCUS_SCALE : 1.0f)
                .setDuration(FOCUS_SCALE_MS)
                .start());
        return button;
    }

    public static Button chip(Context context, CharSequence title) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setTypeface(MEDIUM);
        button.setTextColor(enabledText(context));
        button.setGravity(Gravity.CENTER);
        button.setPaddingRelative(px(context, 24), px(context, 16), px(context, 24), px(context, 16));
        button.setBackground(focusBackground(context));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setText(title);
        size(button, CHIP_PX);
        flatten(button);
        return button;
    }

    public static Drawable focusBackground(Context context) {
        return cardStates(context, TILE_RADIUS_PX);
    }

    static Drawable rowBackground(Context context) {
        return cardStates(context, ROW_RADIUS_PX);
    }

    static Drawable readonlyBackground(Context context) {
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.RECTANGLE);
        base.setColor(context.getColor(R.color.readonly));
        base.setCornerRadius(px(context, ROW_RADIUS_PX));
        return base;
    }

    private static Drawable cardStates(Context context, float radiusPx) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { -android.R.attr.state_enabled },
                card(context, context.getColor(R.color.disabled), radiusPx, false));
        states.addState(new int[] { android.R.attr.state_pressed },
                card(context, context.getColor(R.color.card_pressed), radiusPx, true));
        states.addState(new int[] { android.R.attr.state_focused },
                card(context, context.getColor(R.color.card_focused), radiusPx, true));
        states.addState(new int[] { },
                card(context, context.getColor(R.color.card), radiusPx, false));
        return states;
    }

    private static ColorStateList enabledText(Context context) {
        return new ColorStateList(
                new int[][] {
                    new int[] { -android.R.attr.state_enabled },
                    new int[] { }
                },
                new int[] {
                    context.getColor(R.color.text_disabled),
                    context.getColor(R.color.text_primary)
                });
    }

    private static Drawable card(Context context, int color, float radiusPx, boolean rail) {
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.RECTANGLE);
        base.setColor(color);
        base.setCornerRadius(px(context, radiusPx));
        if (!rail) return base;
        float radius = px(context, radiusPx);
        GradientDrawable accent = new GradientDrawable();
        accent.setShape(GradientDrawable.RECTANGLE);
        accent.setColor(context.getColor(R.color.accent));
        accent.setCornerRadii(new float[] { radius, radius, 0f, 0f, 0f, 0f, radius, radius });
        accent.setSize(px(context, RAIL_PX), -1);
        LayerDrawable layered = new LayerDrawable(new Drawable[] { base, accent });
        layered.setLayerGravity(1, Gravity.START | Gravity.FILL_VERTICAL);
        return layered;
    }

    private static void flatten(View view) {
        view.setElevation(0f);
        view.setStateListAnimator(null);
    }

    private static CharSequence tileText(Context context, CharSequence title, CharSequence subtitle) {
        SpannableStringBuilder text = new SpannableStringBuilder(title == null ? "" : title);
        int start = text.length();
        text.append("\n").append(subtitle);
        text.setSpan(new RelativeSizeSpan(TILE_SUBTITLE_SCALE), start, text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(context.getColor(R.color.text_secondary)), start,
                text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    /**
     * Control page row: weighted label on the leading side, monospace value on
     * the trailing side. The container never takes focus; the Activity focuses
     * the label, and the row aggregates its children's states so the card
     * background follows the label's focus without owning the label's focus
     * listener slot.
     */
    public static final class Row extends LinearLayout {
        public final TextView label;
        public final TextView value;

        public Row(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPaddingRelative(px(context, 24), px(context, 16), px(context, 24), px(context, 16));
            setBackground(rowBackground(context));
            setFocusable(false);
            setFocusableInTouchMode(false);
            setAddStatesFromChildren(true);

            label = new TextView(context);
            size(label, ROW_LABEL_PX);
            label.setTypeface(MEDIUM);
            label.setTextColor(context.getColor(R.color.text_primary));
            label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setFocusable(true);
            label.setFocusableInTouchMode(true);
            addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            value = new TextView(context);
            size(value, ROW_VALUE_PX);
            value.setTypeface(Typeface.MONOSPACE);
            value.setTextColor(context.getColor(R.color.text_primary));
            value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            value.setMaxWidth(px(context, ROW_VALUE_MAX_PX));
            addView(value, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        public void setValue(String formatted) {
            value.setText(formatted);
        }

        public void setReadOnly(boolean readOnly) {
            if (readOnly) {
                label.clearFocus();
            }
            setBackground(readOnly ? readonlyBackground(getContext()) : rowBackground(getContext()));
            label.setFocusable(!readOnly);
            label.setFocusableInTouchMode(!readOnly);
            int color = getContext().getColor(readOnly ? R.color.text_tertiary : R.color.text_primary);
            label.setTextColor(color);
            value.setTextColor(color);
        }
    }
}
