package pro.sketchware.activities.editor.view.xml;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-indents a layout the way Android Studio lays it out: one element per line, indented four spaces per level, with
 * each attribute of an element on its own line (an element with a single attribute stays on one line). Values, text,
 * comments and CDATA are kept as they are. Pure Java.
 *
 * <p>Only well-formed XML is touched: anything else gives {@code null}, so a half-typed layout is never mangled.
 */
public final class XmlFormatter {
    private static final String INDENT = "    ";

    private XmlFormatter() {
    }

    /** @return the formatted layout, or null when {@code xml} is not well-formed */
    @Nullable
    public static String format(@Nullable String xml) {
        if (xml == null || !XmlDiagnostics.check(xml).valid()) {
            return null;
        }
        StringBuilder out = new StringBuilder(xml.length() + xml.length() / 4);
        int depth = 0;
        int i = 0;
        int n = xml.length();
        while (i < n) {
            if (xml.startsWith("<!--", i)) {
                int end = xml.indexOf("-->", i + 4);
                end = end < 0 ? n : end + 3;
                line(out, depth, xml.substring(i, end).strip());
                i = end;
            } else if (xml.startsWith("<![CDATA[", i)) {
                int end = xml.indexOf("]]>", i + 9);
                end = end < 0 ? n : end + 3;
                line(out, depth, xml.substring(i, end));
                i = end;
            } else if (xml.startsWith("<?", i)) {
                int end = xml.indexOf("?>", i + 2);
                end = end < 0 ? n : end + 2;
                line(out, depth, xml.substring(i, end).strip());
                i = end;
            } else if (xml.startsWith("<!", i)) {
                int end = tagEnd(xml, i);
                line(out, depth, xml.substring(i, end).strip());
                i = end;
            } else if (xml.startsWith("</", i)) {
                int end = tagEnd(xml, i);
                depth = Math.max(0, depth - 1);
                line(out, depth, "</" + xml.substring(i + 2, end - 1).strip() + ">");
                i = end;
            } else if (xml.charAt(i) == '<') {
                int end = tagEnd(xml, i);
                String tag = xml.substring(i + 1, end - 1);
                boolean selfClosing = tag.endsWith("/");
                if (selfClosing) {
                    tag = tag.substring(0, tag.length() - 1);
                }
                List<String> lines = startTag(tag, selfClosing);
                // <a>text</a>: an element holding only text stays on one line.
                int textEnd = xml.indexOf('<', end);
                if (!selfClosing && textEnd > end && xml.startsWith("</", textEnd) && !xml.substring(end, textEnd).isBlank()) {
                    int closeEnd = tagEnd(xml, textEnd);
                    String last = lines.remove(lines.size() - 1);
                    lines.add(last + xml.substring(end, textEnd).strip() + "</" + xml.substring(textEnd + 2, closeEnd - 1).strip() + ">");
                    for (String text : lines) {
                        line(out, depth, text);
                    }
                    i = closeEnd;
                    continue;
                }
                for (String text : lines) {
                    line(out, depth, text);
                }
                if (!selfClosing) {
                    depth++;
                }
                i = end;
            } else {
                int end = xml.indexOf('<', i);
                end = end < 0 ? n : end;
                String text = xml.substring(i, end).strip();
                if (!text.isEmpty()) {
                    line(out, depth, text);
                }
                i = end;
            }
        }
        return out.toString();
    }

    private static void line(StringBuilder out, int depth, String text) {
        // A multi-line comment or CDATA keeps its own inner indentation: only its first line is indented.
        out.append(INDENT.repeat(depth)).append(text).append('\n');
    }

    /** @return the index just after the {@code >} that closes the tag opened at {@code from}, quotes respected */
    private static int tagEnd(String xml, int from) {
        char quote = 0;
        for (int i = from + 1; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i + 1;
            }
        }
        return xml.length();
    }

    /** The lines of a start tag: its name, then its attributes, the last one carrying the closing bracket. */
    private static List<String> startTag(String tag, boolean selfClosing) {
        int i = 0;
        int n = tag.length();
        while (i < n && !Character.isWhitespace(tag.charAt(i))) i++;
        String name = tag.substring(0, i);
        List<String> attributes = new ArrayList<>();
        while (i < n) {
            while (i < n && Character.isWhitespace(tag.charAt(i))) i++;
            if (i >= n) break;
            int start = i;
            while (i < n && tag.charAt(i) != '=' && !Character.isWhitespace(tag.charAt(i))) i++;
            String key = tag.substring(start, i);
            while (i < n && Character.isWhitespace(tag.charAt(i))) i++;
            if (i < n && tag.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(tag.charAt(i))) i++;
                if (i < n && (tag.charAt(i) == '"' || tag.charAt(i) == '\'')) {
                    char quote = tag.charAt(i);
                    int close = tag.indexOf(quote, i + 1);
                    close = close < 0 ? n : close + 1;
                    attributes.add(key + "=" + tag.substring(i, close));
                    i = close;
                    continue;
                }
            }
            attributes.add(key);
        }
        String closing = selfClosing ? " />" : ">";
        List<String> lines = new ArrayList<>();
        if (attributes.isEmpty()) {
            lines.add("<" + name + closing);
        } else if (attributes.size() == 1) {
            lines.add("<" + name + " " + attributes.get(0) + closing);
        } else {
            lines.add("<" + name);
            for (int a = 0; a < attributes.size(); a++) {
                lines.add(INDENT + attributes.get(a) + (a == attributes.size() - 1 ? closing : ""));
            }
        }
        return lines;
    }
}
