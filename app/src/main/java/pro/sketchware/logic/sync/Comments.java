package pro.sketchware.logic.sync;

import java.util.ArrayList;
import java.util.List;

/** Finds the comments in Java text without parsing it, skipping what looks like a comment inside a string or character. */
final class Comments {
    /** A comment: where it is in the text, from {@code start} up to {@code end}. */
    record Span(int start, int end) {
    }

    private Comments() {
    }

    static List<Span> scan(String text, int from, int to) {
        List<Span> spans = new ArrayList<>();
        int i = Math.max(0, from);
        int limit = Math.min(text.length(), to);
        while (i < limit) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i, limit);
            } else if (c == '/' && i + 1 < limit && text.charAt(i + 1) == '/') {
                int end = text.indexOf('\n', i);
                end = end < 0 || end > limit ? limit : end;
                spans.add(new Span(i, end));
                i = end;
            } else if (c == '/' && i + 1 < limit && text.charAt(i + 1) == '*') {
                int close = text.indexOf("*/", i + 2);
                int end = close < 0 || close + 2 > limit ? limit : close + 2;
                spans.add(new Span(i, end));
                i = end;
            } else {
                i++;
            }
        }
        return spans;
    }

    static int count(String text) {
        return scan(text, 0, text.length()).size();
    }

    private static int skipLiteral(String text, int start, int limit) {
        char quote = text.charAt(start);
        int i = start + 1;
        while (i < limit) {
            char c = text.charAt(i);
            if (c == '\\') i += 2;
            else if (c == quote || c == '\n') return i + 1;
            else i++;
        }
        return limit;
    }
}
