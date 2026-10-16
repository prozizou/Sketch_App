package pro.sketchware.logic.sync;

import androidx.annotation.Nullable;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.List;

/** The Java one kind of block makes, as trees with sentinels where parameters and stacks go. */
final class Template {
    final BlockDef def;
    /** The statements of a statement block, in normal form. */
    final List<Statement> statements;
    /** The expression of a reporter block, in normal form; null for a statement block. */
    @Nullable
    final Expression expression;
    /** How many nodes it fixes: a bigger template says more about the code it matches. */
    final int size;
    final String key;

    Template(BlockDef def, List<Statement> statements, @Nullable Expression expression) {
        this.def = def;
        this.statements = statements;
        this.expression = expression;
        int count = 0;
        for (Statement statement : statements) count += TemplateMatcher.fixedNodes(statement);
        if (expression != null) count += TemplateMatcher.fixedNodes(expression);
        this.size = count;
        this.key = expression != null ? key(expression) : statements.isEmpty() ? "" : key(statements.get(0));
    }

    /** A quick test to rule a template out: the kind of the first statement, and for a call the name called. */
    static String key(Statement statement) {
        if (statement instanceof ExpressionStmt expression) return "S:" + key(expression.getExpression());
        return "S:" + statement.getClass().getSimpleName();
    }

    static String key(Expression expression) {
        if (expression instanceof MethodCallExpr call) {
            String name = call.getNameAsString();
            return "E:MethodCallExpr:" + (TemplateMatcher.hasParam(name) ? "*" : name);
        }
        return "E:" + expression.getClass().getSimpleName();
    }

    /** Whether code with {@code actualKey} can match this template. */
    boolean mayMatch(String actualKey) {
        if (key.equals(actualKey)) return true;
        return key.endsWith(":*") && actualKey.startsWith(key.substring(0, key.length() - 1));
    }
}
