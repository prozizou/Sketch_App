package pro.sketchware.blocks.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import pro.sketchware.blocks.editor.CodeDiagnostics.Diagnostic;
import pro.sketchware.blocks.editor.CodeDiagnostics.Kind;
import pro.sketchware.blocks.editor.CodeDiagnostics.Severity;

public class CodeDiagnosticsTest {
    private static List<Diagnostic> analyze(String type, int params, String code) {
        return CodeDiagnostics.analyze(code, type, params, "");
    }

    private static Diagnostic find(List<Diagnostic> list, Severity severity, String part) {
        return list.stream().filter(d -> d.severity() == severity && d.message().contains(part)).findFirst().orElse(null);
    }

    @Test
    public void aCorrectBlockOnlyCarriesTheCompilationNotice() {
        List<Diagnostic> d = analyze(" ", 1, "android.widget.Toast.makeText(getApplicationContext(), %s, 0).show();");
        assertEquals(d.toString(), 1, d.size());
        assertEquals(Kind.NEEDS_COMPILATION, d.get(0).kind());
        assertFalse(CodeDiagnostics.hasErrors(d));
    }

    @Test
    public void placeholderProblemsNameTheirLine() {
        String code = "a(%s);\nb(%d);\nc(50%);\nd(%4$s);";
        List<Diagnostic> d = analyze(" ", 1, code);
        assertEquals(2, find(d, Severity.ERROR, "%d fails").line());
        assertEquals(3, find(d, Severity.ERROR, "lone %").line());
        assertEquals(4, find(d, Severity.ERROR, "input 4").line());
        assertTrue(CodeDiagnostics.hasErrors(d));
        for (Diagnostic e : d) {
            if (e.severity() == Severity.ERROR) assertEquals(Kind.STATIC, e.kind());
        }
    }

    @Test
    public void slotsOnlyForIfBlocksAndUnusedInputs() {
        List<Diagnostic> regular = analyze(" ", 1, "run(%s);\n%s");
        assertEquals(2, find(regular, Severity.WARNING, "block slot").line());
        assertTrue(find(analyze("c", 1, "if (%s) {\n%s\n}"), Severity.WARNING, "block slot") == null);
        assertEquals(0, find(analyze(" ", 2, "run(%s);"), Severity.WARNING, "Input 2 isn't used").line());
    }

    @Test
    public void bracketsAndStrings() {
        List<Diagnostic> d = analyze(" ", 0, "if (x) {\n  y(\"open);\n");
        assertEquals(2, find(d, Severity.ERROR, "Unterminated string").line());
        assertEquals(1, find(d, Severity.WARNING, "'{' is never closed").line());
        // Brackets inside strings and comments don't count.
        List<Diagnostic> ok = analyze(" ", 0, "log(\"}\"); // {\n");
        assertTrue(find(ok, Severity.WARNING, "never closed") == null);
        assertTrue(find(ok, Severity.WARNING, "no matching") == null);
    }

    @Test
    public void endingOfStatementsAndValues() {
        assertEquals(2, find(analyze(" ", 0, "a();\nb()"), Severity.WARNING, "should end with ';'").line());
        assertEquals(1, find(analyze("b", 1, "%s.isEmpty();"), Severity.WARNING, "shouldn't end with ';'").line());
        assertTrue(find(analyze("b", 1, "%s.isEmpty()"), Severity.WARNING, "shouldn't end") == null);
    }

    @Test
    public void importHintsAreFlaggedAsNeedingCompilation() {
        List<Diagnostic> d = CodeDiagnostics.analyze("Toast.makeText(ctx, %s, 0).show();\nVibrator v = null;", " ", 1, "");
        Diagnostic toast = find(d, Severity.WARNING, "Toast is used without an import");
        assertEquals(1, toast.line());
        assertEquals("android.widget.Toast", toast.fixImport());
        assertEquals(Kind.NEEDS_COMPILATION, toast.kind());
        // Imported, or written in full: no hint.
        assertTrue(find(CodeDiagnostics.analyze("Toast.makeText(ctx, %s, 0).show();", " ", 1, "import android.widget.Toast;"),
                Severity.WARNING, "Toast is used") == null);
        assertTrue(find(analyze(" ", 1, "android.widget.Toast.makeText(ctx, %s, 0).show();"), Severity.WARNING, "without an import") == null);
        assertTrue(find(analyze(" ", 1, "log(\"Toast\");"), Severity.WARNING, "without an import") == null);
    }

