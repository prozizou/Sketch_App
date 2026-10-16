package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.BlockBean;
import com.github.javaparser.Range;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import a.a.a.jq;
import mod.pranav.viewbinding.ViewBindingBuilder;
import pro.sketchware.logic.sync.TemplateMatcher.Match;

/**
 * Turns the Java of an event back into blocks. The Java is parsed into a syntax tree; each statement is matched against
 * the templates of the editor's own generator, so a statement becomes the block that generates it. Whatever is not a
 * known block (a local variable, a loop of another kind, an unknown call) is kept as a "source" block holding its text,
 * so no code is ever lost.
 * <p>
 * Every block made is checked by generating its Java again and comparing it with the code it came from; a block that
 * doesn't give back the same code is replaced by a source block. So the blocks always generate the Java that was
 * written.
 */
public final class JavaToBlocks {
    /** Writes the Java of blocks the way the panel shows it. */
    public interface CodeGenerator {
        String generate(ArrayList<BlockBean> blocks);
    }

    /** A piece of code that is kept as text because it is not a known block. */
    public record SourceBlock(int line, @NonNull String reason, @NonNull String code) {
    }

    /**
     * @param ok            false when the Java has syntax errors; nothing is converted then
     * @param problems      the syntax errors
     * @param blocks        the blocks, the first statement first (empty when not ok)
     * @param sourceBlocks  code kept as text
     * @param blockCount    how many blocks the event has
     */
    public record Result(boolean ok, @NonNull List<JavaBody.Problem> problems, @NonNull ArrayList<BlockBean> blocks,
                         @NonNull List<SourceBlock> sourceBlocks, int blockCount) {
    }

    private static final String REASON_STATEMENT = "Not a block of the palette";
    private static final String REASON_COMMENT = "A comment";
    private static final String REASON_EXPRESSION = "Not a block of the palette (an expression)";
    private static final String REASON_MISMATCH = "Would not give back the same code as a block";

    private final ProjectContext context;
    private final TemplateSet templates;
    private final CodeGenerator generator;
    private final Map<String, String> viewBindingNames = new HashMap<>();
    /** The text each block shows, by opcode. */
    private final Map<String, String> specs;

    private String specOf(BlockDef def) {
        String spec = specs.get(def.opCode());
        return spec == null ? def.spec() : spec;
    }

    /** Builds the templates, which takes a moment: do it off the main thread and keep the converter. */
    public static JavaToBlocks create(BlockCatalog catalog, ProjectContext context, jq buildConfig, CodeGenerator generator,
                                      BlockSpecs.Strings strings) {
        Map<String, String> specs = new HashMap<>();
        for (BlockDef def : catalog.all()) specs.put(def.opCode(), BlockSpecs.specOf(def, strings));
        return new JavaToBlocks(context, TemplateSet.build(catalog, context, buildConfig, def -> specs.get(def.opCode())), generator, specs);
    }

    private JavaToBlocks(ProjectContext context, TemplateSet templates, CodeGenerator generator, Map<String, String> specs) {
        this.context = context;
        this.templates = templates;
        this.generator = generator;
        this.specs = specs;
        if (context.viewBinding()) {
            for (String id : context.views()) viewBindingNames.put("binding." + ViewBindingBuilder.generateParameterFromId(id), id);
        }
    }

    /**
     * The same converter for a screen whose declarations (variables, lists, widgets, ...) are now {@code newContext}; the
     * templates, which are the slow part, are shared. The generated class and the kind of screen must be the same.
     */
    public JavaToBlocks withContext(ProjectContext newContext) {
        if (!newContext.activityName().equals(context.activityName()) || newContext.fragment() != context.fragment()) {
            throw new IllegalArgumentException("The templates were made for another class");
        }
        return new JavaToBlocks(newContext, templates, generator, specs);
    }

    /** How many templates were made, for tests and diagnostics. */
    int templateCount() {
        return templates.statements.size() + templates.reporters.size();
    }

