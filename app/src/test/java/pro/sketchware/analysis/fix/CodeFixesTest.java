package pro.sketchware.analysis.fix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import pro.sketchware.analysis.CompatibilityAnalyzer;
import pro.sketchware.analysis.Finding;
import pro.sketchware.analysis.ProjectFacts;
import pro.sketchware.analysis.SourceFile;

public class CodeFixesTest {
    private static final String SOURCE = """
            package com.my.app;

            import android.app.PendingIntent;
            import android.content.Intent;
            import android.os.Handler;
            import android.support.v7.app.AppCompatActivity;
            import android.support.v4.content.ContextCompat;
            import android.support.v13.Unknown;

            public class Util {
                // new Handler() in a comment stays
                String s = "new Handler()";
                Handler a = new Handler();
                Handler b = new Handler( );
                void notify(android.content.Context c, Intent i) {
                    PendingIntent p1 = PendingIntent.getActivity(c, 0, i, 0);
                    PendingIntent p2 = PendingIntent.getBroadcast(c, foo(1, 2), i, PendingIntent.FLAG_UPDATE_CURRENT);
                    PendingIntent p3 = android.app.PendingIntent.getService(c, 0, new Intent(c, Util.class), PendingIntent.FLAG_ONE_SHOT, null);
                    PendingIntent ok = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE);
                }
                int foo(int x, int y) { return x + y; }
            }
            """;

    private static List<Finding> analyze(String content, boolean androidX) {
        ProjectFacts facts = new ProjectFacts("com.my.app", 24, 35, Set.of(), List.of(new SourceFile("Util.java", content)), Set.of(),
                androidX, Set.of(), List.of(), true);
        return CompatibilityAnalyzer.analyze(facts);
    }

    private static boolean has(List<Finding> findings, String id) {
        return findings.stream().anyMatch(f -> f.id().equals(id));
    }

    @Test
    public void handlersGetTheMainLooperAndItsImport() {
        CodeFixes.Result result = CodeFixes.handlerLooper(SOURCE);
        assertEquals(2, result.changes());
        assertTrue(result.content().contains("Handler a = new Handler(Looper.getMainLooper());"));
        assertTrue(result.content().contains("// new Handler() in a comment stays"));
        assertTrue(result.content().contains("String s = \"new Handler()\";"));
        assertTrue(result.content().contains("import android.os.Looper;"));
        assertEquals(result.content(), CodeFixes.handlerLooper(result.content()).content());   // nothing left to do
    }

    @Test
    public void filesWithTheirOwnLoopersAreLeftAlone() {
        String code = "class T { void run() { Looper.prepare(); h = new Handler(); } }";
        assertEquals(0, CodeFixes.handlerLooper(code).changes());
    }

    @Test
    public void pendingIntentsGetImmutable() {
        CodeFixes.Result result = CodeFixes.pendingIntentFlags(SOURCE);
        assertEquals(3, result.changes());
        String out = result.content();
        assertTrue(out.contains("PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE);"));
        assertTrue(out.contains("PendingIntent.getBroadcast(c, foo(1, 2), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);"));
        assertTrue(out.contains("getService(c, 0, new Intent(c, Util.class), PendingIntent.FLAG_ONE_SHOT | android.app.PendingIntent.FLAG_IMMUTABLE, null);"));
        assertTrue(out.contains("PendingIntent ok = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE);"));
        assertEquals(0, CodeFixes.pendingIntentFlags(out).changes());
    }

    @Test
    public void knownSupportImportsBecomeAndroidX() {
        CodeFixes.Result result = CodeFixes.supportImports(SOURCE);
        assertEquals(2, result.changes());
        assertTrue(result.content().contains("import androidx.appcompat.app.AppCompatActivity;"));
        assertTrue(result.content().contains("import androidx.core.content.ContextCompat;"));
        assertTrue(result.content().contains("import android.support.v13.Unknown;"));   // no known replacement
    }

    @Test
    public void theFindingsAreGoneAfterTheRewrite() {
        String clean = SOURCE.replace("import android.support.v13.Unknown;\n", "");
        List<Finding> before = analyze(clean, true);
        assertTrue(has(before, "compat.handler-no-looper"));
        assertTrue(has(before, "compat.pending-intent-flag"));
        assertTrue(has(before, "compat.support-library"));
        String fixed = AutoFix.rewrite(clean, EnumSet.of(AutoFix.Rule.HANDLER_LOOPER, AutoFix.Rule.PENDING_INTENT_FLAG, AutoFix.Rule.SUPPORT_LIBRARY)).content();
        List<Finding> after = analyze(fixed, true);
        assertFalse(has(after, "compat.handler-no-looper"));
        assertFalse(has(after, "compat.pending-intent-flag"));
        assertFalse(has(after, "compat.support-library"));
    }

    @Test
    public void thePlanCountsPerFileAndSkipsSupportImportsWithoutAndroidX() {
        List<SourceFile> sources = List.of(new SourceFile("Util.java", SOURCE, "/p/Util.java"), new SourceFile("Other.kt", "val h = Handler()"));
        AutoFix.Plan withAndroidX = AutoFix.plan(List.of(), List.of(), sources, true);
        assertEquals(2, withAndroidX.count(AutoFix.Rule.HANDLER_LOOPER));
        assertEquals(3, withAndroidX.count(AutoFix.Rule.PENDING_INTENT_FLAG));
        assertEquals(2, withAndroidX.count(AutoFix.Rule.SUPPORT_LIBRARY));
        assertEquals("/p/Util.java", withAndroidX.fileEdits().get(0).path());
        assertEquals(0, AutoFix.plan(List.of(), List.of(), sources, false).count(AutoFix.Rule.SUPPORT_LIBRARY));
        assertEquals(2, withAndroidX.only(EnumSet.of(AutoFix.Rule.HANDLER_LOOPER)).count(AutoFix.Rule.HANDLER_LOOPER));
        assertEquals(0, withAndroidX.only(EnumSet.of(AutoFix.Rule.HANDLER_LOOPER)).count(AutoFix.Rule.PENDING_INTENT_FLAG));
    }

    @Test
    public void importsGoAfterTheLastImportOrThePackage() {
        assertEquals("package a;\n\nimport android.os.Looper;\nclass X {}", CodeFixes.addImport("package a;\nclass X {}", "android.os.Looper"));
        assertEquals("import a.B;\nimport android.os.Looper;\nclass X {}", CodeFixes.addImport("import a.B;\nclass X {}", "android.os.Looper"));
        assertEquals("import android.os.*;\nclass X {}", CodeFixes.addImport("import android.os.*;\nclass X {}", "android.os.Looper"));
    }
}
