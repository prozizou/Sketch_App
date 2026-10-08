package pro.sketchware.activities.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Searches the text files of a project folder for a string or regular expression.
 */
public class ProjectSearch {
    public static final int MAX_RESULTS = 2000;
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private static final int MAX_LINE_PREVIEW = 300;

    /**
     * @param line   1-based line
     * @param column 1-based column of the match start
     */
    public record Match(File file, int line, int column, String lineText) {
    }

    public record Result(List<Match> matches, int filesSearched, boolean truncated) {
    }

    private final Pattern pattern;

    /**
     * @throws PatternSyntaxException if {@code useRegex} is set and {@code query} isn't a valid expression
     */
    public ProjectSearch(String query, boolean caseSensitive, boolean useRegex, boolean wholeWord) {
        String expression = useRegex ? query : Pattern.quote(query);
        if (wholeWord) expression = "\\b(?:" + expression + ")\\b";
        pattern = Pattern.compile(expression, caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * @param cancelled polled between files and lines; return true to stop early with the results so far
     */
    public Result search(File root, BooleanSupplier cancelled) {
        List<File> files = new ArrayList<>();
        collect(root, files, cancelled);
        files.sort(Comparator.comparing(File::getPath));

        List<Match> matches = new ArrayList<>();
        int searched = 0;
        boolean truncated = false;
        for (File file : files) {
            if (cancelled.getAsBoolean()) break;
            if (!isSearchable(file)) continue;
            searched++;
            try {
                truncated = searchFile(file, matches, cancelled);
            } catch (IOException ignored) {
                // Unreadable file, skip it
            }
            if (truncated) break;
        }
        return new Result(matches, searched, truncated);
    }

    /** @return whether the result limit was reached */
    private boolean searchFile(File file, List<Match> matches, BooleanSupplier cancelled) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String text;
            int line = 0;
            while ((text = reader.readLine()) != null) {
                line++;
                if ((line & 0x3FF) == 0 && cancelled.getAsBoolean()) return false;
                var matcher = pattern.matcher(text);
                if (matcher.find()) {
                    matches.add(new Match(file, line, matcher.start() + 1, preview(text, matcher.start())));
                    if (matches.size() >= MAX_RESULTS) return true;
                }
            }
        } catch (java.nio.charset.CharacterCodingException notText) {
            // Not UTF-8 after all
        }
        return false;
    }

    private static String preview(String text, int matchStart) {
        int from = Math.max(0, Math.min(matchStart - 40, text.length() - MAX_LINE_PREVIEW));
        from = Math.max(from, 0);
        String shown = text.substring(from, Math.min(text.length(), from + MAX_LINE_PREVIEW));
        return (from > 0 ? "…" : "") + shown.strip();
    }

    private static void collect(File dir, List<File> out, BooleanSupplier cancelled) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File child : children) {
            if (cancelled.getAsBoolean()) return;
            if (child.isDirectory()) collect(child, out, cancelled);
            else out.add(child);
        }
    }

    private static boolean isSearchable(File file) {
        if (file.length() == 0 || file.length() > MAX_FILE_SIZE) return false;
        // Binary files contain NUL bytes near the start
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] head = in.readNBytes(1024);
            for (byte b : head) if (b == 0) return false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}
