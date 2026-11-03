package com.visorcraft.hy450control;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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

    public static void hideSystemBars(Activity activity) {
        activity.getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = activity.getWindow().getInsetsController();
        if (controller == null) return;
        controller.hide(WindowInsets.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
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

    public static TextView section(Context context, String label, float gapPx) {
        TextView kicker = kicker(context, label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = px(context, gapPx);
        params.bottomMargin = px(context, 8f);
        kicker.setLayoutParams(params);
        return kicker;
    }

    public static TextView note(Context context, String value, float textPx, int colorRes) {
        TextView view = text(context, value, textPx, colorRes);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = px(context, 8f);
        view.setLayoutParams(params);
        return view;
    }

    public static LinearLayout chipRow(LinearLayout root, float gapPx) {
        Context context = root.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = px(context, gapPx);
        row.setLayoutParams(params);
        root.addView(row);
        return row;
    }

    public static Button addChip(LinearLayout row, String title, View.OnClickListener action,
            float gapPx) {
        Context context = row.getContext();
        Button button = chip(context, title);
        if (action != null) button.setOnClickListener(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(px(context, gapPx));
        row.addView(button, params);
        return button;
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
        return new CardDrawable(context, TILE_RADIUS_PX, true);
    }

    static Drawable rowBackground(Context context) {
        return new CardDrawable(context, ROW_RADIUS_PX, true);
    }

    static Drawable readonlyBackground(Context context) {
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.RECTANGLE);
        base.setColor(context.getColor(R.color.readonly));
        base.setCornerRadius(px(context, ROW_RADIUS_PX));
        return base;
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

    private static final class CardDrawable extends Drawable {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF baseRect = new RectF();
        private final RectF railRect = new RectF();
        private final ColorStateList baseColors;
        private final float radius;
        private final float railWidth;
        private final float railRadius;
        private final boolean railEnabled;
        private int alpha = 255;
        private int baseColor;
        private boolean showRail;

        CardDrawable(Context context, float radiusPx, boolean railEnabled) {
            baseColors = new ColorStateList(
                    new int[][] {
                        new int[] { -android.R.attr.state_enabled },
                        new int[] { android.R.attr.state_pressed },
                        new int[] { android.R.attr.state_focused },
                        new int[] { }
                    },
                    new int[] {
                        context.getColor(R.color.disabled),
                        context.getColor(R.color.card_pressed),
                        context.getColor(R.color.card_focused),
                        context.getColor(R.color.card)
                    });
            baseColor = context.getColor(R.color.card);
            basePaint.setColor(baseColor);
            railPaint.setColor(context.getColor(R.color.accent));
            radius = px(context, radiusPx);
            railWidth = px(context, RAIL_PX);
            railRadius = Math.min(radius, railWidth / 2f);
            this.railEnabled = railEnabled;
        }

        @Override
        public void draw(Canvas canvas) {
            baseRect.set(getBounds());
            canvas.drawRoundRect(baseRect, radius, radius, basePaint);
            if (!showRail) return;
            railRect.set(baseRect);
            railRect.right = Math.min(baseRect.left + railWidth, baseRect.right);
            canvas.drawRoundRect(railRect, railRadius, railRadius, railPaint);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] stateSet) {
            boolean enabled = false;
            boolean focused = false;
            boolean pressed = false;
            for (int state : stateSet) {
                if (state == -android.R.attr.state_enabled) {
                    enabled = false;
                } else if (state == android.R.attr.state_enabled) {
                    enabled = true;
                } else if (state == android.R.attr.state_focused) {
                    focused = true;
                } else if (state == android.R.attr.state_pressed) {
                    pressed = true;
                }
            }
            boolean rail = railEnabled && enabled && (focused || pressed);
            int color = baseColors.getColorForState(stateSet, baseColors.getDefaultColor());
            if (color == baseColor && rail == showRail) return false;
            baseColor = color;
            basePaint.setColor(color);
            basePaint.setAlpha(alpha);
            showRail = rail;
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            if (this.alpha == alpha) return;
            this.alpha = alpha;
            basePaint.setAlpha(alpha);
            railPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            basePaint.setColorFilter(colorFilter);
            railPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
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
