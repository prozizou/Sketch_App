package com.besome.sketch.editor;

import java.util.List;

/**
 * Where a new copy of blocks goes on the Logic canvas: a free spot inside the visible part of the canvas, so the
 * copy never lands on top of existing code. All values are in block pane pixels (unscaled).
 */
public final class CopyPlacement {
    /** A rectangle, {@code right} and {@code bottom} excluded. */
    public record Box(float left, float top, float right, float bottom) {
        boolean intersects(Box other, float gap) {
            return left < other.right + gap && other.left < right + gap
                    && top < other.bottom + gap && other.top < bottom + gap;
        }
    }

    /** Top-left corner of the copy, and whether it is inside the visible area (else the canvas must scroll). */
    public record Spot(float x, float y, boolean visible) {
    }

    private CopyPlacement() {
    }

    /**
     * @param occupied the blocks already on the canvas
     * @param visible  the part of the canvas the user sees (minus menus and the palette)
     * @param width    width of the copy
     * @param height   height of the copy
     * @param gap      space kept between the copy and other blocks
     * @param step     how far apart the tried positions are
     * @return the first free position, row by row from the top-left of the visible area; when none is free, the
     * left of the visible area just below the lowest block
     */
    public static Spot find(List<Box> occupied, Box visible, float width, float height, float gap, float step) {
        float startX = visible.left() + gap;
        float startY = visible.top() + gap;
        float lastX = Math.max(startX, visible.right() - gap - width);
        float lastY = visible.bottom() - gap - height;
        step = Math.max(1f, step);
        for (float y = startY; y <= lastY; y += step) {
            for (float x = startX; x <= lastX; x += step) {
                Box candidate = new Box(x, y, x + width, y + height);
                if (isFree(candidate, occupied, gap)) {
                    return new Spot(x, y, true);
                }
            }
        }
        float lowest = visible.top();
        for (Box box : occupied) {
            lowest = Math.max(lowest, box.bottom());
        }
        return new Spot(startX, lowest + gap, false);
    }

    private static boolean isFree(Box candidate, List<Box> occupied, float gap) {
        for (Box box : occupied) {
            if (candidate.intersects(box, gap)) return false;
        }
        return true;
    }
}
