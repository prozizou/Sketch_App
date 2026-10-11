package pro.sketchware.blocks.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import pro.sketchware.blocks.manager.BlockChecks.Issue;
import pro.sketchware.blocks.manager.BlockChecks.Severity;

public class BlockChecksTest {
    private static List<Issue> check(String type, String spec, String code) {
        return BlockChecks.check("myBlock", type, spec, code, "", Set.of());
    }

    private static boolean has(List<Issue> issues, Severity severity, String part) {
        return issues.stream().anyMatch(i -> i.severity() == severity && i.message().contains(part));
    }

    @Test
    public void aCorrectBlockHasNoProblem() {
        List<Issue> issues = check("regular", "show %s.text for %d.seconds", "showToast(%s, %s);");
        assertTrue(issues.toString(), issues.isEmpty());
        assertEquals(List.of("%s.text", "%d.seconds"), BlockChecks.inputs("show %s.text for %d.seconds"));
        assertEquals(List.of("%m.view"), BlockChecks.inputs("hide %m.view"));
    }

    @Test
    public void duplicateOpcodeAndBadNames() {
        assertTrue(has(BlockChecks.check("toast", " ", "t", "t();", "", Set.of("toast")), Severity.ERROR, "already uses"));
        assertTrue(has(BlockChecks.check("2toast", " ", "t", "t();", "", Set.of()), Severity.ERROR, "letters"));
        assertTrue(has(BlockChecks.check("", " ", "t", "t();", "", Set.of()), Severity.ERROR, "needs a name"));
    }

    @Test
    public void referencesBeyondTheInputsAreErrors() {
        List<Issue> issues = check("regular", "show %s.text", "a(%s, %s, %s, %s);");
        assertTrue(issues.toString(), has(issues, Severity.ERROR, "refers to input 4"));
        assertTrue(has(check("regular", "show %s.text", "a(%5$s);"), Severity.ERROR, "refers to input 5"));
    }

    @Test
    public void substackSlotsOnlyForIfBlocks() {
        assertTrue(has(check("regular", "do %s.a", "a(%s); %s"), Severity.WARNING, "only \"if\" blocks"));
        assertFalse(has(check("c", "repeat %d.n", "for (int i = 0; i < %s; i++) {\n%s\n}"), Severity.WARNING, "only \"if\" blocks"));
    }

    @Test
    public void numericConversionsFailOnTextInputs() {
        // Inputs are passed as text: %d throws IllegalFormatConversionException, as in the Logic editor.
        assertTrue(has(check("regular", "wait %d.ms", "sleep(%d);"), Severity.ERROR, "can't be turned into Java"));
        assertTrue(has(check("regular", "x", "int p = 50%;"), Severity.ERROR, "can't be turned into Java"));
        assertFalse(has(check("regular", "x", "int p = 50%%;"), Severity.ERROR, "can't be turned into Java"));
    }

    @Test
    public void typesAndUnusedInputs() {
        assertTrue(has(check("b", "is %s.a empty", "%s.isEmpty();"), Severity.WARNING, "shouldn't end with ';'"));
        assertTrue(has(check("regular", "a %s.x %s.y", "a(%s);"), Severity.WARNING, "doesn't use every input"));
        assertTrue(has(check("zz", "a", "a();"), Severity.ERROR, "Unknown block type"));
    }

    @Test
    public void importsDeprecatedApisAndPermissions() {
        List<Issue> issues = BlockChecks.check("v", " ", "vibrate %d.ms",
                "((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(Long.parseLong(%s)); new AsyncTask(){};",
                "android.os.Vibrator\nimport java.util.*;\nnot an import", Set.of());
        assertTrue(issues.toString(), has(issues, Severity.ERROR, "Invalid import: not an import"));
        assertFalse(has(issues, Severity.ERROR, "Invalid import: android.os.Vibrator"));
        assertTrue(has(issues, Severity.WARNING, "AsyncTask"));
        assertTrue(has(issues, Severity.INFO, "android.permission.VIBRATE"));
    }

    @Test
    public void javaPreviewNamesTheInputs() {
        String preview = BlockChecks.javaPreview("show %s.text for %d.seconds", "showToast(%s, %s);", "android.widget.Toast");
        assertEquals("import android.widget.Toast;\n\nshowToast(text, seconds);", preview);
        assertTrue(BlockChecks.javaPreview("if %b.cond", "if (%s) {\n%s\n}", "").contains("/* blocks */"));
    }
}
