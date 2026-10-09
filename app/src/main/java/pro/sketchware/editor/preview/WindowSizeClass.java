package pro.sketchware.editor.preview;

/** Material 3 window size classes: the width and height buckets a layout adapts to. */
public record WindowSizeClass(Bucket width, Bucket height) {
    public enum Bucket {
        COMPACT, MEDIUM, EXPANDED
    }

    public static WindowSizeClass of(int widthDp, int heightDp) {
        return new WindowSizeClass(bucket(widthDp, 600, 840), bucket(heightDp, 480, 900));
    }

    private static Bucket bucket(int dp, int mediumFrom, int expandedFrom) {
        if (dp < mediumFrom) return Bucket.COMPACT;
        if (dp < expandedFrom) return Bucket.MEDIUM;
        return Bucket.EXPANDED;
    }

    /** Short label such as "Compact × Medium" (width × height). */
    public String label() {
        return name(width) + " × " + name(height);
    }

    private static String name(Bucket bucket) {
        String lower = bucket.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
