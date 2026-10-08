package mod.jbk.code.completion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import mod.jbk.code.completion.JavaCompletionEngine.ImportEdit;
import mod.jbk.code.completion.JavaCompletionEngine.Result;
import mod.jbk.code.completion.JavaCompletionEngine.Suggestion;
import mod.jbk.code.completion.JavaCompletionEngine.Type;

public class JavaCompletionEngineTest {
    private static final ClassIndex INDEX = ClassIndex.of(List.of(
            "java.util.List", "java.util.ArrayList", "java.util.Map", "java.lang.String", "java.lang.StringBuilder",
            "android.widget.Button", "android.widget.ButtonBar", "com.my.app.Button", "com.my.app.Helper"));

    private final JavaCompletionEngine engine = new JavaCompletionEngine(INDEX, name -> {
        try {
            return Class.forName(name, false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    });

    /** The caret is where {@code |} is. */
    private Result complete(String sourceWithCaret) {
        int caret = sourceWithCaret.indexOf('|');
        return engine.complete(sourceWithCaret.replace("|", ""), caret);
    }

    private static List<String> labels(Result result) {
        return result.suggestions().stream().map(Suggestion::label).collect(Collectors.toList());
    }

    @Test
    public void suggestsMembersOfALocalVariablesType() {
        Result r = complete("class A {\n void f() {\n  String s = \"\";\n  s.toUp|\n }\n}");
        assertEquals(List.of("toUpperCase()", "toUpperCase(Locale)"), labels(r));
        assertEquals(4, r.prefixLength());
        assertTrue(r.exclusive());
        Suggestion withParameter = r.suggestions().get(1);
        assertEquals("toUpperCase()", withParameter.insertText());
        assertEquals(1, withParameter.caretBack());
        assertEquals(0, r.suggestions().get(0).caretBack());
    }

    @Test
    public void usesTheNearestDeclarationAndGenerics() {
        Result r = complete("import java.util.List;\nclass A {\n List<String> xs;\n void f() {\n  xs.siz|\n }\n}");
        assertEquals(List.of("size()"), labels(r));
    }

    @Test
    public void resolvesImportedAndWildcardImportedTypes() {
        assertTrue(labels(complete("import java.util.ArrayList;\nclass A { void f() { ArrayList a = null; a.ensure| } }")).contains("ensureCapacity(int)"));
        assertTrue(labels(complete("import java.util.*;\nclass A { void f() { ArrayList a = null; a.ensure| } }")).contains("ensureCapacity(int)"));
    }

    @Test
    public void suggestsOnlyStaticMembersOfAClassName() {
        List<String> labels = labels(complete("class A { void f() { Math.ma| } }"));
        assertTrue(labels.contains("max(int, int)"));
        assertFalse(labels.contains("equals(Object)"));
        assertTrue(labels(complete("class A { void f() { Integer.MAX| } }")).contains("MAX_VALUE"));
        Suggestion field = complete("class A { void f() { Integer.MAX| } }").suggestions().get(0);
        assertEquals(Type.FIELD, field.type());
    }

    @Test
    public void infersVarFromNew() {
        assertTrue(labels(complete("class A { void f() { var b = new StringBuilder(); b.revers| } }")).contains("reverse()"));
    }

    @Test
    public void thisListsDeclaredMembersOfTheFile() {
        Result r = complete("class A {\n private int count;\n String name = \"x\";\n void bump(int by) {\n  this.|\n }\n int total() { return count; }\n}");
        List<String> labels = labels(r);
        assertTrue(labels.toString(), labels.containsAll(List.of("count", "name", "bump(int)", "total()")));
    }

    @Test
    public void methodBodiesAreNotMembers() {
        Result r = complete("class A {\n void bump() {\n  int local = 1;\n }\n void g() { this.| }\n}");
        assertFalse(labels(r).contains("local"));
    }

    @Test
    public void suggestsClassNamesWithTheImportTheyNeed() {
        Result r = complete("package com.my.app;\n\nclass A { void f() { Butt| } }");
        List<String> labels = labels(r);
        assertEquals(List.of("Button", "ButtonBar", "Button"), labels);
        assertEquals("android.widget.Button", r.suggestions().get(0).importFqn());
        assertEquals("android.widget", r.suggestions().get(0).detail());
        assertNull("same package needs no import", r.suggestions().get(2).importFqn());
        assertEquals(4, r.prefixLength());
        assertFalse(r.exclusive());
    }

    @Test
    public void classNamesNeedNoImportWhenAlreadyVisible() {
        assertNull(complete("import java.util.List;\nclass A { Lis| }").suggestions().get(0).importFqn());
        assertNull(complete("import java.util.*;\nclass A { Lis| }").suggestions().get(0).importFqn());
        assertNull(complete("class A { Stri| }").suggestions().get(0).importFqn());
    }

    @Test
    public void shortPrefixesAreIgnoredExceptAfterNew() {
        assertTrue(complete("class A { void f() { S| } }").suggestions().isEmpty());
        assertFalse(complete("class A { void f() { Object o = new S| } }").suggestions().isEmpty());
    }

    @Test
    public void completesQualifiedNamesInImports() {
        Result r = complete("import java.ut|");
        assertEquals(List.of("ArrayList", "List", "Map"), labels(r));
        assertEquals("java.util.ArrayList;", r.suggestions().get(0).insertText());
        assertEquals("java.ut".length(), r.prefixLength());
        assertTrue(r.exclusive());
    }

    @Test
    public void staysQuietInsideStringsAndComments() {
        assertTrue(complete("class A { String s = \"Butt|\"; }").suggestions().isEmpty());
        assertTrue(complete("class A { String s; // Butt|\n}").suggestions().isEmpty());
        assertTrue(complete("class A { /* Butt|\n */ }").suggestions().isEmpty());
        assertTrue(labels(complete("class A { void f() { String s = \"\"; s.len| } }")).contains("length()"));
    }

    @Test
    public void doesNotCompleteChainedExpressions() {
        assertTrue(complete("class A { void f() { String s = \"\"; s.trim().len| } }").suggestions().isEmpty());
    }

    @Test
    public void computesWhereToInsertAnImport() {
        String withImports = "package a.b;\n\nimport java.util.List;\nimport java.util.Map;\n\nclass A {}";
        ImportEdit edit = JavaCompletionEngine.importEdit(withImports, "android.widget.Button");
        assertNotNull(edit);
        assertEquals("import android.widget.Button;\n", edit.text());
        assertEquals(withImports.indexOf("import java.util.Map;\n") + "import java.util.Map;\n".length(), edit.offset());

        String onlyPackage = "package a.b;\n\nclass A {}";
        edit = JavaCompletionEngine.importEdit(onlyPackage, "android.widget.Button");
        assertEquals("package a.b;\n".length(), edit.offset());
        assertEquals("\nimport android.widget.Button;\n", edit.text());

        edit = JavaCompletionEngine.importEdit("class A {}", "android.widget.Button");
        assertEquals(0, edit.offset());
        assertEquals("import android.widget.Button;\n\n", edit.text());

        assertNull(JavaCompletionEngine.importEdit(withImports, "java.util.List"));
        assertNull(JavaCompletionEngine.importEdit(withImports, "a.b.Other"));
        assertNull(JavaCompletionEngine.importEdit(withImports, "java.lang.String"));
        assertNull(JavaCompletionEngine.importEdit("import java.util.*;\nclass A {}", "java.util.Set"));
    }
}
