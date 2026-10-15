package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * A resource the build asked for and did not find, with every place that asked for it.
 *
 * @param type        {@code drawable}, {@code mipmap}, {@code color}, {@code string}, ...
 * @param name        the resource name as written in the log ({@code ic_add}, {@code Theme.App})
 * @param packageName {@code android} for a framework resource, the app's package when the log gave one, else ""
 * @param occurrences where it was used, in log order
 */
public record MissingResource(@NonNull String type, @NonNull String name, @NonNull String packageName,
                              @NonNull List<Occurrence> occurrences) {

    /** One place that used the resource.
     *
     * @param path    the file, as the log printed it ("" when the log gave none)
     * @param line    1-based line, 0 when unknown
     * @param column  1-based column, 0 when unknown
     * @param message the log's message for it
     * @param start   start of the message in the log text (what is made tappable)
     * @param end     end of the message in the log text
     */
    public record Occurrence(@NonNull String path, int line, int column, @NonNull String message, int start, int end) {
    }

    /**
     * The distinct places that used it ({@code file:line}); AAPT2 sometimes prints the same line twice, and every
     * printed line stays tappable, but a person wants to see each place once.
     */
    public List<Occurrence> distinctLocations() {
        List<Occurrence> distinct = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Occurrence occurrence : occurrences) {
            if (seen.add(occurrence.path() + ":" + occurrence.line() + ":" + occurrence.column())) distinct.add(occurrence);
        }
        return distinct;
    }

    /** {@code type/name}: what identifies the resource in the log, a plan and a journal. */
    public String key() {
        return type + "/" + name;
    }

    /** A resource of the Android framework: it can't be added to the project, only its use can be fixed. */
    public boolean framework() {
        return "android".equals(packageName);
    }

    /** Whether the resolver can offer a fix: the framework's resources and unsupported types are only reported. */
    public boolean fixable() {
        return !framework() && ResourceTypes.isSupported(type) && ResourceNames.isValidFor(type, name);
    }

    /** Why there is no fix, in a word the UI maps to a text; null when {@link #fixable()}. */
    public Reason unfixableReason() {
        if (framework()) return Reason.FRAMEWORK;
        if (!ResourceTypes.isSupported(type)) return Reason.UNSUPPORTED_TYPE;
        if (!ResourceNames.isValidFor(type, name)) return Reason.INVALID_NAME;
        return null;
    }

    public enum Reason {FRAMEWORK, UNSUPPORTED_TYPE, INVALID_NAME}

    @NonNull
    @Override
    public String toString() {
        return key();
    }
}
