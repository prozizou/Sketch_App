package pro.sketchware.editor.layout;

/** The 4 dp spacing rhythm of Material Design: values to snap margins and paddings to, and to check them against. */
public final class SpacingScale {
    /** The steps Material 3 uses most. */
    public static final int[] STEPS = {0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 56, 64};

    private SpacingScale() {
    }

    public static boolean isOnScale(int dp) {
        for (int step : STEPS) {
            if (step == dp) {
                return true;
            }
        }
        return false;
    }

    /** The nearest step; the smaller one when two are equally near. Values above the scale are rounded to a multiple of 8. */
    public static int nearest(int dp) {
        if (dp < 0) {
            return 0;
        }
        int last = STEPS[STEPS.length - 1];
        if (dp > last) {
            return Math.round(dp / 8f) * 8;
        }
        int best = STEPS[0];
        for (int step : STEPS) {
            if (Math.abs(step - dp) < Math.abs(best - dp)) {
                best = step;
            }
        }
        return best;
    }

    /** Snaps to the scale only when within {@code threshold} dp of a step, otherwise leaves the value alone. */
    public static int snap(int dp, int threshold) {
        int near = nearest(dp);
        return Math.abs(near - dp) <= threshold ? near : dp;
    }
}
