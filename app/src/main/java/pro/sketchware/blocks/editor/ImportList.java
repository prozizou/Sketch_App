package pro.sketchware.blocks.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The imports of a block, one per line. Lines are kept exactly as typed; an entry is a Java import with or without
 * {@code import} and the final semicolon.
 */
public final class ImportList {
    public record Entry(int line, String text, String type, String problem) {
        public boolean valid() {
            return problem == null;
        }
    }

    private static final Pattern IMPORT = Pattern.compile("(?:import\\s+)?(static\\s+)?([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*(?:\\.\\*)?)\\s*;?");

    /** Classes of Android and Java that blocks use often: the simple name and where it comes from. */
    private static final String[][] KNOWN = {
            {"Toast", "android.widget.Toast"}, {"Intent", "android.content.Intent"}, {"Uri", "android.net.Uri"},
            {"View", "android.view.View"}, {"Handler", "android.os.Handler"}, {"Looper", "android.os.Looper"},
            {"Log", "android.util.Log"}, {"Bundle", "android.os.Bundle"}, {"Context", "android.content.Context"},
            {"SharedPreferences", "android.content.SharedPreferences"}, {"Vibrator", "android.os.Vibrator"},
            {"Build", "android.os.Build"}, {"Color", "android.graphics.Color"}, {"Bitmap", "android.graphics.Bitmap"},
            {"File", "java.io.File"}, {"ArrayList", "java.util.ArrayList"}, {"HashMap", "java.util.HashMap"},
            {"Calendar", "java.util.Calendar"}, {"Date", "java.util.Date"}, {"Random", "java.util.Random"},
            {"Pattern", "java.util.regex.Pattern"}, {"Matcher", "java.util.regex.Matcher"},
            {"SimpleDateFormat", "java.text.SimpleDateFormat"}, {"Locale", "java.util.Locale"},
            {"TextView", "android.widget.TextView"}, {"ImageView", "android.widget.ImageView"},
            {"Uri", "android.net.Uri"}, {"Environment", "android.os.Environment"}};

    private ImportList() {
    }

    public static List<Entry> parse(String imports) {
        List<Entry> entries = new ArrayList<>();
        if (imports == null) return entries;
        String[] lines = imports.split("\\R", -1);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            String text = lines[i].trim();
            if (text.isEmpty()) continue;
            Matcher m = IMPORT.matcher(text);
            if (!m.matches()) {
                entries.add(new Entry(i + 1, text, null, "Not a valid import"));
            } else if (!m.group(2).contains(".")) {
                entries.add(new Entry(i + 1, text, m.group(2), "Use the full name, with its package (for example android.widget.Toast)"));
            } else if (!seen.add(m.group(1) + m.group(2))) {
                entries.add(new Entry(i + 1, text, m.group(2), "Listed twice"));
            } else {
                entries.add(new Entry(i + 1, text, m.group(2), null));
            }
        }
        return entries;
    }

    /** @return the full class names already imported */
    public static Set<String> classes(String imports) {
        Set<String> classes = new HashSet<>();
        for (Entry entry : parse(imports)) {
            if (entry.valid()) classes.add(entry.type());
        }
        return classes;
    }

    /** Appends an import line, written as {@code import a.b.C;}; an import already there isn't added twice. */
    public static String add(String imports, String fullName) {
        if (classes(imports).contains(fullName)) return imports == null ? "" : imports;
        String base = imports == null ? "" : imports;
        String separator = base.isEmpty() || base.endsWith("\n") ? "" : "\n";
        return base + separator + "import " + fullName + ";";
    }

    /** Removes the given (1-based) line. */
    public static String removeLine(String imports, int line) {
        String[] lines = imports.split("\\R", -1);
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (i + 1 != line) kept.add(lines[i]);
        }
        return String.join("\n", kept);
    }

    /** Replaces the given (1-based) line. */
    public static String replaceLine(String imports, int line, String newText) {
        String[] lines = imports.split("\\R", -1);
        if (line >= 1 && line <= lines.length) lines[line - 1] = newText;
        return String.join("\n", lines);
    }

    /** @return the full name of a well-known class by its simple name, or null */
    public static String known(String simpleName) {
        for (String[] k : KNOWN) {
            if (k[0].equals(simpleName)) return k[1];
        }
        return null;
    }

    /** @return the well-known classes the code uses by their simple name, in order of first use */
    public static List<String> usedKnownClasses(String code) {
        List<String> used = new ArrayList<>();
        if (code == null) return used;
        Matcher m = Pattern.compile("(?<![.\\w$\"])([A-Z][A-Za-z0-9_]*)\\b").matcher(stripStringsAndComments(code));
        while (m.find()) {
            String full = known(m.group(1));
            if (full != null && !used.contains(full)) used.add(full);
        }
        return used;
    }

    /** The code with the inside of string literals and comments blanked out (line breaks kept). */
    public static String stripStringsAndComments(String code) {
        StringBuilder out = new StringBuilder(code.length());
        int i = 0;
        int n = code.length();
        while (i < n) {
            char c = code.charAt(i);
            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                while (i < n && code.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(code.charAt(i) == '*' && i + 1 < n && code.charAt(i + 1) == '/')) {
                    out.append(code.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
            } else if (c == '"' || c == '\'') {
                out.append(c);
                i++;
                while (i < n && code.charAt(i) != c && code.charAt(i) != '\n') {
                    if (code.charAt(i) == '\\' && i + 1 < n && code.charAt(i + 1) != '\n') {
                        out.append("  ");
                        i += 2;
                    } else {
                        out.append(' ');
                        i++;
                    }
                }
                if (i < n && code.charAt(i) == c) {
                    out.append(c);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    public static String describe(Entry entry) {
        return String.format(Locale.ROOT, "Line %d: %s", entry.line(), entry.problem());
    }
}
