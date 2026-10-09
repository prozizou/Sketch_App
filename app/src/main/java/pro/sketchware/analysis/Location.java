package pro.sketchware.analysis;

/**
 * Where a finding was found, precisely enough to open it: a widget on a screen, a line of a file, a block of an event,
 * or a screen.
 *
 * @param file   WIDGET: the layout name without {@code .xml} (like {@code main}); SOURCE: the file's path;
 *               EVENT and SCREEN: the screen's Java file name (like {@code MainActivity.java})
 * @param target WIDGET: the widget id; EVENT: the event key (like {@code button1_onClick}); otherwise empty
 * @param detail EVENT: the block id, or empty; otherwise empty
 * @param line   SOURCE: the 1-based line; otherwise 0
 * @param label  what the list shows, like {@code main / imageview1} or {@code Util.java:12}
 */
public record Location(Kind kind, String file, String target, String detail, int line, String label) {
    public enum Kind {WIDGET, SOURCE, EVENT, SCREEN}

    public Location {
        file = file == null ? "" : file;
        target = target == null ? "" : target;
        detail = detail == null ? "" : detail;
        label = label == null ? "" : label;
    }

    public static Location widget(String layout, String widgetId) {
        return new Location(Kind.WIDGET, layout, widgetId, "", 0, layout + " / " + widgetId);
    }

    /** @param path where the file is (used to open it); {@code shownName} is what the user sees */
    public static Location source(String path, String shownName, int line) {
        return new Location(Kind.SOURCE, path, "", "", line, shownName + ":" + line);
    }

    public static Location event(String javaName, String eventKey, String blockId) {
        String screen = javaName.endsWith(".java") ? javaName.substring(0, javaName.length() - 5) : javaName;
        return new Location(Kind.EVENT, javaName, eventKey, blockId, 0,
                screen + " / " + eventKey + (blockId == null || blockId.isEmpty() ? "" : " #" + blockId));
    }

    public static Location screen(String javaName) {
        return new Location(Kind.SCREEN, javaName, "", "", 0, javaName.endsWith(".java") ? javaName.substring(0, javaName.length() - 5) : javaName);
    }

    /** The widget id of an event key like {@code button1_onClick}, or the more block name of {@code name_moreBlock}. */
    public String eventTarget() {
        int split = target.lastIndexOf('_');
        return split < 0 ? target : target.substring(0, split);
    }

    /** The event name of an event key like {@code button1_onClick}. */
    public String eventName() {
        int split = target.lastIndexOf('_');
        return split < 0 ? "" : target.substring(split + 1);
    }

    /** The 1-based line of {@code offset} in {@code content}. */
    public static int lineOf(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }
}