    /**
     * @param text     the Java as the panel holds it
     * @param previous the event's blocks now, used to keep the id of a block that is still there; may be empty
     */
    public Result convert(String text, List<BlockBean> previous) {
        JavaBody body = JavaBody.parse(text);
        if (!body.isValid()) return new Result(false, body.problems(), new ArrayList<>(), List.of(), 0);
        Run run = new Run(body);
        BlockStmt block = body.block();
        List<Statement> statements = Ast.statements(block);
        List<BNode> roots = run.entries(block, statements, 0, statements.size());

        List<SourceBlock> sources = new ArrayList<>();
        ArrayList<BlockBean> beans = finish(roots, previous, body, sources);
        return new Result(true, List.of(), beans, sources, beans.size());
    }

    /** Assembles the blocks and checks the whole event once more; if it differs the event becomes one source block. */
    private ArrayList<BlockBean> finish(List<BNode> roots, List<BlockBean> previous, JavaBody body, List<SourceBlock> sources) {
        IdAssigner.assign(roots, previous);
        int first = IdAssigner.firstFreeId(previous);
        ArrayList<BlockBean> beans = BlockAssembler.assemble(roots, first);
        List<BNode> kept = roots;
        if (!roots.isEmpty() && !sameCode(beans, body)) {
            BNode all = BNode.sourceStatement(body.text(), 1, REASON_MISMATCH);
            kept = List.of(all);
            beans = BlockAssembler.assemble(kept, first);
        }
        collectSources(kept, sources);
        // blocks lying loose in the event don't make code; they stay as they were
        for (BlockBean loose : IdAssigner.loose(previous)) beans.add(loose);
        return beans;
    }

    /** The code that ended up kept as text, in the order of the event. */
    private static void collectSources(List<BNode> nodes, List<SourceBlock> into) {
        for (BNode node : nodes) {
            if (node.source) into.add(new SourceBlock(node.line, node.reason, node.params.get(0).text() == null ? "" : node.params.get(0).text()));
            for (BNode.Param param : node.params) if (param.block() != null) collectSources(List.of(param.block()), into);
            collectSources(node.stack1, into);
            collectSources(node.stack2, into);
        }
    }

    private boolean sameCode(ArrayList<BlockBean> beans, JavaBody original) {
        return sameCode(generator.generate(beans), original.text());
    }

