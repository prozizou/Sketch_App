package pro.sketchware.blocks;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

import a.a.a.Rs;
import a.a.a.Ss;

/**
 * Shows the color argument of a block (for example setBackgroundColor) as a filled swatch with a small
 * arrow instead of its code ("0xFF…", "#…"). The argument itself is untouched: only what the block's text
 * field displays changes, and it follows every change of the value (picker, undo/redo, loading a project).
 */
public final class ColorArgSwatch {
    /** Menu name of the color argument slot. */
    private static final String COLOR_MENU = "color";
    /** Figure spaces: they hold the field's width open while the swatch is drawn behind them. */
    private static final char FILLER = ' ';

    /** Text size and colour a field had before it showed a swatch, to put back for a resource name. */
    private static final Map<TextView, float[]> ORIGINAL_TEXT = new WeakHashMap<>();

    private static final float CHIP_WIDTH_DP = 30f;

    private ColorArgSwatch() {
    }

    /** Makes every color slot of {@code block} display a swatch. */
    public static void attach(Rs block) {
        if (block == null || block.V == null) {
            return;
        }
        for (View arg : block.V) {
            if (arg instanceof Ss slot && COLOR_MENU.equals(slot.getMenuName())) {
                attach(slot.V);
            }
        }
    }

    /**
     * Colour a value stands for, or {@code null} when it isn't a plain colour (a resource name such as
     * {@code R.color.primary} is shown as it is). An empty value or {@code Color.TRANSPARENT} is fully
     * transparent.
     */
    @Nullable
    public static Integer parseColor(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty() || text.equals("Color.TRANSPARENT")) {
            return Color.TRANSPARENT;
        }
        try {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                String hex = text.substring(2);
                if (hex.length() == 6) {
                    return 0xFF000000 | (int) Long.parseLong(hex, 16);
                }
                if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
                return null;
            }
            if (text.startsWith("#")) {
                return Color.parseColor(text);
            }
        } catch (IllegalArgumentException ignored) {
            // not a colour code
        }
        return null;
    }

    private static void attach(TextView field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Integer color = parseColor(s.toString());
                if (color != null) {
                    show(field, color, s.toString());
                } else {
                    showText(field);
                }
            }
        });
        // Whatever the field already holds (a block built before this ran).
        Integer color = parseColor(field.getText().toString());
        if (color != null) {
            show(field, color, field.getText().toString());
        }
    }

    /**
     * The field's text stays the real value, because the block reads it back (Ss.getArgValue). It is only
     * made invisible and tiny, so the field is as narrow as the swatch drawn behind it.
     */
    private static void show(TextView field, int color, String code) {
        if (!ORIGINAL_TEXT.containsKey(field)) {
            ORIGINAL_TEXT.put(field, new float[]{field.getTextSize(), field.getCurrentTextColor()});
        }
        float density = field.getResources().getDisplayMetrics().density;
        // The block sizes the field from its text, so scale the invisible text to the width of a chip.
        float[] original = ORIGINAL_TEXT.get(field);
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, original[0]);
        float natural = field.getPaint().measureText(code);
        float size = natural > 0 ? original[0] * CHIP_WIDTH_DP * density / natural : 1f;
        field.setTextColor(Color.TRANSPARENT);
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(1f, size));
        field.setBackground(new SwatchDrawable(color, density));
        field.setContentDescription(code);
    }

    /** A value that isn't a plain colour (a resource name) is shown as text, as before. */
    private static void showText(TextView field) {
        float[] original = ORIGINAL_TEXT.remove(field);
        if (original != null) {
            field.setTextSize(TypedValue.COMPLEX_UNIT_PX, original[0]);
            field.setTextColor((int) original[1]);
        }
        field.setBackground(null);
        field.setContentDescription(null);
    }

    /** A rounded colour chip that fills the field (checkered when transparent). */
    static final class SwatchDrawable extends Drawable {
        private final int color;
        private final float density;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwatchDrawable(int color, float density) {
            this.color = color;
            this.density = density;
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(1f, density));
            stroke.setColor(0x99FFFFFF);
        }

        int getColor() {
            return color;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float inset = 2 * density;
            RectF chip = new RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
            float radius = 4 * density;

            if (Color.alpha(color) < 255) {
                drawChecker(canvas, chip, radius);
            }
            fill.setColor(color);
            canvas.drawRoundRect(chip, radius, radius, fill);
            canvas.drawRoundRect(chip, radius, radius, stroke);
        }

        private void drawChecker(Canvas canvas, RectF chip, float radius) {
            Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
            light.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(chip, radius, radius, light);
            Paint dark = new Paint();
            dark.setColor(0xFFBDBDBD);
            float cell = 4 * density;
            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(chip, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            for (int row = 0; chip.top + row * cell < chip.bottom; row++) {
                for (int col = 0; chip.left + col * cell < chip.right; col++) {
                    if ((row + col) % 2 == 0) {
                        canvas.drawRect(chip.left + col * cell, chip.top + row * cell,
                                chip.left + (col + 1) * cell, chip.top + (row + 1) * cell, dark);
                    }
                }
            }
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
