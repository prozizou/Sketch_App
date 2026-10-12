package pro.sketchware.blocks.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import pro.sketchware.blocks.editor.BlockValidator.Issue;
import pro.sketchware.blocks.editor.BlockValidator.Severity;
import pro.sketchware.blocks.editor.BlockValidator.Tab;

public class BlockValidatorTest {
    private static BlockFields block(String name, String type, String spec, String code) {
        return new BlockFields(name, type, "", spec, "", "#FF5722", "", code);
    }

    private static Issue find(List<Issue> issues, Severity severity, Tab tab, String part) {
        return issues.stream().filter(i -> i.severity() == severity && i.tab() == tab && i.message().contains(part))
                .findFirst().orElse(null);
    }

    @Test
    public void aCorrectBlockHasNoErrorOrWarning() {
        List<Issue> issues = BlockValidator.validate(block("showToast", "regular", "show toast %s",
                "android.widget.Toast.makeText(getApplicationContext(), %s, 0).show();"), Set.of());
        assertTrue(issues.toString(), BlockValidator.blocking(issues, true).isEmpty());
        assertFalse(BlockValidator.hasErrors(issues));
    }

    @Test
    public void generalProblems() {
        assertTrue(find(BlockValidator.validate(block("", "regular", "a", "a();"), Set.of()), Severity.ERROR, Tab.GENERAL, "needs a name") != null);
        assertTrue(find(BlockValidator.validate(block("1a", "regular", "a", "a();"), Set.of()), Severity.ERROR, Tab.GENERAL, "letters") != null);
        assertTrue(find(BlockValidator.validate(block("dup", "regular", "a", "a();"), Set.of("dup")), Severity.ERROR, Tab.GENERAL, "already uses") != null);
        assertTrue(find(BlockValidator.validate(block("x", "zz", "a", "a();"), Set.of()), Severity.ERROR, Tab.GENERAL, "Unknown block type") != null);
        BlockFields badColor = new BlockFields("x", "regular", "", "a", "", "red", "", "a();");
        assertTrue(find(BlockValidator.validate(badColor, Set.of()), Severity.ERROR, Tab.GENERAL, "hex color") != null);
        assertTrue(BlockValidator.isValidColor("#80FF5722"));
        assertFalse(BlockValidator.isValidColor("#FFF"));
    }

    @Test
    public void specProblemsAreOnTheSpecTab() {
        List<Issue> empty = BlockValidator.validate(block("x", "regular", "  ", "a();"), Set.of());
        assertTrue(find(empty, Severity.ERROR, Tab.SPEC, "needs a spec") != null);
        List<Issue> bad = BlockValidator.validate(block("x", "regular", "do %m now %x", "a();"), Set.of());
        assertTrue(find(bad, Severity.ERROR, Tab.SPEC, "'%m'") != null);
        assertTrue(find(bad, Severity.WARNING, Tab.SPEC, "isn't a parameter") != null);
    }

    @Test
    public void codeProblemsKeepTheirLineAndKind() {
        List<Issue> issues = BlockValidator.validate(block("x", "regular", "do %s", "a(%d);\nb();"), Set.of());
        Issue error = find(issues, Severity.ERROR, Tab.CODE, "%d fails");
        assertEquals(1, error.line());
        assertEquals(CodeDiagnostics.Kind.STATIC, error.kind());
        Issue notice = find(issues, Severity.INFO, Tab.CODE, "only checked when the app is compiled");
        assertEquals(CodeDiagnostics.Kind.NEEDS_COMPILATION, notice.kind());
        // A hint that needs compiling never blocks a save.
        List<Issue> hint = BlockValidator.validate(block("x", "regular", "do %s", "Toast.makeText(c, %s, 0).show();"), Set.of());
        assertTrue(find(hint, Severity.WARNING, Tab.CODE, "without an import") != null);
        assertTrue(BlockValidator.blocking(hint, true).isEmpty());
    }

    @Test
    public void invalidImportsAreWarnings() {
        BlockFields f = new BlockFields("x", "regular", "", "do", "", "#FF5722", "android.widget.Toast\nnot valid!", "a();");
        List<Issue> issues = BlockValidator.validate(f, Set.of());
        assertTrue(find(issues, Severity.WARNING, Tab.CODE, "Import line 2") != null);
        assertFalse(BlockValidator.hasErrors(issues));
    }

    @Test
    public void javaPreviewNamesTheInputsAndListsImports() {
        assertEquals("import android.widget.Toast;\n\nshowToast(input1, input2);",
                JavaPreview.build("show %s for %d", "showToast(%s, %s);", "android.widget.Toast"));
        String c = JavaPreview.build("if %b", "if (%s) {\n%s\n}", "");
        assertTrue(c, c.contains("input1") && c.contains("/* blocks */"));
        assertTrue(JavaPreview.build("x", "a(%d);", "").contains("Failed to resolve"));
    }

    @Test
    public void fieldsSurviveTheDraftRoundTrip() {
        BlockFields f = new BlockFields("n", "c", "T", "if %b", "else", "#112233", "a.B", "if (%s) {\n%s\n}");
        com.google.gson.Gson gson = new com.google.gson.Gson();
        assertEquals(f, gson.fromJson(gson.toJson(f), BlockFields.class));
    }
}
