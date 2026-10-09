package pro.sketchware.analysis;

/**
 * What the analyses need to know about one widget of a screen.
 *
 * @param widthDp  fixed width in dp, or {@link #MATCH_PARENT} / {@link #WRAP_CONTENT}
 * @param heightDp fixed height in dp, or {@link #MATCH_PARENT} / {@link #WRAP_CONTENT}
 * @param textColor        ARGB text colour, or null when it is a resource colour or not set
 * @param backgroundColor  ARGB background colour, or null when it is a resource colour, transparent or not set
 * @param textSizeSp       text size in sp, or 0 when it has no text
 * @param hardcodedText    its text is typed in instead of coming from a string resource
 */
public record ViewFacts(String screen, String id, Kind kind, int widthDp, int heightDp, boolean clickable,
                        boolean hasContentDescription, Integer textColor, Integer backgroundColor, int textSizeSp,
                        int marginLeft, int marginRight, int paddingLeft, int paddingRight, boolean hardcodedText) {
    public static final int MATCH_PARENT = -1;
    public static final int WRAP_CONTENT = -2;

    public enum Kind {IMAGE, TEXT, TEXT_INPUT, BUTTON, OTHER}

    public String label() {
        return screen + "/" + id;
    }
}
