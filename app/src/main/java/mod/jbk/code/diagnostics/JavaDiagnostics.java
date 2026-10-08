package mod.jbk.code.diagnostics;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.Problem;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.jbk.code.completion.ClassIndex;

/**
 * Finds problems in a Java file as it is typed: syntax errors, and warnings for imports that are unused
 * or point at an SDK class that doesn't exist.
 * <p>
 * It parses the file without compiling it, so type errors are not found; those still show up in the
 * compile log. The parser stops at the first syntax error, so at most one is reported at a time.
 */
public final class JavaDiagnostics {
    public enum Severity {ERROR, WARNING}

    /**
     * @param start   offset of the first character to underline
     * @param end     offset after the last character to underline, always greater than {@code start}
     * @param message one line, for the underline's tooltip
     * @param detail  more explanation, may be the same as {@code message}
     */
    public record Diagnostic(Severity severity, int start, int end, String message, String detail) {
    }

    /** Packages whose classes the SDK index knows completely; anything else may come from a library. */
    private static final List<String> CHECKED_IMPORT_ROOTS = List.of("android.", "java.", "javax.");
    private static final Pattern FOUND_TOKEN = Pattern.compile("Found (\"(?:[^\"\\\\]|\\\\.)*\"|<EOF>)");
    private static final Pattern EXPECTED_TOKENS = Pattern.compile("expected (?:one of )?(.*)$", Pattern.DOTALL);
    private static final Pattern LEXICAL_ERROR = Pattern.compile("Lexical error at line (\\d+), column (\\d+)\\.\\s+Encountered: (.*)");
    private static final int MAX_EXPECTED_SHOWN = 8;

    private JavaDiagnostics() {
    }

    /**
     * @param index the classes known to exist, used to check imports; may be empty to skip that check
     */
    public static List<Diagnostic> analyze(String source, ClassIndex index) {
        ParserConfiguration configuration = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        ParseResult<CompilationUnit> result;
        try {
            result = new JavaParser(configuration).parse(source);
        } catch (RuntimeException | StackOverflowError e) {
            return List.of();
        }

        LineIndex lines = new LineIndex(source);
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            for (Problem problem : result.getProblems()) {
                Diagnostic diagnostic = clamp(syntaxError(source, lines, problem), source.length());
                if (diagnostic != null) diagnostics.add(diagnostic);
            }
            return diagnostics;
        }

