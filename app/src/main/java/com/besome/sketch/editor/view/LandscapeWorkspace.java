package com.besome.sketch.editor.view;

/**
 * Pure rules of the landscape View editor: when the three-panel workspace is used, how wide the side panels
 * may be, and where the preview sits for each zoom option. Everything is in pixels or dp as named, so it can
 * be tested without a device.
 */
public final class LandscapeWorkspace {
    /** Below this width a landscape screen is too small for three panels and keeps the portrait layout. */
    public static final int MIN_WIDTH_DP = 540;
    public static final float PROPERTIES_MIN_DP = 240f;
    public static final float PROPERTIES_MAX_DP = 460f;
    public static final float PROPERTIES_DEFAULT_DP = 300f;
    /** The preview never gets less than this much of the width, however wide the side panels are. */
    public static final float PREVIEW_MIN_DP = 200f;
    /** Where pinch zoom stops, as a fraction of the real device size. */
    public static final float ZOOM_MIN = 0.25f;
    public static final float ZOOM_MAX = 2.0f;

    /** The zoom choices of the preview bar; {@link #FIT} follows the free space, the others are fixed. */
    public enum Zoom {
        FIT(0f), PERCENT_50(0.5f), PERCENT_75(0.75f), PERCENT_100(1f);

        public final float scale;

        Zoom(float scale) {
            this.scale = scale;
        }

        public static Zoom fromKey(String key) {
            for (Zoom zoom : values()) {
                if (zoom.name().equals(key)) return zoom;
            }
            return FIT;
        }
    }

    private LandscapeWorkspace() {
    }

    /** The workspace is for landscape screens that are wide enough; portrait and small screens are unchanged. */
    public static boolean isEnabled(boolean landscape, int screenWidthDp) {
        return landscape && screenWidthDp >= MIN_WIDTH_DP;
    }

    /**
     * The properties panel's width: kept between its limits and never so wide that the widget palette and the
     * preview no longer fit. A very narrow screen therefore wins over the saved width.
     */
    public static float clampPropertiesDp(float wantedDp, float screenWidthDp, float paletteDp) {
        float room = screenWidthDp - paletteDp - PREVIEW_MIN_DP;
        float max = Math.max(PROPERTIES_MIN_DP, Math.min(PROPERTIES_MAX_DP, room));
        return Math.max(PROPERTIES_MIN_DP, Math.min(max, wantedDp));
    }

    /** The scale that makes the whole content fit the area, with the given margin on each side. */
    public static float fitScale(float areaW, float areaH, float contentW, float contentH, float marginX, float marginY) {
        if (contentW <= 0 || contentH <= 0) return 1f;
        float w = Math.max(1f, areaW - 2 * marginX);
        float h = Math.max(1f, areaH - 2 * marginY);
        return Math.min(w / contentW, h / contentH);
    }

    /** The scale for a zoom choice: fixed percentages are of the real device size, {@link Zoom#FIT} is the fit. */
    public static float scaleFor(Zoom zoom, float fit) {
        return zoom == Zoom.FIT ? fit : zoom.scale;
    }

    public static float clampZoom(float scale) {
        return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, scale));
    }

    /**
     * The offset of the content's top-left corner in the area. Content smaller than the area is centred; larger
     * content can be moved by {@code pan} but never so far that its edge leaves the area by more than
     * {@code margin}, so it can always be brought back.
     */
    public static float origin(float area, float scaledContent, float margin, float pan) {
        if (scaledContent + 2 * margin <= area) return (area - scaledContent) / 2f;
        float max = margin;
        float min = area - scaledContent - margin;
        float centred = (area - scaledContent) / 2f;
        return Math.max(min, Math.min(max, centred + pan));
    }

    /** Limits {@code pan} to what {@link #origin} can use, so a pan never builds up beyond the visible range. */
    public static float clampPan(float area, float scaledContent, float margin, float pan) {
        if (scaledContent + 2 * margin <= area) return 0f;
        float centred = (area - scaledContent) / 2f;
        float used = origin(area, scaledContent, margin, pan);
        return used - centred;
    }
}
