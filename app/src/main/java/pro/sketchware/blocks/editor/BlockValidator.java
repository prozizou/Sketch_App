package pro.sketchware.blocks.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/** Everything that is checked about a block before it is saved, each problem with the tab and line it belongs to. */
public final class BlockValidator {
    public enum Tab {GENERAL, PARAMETERS, SPEC, CODE}

    public enum Severity {ERROR, WARNING, INFO}

    /** @param line 1-based line of the code, or 0 */
    public record Issue(Severity severity, Tab tab, int line, CodeDiagnostics.Kind kind, String message) {
    }

    public static final Pattern NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    public static final Pattern HEX_COLOR = Pattern.compile("#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})");

    private BlockValidator() {
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name.trim()).matches();
    }

    public static boolean isValidColor(String color) {
        return color != null && HEX_COLOR.matcher(color.trim()).matches();
    }

    /** @param otherNames the names (opcodes) of the other custom blocks */
    public static List<Issue> validate(BlockFields f, Collection<String> otherNames) {
        List<Issue> issues = new ArrayList<>();
        String name = f.name() == null ? "" : f.name().trim();
        if (name.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, Tab.GENERAL, 0, CodeDiagnostics.Kind.STATIC, "The block needs a name"));
        } else if (!NAME.matcher(name).matches()) {
            issues.add(new Issue(Severity.ERROR, Tab.GENERAL, 0, CodeDiagnostics.Kind.STATIC,
                    "The name may only contain letters, digits, _ and $, and not start with a digit"));
        } else if (otherNames.contains(name)) {
            issues.add(new Issue(Severity.ERROR, Tab.GENERAL, 0, CodeDiagnostics.Kind.STATIC,
                    "Another block already uses the name (opcode) \"" + name + "\""));
        }
        if (BlockShapes.of(f.type()) == null) {
            issues.add(new Issue(Severity.ERROR, Tab.GENERAL, 0, CodeDiagnostics.Kind.STATIC, "Unknown block type \"" + f.type() + "\""));
        }
        if (!isValidColor(f.color())) {
            issues.add(new Issue(Severity.ERROR, Tab.GENERAL, 0, CodeDiagnostics.Kind.STATIC, "Invalid hex color (use #RRGGBB or #AARRGGBB)"));
        }

        List<BlockSpec.Token> tokens = BlockSpec.parse(f.spec());
        if (tokens.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, Tab.SPEC, 0, CodeDiagnostics.Kind.STATIC, "The block needs a spec"));
        }
        for (BlockSpec.Invalid invalid : BlockSpec.problems(tokens)) {
            issues.add(new Issue(invalid.error() ? Severity.ERROR : Severity.WARNING, Tab.SPEC, 0,
                    CodeDiagnostics.Kind.STATIC, invalid.problem()));
        }
        BlockShapes.Shape shape = BlockShapes.of(f.type());
        if (shape != null && shape.type().equals("e") && (f.spec2() == null || f.spec2().trim().isEmpty())) {
            issues.add(new Issue(Severity.INFO, Tab.SPEC, 0, CodeDiagnostics.Kind.STATIC, "The else part has no text"));
        }
        for (BlockSpec.Invalid invalid : BlockSpec.problems(BlockSpec.parse(f.spec2()))) {
            issues.add(new Issue(invalid.error() ? Severity.ERROR : Severity.WARNING, Tab.SPEC, 0,
                    CodeDiagnostics.Kind.STATIC, "Else part: " + invalid.problem()));
        }

        for (ImportList.Entry entry : ImportList.parse(f.imports())) {
            if (!entry.valid()) {
                issues.add(new Issue(Severity.WARNING, Tab.CODE, 0, CodeDiagnostics.Kind.STATIC,
                        "Import line " + entry.line() + ": " + entry.problem()));
            }
        }
        for (CodeDiagnostics.Diagnostic d : CodeDiagnostics.analyze(f.code(), f.type(), BlockSpec.paramCount(f.spec()), f.imports())) {
            Severity severity = switch (d.severity()) {
                case ERROR -> Severity.ERROR;
                case WARNING -> Severity.WARNING;
                case INFO -> Severity.INFO;
            };
            issues.add(new Issue(severity, Tab.CODE, d.line(), d.kind(), d.message()));
        }
        return issues;
    }

    public static boolean hasErrors(List<Issue> issues) {
        for (Issue issue : issues) {
            if (issue.severity() == Severity.ERROR) return true;
        }
        return false;
    }

    /** @return the problems that need an answer: errors and warnings (hints and notices don't) */
    public static List<Issue> blocking(List<Issue> issues, boolean includeWarnings) {
        List<Issue> result = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.severity() == Severity.ERROR || (includeWarnings && issue.severity() == Severity.WARNING
                    && issue.kind() == CodeDiagnostics.Kind.STATIC)) {
                result.add(issue);
            }
        }
        return result;
    }
}