    /** Whether two Java texts are the same code, comments included. */
    boolean sameCode(String generated, String original) {
        JavaBody a = JavaBody.parse(generated);
        JavaBody b = JavaBody.parse(original);
        if (!a.isValid() || !b.isValid()) return false;
        if (Comments.count(generated) != Comments.count(original)) return false;
        List<Statement> x = Ast.statements(a.block());
        List<Statement> y = Ast.statements(b.block());
        if (x.size() != y.size()) return false;
        for (int i = 0; i < x.size(); i++) if (!Ast.same(x.get(i), y.get(i))) return false;
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** One conversion: the text being converted and what is kept as text so far. */
    private final class Run {
        final JavaBody body;
        final String text;

        Run(JavaBody body) {
            this.body = body;
            this.text = body.text();
        }

        // ---- lists of statements

        /**
         * The blocks for the statements {@code from} up to {@code to} of {@code parent}, with the comments between them
         * as blocks of their own.
         */
        List<BNode> entries(@Nullable BlockStmt parent, List<Statement> statements, int from, int to) {
            List<BNode> nodes = new ArrayList<>();
            List<List<Comments.Span>> gaps = gaps(parent, statements);
            for (int i = from; i <= to && i <= statements.size(); i++) {
                for (Comments.Span comment : gaps.get(i)) nodes.add(commentBlock(comment));
                if (i == to || i == statements.size()) break;
                int consumed = 1;
                BNode node = null;
                int fewest = Integer.MAX_VALUE;
                for (Candidate candidate : candidates(parent, statements, i, to, gaps)) {
                    // the block that needs the fewest pieces of code kept as text is the best fit
                    int cost = cost(candidate.node);
                    if (cost < fewest && verify(candidate.node, statements.subList(i, i + candidate.size))) {
                        node = candidate.node;
                        consumed = candidate.size;
                        fewest = cost;
                        if (fewest == 0) break;
                    }
                }
                if (node == null) {
                    Statement statement = statements.get(i);
                    node = sourceOf(statement, REASON_STATEMENT);
                }
                nodes.add(node);
                i += consumed - 1;
            }
            return nodes;
        }

        /**
         * How much a block needs besides values typed in its parameters: a reporter in a parameter costs one, code kept
         * as text a hundred. The block that fits the code with the lowest cost is the one the person would have used.
         */
        int cost(BNode node) {
            int cost = 0;
            for (BNode.Param param : node.params) {
                if (param.block() != null) cost += (param.block().source ? 100 : 1) + cost(param.block());
            }
            return cost;
        }

        BNode commentBlock(Comments.Span span) {
            String comment = text.substring(span.start(), span.end());
            return BNode.sourceStatement(comment, lineAt(span.start()), REASON_COMMENT);
        }

        BNode sourceOf(Statement statement, String reason) {
            String code = slice(statement);
            int line = statement.getRange().map(r -> JavaBody.lineOf(r.begin.line)).orElse(0);
            return BNode.sourceStatement(code, line, reason);
        }

        /** The text of a statement as the person wrote it. */
        String slice(Statement statement) {
            Range range = statement.getRange().orElse(null);
            if (range == null) return statement.toString();
            int start = body.offsetOf(range.begin.line, range.begin.column);
            int end = Math.min(text.length(), body.offsetOf(range.end.line, range.end.column) + 1);
            return start < end ? text.substring(start, end) : statement.toString();
        }

        int lineAt(int offset) {
            int line = 1;
            for (int i = 0; i < offset && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
            return line;
        }

        /**
         * The comments that sit before each statement of a block ({@code gaps.get(i)} is before statement i, the last
         * entry is after the last statement). A block with no range (made by normalising) has none.
         */
        List<List<Comments.Span>> gaps(@Nullable BlockStmt parent, List<Statement> statements) {
            List<List<Comments.Span>> gaps = new ArrayList<>();
            for (int i = 0; i <= statements.size(); i++) gaps.add(new ArrayList<>());
            if (parent == null || parent.getRange().isEmpty()) return gaps;
            Range range = parent.getRange().get();
            // the event's own block opens before the text, in the wrapper: its region starts with the text
            int regionStart = range.begin.line - 2 < 1 ? 0 : body.offsetOf(range.begin.line, range.begin.column) + 1;
            int regionEnd = Math.max(regionStart, body.offsetOf(range.end.line, range.end.column));
            int cursor = regionStart;
            for (int i = 0; i <= statements.size(); i++) {
                int until = regionEnd;
                int resume = regionEnd;
                if (i < statements.size()) {
                    Range r = statements.get(i).getRange().orElse(null);
                    if (r == null) return gaps;
                    until = body.offsetOf(r.begin.line, r.begin.column);
                    resume = Math.min(regionEnd, body.offsetOf(r.end.line, r.end.column) + 1);
                }
                if (until > cursor) gaps.get(i).addAll(Comments.scan(text, cursor, until));
                cursor = Math.max(cursor, resume);
            }
            return gaps;
        }

        // ---- candidates for one statement

        private record Candidate(BNode node, int size) {
        }

        List<Candidate> candidates(@Nullable BlockStmt parent, List<Statement> statements, int i, int to, List<List<Comments.Span>> gaps) {
            List<Candidate> found = new ArrayList<>();
            String key = Template.key(statements.get(i));
            // a more block's call
            BNode call = moreBlockCall(statements.get(i));
            if (call != null) found.add(new Candidate(call, 1));
            for (Template template : templates.statements) {
                if (!template.mayMatch(key)) continue;
                int size = template.statements.size();
                if (i + size > to) continue;
                boolean separated = false;
                for (int j = i + 1; j < i + size; j++) if (!gaps.get(j).isEmpty()) separated = true;
                if (separated) continue;
                Match match = TemplateMatcher.matchStatements(template.statements, statements.subList(i, i + size));
                if (match == null) continue;
                BNode node = build(template.def, match, statements.get(i));
                if (node != null) found.add(new Candidate(node, size));
            }
            return found;
        }

        BNode build(BlockDef def, Match match, Statement at) {
            BNode node = new BNode(def.opCode(), specOf(def), def.type(), def.typeName());
            node.line = at.getRange().map(r -> JavaBody.lineOf(r.begin.line)).orElse(0);
            for (int i = 0; i < def.params().size(); i++) {
                BNode.Param param = param(def.params().get(i), match, i, node.line);
                if (param == null) return null;
                node.params.add(param);
            }
            for (int stack = 1; stack <= def.stacks(); stack++) {
                List<Statement> inner = match.stacks.getOrDefault(stack, List.of());
                TemplateMatcher.StackPlace place = match.places.get(stack);
                List<BNode> chain;
                if (place != null && place.parent() != null) {
                    List<Statement> all = Ast.statements(place.parent());
                    chain = entries(place.parent(), all, place.from(), place.to());
                } else {
                    chain = entries(null, inner, 0, inner.size());
                }
                (stack == 1 ? node.stack1 : node.stack2).addAll(chain);
            }
            return node;
        }

        // ---- parameters

        @Nullable
        BNode.Param param(String type, Match match, int index, int line) {
            if (type.startsWith("%m")) return selector(type, match, index);
            Expression expression = match.expressions.get(index);
            if (expression == null) {
                // the template doesn't use this parameter, or only a part of a name
                String name = match.names.get(index);
                return BNode.Param.text(name == null ? "" : name);
            }
            // a parameter that takes typed text only (no block can sit in it) holds the code as it was written
            if (type.endsWith(".inputOnly")) return BNode.Param.text(codeOf(expression));
            char base = type.length() > 1 ? type.charAt(1) : 's';
            return value(expression, base, line);
        }

        /** A choice from a menu: a variable, a list, a widget, a component or a constant. */
        @Nullable
        BNode.Param selector(String type, Match match, int index) {
            String text = match.nameOf(index);
            Expression expression = match.expressions.get(index);
            if (text == null && expression != null) {
                text = literalText(expression);
                if (text == null && expression instanceof FieldAccessExpr access) text = viewBindingNames.get(access.toString());
            }
            if (text == null) {
                return expression == null ? BNode.Param.text("") : null;
            }
            String selector = type.length() > 3 ? type.substring(3) : "";
            int dot = selector.indexOf('.');
            if (dot >= 0) selector = selector.substring(0, dot);
            return acceptSelector(selector, text) ? BNode.Param.text(text) : null;
        }

        boolean acceptSelector(String selector, String text) {
            if (text.isEmpty()) return false;
            switch (selector) {
                case "varInt":
                    return "d".equals(context.variables().get(text));
                case "varBool":
                    return "b".equals(context.variables().get(text));
                case "varStr":
                    return "s".equals(context.variables().get(text));
                case "varMap":
                    return "a".equals(context.variables().get(text));
                case "listInt":
                    return "listInt".equals(context.lists().get(text));
                case "listStr":
                    return "listStr".equals(context.lists().get(text));
                case "listMap":
                    return "listMap".equals(context.lists().get(text));
                case "list":
                    return context.lists().containsKey(text);
                default:
                    break;
            }
            if (isViewSelector(selector)) return context.views().contains(text);
            return true;
        }

        boolean isViewSelector(String selector) {
            return switch (selector) {
                case "view", "layout", "textview", "button", "edittext", "imageview", "recyclerview", "listview", "gridview",
                     "cardview", "viewpager", "webview", "videoview", "progressbar", "seekbar", "switch", "checkbox",
                     "spinner", "tablayout", "bottomnavigation", "adview", "swiperefreshlayout", "textinputlayout",
                     "ratingbar", "datepicker", "otpview", "lottie", "badgeview", "codeview", "patternview", "signinbutton",
                     "youtubeview", "calendarview", "mapview", "radiobutton" -> true;
                default -> false;
            };
        }

        /** The text of a literal or a plain constant the way a menu stores it, or null. */
        @Nullable
        String literalText(Expression expression) {
            if (expression instanceof IntegerLiteralExpr || expression instanceof LongLiteralExpr || expression instanceof DoubleLiteralExpr
                    || expression instanceof BooleanLiteralExpr) {
                return expression.toString();
            }
            if (expression instanceof UnaryExpr unary && unary.getOperator() == UnaryExpr.Operator.MINUS && unary.getExpression().isLiteralExpr()) {
                return expression.toString().replace(" ", "");
            }
            if (expression instanceof FieldAccessExpr access && access.getScope() instanceof NameExpr) return access.toString();
            if (expression instanceof StringLiteralExpr string) return unescape(string.getValue());
            return null;
        }

        /**
         * A value for a parameter of type {@code base} ({@code s} text, {@code d} number, {@code b} boolean): a literal
         * as it is typed, or a block that gives the value.
         */
        BNode.Param value(Expression expression, char base, int line) {
            String literal = literal(expression, base);
            if (literal != null) return BNode.Param.text(literal);
            return BNode.Param.of(reporter(expression, base, line));
        }

        /** The text a parameter holds when {@code expression} is a literal of the right kind, else null. */
        @Nullable
        String literal(Expression expression, char base) {
            switch (base) {
                case 's':
                    if (expression instanceof StringLiteralExpr string) return unescape(string.getValue());
                    return null;
                case 'd':
                    return numberLiteral(expression);
                case 'b':
                    return expression instanceof BooleanLiteralExpr bool ? String.valueOf(bool.getValue()) : null;
                default:
                    return null;
            }
        }

        @Nullable
        String numberLiteral(Expression expression) {
            if (expression instanceof IntegerLiteralExpr || expression instanceof LongLiteralExpr) return expression.toString();
            if (expression instanceof DoubleLiteralExpr literal) {
                String value = literal.getValue();
                return value.endsWith("d") || value.endsWith("D") ? value.substring(0, value.length() - 1) : value;
            }
            if (expression instanceof UnaryExpr unary && unary.getOperator() == UnaryExpr.Operator.MINUS) {
                String inner = numberLiteral(unary.getExpression());
                return inner == null ? null : "-" + inner;
            }
            return null;
        }

        /** Reverses the generator's escaping of a text; null when the text has an escape that can't be a block's text. */
        @Nullable
        String unescape(String source) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (i + 1 >= source.length()) return null;
                char next = source.charAt(++i);
                switch (next) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case 'n', 't' -> out.append('\\').append(next);
                    default -> {
                        return null;
                    }
                }
            }
            String text = out.toString();
            return escapeLikeGenerator(text).equals(source) ? text : null;
        }

