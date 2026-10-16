package pro.sketchware.logic.sync;

import com.besome.sketch.beans.BlockBean;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import a.a.a.Fx;
import a.a.a.jq;

/**
 * The templates of every block of a catalog, made by asking the editor's own generator what each block turns into with
 * recognisable values in its parameters. The reverse conversion therefore agrees with the generator by construction:
 * there is no second table of Java to keep in step, and a block added to the generator is understood at once.
 */
final class TemplateSet {
    final List<Template> statements = new ArrayList<>();
    final List<Template> reporters = new ArrayList<>();

    private TemplateSet() {
    }

    static TemplateSet build(BlockCatalog catalog, ProjectContext context, jq buildConfig, java.util.function.Function<BlockDef, String> specs) {
        TemplateSet set = new TemplateSet();
        for (BlockDef def : catalog.all()) {
            if (def.opCode().equals("addSourceDirectly") || def.opCode().startsWith("asd")) continue;
            Template template = derive(def, specs.apply(def), context, buildConfig);
            if (template == null) continue;
            if (template.expression != null) set.reporters.add(template);
            else set.statements.add(template);
        }
        Comparator<Template> bySize = Comparator.<Template>comparingInt(t -> t.size).reversed().thenComparing(t -> !t.def.builtIn()).thenComparing(t -> t.def.opCode());
        set.statements.sort(bySize);
        set.reporters.sort(bySize);
        return set;
    }

    private static Template derive(BlockDef def, String spec, ProjectContext context, jq buildConfig) {
        ArrayList<BlockBean> blocks = new ArrayList<>();
        BlockBean bean = new BlockBean("1", spec, def.type(), def.typeName(), def.opCode());
        for (int i = 0; i < def.params().size(); i++) bean.parameters.add(TemplateMatcher.PARAM + i);
        blocks.add(bean);
        for (int stack = 1; stack <= def.stacks(); stack++) {
            BlockBean inner = new BlockBean(String.valueOf(stack + 1), "add source directly %s.inputOnly", " ", "addSourceDirectly");
            inner.parameters.add(TemplateMatcher.STACK + stack + ";");
            blocks.add(inner);
            if (stack == 1) bean.subStack1 = stack + 1;
            else bean.subStack2 = stack + 1;
        }
        String code;
        try {
            code = new Fx(context.activityName(), buildConfig, blocks, false).a();
        } catch (RuntimeException e) {
            return null;
        }
        if (code == null || code.isBlank()) return null;
        code = code.replace("\r", "")
                .replaceAll("\\$className", context.activityName())
                .replaceAll("\\$context", context.fragment() ? "getContext()" : context.activityName() + ".this");
        try {
            if (def.isReporter()) {
                JavaBody body = JavaBody.parse("Object sketchValue = " + code + ";");
                if (!body.isValid() || body.block().getStatements().size() != 1) return null;
                Statement only = body.block().getStatement(0);
                if (only instanceof ExpressionStmt declaration && declaration.getExpression().isVariableDeclarationExpr()) {
                    VariableDeclarator variable = declaration.getExpression().asVariableDeclarationExpr().getVariable(0);
                    Expression value = variable.getInitializer().orElse(null);
                    return value == null || isOnlySentinel(value) ? null : new Template(def, List.of(), Ast.normalized(value));
                }
                return null;
            }
            JavaBody body = JavaBody.parse(code);
            if (!body.isValid()) return null;
            BlockStmt block = body.block();
            if (block.getStatements().isEmpty()) return null;
            List<Statement> statements = Ast.statements(block);
            return new Template(def, statements, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A template that is only a parameter ({@code SKP0}) would match everything. */
    private static boolean isOnlySentinel(Expression value) {
        return value instanceof NameExpr name && TemplateMatcher.isParamName(name.getNameAsString())
                || value instanceof StringLiteralExpr literal && TemplateMatcher.isParamName(literal.getValue());
    }
}
