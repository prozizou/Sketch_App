package pro.sketchware.resolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.resolver.MissingResource.Occurrence;

/**
 * Finds every "resource not found" in a build log: AAPT2's link errors for any resource type, and the Java compiler's
 * "cannot be resolved" for {@code R.type.name}. The same resource used in several places is one entry with several
 * occurrences. Nothing is guessed: a line that doesn't say which resource is missing is not reported.
 */
public final class MissingResourceParser {
    /**
     * {@code path:12: error: resource drawable/ic_add (aka com.my.app:drawable/ic_add) not found.}, with or without the
     * location, with or without "aka", and for a framework resource ({@code resource android:drawable/x not found}).
     */
    private static final Pattern RESOURCE = Pattern.compile(
            "^(?:(?<path>/?[^\\s:][^:\\n]*?\\.[A-Za-z0-9]+)(?::(?<line>\\d+))?(?::(?<column>\\d+))?: )?"
                    + "(?<message>error: resource (?:(?<pkg>[\\w.]+):)?(?<type>[a-z]+)/(?<name>[\\w.\\-]+)"
                    + "(?: \\(aka (?<aka>[^)]*)\\))? not found\\.?)\\s*$", Pattern.MULTILINE);
    /** {@code error: style attribute 'attr/foo (aka pkg:attr/foo)' not found} */
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "^(?:(?<path>/?[^\\s:][^:\\n]*?\\.[A-Za-z0-9]+)(?::(?<line>\\d+))?(?::(?<column>\\d+))?: )?"
                    + "(?<message>error: style attribute '(?:(?<pkg>[\\w.]+):)?attr/(?<name>[\\w.\\-]+)(?: \\(aka [^)]*\\))?' not found\\.?)\\s*$",
            Pattern.MULTILINE);
    /** An ECJ problem: header, source line, caret line, message. */
    private static final Pattern ECJ = Pattern.compile(
            "^\\d+\\. ERROR in (?<path>.+?) \\(at line (?<line>\\d+)\\)\\n(?<source>[^\\n]*)\\n(?<caret>[^\\n]*)\\n(?<message>[^\\n]*)$",
            Pattern.MULTILINE);
    private static final Pattern UNRESOLVED_FIELD = Pattern.compile("^(?<id>\\w+) cannot be resolved or is not a field$");
    private static final Pattern R_REFERENCE = Pattern.compile("(?<![\\w.])R\\.(?<type>[a-z]+)\\.(?<name>\\w+)");

    private MissingResourceParser() {
    }

    /** @return the missing resources of the log in the order they first appear; empty for a null or unrelated log */
    public static List<MissingResource> parse(String log) {
        Map<String, Builder> found = new LinkedHashMap<>();
        if (log == null || log.isEmpty()) return new ArrayList<>();

        List<Hit> hits = new ArrayList<>();
        Matcher m = RESOURCE.matcher(log);
        while (m.find()) {
            String pkg = m.group("pkg") == null ? packageOf(m.group("aka")) : m.group("pkg");
            hits.add(new Hit(m.start(), m.group("type"), m.group("name"), pkg, occurrence(m, log)));
        }
        m = ATTRIBUTE.matcher(log);
        while (m.find()) {
            String pkg = m.group("pkg") == null ? "" : m.group("pkg");
            hits.add(new Hit(m.start(), "attr", m.group("name"), pkg, occurrence(m, log)));
        }
        m = ECJ.matcher(log);
        while (m.find()) {
            Matcher field = UNRESOLVED_FIELD.matcher(m.group("message").trim());
            if (!field.matches()) continue;
            String id = field.group("id");
            Matcher reference = R_REFERENCE.matcher(m.group("source"));
            while (reference.find()) {
                // "ic_add cannot be resolved" names the field; "drawable cannot be resolved" names a type with no resources at all
                if (reference.group("name").equals(id) || reference.group("type").equals(id)) {
                    String type = reference.group("type");
                    if (!ResourceTypes.KNOWN.contains(type)) break;
                    hits.add(new Hit(m.start(), type, reference.group("name"), "",
                            new Occurrence(m.group("path"), Integer.parseInt(m.group("line")), 0, m.group("message").trim(),
                                    m.start("message"), m.end("message"))));
                    break;
                }
            }
        }
        hits.sort((a, b) -> Integer.compare(a.position, b.position));
        for (Hit hit : hits) {
            found.computeIfAbsent(hit.type + "/" + hit.name, k -> new Builder(hit.type, hit.name, hit.pkg)).add(hit.pkg, hit.occurrence);
        }
        List<MissingResource> result = new ArrayList<>();
        for (Builder builder : found.values()) result.add(builder.build());
        return result;
    }

    private static Occurrence occurrence(Matcher m, String log) {
        String path = m.group("path") == null ? "" : m.group("path");
        int line = m.group("line") == null ? 0 : Integer.parseInt(m.group("line"));
        int column = m.group("column") == null ? 0 : Integer.parseInt(m.group("column"));
        return new Occurrence(path, line, column, m.group("message"), m.start("message"), m.end("message"));
    }

    /** {@code com.my.app:drawable/x} gives {@code com.my.app}. */
    private static String packageOf(String aka) {
        if (aka == null) return "";
        int colon = aka.indexOf(':');
        return colon > 0 ? aka.substring(0, colon) : "";
    }

    private record Hit(int position, String type, String name, String pkg, Occurrence occurrence) {
    }

    private static final class Builder {
        private final String type;
        private final String name;
        private String pkg;
        private final List<Occurrence> occurrences = new ArrayList<>();

        Builder(String type, String name, String pkg) {
            this.type = type;
            this.name = name;
            this.pkg = pkg;
        }

        void add(String hitPackage, Occurrence occurrence) {
            // Prefer the framework marker if any occurrence has it; otherwise the first package seen.
            if ("android".equals(hitPackage)) pkg = hitPackage;
            else if (pkg.isEmpty()) pkg = hitPackage;
            for (Occurrence existing : occurrences) {
                if (existing.start() == occurrence.start()) return;
            }
            occurrences.add(occurrence);
        }

        MissingResource build() {
            return new MissingResource(type, name, pkg, List.copyOf(occurrences));
        }
    }
}
