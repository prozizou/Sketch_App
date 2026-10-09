package pro.sketchware.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

public class BuildDoctorTest {
    private static List<String> ids(String log) {
        return BuildDoctor.diagnose(log).stream().map(Finding::id).collect(Collectors.toList());
    }

    @Test
    public void emptyOrUnrecognisedLogsGiveNothing() {
        assertEquals(List.of(), BuildDoctor.diagnose(null));
        assertEquals(List.of(), BuildDoctor.diagnose("   \n"));
        assertEquals(List.of(), BuildDoctor.diagnose("Everything went fine.\nBUILD SUCCESSFUL"));
    }

    @Test
    public void outOfMemory() {
        for (String log : new String[]{"Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space", "GC overhead limit exceeded"}) {
            assertEquals(List.of("build.out-of-memory"), ids(log));
        }
        Finding f = BuildDoctor.diagnose("x\nException: java.lang.OutOfMemoryError: Java heap space\ny").get(0);
        assertEquals(Severity.ERROR, f.severity());
        assertEquals(Category.BUILD, f.category());
        assertEquals("Exception: java.lang.OutOfMemoryError: Java heap space", f.evidence());
        assertTrue(f.solution().contains("shrinker"));
    }

    @Test
    public void duplicateClassesFromD8() {
        String log = "Type com.google.common.util.concurrent.ListenableFuture is defined multiple times: /a/guava.jar:com/google/..., /b/lib.jar:com/google/...";
        assertEquals(List.of("build.duplicate-class"), ids(log));
        assertTrue(BuildDoctor.diagnose(log).get(0).solution().contains("Exclude built-in"));
    }

    @Test
    public void dexLimit() {
        assertEquals(List.of("build.dex-limit"), ids("Cannot fit requested classes in a single dex file (# fields: 70000 > 65536)"));
    }

    @Test
    public void aapt2() {
        assertEquals(List.of("build.aapt2-resource-missing"),
                ids("res/layout/main.xml:12: error: resource attr/colorOnPrimary (aka com.my.app:attr/colorOnPrimary) not found."));
        assertEquals(List.of("build.aapt2-attribute-missing"),
                ids("res/layout/main.xml:7: error: attribute app:cardCornerRadius (aka com.my.app:cardCornerRadius) not found."));
        assertEquals(List.of("build.aapt2-resource-missing"), ids("error: failed linking references."));
    }

    @Test
    public void ecj() {
        String log = "1. ERROR in /p/Main.java (at line 3)\n\timport androidx.foo.Bar;\n\nThe import androidx.foo cannot be resolved\n"
                + "2. ERROR in /p/Main.java (at line 9)\n\tButton b;\nButton cannot be resolved to a type\n";
        assertEquals(List.of("build.ecj-unresolved-import", "build.ecj-unresolved-symbol"), ids(log));
        assertEquals(List.of("build.java-syntax"), ids("Syntax error on token \";\", delete this token"));
    }

    @Test
    public void kotlin() {
        assertEquals(List.of("build.kotlin"), ids("e: /data/605/files/java/Foo.kt: (10, 5): Unresolved reference: bar"));
    }

    @Test
    public void r8() {
        List<String> ids = ids("R8: Missing class org.slf4j.impl.StaticLoggerBinder (referenced from: void foo())");
        assertTrue(ids.contains("build.r8-missing-class"));
        assertTrue(ids.contains("build.r8-failure"));
        assertEquals(Severity.WARNING, BuildDoctor.diagnose("Missing class org.x.Y (referenced from: a)").get(0).severity());
    }

    @Test
    public void signingStorageAndNetwork() {
        assertEquals(List.of("build.signing"), ids("java.io.IOException: Keystore was tampered with, or password was incorrect"));
        assertEquals(List.of("build.no-space"), ids("java.io.IOException: No space left on device"));
        assertEquals(List.of("build.download"), ids("java.net.UnknownHostException: repo1.maven.org"));
    }

    @Test
    public void minSdkAndClassVersion() {
        assertEquals(List.of("build.default-interface-methods"),
                ids("Default interface methods are only supported starting with Android N (--min-api 24)"));
        assertEquals(List.of("build.wrong-class-version"), ids("Unsupported class file major version 65"));
    }

    @Test
    public void severeFindingsComeFirstAndEachRuleReportsOnce() {
        String log = "Missing class a.B (referenced from: x)\nMissing class a.C (referenced from: y)\njava.lang.OutOfMemoryError\n";
        List<Finding> findings = BuildDoctor.diagnose(log);
        assertEquals(Severity.ERROR, findings.get(0).severity());
        assertEquals(1, findings.stream().filter(f -> f.id().equals("build.r8-missing-class")).count());
        assertFalse(findings.isEmpty());
    }

    @Test
    public void everyRuleHasAnIdTitleCauseAndSolution() {
        // A log containing a sample of everything exercises every rule's text through the Finding constructor
        String log = String.join("\n", "OutOfMemoryError", "No space left on device", "is defined multiple times", "Cannot fit requested classes in a single dex file",
                "Unsupported class file major version 99", "Default interface methods are only supported starting with Android N",
                "error: resource attr/x (aka p:attr/x) not found", "error: attribute a:b not found", "Manifest merger failed",
                "The import a.b cannot be resolved", "Foo cannot be resolved to a type", "Syntax error on token", "e: /a/B.kt: (1, 2): x",
                "Missing class a.B (referenced from: c)", "R8: boom", "Keystore was tampered with, or password was incorrect", "UnknownHostException", "NoClassDefFoundError");
        List<Finding> findings = BuildDoctor.diagnose(log);
        assertEquals(18, findings.size());
        for (Finding f : findings) {
            assertTrue(f.id(), f.id().startsWith("build."));
            assertFalse(f.title().isBlank());
            assertFalse(f.cause().isBlank());
            assertFalse(f.solution().isBlank());
        }
    }
}
