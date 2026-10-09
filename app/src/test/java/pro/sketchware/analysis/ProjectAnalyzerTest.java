package pro.sketchware.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProjectAnalyzerTest {
    @Test
    public void aCleanProjectScoresOneHundred() {
        ProjectFacts facts = new ProjectFacts("com.my.app", 24, 34, Set.of("android.permission.INTERNET"), List.of(new SourceFile("A.java", "class A {}")),
                Set.of(), true, Set.of(), List.of(), true);
        AnalysisReport report = ProjectAnalyzer.run(facts, List.of(), List.of("appcompat-1.7.1"), Map.of("appcompat-1.7.1", Set.of("androidx.appcompat.A")));
        assertEquals(List.of(), report.findings());
        assertEquals(100, report.score());
    }

    @Test
    public void findingsFromEveryAnalysisAreCombinedAndScored() {
        ProjectFacts facts = new ProjectFacts("com.my.app", 24, 28, Set.of("android.permission.MANAGE_EXTERNAL_STORAGE"),
                List.of(new SourceFile("A.java", "String key = \"AKIAIOSFODNN7EXAMPLE\";")), Set.of(), true, Set.of(), List.of(), true);
        AnalysisReport report = ProjectAnalyzer.run(facts, List.of(), List.of("gson-2.8.5", "core-1.9.0", "core-1.17.0"),
                Map.of("a-1", Set.of("x.Same"), "b-1", Set.of("x.Same")));

        List<String> ids = report.findings().stream().map(Finding::id).toList();
        assertTrue(ids.toString(), ids.containsAll(List.of("compat.target-sdk-old", "compat.perm-manage-storage", "security.secret-aws",
                "security.vulnerable-library", "deps.duplicate-classes", "deps.version-conflict")));
        assertEquals(Severity.ERROR, report.findings().get(0).severity());
        assertTrue(report.score() < 100);
        assertTrue(report.priorityFixes(3).stream().allMatch(f -> f.severity() != Severity.INFO));
    }
}
