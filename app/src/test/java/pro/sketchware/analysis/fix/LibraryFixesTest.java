package pro.sketchware.analysis.fix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import pro.sketchware.analysis.DependencyInspector;
import pro.sketchware.analysis.DependencyInspector.Library;
import pro.sketchware.analysis.fix.LibraryFixes.Action;
import pro.sketchware.analysis.fix.LibraryFixes.Kind;
import pro.sketchware.analysis.fix.LibraryFixes.Libraries;
import pro.sketchware.analysis.fix.LibraryFixes.Plan;

public class LibraryFixesTest {
    private static Map<String, Library> graph() {
        Map<String, Library> g = new TreeMap<>();
        g.put("appcompat-1.7.1", new Library("appcompat-1.7.1", List.of("core-1.17.0"), Set.of("androidx.appcompat")));
        g.put("core-1.17.0", new Library("core-1.17.0", List.of(), Set.of("androidx.core")));
        g.put("cardview-1.0.0", new Library("cardview-1.0.0", List.of(), Set.of("androidx.cardview")));
        return g;
    }

    private static Libraries libraries(Map<String, Set<String>> classes, Set<String> local) {
        Set<String> builtIn = new java.util.TreeSet<>(classes.keySet());
        builtIn.removeAll(local);
        return new Libraries(classes, local, builtIn, graph(), List.of("appcompat-1.7.1"), Set.of());
    }

    private static List<String> names(Plan plan, Kind kind) {
        return plan.actions().stream().filter(a -> a.kind() == kind).map(Action::library).sorted().toList();
    }

    @Test
    public void onlyTheThreeLibraryFindingsAreHandled() {
        assertTrue(LibraryFixes.handles("deps.support-androidx-mix"));
        assertTrue(LibraryFixes.handles("deps.duplicate-classes"));
        assertTrue(LibraryFixes.handles("deps.version-conflict"));
        assertFalse(LibraryFixes.handles("compat.support-library"));
        assertFalse(LibraryFixes.handles("deps.exclude-required"));
    }

