package pro.sketchware.editor.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Alignment guides, snapping and distances for the widgets of a screen. Works on measured rectangles, so it
 * applies to every layout kind. Pure geometry: the editor measures, this decides what to show or snap to.
 */
public final class GuideEngine {
    private GuideEngine() {
    }

    /** Which feature of the target and of the other box lined up. */
    public enum Feature {
        START, CENTER, END
    }

    /**
     * A line the target is aligned with.
     *
     * @param axis     X for a vertical line, Y for a horizontal one
     * @param position where the line is, on the axis
     * @param from     where the line starts, along the other axis (covers target and the matching box)
     * @param to       where it ends
     * @param feature  edge or centre that lined up
     * @param parent   true when the other box is the parent
     */
    public record Guide(Axis axis, float position, float from, float to, Feature feature, boolean parent) {
    }

    /** The space between the target and its nearest neighbour (or the parent edge) on one side. */
    public record Gap(Side side, float size, boolean toParent) {
    }

    public enum Side {
        LEFT, TOP, RIGHT, BOTTOM
    }

    /**
     * Guides for boxes that are already aligned, within {@code tolerance}. Every sibling and the parent are
     * compared on start, centre and end; the same line is reported once.
     */
    public static List<Guide> alignments(Box target, List<Box> others, Box parent, float tolerance) {
        List<Guide> guides = new ArrayList<>();
        List<Box> all = new ArrayList<>(others);
        for (int i = 0; i <= all.size(); i++) {
            boolean isParent = i == all.size();
            Box other = isParent ? parent : all.get(i);
            if (other == null) {
                continue;
            }
            addAxis(guides, target, other, isParent, tolerance, true);
            addAxis(guides, target, other, isParent, tolerance, false);
        }
        return dedupe(guides);
    }

    private static void addAxis(List<Guide> out, Box t, Box o, boolean parent, float tol, boolean vertical) {
        float[] tv = vertical ? new float[]{t.left(), t.centerX(), t.right()} : new float[]{t.top(), t.centerY(), t.bottom()};
        float[] ov = vertical ? new float[]{o.left(), o.centerX(), o.right()} : new float[]{o.top(), o.centerY(), o.bottom()};
        Feature[] features = Feature.values();
        float from = vertical ? Math.min(t.top(), o.top()) : Math.min(t.left(), o.left());
        float to = vertical ? Math.max(t.bottom(), o.bottom()) : Math.max(t.right(), o.right());
        for (int i = 0; i < 3; i++) {
            // A box lines up with its twin feature; an edge of one box against the centre of another is no alignment.
            if (Math.abs(tv[i] - ov[i]) <= tol) {
                out.add(new Guide(vertical ? Axis.X : Axis.Y, ov[i], from, to, features[i], parent));
            }
        }
    }

