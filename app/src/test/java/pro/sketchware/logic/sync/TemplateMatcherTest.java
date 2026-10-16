package pro.sketchware.logic.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;

import org.junit.Test;

import java.util.List;

public class TemplateMatcherTest {
    private static List<Statement> statements(String code) {
        JavaBody body = JavaBody.parse(code);
        assertTrue(body.problems().toString(), body.isValid());
        return Ast.statements(body.block());
    }

    private static TemplateMatcher.Match match(String template, String code) {
        return TemplateMatcher.matchStatements(statements(template), statements(code));
    }

    @Test
    public void aParameterStandsForAnyExpression() {
        TemplateMatcher.Match match = match("SKP0.add(SKP1);", "items.add(a + b * 2);");
        assertNotNull(match);
        assertEquals("items", match.nameOf(0));
        assertEquals("a + b * 2", match.expressions.get(1).toString());
    }

    @Test
    public void aParameterInAStringStandsForTheWholeText() {
        TemplateMatcher.Match match = match("show(\"SKP0\");", "show(\"hello\");");
        assertNotNull(match);
        assertEquals("\"hello\"", match.expressions.get(0).toString());
    }

    @Test
    public void aParameterThatOnlyStandsForANameDoesNotMatchAnythingElse() {
        assertNull(match("Calendar.SKP0.toString();", "Calendar.YEAR.toString();") == null ? "no" : null);
        TemplateMatcher.Match match = match("x.set(Calendar.SKP0, 1);", "x.set(Calendar.YEAR, 1);");
        assertNotNull(match);
        assertEquals("YEAR", match.names.get(0));
        assertNull(match("x.set(Calendar.SKP0, 1);", "x.set(5, 1);"));
    }

    @Test
    public void aParameterInsideAnIdentifierStandsForARunOfName() {
        TemplateMatcher.Match match = match("SKP0.send(_SKP0_listener);", "bt.send(_bt_listener);");
        assertNotNull(match);
        assertEquals("bt", match.nameOf(0));
        assertNull("the same parameter must be the same name both times", match("SKP0.send(_SKP0_listener);", "bt.send(_other_listener);"));
    }

    @Test
    public void theCaseOfANameInsideAnIdentifierMayDiffer() {
        TemplateMatcher.Match match = match("start(SKP0, REQ_SKP0);", "start(camera1, REQ_CAMERA1);");
        assertNotNull(match);
        assertEquals("the name as written as a whole expression wins", "camera1", match.nameOf(0));
    }

    @Test
    public void theSameParameterMustBeTheSameExpression() {
        assertNotNull(match("SKP0 = SKP0 + 1;", "x = x + 1;"));
        assertNull(match("SKP0 = SKP0 + 1;", "x = y + 1;"));
    }

    @Test
    public void aStackStandsForTheStatementsInsideABlock() {
        TemplateMatcher.Match match = match("if (SKP0) {\nSKSUB1;\n}", "if (ready) {\n  a();\n  b();\n}");
        assertNotNull(match);
        assertEquals(2, match.stacks.get(1).size());
        assertEquals("ready", match.nameOf(0));
    }

    @Test
    public void anEmptyStackMatchesAnEmptyBlock() {
        TemplateMatcher.Match match = match("if (SKP0) {\nSKSUB1;\n}", "if (ready) { }");
        assertNotNull(match);
        assertTrue(match.stacks.get(1).isEmpty());
    }

    @Test
    public void statementsAroundAStackAreMatchedToo() {
        TemplateMatcher.Match match = match("{\nbefore();\nSKSUB1;\nafter();\n}", "{\n  before();\n  mid();\n  after();\n}");
        assertNotNull(match);
        assertEquals(1, match.stacks.get(1).size());
        assertNull(match("{\nbefore();\nSKSUB1;\nafter();\n}", "{\n  other();\n  mid();\n  after();\n}"));
    }

    @Test
    public void ifWithAnElseIsNotAnIfWithout() {
        assertNull(match("if (SKP0) {\nSKSUB1;\n}", "if (a) { x(); } else { y(); }"));
        assertNotNull(match("if (SKP0) {\nSKSUB1;\n} else {\nSKSUB2;\n}", "if (a) { x(); } else { y(); }"));
    }

    @Test
    public void aMoreSpecificTemplateFixesMoreOfTheCode() {
        TemplateMatcher.Match generic = match("SKP0.put(SKP1, SKP2);", "m.put(\"k\", 1);");
        TemplateMatcher.Match specific = match("m.put(\"k\", SKP0);", "m.put(\"k\", 1);");
        assertTrue(specific.specificity > 0 && generic.specificity > 0);
        assertTrue(TemplateMatcher.fixedNodes(statements("m.put(\"k\", SKP0);").get(0)) > TemplateMatcher.fixedNodes(statements("SKP0.put(SKP1, SKP2);").get(0)));
    }

    @Test
    public void differentFixedCodeDoesNotMatch() {
        assertNull(match("SKP0.add(SKP1);", "items.remove(a);"));
        assertNull(match("SKP0.add(SKP1);", "items.add(a, b);"));
        assertNull(match("foo();\nbar();", "foo();"));
    }

    @Test
    public void expressionsAreMatchedLikeStatements() {
        Expression template = statements("x = SKP0.length();").get(0).asExpressionStmt().getExpression().asAssignExpr().getValue();
        Expression code = statements("x = name.length();").get(0).asExpressionStmt().getExpression().asAssignExpr().getValue();
        TemplateMatcher.Match match = TemplateMatcher.match(template, code);
        assertNotNull(match);
        assertEquals("name", match.nameOf(0));
    }
}
