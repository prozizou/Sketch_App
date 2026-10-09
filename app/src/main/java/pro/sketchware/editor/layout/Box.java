package pro.sketchware.editor.layout;

/** An axis-aligned rectangle in any consistent unit (pixels or dp). Plain numbers, so it is testable on a JVM. */
public record Box(float left, float top, float right, float bottom) {
    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) / 2f;
    }

    public float centerY() {
        return (top + bottom) / 2f;
    }

    public Box offset(float dx, float dy) {
        return new Box(left + dx, top + dy, right + dx, bottom + dy);
    }

    public boolean intersects(Box other) {
        return left < other.right && other.left < right && top < other.bottom && other.top < bottom;
    }

    /** True when the horizontal extents overlap, so the boxes face each other vertically. */
    boolean overlapsX(Box other) {
        return left < other.right && other.left < right;
    }

    boolean overlapsY(Box other) {
        return top < other.bottom && other.top < bottom;
    }
}
