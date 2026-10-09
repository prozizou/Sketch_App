package pro.sketchware.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class AnalysisReportTest {
    private static Finding finding(String id, Category category, Severity severity) {
        return new Finding(id, category, severity, "title " + id, "cause", "solution", null);
    }

    @Test
    public void aCleanProjectScoresOneHundred() {
        AnalysisReport report = new AnalysisReport(List.of());
        assertEquals(100, report.score());
        assertEquals(List.of(), report.priorityFixes(5));
    }

    @Test
    public void eachFindingCostsItsSeverityWeight() {
        AnalysisReport report = new AnalysisReport(List.of(
                finding("a", Category.SECURITY, Severity.ERROR),
                finding("b", Category.COMPATIBILITY, Severity.WARNING),
                finding("c", Category.QUALITY, Severity.INFO)));
        assertEquals(100 - 15 - 5 - 1, report.score());
    }

    @Test
    public void aRepeatedRuleCostsAtMostTwiceItsWeight() {
        Finding warning = finding("noisy", Category.QUALITY, Severity.WARNING);
        AnalysisReport report = new AnalysisReport(List.of(warning, warning, warning, warning, warning, warning));
        assertEquals(100 - 10, report.score());
    }

    @Test
    public void theScoreNeverGoesBelowZero() {
        java.util.ArrayList<Finding> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) many.add(finding("rule" + i, Category.SECURITY, Severity.ERROR));
        assertEquals(0, new AnalysisReport(many).score());
    }

    @Test
    public void findingsAreOrderedMostSevereFirst() {
        AnalysisReport report = new AnalysisReport(List.of(
                finding("i", Category.QUALITY, Severity.INFO),
                finding("w", Category.SECURITY, Severity.WARNING),
                finding("e", Category.BUILD, Severity.ERROR)));
        assertEquals(List.of("e", "w", "i"), report.findings().stream().map(Finding::id).toList());
    }

    @Test
    public void priorityFixesSkipInfoAndRepeatsAndRespectTheLimit() {
        AnalysisReport report = new AnalysisReport(List.of(
                finding("e1", Category.SECURITY, Severity.ERROR),
                finding("e1", Category.SECURITY, Severity.ERROR),
                finding("e2", Category.BUILD, Severity.ERROR),
                finding("w1", Category.COMPATIBILITY, Severity.WARNING),
                finding("i1", Category.QUALITY, Severity.INFO)));
        assertEquals(List.of("e1", "e2"), report.priorityFixes(2).stream().map(Finding::id).toList());
        assertEquals(List.of("e1", "e2", "w1"), report.priorityFixes(10).stream().map(Finding::id).toList());
    }

    @Test
    public void mergeCombinesReportsAndCountsByCategory() {
        AnalysisReport a = new AnalysisReport(List.of(finding("a", Category.SECURITY, Severity.ERROR)));
        AnalysisReport b = new AnalysisReport(List.of(finding("b", Category.SECURITY, Severity.WARNING), finding("c", Category.BUILD, Severity.INFO)));
        AnalysisReport merged = AnalysisReport.merge(a, b);
        assertEquals(3, merged.findings().size());
        assertEquals(2, (int) merged.countsByCategory().get(Category.SECURITY));
        assertEquals(1, merged.count(Severity.ERROR));
        assertEquals(1, merged.findings(Category.BUILD).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aFindingNeedsAllItsParts() {
        new Finding("x", Category.BUILD, Severity.ERROR, "title", null, "solution", null);
    }

    @Test
    public void evidenceCanBeAttachedLater() {
        Finding f = finding("x", Category.BUILD, Severity.ERROR).withEvidence("line 3");
        assertEquals("line 3", f.evidence());
        assertTrue(f.title().startsWith("title"));
    }
}
