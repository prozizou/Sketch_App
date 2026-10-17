package pro.sketchware.evolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import pro.sketchware.evolve.CodeHighlighter.Language;
import pro.sketchware.evolve.CodeHighlighter.Span;
import pro.sketchware.evolve.CodeHighlighter.Token;

public class CodeHighlighterTest {
    private static String part(String text, Span span) {
        return text.substring(span.start(), span.end());
    }

    private static String describe(Language language, String text) {
        StringBuilder out = new StringBuilder();
        for (Span span : CodeHighlighter.highlight(language, text, 100_000)) {
            out.append(span.token()).append('[').append(part(text, span)).append("] ");
        }
        return out.toString().trim();
    }

    @Test
    public void javaKeywordsTypesStringsAndNumbers() {
        assertEquals("KEYWORD[public] KEYWORD[class] TYPE[Foo] KEYWORD[int] NUMBER[42] TYPE[String] STRING[\"a\\\"b\"]",
                describe(Language.JAVA, "public class Foo { int x = 42; String s = \"a\\\"b\"; }"));
    }

    @Test
    public void javaCommentsAndAnnotations() {
        String code = "// line\n@Override /* block */ void f() {}";
        assertEquals("COMMENT[// line] ANNOTATION[@Override] COMMENT[/* block */] KEYWORD[void]", describe(Language.JAVA, code));
    }

    @Test
    public void anUnclosedBlockCommentRunsToTheEnd() {
        assertEquals("COMMENT[/* never closed]", describe(Language.JAVA, "/* never closed"));
    }

    @Test
    public void upperCaseConstantsAreNotTypes() {
        assertEquals("", describe(Language.JAVA, "MAX_SIZE"));
        assertEquals("TYPE[String]", describe(Language.JAVA, "String"));
    }

    @Test
    public void kotlinRawStringsAndKeywords() {
        assertEquals("KEYWORD[fun] STRING[\"\"\"raw \" text\"\"\"]", describe(Language.KOTLIN, "fun main() = \"\"\"raw \" text\"\"\""));
    }

    @Test
    public void xmlTagsAttributesAndValues() {
        String xml = "<LinearLayout android:id=\"@+id/a\">\n  <!-- hi -->\n  <TextView/>\n</LinearLayout>";
        assertEquals("TAG[LinearLayout] ATTRIBUTE[android:id] STRING[\"@+id/a\"] COMMENT[<!-- hi -->] TAG[TextView] TAG[LinearLayout]",
                describe(Language.XML, xml));
    }

    @Test
    public void jsonKeysAreAttributesAndValuesAreStringsNumbersAndKeywords() {
        assertEquals("ATTRIBUTE[\"name\"] STRING[\"x\"] ATTRIBUTE[\"n\"] NUMBER[-1.5] ATTRIBUTE[\"ok\"] KEYWORD[true]",
                describe(Language.JSON, "{\"name\": \"x\", \"n\": -1.5, \"ok\": true}"));
    }

    @Test
    public void spansNeverOverlapAndStayInsideTheText() {
        String[] samples = {"", "\"", "'", "/*", "<", "<a", "<a b=\"", "{\"", "@", "0x", "1e", "\"\"\"", "a\\", "\\", "<!--", "</", "<?xml version=\"1.0\"?>"};
        for (Language language : Language.values()) {
            for (String sample : samples) {
                int end = 0;
                for (Span span : CodeHighlighter.highlight(language, sample, 1000)) {
                    assertTrue(language + " " + sample, span.start() >= end);
                    assertTrue(language + " " + sample, span.end() > span.start() && span.end() <= sample.length());
                    end = span.end();
                }
            }
        }
    }

    @Test
    public void onlyTheStartIsScanned() {
        String text = "int a;" + " ".repeat(100) + "int b;";
        List<Span> spans = CodeHighlighter.highlight(Language.JAVA, text, 20);
        assertEquals(1, spans.size());
        assertEquals(Token.KEYWORD, spans.get(0).token());
    }

    @Test
    public void plainAndEmptyTextHaveNoSpans() {
        assertTrue(CodeHighlighter.highlight(Language.PLAIN, "public class", 100).isEmpty());
        assertTrue(CodeHighlighter.highlight(Language.JAVA, "", 100).isEmpty());
        assertTrue(CodeHighlighter.highlight(Language.JAVA, null, 100).isEmpty());
    }

    @Test
    public void everyKindHasALanguage() {
        for (SharedKind kind : SharedKind.values()) {
            assertTrue(kind.name(), CodeHighlighter.languageOf(kind) != null);
        }
        assertEquals(Language.XML, CodeHighlighter.languageOf(SharedKind.VIEW));
        assertEquals(Language.KOTLIN, CodeHighlighter.languageOf(SharedKind.KOTLIN));
    }
}
