package mod.jbk.diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the errors and warnings in a compile log together with the file and line they point to,
 * so the log viewer can make them clickable.
 */
public class CompileDiagnosticParser {
    public enum Severity {ERROR, WARNING}

    /**
     * One problem found in a log.
     *
     * @param start start of the clickable location in the log text
     * @param end   end of the clickable location in the log text
     * @param line  1-based line, or 0 if the log gave none
     * @param column 1-based column, or 0 if the log gave none
     */
    public record Diagnostic(Severity severity, String path, int line, int column, String message, int start, int end) {
    }

    /** ECJ: {@code 1. ERROR in /path/File.java (at line 12)} */
    private static final Pattern ECJ = Pattern.compile("^\\d+\\. (ERROR|WARNING) in (.+?) \\(at line (\\d+)\\)", Pattern.MULTILINE);
    /** javac and aapt2: {@code /path/File.java:12: error: message}, {@code /path/a.xml:12:5: warning: message} */
    private static final Pattern COLON = Pattern.compile("^(/?[^\\s:][^:\\n]*?\\.[A-Za-z0-9]+):(\\d+)(?::(\\d+))?: (error|warning):? ?(.*)$", Pattern.MULTILINE);
    /** Kotlin: {@code e: /path/File.kt: (12, 5): message} */
    private static final Pattern KOTLIN = Pattern.compile("^([ew]): (/?[^\\n]*?\\.kts?): \\((\\d+), (\\d+)\\): (.*)$", Pattern.MULTILINE);

    public static List<Diagnostic> parse(String log) {
        List<Diagnostic> found = new ArrayList<>();
        if (log == null || log.isEmpty()) return found;

        Matcher m = ECJ.matcher(log);
        while (m.find()) {
            int line = Integer.parseInt(m.group(3));
            found.add(new Diagnostic(severity(m.group(1)), m.group(2), line, 0,
                    ecjMessage(log, m.end()), m.start(2), m.end(3) + 1));
        }

        m = COLON.matcher(log);
        while (m.find()) {
            int column = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            int locationEnd = m.group(3) == null ? m.end(2) : m.end(3);
            found.add(new Diagnostic(severity(m.group(4)), m.group(1), Integer.parseInt(m.group(2)), column,
                    m.group(5).trim(), m.start(1), locationEnd));
        }

        m = KOTLIN.matcher(log);
        while (m.find()) {
            found.add(new Diagnostic("e".equals(m.group(1)) ? Severity.ERROR : Severity.WARNING, m.group(2),
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), m.group(5).trim(),
                    m.start(2), m.end(4) + 1));
        }

        found.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return found;
    }

    public static int count(List<Diagnostic> diagnostics, Severity severity) {
        int count = 0;
        for (Diagnostic d : diagnostics) if (d.severity() == severity) count++;
        return count;
    }

    private static Severity severity(String word) {
        return word.equalsIgnoreCase("warning") ? Severity.WARNING : Severity.ERROR;
    }

    /** The message of an ECJ problem is the line after the source line and the caret marker. */
    private static String ecjMessage(String log, int from) {
        String[] lines = log.substring(from).split("\n", 6);
        // lines[0] is the rest of the header line, then source line, caret line, message
        return lines.length > 3 ? lines[3].trim() : "";
    }
}
