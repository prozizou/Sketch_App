package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.metamodel.PropertyMetaModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches a template (the Java a block makes, with sentinels where its parameters and inner stacks go) against code.
 * A sentinel stands for any expression ({@code SKP0}), a name inside an identifier ({@code _SKP0_listener}), or a list
 * of statements ({@code SKSUB1;}). Both trees must be in the normal form of {@link Ast}.
 */
final class TemplateMatcher {
    static final String PARAM = "SKP";
    static final String STACK = "SKSUB";
    private static final Pattern PARAM_NAME = Pattern.compile("skp(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STACK_NAME = Pattern.compile("sksub(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAM_IN_TEXT = Pattern.compile("skp(\\d+)", Pattern.CASE_INSENSITIVE);

    /** What a successful match bound. */
    static final class Match {
        /** Parameters bound to a whole expression. */
        final Map<Integer, Expression> expressions = new HashMap<>();
        /** Parameters bound to a name or a part of one. */
        final Map<Integer, String> names = new HashMap<>();
        /** Inner stacks bound to the statements at that place. */
        final Map<Integer, List<Statement>> stacks = new HashMap<>();
        /** Where each stack is: the block that holds it and the range of its statements in that block. */
        final Map<Integer, StackPlace> places = new HashMap<>();
        /** How many fixed nodes matched: a template that fixes more of the code is more specific. */
        int specificity;

        /** The text a parameter stands for, when it is a plain name or part of one. */
        @Nullable
        String nameOf(int index) {
            // a whole expression says the name as it was written; a part of an identifier may have another case
            Expression expression = expressions.get(index);
            String plain = expression == null ? null : Ast.plainName(expression);
            return plain != null ? plain : names.get(index);
        }
    }

    /** The block a stack's statements are in, and which of the block's statements ({@code from} up to {@code to}) they are. */
    record StackPlace(@Nullable BlockStmt parent, int from, int to) {
    }

    private final Match result = new Match();

    private TemplateMatcher() {
    }

    @Nullable
    static Match match(Node template, Node actual) {
        TemplateMatcher matcher = new TemplateMatcher();
        return matcher.node(template, actual) ? matcher.result : null;
    }

    /** Matches a list of template statements against the code's statements, from the start to the end of both. */
    @Nullable
    static Match matchStatements(List<Statement> template, List<Statement> actual) {
        TemplateMatcher matcher = new TemplateMatcher();
        NodeList<Node> t = new NodeList<>();
        NodeList<Node> a = new NodeList<>();
        t.addAll(template);
        a.addAll(actual);
        return matcher.list(t, a) ? matcher.result : null;
    }

    // ---- sentinels

    /** Whether a node of a template stands for something in the code instead of being fixed text. */
    static boolean isHole(Node node) {
        if (stackIndex(node) != null) return true;
        if (node instanceof NameExpr name) return PARAM_IN_TEXT.matcher(name.getNameAsString()).find();
        if (node instanceof StringLiteralExpr literal) return PARAM_IN_TEXT.matcher(literal.getValue()).find();
        if (node instanceof SimpleName name) return PARAM_IN_TEXT.matcher(name.getIdentifier()).find();
        if (node instanceof Name name) return name.getQualifier().isEmpty() && PARAM_IN_TEXT.matcher(name.getIdentifier()).find();
        return false;
    }

    /** How many nodes of a template are fixed text: the nodes under a sentinel don't count. */
    static int fixedNodes(Node node) {
        if (isHole(node)) return 0;
        int count = 1;
        for (Node child : node.getChildNodes()) count += fixedNodes(child);
        return count;
    }

    /** Whether a name or text holds a parameter sentinel. */
    static boolean hasParam(String text) {
        return PARAM_IN_TEXT.matcher(text).find();
    }

    static boolean isParamName(String text) {
        return PARAM_NAME.matcher(text).matches();
    }

    @Nullable
    private static Integer paramIndex(String text) {
        Matcher matcher = PARAM_NAME.matcher(text);
        return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
    }

    @Nullable
    private static Integer stackIndex(Node node) {
        if (node instanceof ExpressionStmt statement && statement.getExpression() instanceof NameExpr name) {
            Matcher matcher = STACK_NAME.matcher(name.getNameAsString());
            if (matcher.matches()) return Integer.valueOf(matcher.group(1));
        }
        return null;
    }

    // ---- matching

    private boolean node(Node t, Node a) {
        if (t instanceof NameExpr name) {
            Integer index = paramIndex(name.getNameAsString());
            if (index != null) return a instanceof Expression expression && bindExpression(index, expression);
            if (PARAM_IN_TEXT.matcher(name.getNameAsString()).find()) return a instanceof NameExpr other && partial(name.getNameAsString(), other.getNameAsString());
        }
        if (t instanceof StringLiteralExpr literal) {
            Integer index = paramIndex(literal.getValue());
            if (index != null) return a instanceof Expression expression && bindExpression(index, expression);
            if (PARAM_IN_TEXT.matcher(literal.getValue()).find()) return a instanceof StringLiteralExpr other && partial(literal.getValue(), other.getValue());
        }
        if (t instanceof SimpleName name) {
            Integer index = paramIndex(name.getIdentifier());
            if (index != null) return a instanceof SimpleName other && bindName(index, other.getIdentifier());
            if (PARAM_IN_TEXT.matcher(name.getIdentifier()).find()) return a instanceof SimpleName other && partial(name.getIdentifier(), other.getIdentifier());
        }
        if (t instanceof Name name && name.getQualifier().isEmpty()) {
            // the name in {@code Something.this} or in an annotation
            Integer index = paramIndex(name.getIdentifier());
            if (index != null) return a instanceof Name other && other.getQualifier().isEmpty() && bindName(index, other.getIdentifier());
            if (PARAM_IN_TEXT.matcher(name.getIdentifier()).find()) return a instanceof Name other && other.getQualifier().isEmpty() && partial(name.getIdentifier(), other.getIdentifier());
        }
        if (t.getClass() != a.getClass()) return false;
        result.specificity++;
        for (PropertyMetaModel property : t.getMetaModel().getAllPropertyMetaModels()) {
            if (property.getName().equals("comment")) continue;
            if (!value(property.getValue(t), property.getValue(a))) return false;
        }
        return true;
    }

    private boolean value(Object t, Object a) {
        if (t instanceof Optional<?> ot) {
            if (!(a instanceof Optional<?> oa) || ot.isPresent() != oa.isPresent()) return false;
            return ot.isEmpty() || value(ot.get(), oa.get());
        }
        if (t instanceof NodeList<?> lt) {
            if (!(a instanceof NodeList<?> la)) return false;
            @SuppressWarnings("unchecked") NodeList<Node> tl = (NodeList<Node>) lt;
            @SuppressWarnings("unchecked") NodeList<Node> al = (NodeList<Node>) la;
            return list(tl, al);
        }
        if (t instanceof Node nt) return a instanceof Node na && node(nt, na);
        return Objects.equals(t, a);
    }

    private boolean list(NodeList<Node> t, NodeList<Node> a) {
        int hole = -1;
        for (int i = 0; i < t.size(); i++) {
            if (stackIndex(t.get(i)) != null) {
                if (hole >= 0) return false; // two stacks in one list can't be told apart
                hole = i;
            }
        }
        if (hole < 0) {
            if (t.size() != a.size()) return false;
            for (int i = 0; i < t.size(); i++) if (!node(t.get(i), a.get(i))) return false;
            return true;
        }
        int after = t.size() - hole - 1;
        if (a.size() < hole + after) return false;
        for (int i = 0; i < hole; i++) if (!node(t.get(i), a.get(i))) return false;
        for (int i = 0; i < after; i++) if (!node(t.get(hole + 1 + i), a.get(a.size() - after + i))) return false;
        List<Statement> middle = new ArrayList<>();
        for (int i = hole; i < a.size() - after; i++) {
            if (!(a.get(i) instanceof Statement statement)) return false;
            middle.add(statement);
        }
        int index = stackIndex(t.get(hole));
        result.stacks.put(index, middle);
        BlockStmt parent = a.getParentNode().filter(BlockStmt.class::isInstance).map(BlockStmt.class::cast).orElse(null);
        result.places.put(index, new StackPlace(parent, hole, a.size() - after));
        return true;
    }

    private boolean bindExpression(int index, Expression expression) {
        Expression before = result.expressions.get(index);
        if (before != null) return Ast.same(before, expression);
        String name = result.names.get(index);
        // the generator may change the case of a name it writes into an identifier ({@code REQ_CD_INTENT})
        if (name != null && !name.equalsIgnoreCase(Ast.plainName(expression))) return false;
        result.expressions.put(index, expression);
        return true;
    }

    private boolean bindName(int index, String text) {
        String before = result.names.get(index);
        if (before != null) return before.equalsIgnoreCase(text);
        Expression expression = result.expressions.get(index);
        if (expression != null && !text.equalsIgnoreCase(Ast.plainName(expression))) return false;
        result.names.put(index, text);
        return true;
    }

    /** A template text like {@code _SKP0_listener} against a real one: the sentinel stands for a run of name characters. */
    private boolean partial(String template, String actual) {
        StringBuilder regex = new StringBuilder();
        List<Integer> order = new ArrayList<>();
        Matcher matcher = PARAM_IN_TEXT.matcher(template);
        int last = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(template.substring(last, matcher.start())));
            regex.append("([A-Za-z0-9_]+?)");
            order.add(Integer.parseInt(matcher.group(1)));
            last = matcher.end();
        }
        regex.append(Pattern.quote(template.substring(last)));
        Matcher found = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE).matcher(actual);
        if (!found.matches()) return false;
        result.specificity++;
        for (int i = 0; i < order.size(); i++) if (!bindName(order.get(i), found.group(i + 1))) return false;
        return true;
    }

    /** Statements a stack hole stands for must not be empty-handed: an empty stack matches an empty list. */
    @NonNull
    static String describe(Match match) {
        return "expressions=" + match.expressions.keySet() + " names=" + match.names.keySet() + " stacks=" + match.stacks.keySet();
    }
}
