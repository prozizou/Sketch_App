package pro.sketchware.logic.sync;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.metamodel.PropertyMetaModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Tools on syntax trees: a normal form in which code that means the same is written the same way, and equality of two
 * trees in that form. Comments, positions and the shape of braces and parentheses don't count.
 */
public final class Ast {
    private static final Pattern REPEAT_VARIABLE = Pattern.compile("_repeat\\d+");

    private Ast() {
    }

    /**
     * A copy of {@code node} in normal form: parentheses are dropped (the tree already says what is grouped), the body
     * of an {@code if}, {@code while}, {@code for} or {@code do} is always a block, {@code else if} is an {@code else}
     * holding an {@code if}, a floating-point literal has no {@code d} suffix, and the counter of a "repeat" loop,
     * whose name carries the block's id, is called {@code _repeat#}.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Node> T normalized(T node) {
        Node copy = node.clone();
        stripComments(copy);
        if (copy instanceof EnclosedExpr enclosed) copy = enclosed.getInner().clone();
        peel(copy);
        blocks(copy);
        literals(copy);
        for (SimpleName name : copy.findAll(SimpleName.class)) {
            if (REPEAT_VARIABLE.matcher(name.getIdentifier()).matches()) name.setIdentifier("_repeat#");
        }
        if (copy instanceof SimpleName name && REPEAT_VARIABLE.matcher(name.getIdentifier()).matches()) name.setIdentifier("_repeat#");
        return (T) copy;
    }

    private static void stripComments(Node root) {
        for (Node node : root.findAll(Node.class)) {
            node.removeComment();
            new ArrayList<>(node.getOrphanComments()).forEach(Node::remove);
        }
        root.removeComment();
        new ArrayList<>(root.getOrphanComments()).forEach(Node::remove);
    }

    private static void peel(Node root) {
        Optional<EnclosedExpr> found;
        while ((found = root.findFirst(EnclosedExpr.class)).isPresent()) {
            EnclosedExpr enclosed = found.get();
            enclosed.replace(enclosed.getInner().clone());
        }
    }

    private static void blocks(Node root) {
        for (IfStmt statement : root.findAll(IfStmt.class)) {
            statement.setThenStmt(asBlock(statement.getThenStmt()));
            statement.getElseStmt().ifPresent(other -> statement.setElseStmt(asBlock(other)));
        }
        for (WhileStmt statement : root.findAll(WhileStmt.class)) statement.setBody(asBlock(statement.getBody()));
        for (ForStmt statement : root.findAll(ForStmt.class)) statement.setBody(asBlock(statement.getBody()));
        for (ForEachStmt statement : root.findAll(ForEachStmt.class)) statement.setBody(asBlock(statement.getBody()));
        for (DoStmt statement : root.findAll(DoStmt.class)) statement.setBody(asBlock(statement.getBody()));
    }

    private static Statement asBlock(Statement statement) {
        if (statement instanceof BlockStmt) return statement;
        BlockStmt block = new BlockStmt();
        block.addStatement(statement.clone());
        return block;
    }

    private static void literals(Node root) {
        for (DoubleLiteralExpr literal : root.findAll(DoubleLiteralExpr.class)) {
            String value = literal.getValue();
            if (value.endsWith("d") || value.endsWith("D")) literal.setValue(value.substring(0, value.length() - 1));
        }
    }

    /** Whether two trees that are already in normal form are the same code. */
    public static boolean same(Node a, Node b) {
        if (a == null || b == null) return a == b;
        if (a.getClass() != b.getClass()) return false;
        for (PropertyMetaModel property : a.getMetaModel().getAllPropertyMetaModels()) {
            if (property.getName().equals("comment")) continue;
            if (!sameValue(property.getValue(a), property.getValue(b))) return false;
        }
        return true;
    }

    private static boolean sameValue(Object x, Object y) {
        if (x instanceof Optional<?> ox) {
            if (!(y instanceof Optional<?> oy)) return false;
            if (ox.isPresent() != oy.isPresent()) return false;
            return ox.isEmpty() || sameValue(ox.get(), oy.get());
        }
        if (x instanceof NodeList<?> lx) {
            if (!(y instanceof NodeList<?> ly) || lx.size() != ly.size()) return false;
            for (int i = 0; i < lx.size(); i++) if (!same(lx.get(i), ly.get(i))) return false;
            return true;
        }
        if (x instanceof Node nx) return y instanceof Node ny && same(nx, ny);
        return Objects.equals(x, y);
    }

    /** The statements of a block in normal form. */
    public static List<Statement> statements(BlockStmt block) {
        List<Statement> list = new ArrayList<>();
        for (Statement statement : block.getStatements()) list.add(normalized(statement));
        return list;
    }

    /** The name an expression is made of when it is a plain name, else null. */
    public static String plainName(Expression expression) {
        return expression instanceof NameExpr name ? name.getNameAsString() : null;
    }
}
