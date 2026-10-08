package mod.jbk.code.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import mod.jbk.code.completion.ClassIndex;
import mod.jbk.code.diagnostics.JavaDiagnostics.Diagnostic;
import mod.jbk.code.diagnostics.JavaDiagnostics.Severity;

public class JavaDiagnosticsTest {
    private static final ClassIndex INDEX = ClassIndex.of(List.of(
            "android.app.Activity", "android.view.View", "android.widget.Button", "java.util.List", "java.util.ArrayList"));

    private static List<Diagnostic> analyze(String source) {
        return JavaDiagnostics.analyze(source, INDEX);
    }

    private static String underlined(String source, Diagnostic d) {
        return source.substring(d.start(), d.end());
    }

    @Test
    public void validCodeHasNoProblems() {
        String source = "package a;\nimport android.app.Activity;\nimport java.util.List;\n\npublic class A extends Activity {\n  List<String> xs;\n  void f() { var r = switch (1) { case 1 -> 2; default -> 3; }; }\n  record P(int x) {}\n}\n";
        assertEquals(List.of(), analyze(source));
    }

    @Test
    public void missingSemicolonPointsAtTheEndOfTheStatement() {
        String source = "class A {\n  void f() {\n    int x = 1\n    int y = 2;\n  }\n}";
        List<Diagnostic> d = analyze(source);
        assertEquals(1, d.size());
        assertEquals(Severity.ERROR, d.get(0).severity());
        assertEquals("Missing ';'", d.get(0).message());
        assertEquals("1", underlined(source, d.get(0)));
    }

    @Test
    public void unexpectedTokenIsUnderlined() {
        String source = "class A {\n  void f() {\n    foo(;\n  }\n}";
        Diagnostic d = analyze(source).get(0);
        assertEquals("Unexpected ';'", d.message());
        assertEquals(";", underlined(source, d));
        assertTrue(d.detail().startsWith("Unexpected ';'. Expected:"));
    }

    @Test
    public void endOfFileMeansSomethingIsNotClosed() {
        String source = "class A {\n  void f() {\n    if (x) {\n  }\n}";
        Diagnostic d = analyze(source).get(0);
        assertTrue(d.message(), d.message().startsWith("Reached the end of the file"));
        assertEquals(source.length() - 1, d.start());
        assertEquals(source.length(), d.end());
    }

    @Test
    public void extraClosingBrace() {
        String source = "class A {\n  void f() {\n  }\n}\n}";
        Diagnostic d = analyze(source).get(0);
        assertEquals("Unexpected '}'", d.message());
        assertEquals(source.length() - 1, d.start());
    }

    @Test
    public void unclosedStringLiteral() {
        String source = "class A {\n  String s = \"abc;\n}";
        Diagnostic d = analyze(source).get(0);
        assertEquals("Unclosed string literal", d.message());
        assertEquals("\"abc;", underlined(source, d));
    }

    @Test
    public void tabsDoNotShiftThePosition() {
        String source = "class A {\n\tvoid f() {\n\t\tint x = 1\n\t\tint y = 2;\n\t}\n}";
        Diagnostic d = analyze(source).get(0);
        assertEquals("Missing ';'", d.message());
        assertEquals("1", underlined(source, d));
    }

    @Test
    public void everyDiagnosticHasANonEmptyRangeInsideTheText() {
        String[] broken = {"", "class", "class A {", "class A { void f( }", "}", "class A { int = ; }", "import ;", "class A { void f() { \"", "/* open", "class A { int x = 'ab'; }"};
        for (String source : broken) {
            for (Diagnostic d : analyze(source)) {
                assertTrue(source + " -> " + d, d.start() >= 0 && d.end() > d.start() && d.end() <= Math.max(source.length(), 1));
            }
        }
    }

    @Test
    public void unusedImportsAreWarnings() {
        String source = "import android.app.Activity;\nimport java.util.List;\nimport java.util.ArrayList;\n\nclass A extends Activity {\n  List<String> xs = null;\n}\n";
        List<Diagnostic> d = analyze(source);
        assertEquals(1, d.size());
        assertEquals(Severity.WARNING, d.get(0).severity());
        assertEquals("Unused import 'ArrayList'", d.get(0).message());
        assertEquals("import java.util.ArrayList;", underlined(source, d.get(0)));
    }

    @Test
    public void usageInJavadocOrAnnotationCounts() {
        String source = "import java.util.List;\nimport android.view.View;\n\n/** See {@link List}. */\nclass A {\n  @SuppressWarnings(\"x\") View v;\n}\n";
        assertEquals(List.of(), analyze(source));
    }

    @Test
    public void unknownSdkClassesAreFlaggedButUnknownPackagesAreNot() {
        String source = "import android.widget.Buton;\nimport com.example.lib.Thing;\nimport android.view.View.OnClickListener;\n\nclass A { Buton b; Thing t; OnClickListener l; }\n";
        List<Diagnostic> d = analyze(source);
        assertEquals(1, d.size());
        assertEquals("Can't find class 'Buton' in package 'android.widget'", d.get(0).message());
    }

    @Test
    public void staticAndWildcardImportsAreLeftAlone() {
        String source = "import static java.lang.Math.max;\nimport java.util.*;\n\nclass A { }\n";
        assertEquals(List.of(), analyze(source));
    }

    @Test
    public void withoutAnIndexImportsAreOnlyCheckedForUse() {
        String source = "import android.widget.Buton;\nclass A { Buton b; }\n";
        assertFalse(JavaDiagnostics.analyze(source, ClassIndex.empty()).stream().anyMatch(d -> d.message().startsWith("Can't find")));
    }

    @Test
    public void nextProblemWrapsAround() {
        Diagnostic a = new Diagnostic(Severity.WARNING, 10, 15, "a", "a");
        Diagnostic b = new Diagnostic(Severity.ERROR, 40, 41, "b", "b");
        List<Diagnostic> all = List.of(b, a);
        assertEquals(a, JavaDiagnostics.nextAfter(all, 0));
        assertEquals(b, JavaDiagnostics.nextAfter(all, 10));
        assertEquals(a, JavaDiagnostics.nextAfter(all, 40));
        assertEquals(null, JavaDiagnostics.nextAfter(List.of(), 5));
    }
}
