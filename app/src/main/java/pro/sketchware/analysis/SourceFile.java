package pro.sketchware.analysis;

/**
 * A file of the project, by name and content.
 *
 * @param path where it is on the device, to open it; null when unknown (the name is then used)
 */
public record SourceFile(String name, String content, String path) {
    public SourceFile(String name, String content) {
        this(name, content, null);
    }

    /** What to open: the path when known, else the name. */
    public String openPath() {
        return path != null ? path : name;
    }
}
