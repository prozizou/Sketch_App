package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Splits source text into coloured spans for the code previews of Evolve posts: Java, Kotlin, XML and JSON. It is a
 * small scanner (comments, strings, numbers, keywords, annotations, types, XML tags and attributes), not a parser: it
 * never fails, never looks back, and costs one pass over the text, which keeps feed cards cheap on a 2 GB phone. Pure
 * Java; {@link HighlightedText} paints the result.
 */
public final class CodeHighlighter {

    public enum Language {JAVA, KOTLIN, XML, JSON, PLAIN}

    public enum Token {COMMENT, STRING, NUMBER, KEYWORD, ANNOTATION, TYPE, TAG, ATTRIBUTE}

    /** A coloured part of the text. @param end exclusive */
    public record Span(int start, int end, Token token) {
    }

    private static final Set<String> JAVA_KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
            "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var", "record");
    private static final Set<String> KOTLIN_KEYWORDS = Set.of("as", "break", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "val", "var", "when", "while", "by", "catch", "constructor", "delegate", "finally", "get", "import", "init", "set",
            "abstract", "enum", "open", "override", "private", "protected", "public", "internal", "data", "sealed", "companion",
            "lateinit", "suspend", "inline", "const", "vararg", "out", "where", "lazy");
    private static final Set<String> JSON_KEYWORDS = Set.of("true", "false", "null");

    private CodeHighlighter() {
    }

    /** @return the language a post of {@code kind} is written in */
    public static Language languageOf(SharedKind kind) {
        return switch (kind) {
            case JAVA -> Language.JAVA;
            case KOTLIN -> Language.KOTLIN;
            case VIEW -> Language.XML;
            case COMPONENT, BLOCKS, MOREBLOCK -> Language.JSON;
        };
    }

    /**
     * @param maxChars only this many characters are scanned (a card shows the start of a long file)
     * @return the spans in text order, never overlapping
     */
    public static List<Span> highlight(Language language, String text, int maxChars) {
        if (text == null || text.isEmpty() || language == Language.PLAIN) return List.of();
        String source = text.length() > maxChars ? text.substring(0, maxChars) : text;
        List<Span> spans = new ArrayList<>();
        switch (language) {
            case JAVA -> scanCode(source, JAVA_KEYWORDS, false, spans);
            case KOTLIN -> scanCode(source, KOTLIN_KEYWORDS, true, spans);
            case XML -> scanXml(source, spans);
            case JSON -> scanJson(source, spans);
            default -> {
            }
        }
        return spans;
    }

    // ---- Java and Kotlin

    private static void scanCode(String s, Set<String> keywords, boolean kotlin, List<Span> out) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                int end = lineEnd(s, i);
                out.add(new Span(i, end, Token.COMMENT));
                i = end;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int close = s.indexOf("*/", i + 2);
                int end = close < 0 ? n : close + 2;
                out.add(new Span(i, end, Token.COMMENT));
                i = end;
            } else if (kotlin && s.startsWith("\"\"\"", i)) {
                int close = s.indexOf("\"\"\"", i + 3);
                int end = close < 0 ? n : close + 3;
                out.add(new Span(i, end, Token.STRING));
                i = end;
            } else if (c == '"' || c == '\'') {
                int end = quoted(s, i, c);
                out.add(new Span(i, end, Token.STRING));
                i = end;
            } else if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(s.charAt(i + 1))) {
                int end = i + 1;
                while (end < n && (Character.isJavaIdentifierPart(s.charAt(end)) || s.charAt(end) == '.')) end++;
                out.add(new Span(i, end, Token.ANNOTATION));
                i = end;
            } else if (Character.isDigit(c)) {
                int end = number(s, i);
                out.add(new Span(i, end, Token.NUMBER));
                i = end;
            } else if (Character.isJavaIdentifierStart(c)) {
                int end = i + 1;
                while (end < n && Character.isJavaIdentifierPart(s.charAt(end))) end++;
                String word = s.substring(i, end);
                if (keywords.contains(word)) {
                    out.add(new Span(i, end, Token.KEYWORD));
                } else if (Character.isUpperCase(c) && word.length() > 1 && !word.equals(word.toUpperCase())) {
                    out.add(new Span(i, end, Token.TYPE));
                }
                i = end;
            } else {
                i++;
            }
        }
    }

    // ---- XML

    private static void scanXml(String s, List<Span> out) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (s.startsWith("<!--", i)) {
                int close = s.indexOf("-->", i + 4);
                int end = close < 0 ? n : close + 3;
                out.add(new Span(i, end, Token.COMMENT));
                i = end;
            } else if (c == '<') {
                i = scanTag(s, i, out);
            } else {
                i++;
            }
        }
    }

    /** Scans one tag from its {@code <}; @return the index after it (or after what could be read) */
    private static int scanTag(String s, int start, List<Span> out) {
        int n = s.length();
        int i = start + 1;
        if (i < n && (s.charAt(i) == '/' || s.charAt(i) == '?' || s.charAt(i) == '!')) i++;
        int nameStart = i;
        while (i < n && isXmlNameChar(s.charAt(i))) i++;
        if (i > nameStart) out.add(new Span(nameStart, i, Token.TAG));
        while (i < n && s.charAt(i) != '>') {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int end = quoted(s, i, c);
                out.add(new Span(i, end, Token.STRING));
                i = end;
            } else if (isXmlNameChar(c)) {
                int attributeStart = i;
                while (i < n && isXmlNameChar(s.charAt(i))) i++;
                out.add(new Span(attributeStart, i, Token.ATTRIBUTE));
            } else {
                i++;
            }
        }
        return Math.min(n, i + 1);
    }

    private static boolean isXmlNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.' || c == '-';
    }

    // ---- JSON

    private static void scanJson(String s, List<Span> out) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"') {
                int end = quoted(s, i, '"');
                int next = end;
                while (next < n && Character.isWhitespace(s.charAt(next))) next++;
                boolean key = next < n && s.charAt(next) == ':';
                out.add(new Span(i, end, key ? Token.ATTRIBUTE : Token.STRING));
                i = end;
            } else if (Character.isDigit(c) || c == '-' && i + 1 < n && Character.isDigit(s.charAt(i + 1))) {
                int end = number(s, c == '-' ? i + 1 : i);
                out.add(new Span(i, end, Token.NUMBER));
                i = end;
            } else if (Character.isLetter(c)) {
                int end = i + 1;
                while (end < n && Character.isLetter(s.charAt(end))) end++;
                if (JSON_KEYWORDS.contains(s.substring(i, end))) out.add(new Span(i, end, Token.KEYWORD));
                i = end;
            } else {
                i++;
            }
        }
    }

    // ---- shared pieces

    private static int lineEnd(String s, int from) {
        int end = s.indexOf('\n', from);
        return end < 0 ? s.length() : end;
    }

    /** @return the index after the string that starts at {@code start}; it ends at the closing quote or the line's end */
    private static int quoted(String s, int start, char quote) {
        int n = s.length();
        int i = start + 1;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (c == '\n' && quote != '"' || c == '\n') {
                return i;
            } else {
                i++;
            }
        }
        return n;
    }

    private static int number(String s, int start) {
        int n = s.length();
        int i = start;
        if (s.startsWith("0x", i) || s.startsWith("0X", i)) {
            i += 2;
            while (i < n && (Character.digit(s.charAt(i), 16) >= 0 || s.charAt(i) == '_')) i++;
        } else {
            while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == '_'
                    || (s.charAt(i) == 'e' || s.charAt(i) == 'E') && i + 1 < n && (Character.isDigit(s.charAt(i + 1)) || s.charAt(i + 1) == '-'))) {
                i++;
            }
        }
        while (i < n && "lLfFdD".indexOf(s.charAt(i)) >= 0) i++;
        return Math.max(i, start + 1);
    }
}
