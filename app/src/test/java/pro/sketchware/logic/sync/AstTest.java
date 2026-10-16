package pro.sketchware.logic.sync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.github.javaparser.ast.stmt.Statement;

import org.junit.Test;

import java.util.List;

public class AstTest {
    private static List<Statement> normal(String code) {
        JavaBody body = JavaBody.parse(code);
        assertTrue(body.problems().toString(), body.isValid());
        return Ast.statements(body.block());
    }

    private static boolean same(String a, String b) {
        List<Statement> x = normal(a);
        List<Statement> y = normal(b);
        if (x.size() != y.size()) return false;
        for (int i = 0; i < x.size(); i++) if (!Ast.same(x.get(i), y.get(i))) return false;
        return true;
    }

    @Test
    public void whitespaceAndCommentsDoNotCount() {
        assertTrue(same("foo( 1,2 );", "foo(1,\n   2);  // note"));
        assertTrue(same("/* a */ foo();", "foo();"));
    }

    @Test
    public void redundantParenthesesDoNotCount() {
        assertTrue(same("x = (a + b);", "x = a + b;"));
        assertTrue(same("x = (int)(y);", "x = (int) y;"));
    }

    @Test
    public void parenthesesThatGroupDifferentlyDo() {
        assertFalse(same("x = (a + b) * c;", "x = a + b * c;"));
        assertFalse(same("x = a - (b - c);", "x = a - b - c;"));
    }

    @Test
    public void bracesAroundASingleStatementDoNotCount() {
        assertTrue(same("if (a) foo();", "if (a) { foo(); }"));
        assertTrue(same("while (a) foo();", "while (a) {\n foo();\n}"));
        assertTrue(same("for (int i = 0; i < 3; i++) foo();", "for (int i = 0; i < 3; i++) { foo(); }"));
    }

    @Test
    public void elseIfIsAnElseHoldingAnIf() {
        assertTrue(same("if (a) { x(); } else if (b) { y(); } else { z(); }", "if (a) { x(); } else { if (b) { y(); } else { z(); } }"));
        assertFalse(same("if (a) { x(); } else if (b) { y(); }", "if (a) { x(); } else { y(); }"));
    }

    @Test
    public void aDoubleLiteralHasTheSameValueWithOrWithoutItsSuffix() {
        assertTrue(same("x = 1.5d;", "x = 1.5;"));
        assertFalse(same("x = 1.5;", "x = 2.5;"));
    }

    @Test
    public void theCounterOfARepeatLoopIsNotCompared() {
        assertTrue(same("for (int _repeat7 = 0; _repeat7 < (int)(5); _repeat7++) { foo(); }",
                "for (int _repeat12 = 0; _repeat12 < (int)(5); _repeat12++) { foo(); }"));
    }

    @Test
    public void differentCodeIsDifferent() {
        assertFalse(same("foo(1);", "foo(2);"));
        assertFalse(same("foo(1);", "bar(1);"));
        assertFalse(same("foo();", "foo();\nfoo();"));
        assertFalse(same("a = \"x\";", "a = \"y\";"));
    }

    @Test
    public void normalisingDoesNotChangeTheTreeItIsGiven() {
        JavaBody body = JavaBody.parse("x = (a + b);");
        String before = body.block().getStatement(0).toString();
        Ast.normalized(body.block().getStatement(0));
        org.junit.Assert.assertEquals(before, body.block().getStatement(0).toString());
    }
}