    @Test
    public void deprecatedApisPermissionsAndLibrariesWithLines() {
        List<Diagnostic> d = analyze(" ", 0, "a();\nnew android.os.AsyncTask(){};\nvibrator.vibrate(1); // Vibrator\ncom.google.firebase.auth.FirebaseAuth.getInstance();");
        assertEquals(2, find(d, Severity.WARNING, "AsyncTask").line());
        assertEquals(3, find(d, Severity.INFO, "android.permission.VIBRATE").line());
        Diagnostic firebase = find(d, Severity.INFO, "Firebase Authentication");
        assertEquals(4, firebase.line());
        assertEquals(Kind.NEEDS_COMPILATION, firebase.kind());
    }

    @Test
    public void emptyCodeIsReportedOnce() {
        List<Diagnostic> d = analyze(" ", 0, "  \n ");
        assertEquals(1, d.size());
        assertEquals(0, d.get(0).line());
    }

    @Test
    public void referencedInputsAndRenumbering() {
        assertEquals(Set.of(1, 2), CodeDiagnostics.referencedInputs("a(%s, %s);"));
        assertEquals(Set.of(2, 3), CodeDiagnostics.referencedInputs("a(%2$s); b(%3$s, %2$s); c(50%%);"));
        // Input 1 and 2 swapped, input 3 removed, block slots shift by -1.
        assertEquals("a(%2$s, %1$s, %0$s, %3$s); %s", CodeDiagnostics.renumber("a(%1$s, %2$s, %3$s, %4$s); %s", new int[]{2, 1, 0}, -1));
        assertEquals("100%% %2$S", CodeDiagnostics.renumber("100%% %1$S", new int[]{2}, 0));
    }

    @Test
    public void importListParsesLinesAndKeepsThemAsTyped() {
        String imports = "import android.widget.Toast;\n\nandroid.net.Uri\nnot valid!\nToast\nimport android.widget.Toast;";
        List<ImportList.Entry> entries = ImportList.parse(imports);
        assertEquals(5, entries.size());
        assertTrue(entries.get(0).valid());
        assertEquals(3, entries.get(1).line());
        assertEquals("Not a valid import", entries.get(2).problem());
        assertTrue(entries.get(3).problem().contains("full name"));
        assertEquals("Listed twice", entries.get(4).problem());
        assertEquals(Set.of("android.widget.Toast", "android.net.Uri"), ImportList.classes(imports));
        assertEquals("import a.b.C;", ImportList.add("", "a.b.C"));
        assertEquals("import a.b.C;\nimport d.E;", ImportList.add("import a.b.C;", "d.E"));
        assertEquals("import a.b.C;", ImportList.add("import a.b.C;", "a.b.C"));
        assertEquals("one\nthree", ImportList.removeLine("one\ntwo\nthree", 2));
        assertEquals("one\nTWO\nthree", ImportList.replaceLine("one\ntwo\nthree", 2, "TWO"));
        assertEquals(List.of("android.widget.Toast", "android.content.Intent"), ImportList.usedKnownClasses("Toast.make(); new Intent(); log(\"Uri\");"));
    }

    @Test
    public void templatesAreCompatibleWithTheBlockType() {
        assertEquals(BlockTemplates.ALL.size(), BlockTemplates.compatible("regular", true).size());
        List<BlockTemplates.Template> value = BlockTemplates.compatible("s", false);
        assertTrue(value.stream().anyMatch(t -> t.id().equals("view.text")));
        assertTrue(value.stream().anyMatch(t -> t.group() == BlockTemplates.Group.CUSTOM));
        assertTrue(value.stream().noneMatch(t -> t.id().equals("java.toast")));
        // Every template is itself a correct block: no errors, a known shape, a spec that parses.
        for (BlockTemplates.Template t : BlockTemplates.ALL) {
            assertTrue(t.id(), BlockShapes.of(t.type()) != null);
            assertTrue(t.id(), BlockSpec.problems(BlockSpec.parse(t.spec())).isEmpty());
            if (!t.code().isEmpty()) {
                List<Diagnostic> d = CodeDiagnostics.analyze(t.code(), t.type(), BlockSpec.paramCount(t.spec()), "");
                assertFalse(t.id() + " " + d, CodeDiagnostics.hasErrors(d));
                assertTrue(t.id() + " " + d, find(d, Severity.WARNING, "isn't used") == null);
            }
        }
    }
}
