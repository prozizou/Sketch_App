package pro.sketchware.colorwheel;

/** Geometry of the color wheel: hue is the angle (0° to the right, clockwise on screen), saturation the distance. */
public final class ColorWheelMath {
    private ColorWheelMath() {
    }

    /** @return {hue 0..360, saturation 0..1} of the point (dx, dy) from the centre of a wheel of this radius */
    public static float[] hueSaturationAt(float dx, float dy, float radius) {
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360;
        float distance = (float) Math.hypot(dx, dy);
        float saturation = radius <= 0 ? 0 : Math.min(1f, distance / radius);
        return new float[]{(float) (angle % 360), saturation};
    }

    /** @return {dx, dy} from the centre for this hue (degrees) and saturation (0..1) */
    public static float[] positionOf(float hue, float saturation, float radius) {
        double angle = Math.toRadians(hue);
        float distance = Math.max(0f, Math.min(1f, saturation)) * radius;
        return new float[]{(float) (Math.cos(angle) * distance), (float) (Math.sin(angle) * distance)};
    }

    /** @return the color as {@code #RRGGBB}, or {@code #AARRGGBB} when it isn't opaque */
    public static String toHex(int color) {
        int alpha = color >>> 24;
        return alpha == 0xFF ? String.format("#%06X", color & 0xFFFFFF) : String.format("#%08X", color);
    }

    /**
     * Reads {@code RGB}, {@code RRGGBB} or {@code AARRGGBB}, with or without {@code #}.
     *
     * @return the color, or null when the text isn't one
     */
    public static Integer parseHex(String text) {
        if (text == null) return null;
        String hex = text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9A-Fa-f]+")) return null;
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() == 6) return (int) (0xFF000000L | Long.parseLong(hex, 16));
        if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
        return null;
    }
}
