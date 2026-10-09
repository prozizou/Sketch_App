package pro.sketchware.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import pro.sketchware.analysis.DependencyInspector.ExclusionCheck;
import pro.sketchware.analysis.DependencyInspector.Library;
import pro.sketchware.analysis.DependencyInspector.Node;

public class DependencyInspectorTest {
    /** appcompat <-> appcompat-resources is a real cycle in the built-in graph. */
    private static Map<String, Library> graph() {
        Map<String, Library> g = new TreeMap<>();
        g.put("appcompat-1.7.1", new Library("appcompat-1.7.1", List.of("core-1.17.0", "appcompat-resources-1.7.1", "annotation-1.9.1"), Set.of("androidx.appcompat")));
        g.put("appcompat-resources-1.7.1", new Library("appcompat-resources-1.7.1", List.of("appcompat-1.7.1", "annotation-1.9.1"), Set.of("androidx.appcompat.resources")));
        g.put("core-1.17.0", new Library("core-1.17.0", List.of("annotation-1.9.1", "lifecycle-2.6.2"), Set.of("androidx.core")));
        g.put("lifecycle-2.6.2", new Library("lifecycle-2.6.2", List.of("annotation-1.9.1"), Set.of("androidx.lifecycle")));
        g.put("annotation-1.9.1", new Library("annotation-1.9.1", List.of(), Set.of("androidx.annotation")));
        g.put("cardview-1.0.0", new Library("cardview-1.0.0", List.of("annotation-1.9.1"), Set.of("androidx.cardview")));
        return g;
    }

    @Test
    public void theTreeShowsEachLibraryOnceAndSurvivesCycles() {
        List<Node> tree = DependencyInspector.tree(graph(), List.of("appcompat-1.7.1"));
        List<String> lines = tree.stream().map(n -> "  ".repeat(n.depth()) + n.name() + (n.repeated() ? " (see above)" : "")).collect(Collectors.toList());
        assertEquals(List.of(
                "appcompat-1.7.1",
                "  annotation-1.9.1",
                "  appcompat-resources-1.7.1",
                "    annotation-1.9.1 (see above)",
                "    appcompat-1.7.1 (see above)",
                "  core-1.17.0",
                "    annotation-1.9.1 (see above)",
                "    lifecycle-2.6.2",
                "      annotation-1.9.1 (see above)"), lines);
    }

    @Test
    public void closureFollowsDependenciesAndCanSkipLibraries() {
        assertEquals(Set.of("cardview-1.0.0", "annotation-1.9.1"), DependencyInspector.closure(graph(), List.of("cardview-1.0.0"), Set.of()));
        assertEquals(Set.of("cardview-1.0.0"), DependencyInspector.closure(graph(), List.of("cardview-1.0.0"), Set.of("annotation-1.9.1")));
    }

    @Test
    public void duplicateClassesAreReportedPerPair() {
        List<Finding> findings = DependencyInspector.findDuplicateClasses(Map.of(
                "guava-31", Set.of("com.google.common.A", "com.google.common.B", "module-info"),
                "my-lib", Set.of("com.google.common.A", "com.google.common.B", "module-info", "com.mine.C"),
                "other", Set.of("com.other.D")));
        assertEquals(1, findings.size());
        Finding f = findings.get(0);
        assertEquals(Severity.ERROR, f.severity());
        assertEquals("Libraries that contain the same classes (1 pair)", f.title());
        assertEquals("guava-31 and my-lib: 2 classes, for example com.google.common.A", f.evidence());
        assertEquals(List.of(), DependencyInspector.findDuplicateClasses(Map.of("a", Set.of("x.A"), "b", Set.of("x.B"))));
    }

    @Test
    public void versionConflictsAreFoundFromTheNames() {
        List<Finding> findings = DependencyInspector.findVersionConflicts(List.of("core-1.9.0", "core-1.17.0", "gson-2.8.5", "cardview-1.0.0", "no-version"));
        assertEquals(1, findings.size());
        assertEquals("core: 1.17.0, 1.9.0", findings.get(0).evidence());
        assertEquals(List.of(), DependencyInspector.findVersionConflicts(List.of("core-1.9.0", "gson-2.8.5")));
        assertEquals(List.of(), DependencyInspector.findVersionConflicts(List.of()));
    }

    @Test
    public void mixingSupportLibraryAndAndroidXIsAnError() {
        List<Finding> findings = DependencyInspector.findSupportLibraryMix(Map.of(
                "old-lib", Set.of("android.support.v4.app.Fragment"), "appcompat-1.7.1", Set.of("androidx.appcompat.app.AppCompatActivity")));
        assertEquals("deps.support-androidx-mix", findings.get(0).id());
        assertEquals("old-lib", findings.get(0).evidence());
        assertEquals(List.of(), DependencyInspector.findSupportLibraryMix(Map.of("old-lib", Set.of("android.support.v4.app.Fragment"))));
        assertEquals(List.of(), DependencyInspector.findSupportLibraryMix(Map.of("a", Set.of("androidx.core.A"))));
    }

    @Test
    public void excludingWhatARemainingLibraryNeedsWarns() {
        ExclusionCheck check = DependencyInspector.checkExclusion(graph(), List.of("appcompat-1.7.1"), Set.of("core-1.17.0"), Set.of());
        assertFalse(check.safe());
        Finding f = check.findings().stream().filter(x -> x.id().equals("deps.exclude-breaks-dependents")).findFirst().orElseThrow();
        assertEquals(Severity.WARNING, f.severity());
        assertEquals("appcompat-1.7.1 needs core-1.17.0", f.evidence());
        assertEquals(Set.of("lifecycle-2.6.2"), check.alsoUnused());
    }

    @Test
    public void aLocalLibraryThatProvidesThePackageMakesTheExclusionSafe() {
        ExclusionCheck check = DependencyInspector.checkExclusion(graph(), List.of("appcompat-1.7.1"), Set.of("core-1.17.0"), Set.of("androidx.core"));
        assertFalse(check.findings().stream().anyMatch(f -> f.id().equals("deps.exclude-breaks-dependents")));
    }

    @Test
    public void excludingALibraryTheFeaturesNeedIsAnError() {
        ExclusionCheck check = DependencyInspector.checkExclusion(graph(), List.of("appcompat-1.7.1", "cardview-1.0.0"), Set.of("appcompat-1.7.1"), Set.of());
        assertFalse(check.safe());
        Finding f = check.findings().stream().filter(x -> x.id().equals("deps.exclude-required")).findFirst().orElseThrow();
        assertEquals(Severity.ERROR, f.severity());
        assertEquals("appcompat-1.7.1", f.evidence());
    }

    @Test
    public void excludingSomethingThatIsNotInTheProjectChangesNothing() {
        ExclusionCheck check = DependencyInspector.checkExclusion(graph(), List.of("cardview-1.0.0"), Set.of("core-1.17.0"), Set.of());
        assertTrue(check.safe());
        assertEquals(Set.of(), check.alsoUnused());
    }
}
