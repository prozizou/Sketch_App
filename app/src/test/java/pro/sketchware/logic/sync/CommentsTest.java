package pro.sketchware.logic.sync;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class CommentsTest {
    private static List<String> found(String text) {
        return Comments.scan(text, 0, text.length()).stream().map(s -> text.substring(s.start(), s.end())).toList();
    }

    @Test
    public void findsLineAndBlockComments() {
        assertEquals(List.of("// one", "/* two */"), found("a(); // one\nb(); /* two */"));
    }

    @Test
    public void aCommentMarkerInsideAStringIsNotAComment() {
        assertEquals(List.of(), found("a(\"http://x\"); b('/');"));
        assertEquals(List.of("// real"), found("a(\"// no\"); // real"));
    }

    @Test
    public void anEscapedQuoteDoesNotEndTheString() {
        assertEquals(List.of(), found("a(\"say \\\"// hi\\\"\");"));
    }

    @Test
    public void anUnclosedBlockCommentRunsToTheEnd() {
        assertEquals(List.of("/* never closed"), found("a(); /* never closed"));
    }

    @Test
    public void countsOnlyInTheGivenRange() {
        String text = "// a\nfoo();\n// b";
        assertEquals(1, Comments.scan(text, 0, 5).size());
        assertEquals(2, Comments.count(text));
    }
}