        /** The generator's escaping of a text parameter, as it writes it between quotes. */
        String escapeLikeGenerator(String input) {
            StringBuilder escaped = new StringBuilder();
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (c == '"') {
                    escaped.append("\\\"");
                } else if (c == '\\') {
                    if (i < input.length() - 1) {
                        char next = input.charAt(i + 1);
                        if (next != 'n' && next != 't') {
                            escaped.append("\\\\");
                        } else {
                            escaped.append('\\').append(next);
                            i++;
                        }
                    } else {
                        escaped.append("\\\\");
                    }
                } else if (c == '\n') {
                    escaped.append("\\n");
                } else {
                    escaped.append(c);
                }
            }
            return escaped.toString();
        }

        // ---- reporters

        /** The block that gives the value of {@code expression} in a parameter of type {@code base}. */
        BNode reporter(Expression expression, char base, int line) {
            BNode known = knownReporter(expression, base);
            if (known != null) return known;
            BNode best = null;
            String key = Template.key(expression);
            for (Template template : templates.reporters) {
                if (!template.mayMatch(key) || !accepts(base, template.def.type())) continue;
                Match match = TemplateMatcher.match(template.expression, expression);
                if (match == null) continue;
                BNode node = buildReporter(template.def, match, line);
                if (node != null) {
                    best = node;
                    break;
                }
            }
            if (best != null) return best;
            return BNode.sourceReporter(codeOf(expression), base == 'b' || base == 'd' ? String.valueOf(base) : "s", line, REASON_EXPRESSION);
        }

        /** Whether a block of {@code blockType} may sit in a parameter of type {@code base}. */
        boolean accepts(char base, String blockType) {
            return switch (base) {
                case 'b' -> blockType.equals("b");
                case 'd' -> blockType.equals("d");
                default -> blockType.equals("s") || blockType.equals("d") || blockType.equals("b");
            };
        }

        @Nullable
        BNode buildReporter(BlockDef def, Match match, int line) {
            BNode node = new BNode(def.opCode(), specOf(def), def.type(), def.typeName());
            node.line = line;
            for (int i = 0; i < def.params().size(); i++) {
                String type = def.params().get(i);
                BNode.Param param;
                if (type.startsWith("%m")) {
                    param = selector(type, match, i);
                } else {
                    Expression expression = match.expressions.get(i);
                    if (expression == null) {
                        param = BNode.Param.text(match.names.getOrDefault(i, ""));
                    } else if (type.endsWith(".inputOnly")) {
                        param = BNode.Param.text(codeOf(expression));
                    } else {
                        char base = type.length() > 1 ? type.charAt(1) : 's';
                        String literal = literal(expression, base);
                        if (literal != null) {
                            param = BNode.Param.text(literal);
                        } else {
                            BNode inner = strictReporter(expression, base, line);
                            param = inner == null ? null : BNode.Param.of(inner);
                        }
                    }
                }
                if (param == null) return null;
                node.params.add(param);
            }
            return node;
        }

        /**
         * A reporter for an operand of a reporter: a variable, an argument, or a block that gives exactly this code.
         * An operand that is not one of these makes the whole expression a source block, which reads better than a
         * block full of source blocks.
         */
        @Nullable
        BNode strictReporter(Expression expression, char base, int line) {
            BNode known = knownReporter(expression, base);
            if (known != null) return known;
            String key = Template.key(expression);
            for (Template template : templates.reporters) {
                if (!template.mayMatch(key) || !accepts(base, template.def.type())) continue;
                Match match = TemplateMatcher.match(template.expression, expression);
                if (match == null) continue;
                BNode node = buildReporter(template.def, match, line);
                if (node != null) return node;
            }
            return null;
        }

        /** A variable, an event argument, a string resource, or a call of a more block. */
        @Nullable
        BNode knownReporter(Expression expression, char base) {
            if (expression instanceof NameExpr name) {
                String id = name.getNameAsString();
                String variable = context.variables().get(id);
                if (variable != null) {
                    String type = variable.startsWith("v:") ? "v" : variable;
                    String typeName = variable.startsWith("v:") ? variable.substring(2) : "";
                    if (!accepts(base, type) && !(type.equals("a") && base == 's')) return null;
                    return new BNode("getVar", id, type, typeName);
                }
                if (id.startsWith("_") && id.length() > 1) {
                    String arg = context.args().get(id.substring(1));
                    if (arg != null && accepts(base, arg)) return new BNode("getArg", id.substring(1), arg, "");
                }
                return null;
            }
            if (expression instanceof MethodCallExpr call) {
                if (call.getScope().isEmpty() && call.getNameAsString().equals("getString") && call.getArguments().size() == 1
                        && call.getArgument(0) instanceof FieldAccessExpr access && access.getScope().toString().equals("R.string")
                        && base != 'd' && base != 'b') {
                    return new BNode("getResStr", access.getNameAsString(), "s", "");
                }
                return moreBlockReporter(call, base);
            }
            return null;
        }

        /** The code of an expression as the person wrote it. */
        String codeOf(Expression expression) {
            Range range = expression.getRange().orElse(null);
            if (range == null) return expression.toString();
            int start = body.offsetOf(range.begin.line, range.begin.column);
            int end = Math.min(text.length(), body.offsetOf(range.end.line, range.end.column) + 1);
            // the tree may have lost parentheses the text still has: the text is what the person wrote
            return start < end ? text.substring(start, end) : expression.toString();
        }

        // ---- more blocks

        @Nullable
        BNode moreBlockCall(Statement statement) {
            if (!(statement instanceof ExpressionStmt expressionStatement) || !(expressionStatement.getExpression() instanceof MethodCallExpr call)) return null;
            ProjectContext.MoreBlock block = moreBlockOf(call);
            if (block == null || !block.type().equals(" ")) return null;
            return moreBlockNode(call, block, statement.getRange().map(r -> JavaBody.lineOf(r.begin.line)).orElse(0));
        }

        @Nullable
        BNode moreBlockReporter(MethodCallExpr call, char base) {
            ProjectContext.MoreBlock block = moreBlockOf(call);
            if (block == null || block.type().equals(" ") || !accepts(base, block.type())) return null;
            return moreBlockNode(call, block, 0);
        }

        @Nullable
        ProjectContext.MoreBlock moreBlockOf(MethodCallExpr call) {
            if (call.getScope().isPresent()) return null;
            String name = call.getNameAsString();
            if (!name.startsWith("_")) return null;
            return context.moreBlocks().get(name.substring(1));
        }

        @Nullable
        BNode moreBlockNode(MethodCallExpr call, ProjectContext.MoreBlock block, int line) {
            List<String> types = BlockCatalog.paramsOf(block.spec());
            if (types.size() != call.getArguments().size()) return null;
            BNode node = new BNode("definedFunc", block.spec(), block.type(), block.typeName());
            node.line = line;
            for (int i = 0; i < types.size(); i++) {
                String type = types.get(i);
                Expression argument = call.getArgument(i);
                if (type.startsWith("%m")) {
                    String text = literalText(argument);
                    if (text == null && argument instanceof NameExpr name) text = name.getNameAsString();
                    if (text == null) return null;
                    node.params.add(BNode.Param.text(text));
                } else {
                    node.params.add(value(argument, type.length() > 1 ? type.charAt(1) : 's', line));
                }
            }
            return node;
        }

        // ---- checking

        /** Whether the blocks of an entry give back the code of the statements they came from. */
        boolean verify(BNode node, List<Statement> originals) {
            if (node.source) return true;
            ArrayList<BlockBean> beans = BlockAssembler.assemble(List.of(node), 1);
            String generated;
            try {
                generated = generator.generate(beans);
            } catch (RuntimeException e) {
                return false;
            }
            JavaBody back = JavaBody.parse(generated);
            if (!back.isValid()) return false;
            List<Statement> again = Ast.statements(back.block());
            if (again.size() != originals.size()) return false;
            for (int i = 0; i < again.size(); i++) if (!Ast.same(again.get(i), originals.get(i))) return false;
            int comments = 0;
            for (Statement original : originals) comments += Comments.count(slice(original));
            return Comments.count(generated) == comments;
        }
    }
}
