package com.besome.sketch.editor.view;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import java.util.List;

import pro.sketchware.R;

/**
 * A phone bezel the View editor can draw around its preview. Each frame is a picture whose screen is
 * see-through; the numbers describe it (in the picture's own pixels) so it can be fitted to any preview
 * size without distorting the parts that must keep their shape.
 *
 * @param key          stable name saved in the preferences
 * @param labelRes     name shown in the picker
 * @param labelNumber  number put in the name when the label has a placeholder, else 0
 * @param width        picture size
 * @param height       picture size
 * @param insetLeft    bezel thickness from the outer edge to the screen opening, left
 * @param insetRight   same, right
 * @param insetTop     same, top
 * @param insetBottom  same, bottom
 * @param corner       width of the corner pieces; they hold the whole rounded corner
 * @param topFixed     height of the top row, which is never stretched vertically
 * @param bottomFixed  height of the bottom row, which is never stretched vertically
 * @param featureStart where the camera / notch / speaker starts on the top edge, or -1 if there is none
 * @param featureEnd   where it ends; this span is only scaled evenly, never stretched
 */
public record PhoneFrame(String key, @StringRes int labelRes, int labelNumber, @DrawableRes int drawableRes,
                         int width, int height,
                         int insetLeft, int insetRight, int insetTop, int insetBottom,
                         int corner, int topFixed, int bottomFixed,
                         int featureStart, int featureEnd) {
    /** Also the frame used until the user picks another one. */
    public static final String DEFAULT_KEY = "waterdrop";
    /** Saved choice meaning "show no frame". */
    public static final String NONE_KEY = "none";

    public static final List<PhoneFrame> ALL = List.of(
            new PhoneFrame("waterdrop", R.string.phone_frame_waterdrop, 0, R.drawable.phone_frame_waterdrop,
                    480, 1025, 27, 27, 27, 42, 80, 80, 80, 200, 280),
            new PhoneFrame("modern1", R.string.phone_frame_modern, 1, R.drawable.phone_frame_modern1,
                    480, 1010, 34, 34, 21, 24, 88, 88, 88, 220, 258),
            new PhoneFrame("modern2", R.string.phone_frame_modern, 2, R.drawable.phone_frame_modern2,
                    480, 1011, 27, 32, 24, 19, 77, 77, 77, 217, 257),
            new PhoneFrame("modern3", R.string.phone_frame_modern, 3, R.drawable.phone_frame_modern3,
                    480, 1004, 31, 31, 24, 26, 84, 84, 84, 221, 258),
            new PhoneFrame("island", R.string.phone_frame_island, 0, R.drawable.phone_frame_island,
                    480, 1007, 26, 26, 20, 21, 90, 90, 90, 173, 307),
            new PhoneFrame("modern4", R.string.phone_frame_modern, 4, R.drawable.phone_frame_modern4,
                    480, 1022, 26, 29, 23, 29, 79, 79, 79, 217, 259),
            new PhoneFrame("modern5", R.string.phone_frame_modern, 5, R.drawable.phone_frame_modern5,
                    480, 1017, 28, 34, 23, 25, 73, 73, 73, 218, 255),
            new PhoneFrame("modern6", R.string.phone_frame_modern, 6, R.drawable.phone_frame_modern6,
                    480, 1025, 20, 39, 25, 29, 80, 80, 80, 221, 259),
            new PhoneFrame("classic", R.string.phone_frame_classic, 0, R.drawable.phone_frame_classic,
                    480, 951, 26, 27, 77, 106, 70, 91, 120, 92, 328));

    public boolean hasFeature() {
        return featureStart >= 0 && featureEnd > featureStart;
    }

    /** The bezel side that sets the scale: the one the thickness in dp is given for. */
    int referenceInset() {
        return Math.max(Math.max(insetLeft, insetRight), 1);
    }

    /** The frame saved under {@code key}; the default for an unknown key, {@code null} for "no frame". */
    public static PhoneFrame byKey(String key) {
        if (NONE_KEY.equals(key)) {
            return null;
        }
        for (PhoneFrame frame : ALL) {
            if (frame.key.equals(key)) {
                return frame;
            }
        }
        return ALL.get(0);
    }
}
