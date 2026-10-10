package pro.sketchware.utility;

import android.widget.ImageView;

import com.bumptech.glide.Glide;

import coil.util.CoilUtils;

/**
 * Images are loaded with Glide and vector images (SVG, converted XML) with Coil. When a list reuses an
 * ImageView, neither library cancels the other's pending load, so a late result could replace the image now
 * shown there: a vector icon appeared on a photo. Call {@link #reset} before loading into a reused view.
 */
public final class ImageLoads {

    private ImageLoads() {
    }

    /** Cancels what Glide and Coil are still loading into {@code view} and empties it. */
    public static void reset(ImageView view) {
        CoilUtils.dispose(view);
        try {
            Glide.with(view).clear(view);
        } catch (IllegalArgumentException ignored) {
            // The view's activity is already destroyed: nothing will be delivered to it anyway.
        }
        view.setImageDrawable(null);
    }
}
