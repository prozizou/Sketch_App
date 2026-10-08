package mod.jbk.diagnostic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import mod.jbk.diagnostic.CompileDiagnosticParser.Diagnostic;
import mod.jbk.diagnostic.CompileDiagnosticParser.Severity;

public class CompileDiagnosticParserTest {
    private static final String ECJ_LOG = "----------\n"
            + "1. ERROR in /sdcard/.sketchware/data/605/files/java/com/my/app/MainActivity.java (at line 42)\n"
            + "\tfoo.bar();\n\t    ^^^\nThe method bar() is undefined for the type Foo\n----------\n"
            + "2. WARNING in /x/Y.java (at line 7)\n\tint a;\n\t    ^\nThe value of the local variable a is not used\n----------\n";

    @Test
    public void parsesEcjProblems() {
        List<Diagnostic> d = CompileDiagnosticParser.parse(ECJ_LOG);
        assertEquals(2, d.size());
        assertEquals(Severity.ERROR, d.get(0).severity());
        assertEquals("/sdcard/.sketchware/data/605/files/java/com/my/app/MainActivity.java", d.get(0).path());
        assertEquals(42, d.get(0).line());
        assertEquals("The method bar() is undefined for the type Foo", d.get(0).message());
        assertEquals(Severity.WARNING, d.get(1).severity());
        assertEquals("/x/Y.java", d.get(1).path());
        assertEquals(7, d.get(1).line());
    }

    @Test
    public void ecjLocationSpanCoversPathAndLine() {
        Diagnostic d = CompileDiagnosticParser.parse(ECJ_LOG).get(1);
        assertEquals("/x/Y.java (at line 7)", ECJ_LOG.substring(d.start(), d.end()));
    }

    @Test
    public void parsesAapt2Errors() {
        String log = "/data/605/res/layout/main.xml:12: error: attribute android:foo not found.\n"
                + "/data/605/res/values/strings.xml:3:9: warning: something odd\n";
        List<Diagnostic> d = CompileDiagnosticParser.parse(log);
        assertEquals(2, d.size());
        assertEquals("/data/605/res/layout/main.xml", d.get(0).path());
        assertEquals(12, d.get(0).line());
        assertEquals(0, d.get(0).column());
        assertEquals("attribute android:foo not found.", d.get(0).message());
        assertEquals(Severity.WARNING, d.get(1).severity());
        assertEquals(9, d.get(1).column());
    }

    @Test
    public void parsesKotlinErrors() {
        String log = "e: /data/605/files/java/Foo.kt: (10, 5): Unresolved reference: bar\n";
        Diagnostic d = CompileDiagnosticParser.parse(log).get(0);
        assertEquals(Severity.ERROR, d.severity());
        assertEquals("/data/605/files/java/Foo.kt", d.path());
        assertEquals(10, d.line());
        assertEquals(5, d.column());
        assertEquals("Unresolved reference: bar", d.message());
    }

    @Test
    public void ordersByPositionAndCounts() {
        String log = "e: /a/B.kt: (1, 1): x\n/a/c.xml:2: error: y\n";
        List<Diagnostic> d = CompileDiagnosticParser.parse(log);
        assertEquals(2, CompileDiagnosticParser.count(d, Severity.ERROR));
        assertTrue(d.get(0).start() < d.get(1).start());
    }

    @Test
    public void plainTextHasNoDiagnostics() {
        assertTrue(CompileDiagnosticParser.parse("java.lang.RuntimeException: boom\n\tat a.b(C.java:5)").isEmpty());
        assertTrue(CompileDiagnosticParser.parse(null).isEmpty());
    }
}
