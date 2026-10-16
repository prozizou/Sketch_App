package pro.sketchware.logic.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JavaBodyTest {
    @Test
    public void validStatementsAreParsed() {
        JavaBody body = JavaBody.parse("int a = 1;\nfoo(a);");
        assertTrue(body.problems().toString(), body.isValid());
        assertEquals(2, body.block().getStatements().size());
    }

    @Test
    public void anEmptyBodyIsValid() {
        assertTrue(JavaBody.parse("").isValid());
        assertTrue(JavaBody.parse("  \n // only a comment\n").isValid());
    }

    @Test
    public void aMissingSemicolonIsReportedOnTheLineItIsOn() {
        JavaBody body = JavaBody.parse("foo();\nbar()\nbaz();");
        assertFalse(body.isValid());
        JavaBody.Problem problem = body.problems().get(0);
        assertEquals("the person's line, not the wrapper's", 2, problem.line());
        assertTrue(problem.message(), problem.message().contains(";"));
        assertTrue(problem.start() >= "foo();\n".length() && problem.end() <= "foo();\nbar()".length() + 1);
    }

    @Test
    public void anUnclosedStringIsReported() {
        JavaBody body = JavaBody.parse("foo(\"abc);\nbar();");
        assertFalse(body.isValid());
        assertEquals(1, body.problems().get(0).line());
    }

    @Test
    public void aMissingClosingBraceIsReported() {
        JavaBody body = JavaBody.parse("if (a) {\n  foo();\n");
        assertFalse(body.isValid());
        assertFalse(body.problems().isEmpty());
        assertTrue(body.problems().get(0).end() <= "if (a) {\n  foo();\n".length());
    }

    @Test
    public void anExtraClosingBraceCannotEscapeTheEvent() {
        // Without a check, "} void other() {" would turn into a second method of the wrapper
        JavaBody body = JavaBody.parse("foo();\n}\nvoid other() {\nbar();");
        assertFalse(body.isValid());
        assertFalse(body.problems().isEmpty());
    }

    @Test
    public void offsetsAreInThePersonsText() {
        String text = "foo();\n  bar();";
        JavaBody body = JavaBody.parse(text);
        com.github.javaparser.ast.stmt.Statement second = body.block().getStatement(1);
        int start = body.offsetOf(second.getRange().get().begin.line, second.getRange().get().begin.column);
        assertEquals("bar();", text.substring(start, start + 6));
        assertEquals(2, JavaBody.lineOf(second.getRange().get().begin.line));
    }

    @Test
    public void everyProblemOffsetStaysInsideTheText() {
        for (String text : new String[]{"", "(", "}", "foo(", "\"", "a b c", "if", "for (;;"}) {
            JavaBody body = JavaBody.parse(text);
            if (body.isValid()) continue;
            for (JavaBody.Problem problem : body.problems()) {
                assertTrue(text + " -> " + problem, problem.start() >= 0 && problem.end() >= problem.start() && problem.end() <= Math.max(text.length(), 1));
            }
        }
    }
}