        checkImports(source, lines, result.getResult().get(), index, diagnostics);
        return diagnostics;
    }

    /**
     * The problem to jump to from {@code caretOffset}: the first one starting after it, wrapping around
     * to the first of the file, or null if there are none. Errors and warnings are treated alike.
     */
    public static Diagnostic nextAfter(List<Diagnostic> diagnostics, int caretOffset) {
        Diagnostic first = null;
        for (Diagnostic d : diagnostics) {
            if (first == null || d.start() < first.start()) first = d;
        }
        Diagnostic next = null;
        for (Diagnostic d : diagnostics) {
            if (d.start() > caretOffset && (next == null || d.start() < next.start())) next = d;
        }
        return next != null ? next : first;
    }

    /** Keeps the underline inside the text and at least one character wide, or drops it if the text is empty. */
    private static Diagnostic clamp(Diagnostic d, int length) {
        if (d == null || length == 0) return null;
        int start = Math.max(0, Math.min(d.start(), length - 1));
        int end = Math.max(start + 1, Math.min(d.end(), length));
        return new Diagnostic(d.severity(), start, end, d.message(), d.detail());
    }

    // ---- syntax errors

    private static Diagnostic syntaxError(String source, LineIndex lines, Problem problem) {
        String message = problem.getMessage();

        Matcher lexical = LEXICAL_ERROR.matcher(message);
        if (lexical.find()) {
            int at = lines.offset(Integer.parseInt(lexical.group(1)), Integer.parseInt(lexical.group(2)), source.length());
            String encountered = lexical.group(3);
            boolean unclosedString = encountered.contains("after : \"\\\"");
            String brief = unclosedString ? "Unclosed string literal" : "Unexpected character";
            int line = lines.lineOf(Math.min(at, Math.max(0, source.length() - 1)));
            int start = unclosedString ? Math.max(lines.lineStart(line), source.lastIndexOf('"', Math.max(0, at - 1))) : at;
            int end = unclosedString ? lines.lineEnd(line, source.length()) : at + 1;
            return new Diagnostic(Severity.ERROR, start, end, brief, message);
        }

        Matcher found = FOUND_TOKEN.matcher(message);
        if (!found.find()) return null;
        String foundToken = found.group(1);
        boolean atEndOfFile = foundToken.equals("<EOF>");

        // The reported range starts at the last token that was fine; the problem is the token after it
        int lastGoodEnd = 0;
        int lastGoodLine = 0;
        String lastGoodText = "";
        TokenRange range = problem.getLocation().orElse(null);
        if (range != null && range.getBegin().getRange().isPresent()) {
            Position begin = range.getBegin().getRange().get().begin;
            lastGoodLine = begin.line;
            lastGoodText = range.getBegin().getText();
            lastGoodEnd = lines.offset(begin.line, begin.column, source.length()) + lastGoodText.length();
        }

        int problemStart = skipWhitespaceAndComments(source, lastGoodEnd);
        int problemLine = lines.lineOf(Math.min(problemStart, Math.max(0, source.length() - 1))) + 1; // 1-based like the parser's
        String expected = expectedList(message);

        if (atEndOfFile) {
            int end = source.length();
            int start = Math.max(0, end - 1);
            return new Diagnostic(Severity.ERROR, start, Math.max(start + 1, end), "Reached the end of the file while parsing: a '}' or ')' is probably missing", message.length() > 300 ? "Unexpected end of file. Expected " + expected : message);
        }

        boolean endsAStatementOrBlock = lastGoodText.equals("}") || lastGoodText.equals("{") || lastGoodText.equals(";");
        if (expected.contains("\";\"") && !endsAStatementOrBlock && problemLine > lastGoodLine && lastGoodLine > 0 && lastGoodEnd > 0) {
            int end = lastGoodEnd;
            return new Diagnostic(Severity.ERROR, Math.max(0, end - 1), end, "Missing ';'", "A ';' is expected at the end of this statement.");
        }

        String shown = foundToken.substring(1, foundToken.length() - 1);
        int length = Math.max(1, shown.length());
        int end = Math.min(source.length(), problemStart + length);
        int start = Math.min(problemStart, Math.max(0, source.length() - 1));
        if (end <= start) end = Math.min(source.length(), start + 1);
        return new Diagnostic(Severity.ERROR, start, end, "Unexpected '" + shown + "'",
                "Unexpected '" + shown + "'. Expected: " + expected);
    }

    /** The first few alternatives the parser would have accepted, as a short sentence part. */
    private static String expectedList(String message) {
        Matcher m = EXPECTED_TOKENS.matcher(message);
        if (!m.find()) return "";
        String all = m.group(1).trim();
        if (all.length() < 200) return all;
        List<String> tokens = new ArrayList<>();
        Matcher token = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"").matcher(all);
        while (token.find() && tokens.size() < MAX_EXPECTED_SHOWN) tokens.add(token.group());
        return String.join(" ", tokens) + " …";
    }

    private static int skipWhitespaceAndComments(String source, int from) {
        int i = from;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (source.startsWith("//", i)) {
                int newline = source.indexOf('\n', i);
                i = newline < 0 ? source.length() : newline + 1;
            } else if (source.startsWith("/*", i)) {
                int close = source.indexOf("*/", i + 2);
                i = close < 0 ? source.length() : close + 2;
            } else {
                break;
            }
        }
        return i;
    }

    // ---- imports

    private static void checkImports(String source, LineIndex lines, CompilationUnit unit, ClassIndex index, List<Diagnostic> out) {
        List<ImportDeclaration> imports = unit.getImports();
        char[] withoutImports = source.toCharArray();
        for (ImportDeclaration declaration : imports) {
            int[] span = span(declaration, lines, source.length());
            if (span == null) continue;
            for (int i = span[0]; i < span[1]; i++) if (withoutImports[i] != '\n') withoutImports[i] = ' ';
        }
        String rest = new String(withoutImports);

        for (ImportDeclaration declaration : imports) {
            if (declaration.isStatic() || declaration.isAsterisk()) continue;
            int[] span = span(declaration, lines, source.length());
            if (span == null) continue;
            String name = declaration.getNameAsString();
            String simple = ClassIndex.simpleName(name);

            if (index.size() > 0 && isCheckedRoot(name) && !index.contains(name)
                    && !index.contains(ClassIndex.packageOf(name)) && index.hasPackage(ClassIndex.packageOf(name))) {
                out.add(new Diagnostic(Severity.WARNING, span[0], span[1], "Can't find class '" + simple + "' in package '" + ClassIndex.packageOf(name) + "'",
                        "The import " + name + " doesn't match any class of the Android SDK."));
            } else if (!Pattern.compile("(?<![\\w$])" + Pattern.quote(simple) + "(?![\\w$])").matcher(rest).find()) {
                out.add(new Diagnostic(Severity.WARNING, span[0], span[1], "Unused import '" + simple + "'", "The import " + name + " is never used in this file."));
            }
        }
    }

    private static boolean isCheckedRoot(String qualifiedName) {
        for (String root : CHECKED_IMPORT_ROOTS) if (qualifiedName.startsWith(root)) return true;
        return false;
    }

    private static int[] span(ImportDeclaration declaration, LineIndex lines, int length) {
        if (declaration.getRange().isEmpty()) return null;
        var range = declaration.getRange().get();
        int start = lines.offset(range.begin.line, range.begin.column, length);
        int end = Math.min(length, lines.offset(range.end.line, range.end.column, length) + 1);
        return end > start ? new int[]{start, end} : null;
    }

    /** Converts the parser's 1-based line and column positions to offsets in the text. */
    private static final class LineIndex {
        private final List<Integer> starts = new ArrayList<>();

        LineIndex(String text) {
            starts.add(0);
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') starts.add(i + 1);
        }

        int offset(int line, int column, int textLength) {
            int index = Math.max(0, Math.min(line - 1, starts.size() - 1));
            return Math.min(textLength, starts.get(index) + Math.max(0, column - 1));
        }

        int lineStart(int zeroBasedLine) {
            return starts.get(zeroBasedLine);
        }

        int lineEnd(int zeroBasedLine, int textLength) {
            return zeroBasedLine + 1 < starts.size() ? starts.get(zeroBasedLine + 1) - 1 : textLength;
        }

        /** Zero-based line holding {@code offset}. */
        int lineOf(int offset) {
            int low = 0;
            int high = starts.size() - 1;
            while (low < high) {
                int mid = (low + high + 1) >>> 1;
                if (starts.get(mid) <= offset) low = mid;
                else high = mid - 1;
            }
            return low;
        }
    }
}
