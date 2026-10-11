package pro.sketchware.colorwheel;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.sketchware.R;
import pro.sketchware.databinding.DialogColorWheelBinding;

/**
 * Picks a color on a wheel: the ball sets hue and saturation, sliders set brightness and (when allowed) opacity,
 * and the hex field follows along or can be typed in.
 */
public final class ColorWheelDialog {
    public interface OnColorPicked {
        void onColorPicked(int color);
    }

    private final DialogColorWheelBinding binding;
    private final boolean withOpacity;
    private final float[] hsv = new float[3];
    private int alpha = 0xFF;
    private boolean updatingHex;

    private ColorWheelDialog(Activity activity, int initialColor, boolean withOpacity) {
        this.withOpacity = withOpacity;
        binding = DialogColorWheelBinding.inflate(activity.getLayoutInflater());
        Color.colorToHSV(initialColor, hsv);
        alpha = withOpacity ? Color.alpha(initialColor) : 0xFF;

        binding.wheel.setColor(hsv[0], hsv[1], hsv[2]);
        binding.wheel.setOnColorChangedListener((hue, saturation, fromUser) -> {
            hsv[0] = hue;
            hsv[1] = saturation;
            // Picking a hue on a black color would show nothing: bring the brightness up.
            if (hsv[2] < 0.15f) {
                hsv[2] = 1f;
                binding.brightness.setValue(100);
                binding.wheel.setValue(1f);
            }
            refresh(true);
        });
        binding.brightness.setValue(Math.round(hsv[2] * 100));
        binding.brightness.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            hsv[2] = value / 100f;
            binding.wheel.setValue(hsv[2]);
            refresh(true);
        });
        binding.opacity.setVisibility(withOpacity ? View.VISIBLE : View.GONE);
        binding.opacityLabel.setVisibility(withOpacity ? View.VISIBLE : View.GONE);
        binding.opacity.setValue(Math.round(alpha * 100 / 255f));
        binding.opacity.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            alpha = Math.round(value * 255 / 100f);
            refresh(true);
        });
        binding.hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingHex) return;
                Integer typed = ColorWheelMath.parseHex(s.toString());
                binding.hexLayout.setError(typed == null && s.length() > 0 ? " " : null);
                if (typed == null) return;
                Color.colorToHSV(typed, hsv);
                if (withOpacity) alpha = Color.alpha(typed);
                binding.wheel.setColor(hsv[0], hsv[1], hsv[2]);
                binding.brightness.setValue(Math.round(hsv[2] * 100));
                binding.opacity.setValue(Math.round(alpha * 100 / 255f));
                refresh(false);
            }
        });
        refresh(true);
    }

    /** The chosen color, with its opacity. */
    int color() {
        return (alpha << 24) | (Color.HSVToColor(hsv) & 0xFFFFFF);
    }

    private void refresh(boolean updateHexField) {
        int color = color();
        binding.preview.setCardBackgroundColor(ColorStateList.valueOf(color));
        if (updateHexField) {
            updatingHex = true;
            binding.hex.setText(ColorWheelMath.toHex(color));
            updatingHex = false;
            binding.hexLayout.setError(null);
        }
    }

    /**
     * @param withOpacity whether the color may be see-through (else it is always opaque)
     * @param onPalettes  shown as a "Palettes" button that opens the named colors, colors.xml and Material 3
     *                    colors instead; null hides it
     */
    public static void show(Activity activity, int initialColor, boolean withOpacity, OnColorPicked onPicked,
                            @Nullable Runnable onPalettes) {
        ColorWheelDialog dialog = new ColorWheelDialog(activity, initialColor, withOpacity);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.color_wheel_title)
                .setView(dialog.binding.getRoot())
                .setPositiveButton(R.string.common_word_select, (d, which) -> onPicked.onColorPicked(dialog.color()))
                .setNegativeButton(R.string.common_word_cancel, null);
        if (onPalettes != null) {
            builder.setNeutralButton(R.string.color_wheel_palettes, (d, which) -> onPalettes.run());
        }
        builder.show();
    }
}
