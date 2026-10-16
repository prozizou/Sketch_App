package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import mod.jbk.code.completion.ClassIndex;
import mod.jbk.code.diagnostics.JavaDiagnostics;

/**
 * The statements of an event as the Java panel shows them, parsed into a syntax tree. The text is the body of a
 * method, so it is placed inside a method for the parser, and every position the parser gives is moved back to the
 * person's text.
 */
public final class JavaBody {
    private static final String PREFIX = "class SketchBlocks {\nvoid sketchEvent() {\n";
    private static final String SUFFIX = "\n}\n}\n";

    /**
     * One thing wrong with the text.
     *
     * @param line    1-based line in the person's text
     * @param start   offset of the first character to mark
     * @param end     offset after the last character, at least {@code start + 1} when the text is not empty
     * @param message one line saying what is wrong
     */
    public record Problem(int line, int start, int end, @NonNull String message) {
    }

    private final String text;
    @Nullable
    private final BlockStmt block;
    private final List<Problem> problems;
    private final int[] lineStarts;

    private JavaBody(String text, @Nullable BlockStmt block, List<Problem> problems) {
        this.text = text;
        this.block = block;
        this.problems = List.copyOf(problems);
        this.lineStarts = lineStarts(text);
    }

    /** Parses {@code text}; it never throws, a text that is not valid Java gives {@link #problems()}. */
    public static JavaBody parse(String text) {
        String wrapped = PREFIX + text + SUFFIX;
        List<Problem> problems = new ArrayList<>();
        ParseResult<CompilationUnit> result;
        try {
            result = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)).parse(wrapped);
        } catch (RuntimeException | StackOverflowError e) {
            problems.add(problem(text, 0, Math.min(1, text.length()), "The code is too deeply nested to read"));
            return new JavaBody(text, null, problems);
        }
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            for (JavaDiagnostics.Diagnostic diagnostic : JavaDiagnostics.analyze(wrapped, ClassIndex.empty())) {
                if (diagnostic.severity() != JavaDiagnostics.Severity.ERROR) continue;
                int start = clamp(diagnostic.start() - PREFIX.length(), text.length());
                int end = Math.max(start, clamp(diagnostic.end() - PREFIX.length(), text.length()));
                problems.add(problem(text, start, end, diagnostic.message()));
            }
            if (problems.isEmpty()) {
                // The parser refused it without a position we can use
                String reason = result.getProblems().isEmpty() ? "The code isn't valid Java" : firstLine(result.getProblems().get(0).getMessage());
                problems.add(problem(text, 0, Math.min(1, text.length()), reason));
            }
            return new JavaBody(text, null, problems);
        }
        CompilationUnit unit = result.getResult().get();
        BlockStmt body = singleBody(unit);
        if (body == null) {
            // Extra closing and opening braces turned the text into more than one method
            problems.add(problem(text, Math.max(0, text.length() - 1), text.length(), "The braces don't match: a '}' closes the event too early"));
            return new JavaBody(text, null, problems);
        }
        return new JavaBody(text, body, problems);
    }

    @Nullable
    private static BlockStmt singleBody(CompilationUnit unit) {
        if (unit.getTypes().size() != 1) return null;
        TypeDeclaration<?> type = unit.getType(0);
        if (type.getMembers().size() != 1) return null;
        BodyDeclaration<?> member = type.getMember(0);
        if (!(member instanceof MethodDeclaration method)) return null;
        Optional<BlockStmt> body = method.getBody();
        return body.orElse(null);
    }

    private static Problem problem(String text, int start, int end, String message) {
        int[] starts = lineStarts(text);
        int line = 1;
        for (int i = 0; i < starts.length; i++) if (starts[i] <= start) line = i + 1;
        return new Problem(line, start, end, message);
    }

    private static int clamp(int offset, int length) {
        return Math.max(0, Math.min(offset, length));
    }

    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    public boolean isValid() {
        return block != null;
    }

    public List<Problem> problems() {
        return problems;
    }

    public String text() {
        return text;
    }

    /** The statements, null when the text is not valid. Positions in it are those of the wrapped text: use {@link #offsetOf}. */
    @Nullable
    public BlockStmt block() {
        return block;
    }

    /** Offset in the person's text of a position the parser gave (1-based line and column of the wrapped text). */
    public int offsetOf(int wrappedLine, int wrappedColumn) {
        int line = wrappedLine - 2; // the wrapper adds two lines before the text
        if (line < 1) return 0;
        if (line > lineStarts.length) return text.length(); // in the wrapper, after the text
        int index = line - 1;
        return Math.min(text.length(), lineStarts[index] + Math.max(0, wrappedColumn - 1));
    }

    /** 1-based line in the person's text of a parser line. */
    public static int lineOf(int wrappedLine) {
        return Math.max(1, wrappedLine - 2);
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') starts.add(i + 1);
        int[] array = new int[starts.size()];
        for (int i = 0; i < array.length; i++) array[i] = starts.get(i);
        return array;
    }
}