    private static List<Guide> dedupe(List<Guide> guides) {
        List<Guide> result = new ArrayList<>();
        for (Guide guide : guides) {
            boolean merged = false;
            for (int i = 0; i < result.size(); i++) {
                Guide kept = result.get(i);
                if (kept.axis() == guide.axis() && Math.abs(kept.position() - guide.position()) < 0.01f) {
                    result.set(i, new Guide(kept.axis(), kept.position(), Math.min(kept.from(), guide.from()),
                            Math.max(kept.to(), guide.to()), kept.feature(), kept.parent() && guide.parent()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(guide);
            }
        }
        return result;
    }

    /**
     * Gaps to the nearest box on each side that faces the target (overlaps it on the other axis), or to the
     * parent edge when nothing is in the way. Sides where the target sticks out of the parent are left out.
     */
    public static List<Gap> gaps(Box target, List<Box> others, Box parent) {
        float[] size = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        boolean[] toParent = new boolean[4];
        if (parent != null) {
            if (target.left() >= parent.left()) size[0] = target.left() - parent.left();
            if (target.top() >= parent.top()) size[1] = target.top() - parent.top();
            if (target.right() <= parent.right()) size[2] = parent.right() - target.right();
            if (target.bottom() <= parent.bottom()) size[3] = parent.bottom() - target.bottom();
            for (int i = 0; i < 4; i++) {
                toParent[i] = !Float.isNaN(size[i]);
            }
        }
        for (Box other : others) {
            if (target.overlapsY(other)) {
                if (other.right() <= target.left()) consider(size, toParent, 0, target.left() - other.right());
                if (other.left() >= target.right()) consider(size, toParent, 2, other.left() - target.right());
            }
            if (target.overlapsX(other)) {
                if (other.bottom() <= target.top()) consider(size, toParent, 1, target.top() - other.bottom());
                if (other.top() >= target.bottom()) consider(size, toParent, 3, other.top() - target.bottom());
            }
        }
        List<Gap> gaps = new ArrayList<>();
        Side[] sides = Side.values();
        for (int i = 0; i < 4; i++) {
            if (!Float.isNaN(size[i])) {
                gaps.add(new Gap(sides[i], size[i], toParent[i]));
            }
        }
        return gaps;
    }

    /** A neighbour replaces what is there when it is closer, or as close as the parent edge. */
    private static void consider(float[] size, boolean[] toParent, int side, float gap) {
        if (Float.isNaN(size[side]) || gap < size[side] || (toParent[side] && gap <= size[side])) {
            size[side] = gap;
            toParent[side] = false;
        }
    }

    /** How far to move {@code moving} to snap it, and the guides that explain the snap. */
    public record Snap(float dx, float dy, List<Guide> guides) {
    }

    /**
     * Snaps a box being moved to the edges and centres of {@code others} and {@code parent}, and to a grid when
     * {@code grid} is above zero. Each axis snaps on its own to the closest candidate within {@code threshold};
     * a candidate from another box beats the grid when they are equally close.
     */
    public static Snap snapMove(Box moving, List<Box> others, Box parent, float grid, float threshold) {
        float dx = snapAxis(moving, others, parent, grid, threshold, true);
        float dy = snapAxis(moving, others, parent, grid, threshold, false);
        Box snapped = moving.offset(dx, dy);
        return new Snap(dx, dy, alignments(snapped, others, parent, 0.01f));
    }

    private static float snapAxis(Box m, List<Box> others, Box parent, float grid, float threshold, boolean horizontal) {
        float[] mine = horizontal ? new float[]{m.left(), m.centerX(), m.right()} : new float[]{m.top(), m.centerY(), m.bottom()};
        float best = 0f;
        float bestDistance = Float.MAX_VALUE;
        List<Box> targets = new ArrayList<>(others);
        if (parent != null) {
            targets.add(parent);
        }
        for (Box other : targets) {
            float[] theirs = horizontal ? new float[]{other.left(), other.centerX(), other.right()} : new float[]{other.top(), other.centerY(), other.bottom()};
            for (int i = 0; i < 3; i++) {
                float delta = theirs[i] - mine[i];
                if (Math.abs(delta) <= threshold && Math.abs(delta) < bestDistance) {
                    best = delta;
                    bestDistance = Math.abs(delta);
                }
            }
        }
        if (grid > 0f) {
            float origin = horizontal ? m.left() : m.top();
            float onGrid = Math.round(origin / grid) * grid;
            float delta = onGrid - origin;
            if (Math.abs(delta) <= threshold && Math.abs(delta) < bestDistance) {
                best = delta;
            }
        }
        return best;
    }

    /** Boxes sorted left to right, then top to bottom: the order a person reads them in. */
    public static List<Box> inReadingOrder(List<Box> boxes) {
        List<Box> sorted = new ArrayList<>(boxes);
        Collections.sort(sorted, Comparator.comparingDouble(Box::top).thenComparingDouble(Box::left));
        return sorted;
    }
}
