package com.besome.sketch.editor.view;

/** Limits and defaults for the resizable widget sidebar of the View editor. */
public final class SidebarWidth {
    public static final float MIN_DP = 56f;
    public static final float MAX_DP = 140f;
    public static final float DEFAULT_DP = 72f;
    /** Below this the "+ Widget" button keeps only its plus sign, so nothing is cut off. */
    private static final float LABEL_MIN_DP = 84f;

    private SidebarWidth() {
    }

    public static float clampDp(float dp) {
        return Math.max(MIN_DP, Math.min(MAX_DP, dp));
    }

    public static boolean showsLabel(float dp) {
        return dp >= LABEL_MIN_DP;
    }
}
