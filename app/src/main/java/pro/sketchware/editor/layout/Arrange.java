package pro.sketchware.editor.layout;

import java.util.ArrayList;
import java.util.List;

/** Align and distribute for a set of boxes: how far each one has to move. The selection's own bounds are the reference. */
public final class Arrange {
    private Arrange() {
    }

    public enum Align {
        LEFT, CENTER_HORIZONTAL, RIGHT, TOP, CENTER_VERTICAL, BOTTOM
    }

    public record Offset(float dx, float dy) {
        public boolean isZero() {
            return Math.abs(dx) < 0.001f && Math.abs(dy) < 0.001f;
        }
    }

    public static Box bounds(List<Box> boxes) {
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (Box box : boxes) {
            l = Math.min(l, box.left());
            t = Math.min(t, box.top());
            r = Math.max(r, box.right());
            b = Math.max(b, box.bottom());
        }
        return new Box(l, t, r, b);
    }

    /** Offsets that line every box up with the selection's left, centre, right, top, middle or bottom. */
    public static List<Offset> align(List<Box> boxes, Align mode) {
        List<Offset> result = new ArrayList<>();
        if (boxes.isEmpty()) {
            return result;
        }
        Box all = bounds(boxes);
        for (Box box : boxes) {
            result.add(switch (mode) {
                case LEFT -> new Offset(all.left() - box.left(), 0);
                case CENTER_HORIZONTAL -> new Offset(all.centerX() - box.centerX(), 0);
                case RIGHT -> new Offset(all.right() - box.right(), 0);
                case TOP -> new Offset(0, all.top() - box.top());
                case CENTER_VERTICAL -> new Offset(0, all.centerY() - box.centerY());
                case BOTTOM -> new Offset(0, all.bottom() - box.bottom());
            });
        }
        return result;
    }

    /**
     * Offsets that give equal gaps between the boxes along {@code axis} (X = left to right), keeping the first
     * and the last where they are. Needs at least three boxes; fewer are returned unmoved.
     */
    public static List<Offset> distribute(List<Box> boxes, Axis axis) {
        int n = boxes.size();
        Offset[] result = new Offset[n];
        for (int i = 0; i < n; i++) {
            result[i] = new Offset(0, 0);
        }
        if (n >= 3) {
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) {
                order[i] = i;
            }
            java.util.Arrays.sort(order, (a, b) -> Float.compare(start(boxes.get(a), axis), start(boxes.get(b), axis)));
            float first = start(boxes.get(order[0]), axis);
            float last = end(boxes.get(order[n - 1]), axis);
            float total = 0;
            for (Integer index : order) {
                total += end(boxes.get(index), axis) - start(boxes.get(index), axis);
            }
            float gap = (last - first - total) / (n - 1);
            float cursor = first;
            for (Integer index : order) {
                Box box = boxes.get(index);
                float delta = cursor - start(box, axis);
                result[index] = axis == Axis.X ? new Offset(delta, 0) : new Offset(0, delta);
                cursor += end(box, axis) - start(box, axis) + gap;
            }
        }
        return List.of(result);
    }

    private static float start(Box box, Axis axis) {
        return axis == Axis.X ? box.left() : box.top();
    }

    private static float end(Box box, Axis axis) {
        return axis == Axis.X ? box.right() : box.bottom();
    }
}
