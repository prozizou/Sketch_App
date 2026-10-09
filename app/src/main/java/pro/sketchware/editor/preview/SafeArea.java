package pro.sketchware.editor.preview;

import pro.sketchware.editor.layout.Box;

/**
 * The parts of a screen that system UI or hardware can cover, in dp, for a preset and orientation. They are
 * typical values (a status bar of 24 dp, a gesture bar of 16 dp, a camera cut-out), not those of a given phone.
 *
 * @param statusTop   status bar height
 * @param cutoutTop   extra height covered by a cut-out at the top, counted from the screen's top edge
 *                    (so the unsafe top is the larger of the two)
 * @param cutoutSide  width a cut-out takes at the left edge in landscape
 * @param navBottom   gesture or navigation bar height at the bottom
 * @param hinge       the hinge strip, or {@code null}
 */
public record SafeArea(int width, int height, int statusTop, int cutoutTop, int cutoutSide, int navBottom, Box hinge) {
    public static final int STATUS_BAR_DP = 24;
    public static final int GESTURE_BAR_DP = 16;

    public static SafeArea of(DevicePreset preset, Orientation orientation, int realWidthDp, int realHeightDp) {
        int w = preset.isThisDevice() ? realWidthDp : preset.width(orientation);
        int h = preset.isThisDevice() ? realHeightDp : preset.height(orientation);
        boolean portrait = h >= w;
        int cutout = preset.cutoutDp();
        Box hinge = null;
        if (preset.hingeWidthDp() > 0) {
            // In portrait the hinge splits the width; turned sideways it splits the height.
            float half = preset.hingeWidthDp() / 2f;
            hinge = orientation == Orientation.PORTRAIT
                    ? new Box(w / 2f - half, 0, w / 2f + half, h)
                    : new Box(0, h / 2f - half, w, h / 2f + half);
        }
        return new SafeArea(w, h, STATUS_BAR_DP, portrait ? cutout : 0, portrait ? 0 : cutout, GESTURE_BAR_DP, hinge);
    }

    /** The top inset: status bar or cut-out, whichever reaches further. */
    public int topInset() {
        return Math.max(statusTop, cutoutTop);
    }

    /** The rectangle content can use without being covered (the hinge is excluded separately). */
    public Box safeBox() {
        return new Box(cutoutSide, topInset(), width, height - navBottom);
    }

    /** The unsafe strips: top, bottom, left (cut-out) and the hinge, for drawing. Empty ones are skipped. */
    public Box[] unsafeBoxes() {
        java.util.List<Box> boxes = new java.util.ArrayList<>();
        if (topInset() > 0) boxes.add(new Box(0, 0, width, topInset()));
        if (navBottom > 0) boxes.add(new Box(0, height - navBottom, width, height));
        if (cutoutSide > 0) boxes.add(new Box(0, topInset(), cutoutSide, height - navBottom));
        if (hinge != null) boxes.add(hinge);
        return boxes.toArray(new Box[0]);
    }

    /** True when {@code box} (screen dp) overlaps the hinge. */
    public boolean crossesHinge(Box box) {
        return hinge != null && hinge.intersects(box);
    }
}