    @Test
    public void aLocalLibraryThatUsesTheSupportLibraryIsTakenOffTheProject() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("appcompat-1.7.1", Set.of("androidx.appcompat.app.AppCompatActivity"));
        classes.put("old-ui", Set.of("com.old.Ui", "android.support.v7.app.AppCompatActivity"));
        Plan plan = LibraryFixes.plan(LibraryFixes.SUPPORT_MIX, libraries(classes, Set.of("old-ui")));
        assertEquals(List.of("old-ui"), names(plan, Kind.REMOVE_LOCAL));
        assertTrue(names(plan, Kind.EXCLUDE_BUILT_IN).isEmpty());
        assertTrue(plan.unresolved().isEmpty());
        assertTrue(plan.actions().get(0).reason().contains("Support Library"));
    }

    @Test
    public void aBuiltInLibraryNobodyNeedsIsExcluded() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("appcompat-1.7.1", Set.of("androidx.appcompat.A"));
        classes.put("cardview-1.0.0", Set.of("android.support.v7.widget.CardView"));
        Plan plan = LibraryFixes.plan(LibraryFixes.SUPPORT_MIX, libraries(classes, Set.of()));
        assertEquals(List.of("cardview-1.0.0"), names(plan, Kind.EXCLUDE_BUILT_IN));
    }

    @Test
    public void aBuiltInLibraryTheProjectNeedsIsNotExcludedAndIsExplained() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("appcompat-1.7.1", Set.of("androidx.appcompat.A", "android.support.v4.Legacy"));
        Plan plan = LibraryFixes.plan(LibraryFixes.SUPPORT_MIX, libraries(classes, Set.of()));
        assertTrue("excluding the feature's own library would break the build", plan.isEmpty());
        assertEquals(1, plan.unresolved().size());
        assertTrue(plan.unresolved().get(0).contains("appcompat-1.7.1"));
    }

    @Test
    public void theOlderVersionOfALibraryGoesAndTheNewestStays() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("glide-4.9.0", Set.of("com.bumptech.glide.A"));
        classes.put("glide-4.16.0", Set.of("com.bumptech.glide.A"));
        classes.put("glide-4.10.0", Set.of("com.bumptech.glide.A"));
        Plan plan = LibraryFixes.plan(LibraryFixes.VERSION_CONFLICT, libraries(classes, Set.of("glide-4.9.0", "glide-4.16.0", "glide-4.10.0")));
        assertEquals("numbers are compared, not text: 4.9 < 4.10 < 4.16", List.of("glide-4.10.0", "glide-4.9.0"), names(plan, Kind.REMOVE_LOCAL));
    }

    @Test
    public void versionsAreOrderedByNumberAndQualifiers() {
        assertTrue(DependencyInspector.compareVersions("1.9.0", "1.17.0") < 0);
        assertTrue(DependencyInspector.compareVersions("1.0.0-alpha01", "1.0.0") < 0);
        assertTrue(DependencyInspector.compareVersions("1.0.0-beta01", "1.0.0-alpha05") > 0);
        assertEquals(0, DependencyInspector.compareVersions("2.1", "2.1"));
        assertTrue(DependencyInspector.compareVersions("2.1", "2.1.1") < 0);
    }

    @Test
    public void aLibraryFullyContainedInAnotherIsTheOneThatGoes() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("big-lib", Set.of("a.A", "a.B", "a.C"));
        classes.put("small-lib", Set.of("a.A", "a.B"));
        Plan plan = LibraryFixes.plan(LibraryFixes.DUPLICATE_CLASSES, libraries(classes, Set.of("big-lib", "small-lib")));
        assertEquals(List.of("small-lib"), names(plan, Kind.REMOVE_LOCAL));
        assertTrue(plan.actions().get(0).reason().contains("big-lib"));
    }

    @Test
    public void ofTwoCopiesOfTheSameClassesTheLocalOneGoes() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("cardview-1.0.0", Set.of("androidx.cardview.widget.CardView"));
        classes.put("cardview-copy", Set.of("androidx.cardview.widget.CardView"));
        Plan plan = LibraryFixes.plan(LibraryFixes.DUPLICATE_CLASSES, libraries(classes, Set.of("cardview-copy")));
        assertEquals(List.of("cardview-copy"), names(plan, Kind.REMOVE_LOCAL));
        assertTrue(names(plan, Kind.EXCLUDE_BUILT_IN).isEmpty());
    }

    @Test
    public void whenNeitherContainsTheOtherNothingIsGuessed() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("one-lib", Set.of("a.Shared", "a.OnlyOne"));
        classes.put("two-lib", Set.of("a.Shared", "a.OnlyTwo"));
        Plan plan = LibraryFixes.plan(LibraryFixes.DUPLICATE_CLASSES, libraries(classes, Set.of("one-lib", "two-lib")));
        assertTrue(plan.isEmpty());
        assertEquals(1, plan.unresolved().size());
        assertTrue(plan.unresolved().get(0).contains("one-lib and two-lib"));
    }

    @Test
    public void aChainOfContainedLibrariesLeavesOnlyTheBiggest() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("a-lib", Set.of("x.A"));
        classes.put("b-lib", Set.of("x.A", "x.B"));
        classes.put("c-lib", Set.of("x.A", "x.B", "x.C"));
        Plan plan = LibraryFixes.plan(LibraryFixes.DUPLICATE_CLASSES, libraries(classes, Set.of("a-lib", "b-lib", "c-lib")));
        assertEquals(List.of("a-lib", "b-lib"), names(plan, Kind.REMOVE_LOCAL));
    }

    @Test
    public void moduleInfoAndPackageInfoDoNotCountAsDuplicates() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("one-lib", Set.of("module-info", "a.One"));
        classes.put("two-lib", Set.of("module-info", "a.Two"));
        Plan plan = LibraryFixes.plan(LibraryFixes.DUPLICATE_CLASSES, libraries(classes, Set.of("one-lib", "two-lib")));
        assertTrue(plan.isEmpty());
        assertTrue(plan.unresolved().isEmpty());
    }

    @Test
    public void aFixNeverPlansTheSameLibraryTwice() {
        Map<String, Set<String>> classes = new TreeMap<>();
        classes.put("glide-4.9.0", Set.of("g.A"));
        classes.put("glide-4.16.0", Set.of("g.A", "g.B"));
        Plan plan = LibraryFixes.plan(LibraryFixes.DUPLICATE_CLASSES, libraries(classes, Set.of("glide-4.9.0", "glide-4.16.0")));
        assertEquals(List.of("glide-4.9.0"), names(plan, Kind.REMOVE_LOCAL));
        assertEquals(1, plan.actions().size());
    }
}
