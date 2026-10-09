package pro.sketchware.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The findings of one or several analyses, with the project's health score.
 * <p>
 * The score starts at 100 and loses the {@linkplain Severity#weight() weight} of each finding, to a minimum of 0.
 * Repeating a rule does not cost more than twice its weight, so one noisy rule can't empty the score on its own.
 */
public final class AnalysisReport {
    private static final int MAX_COST_PER_RULE_IN_WEIGHTS = 2;

    private final List<Finding> findings;

    public AnalysisReport(List<Finding> findings) {
        List<Finding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparing((Finding f) -> f.severity()).thenComparing(Finding::category).thenComparing(Finding::id));
        this.findings = List.copyOf(sorted);
    }

    public static AnalysisReport merge(AnalysisReport... reports) {
        List<Finding> all = new ArrayList<>();
        for (AnalysisReport report : reports) all.addAll(report.findings);
        return new AnalysisReport(all);
    }

    /** Most severe first, then by category and id. */
    public List<Finding> findings() {
        return findings;
    }

    public List<Finding> findings(Category category) {
        return findings.stream().filter(f -> f.category() == category).toList();
    }

    public int count(Severity severity) {
        return (int) findings.stream().filter(f -> f.severity() == severity).count();
    }

    public Map<Category, Integer> countsByCategory() {
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Finding finding : findings) counts.merge(finding.category(), 1, Integer::sum);
        return counts;
    }

    /** 0 to 100, higher is healthier. */
    public int score() {
        Map<String, Integer> costPerRule = new java.util.HashMap<>();
        for (Finding finding : findings) {
            int cap = finding.severity().weight() * MAX_COST_PER_RULE_IN_WEIGHTS;
            costPerRule.merge(finding.id(), finding.severity().weight(), (a, b) -> Math.min(cap, a + b));
        }
        int cost = costPerRule.values().stream().mapToInt(Integer::intValue).sum();
        return Math.max(0, 100 - cost);
    }

    /** The findings to fix first: the most severe, one per rule. */
    public List<Finding> priorityFixes(int max) {
        List<Finding> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Finding finding : findings) {
            if (finding.severity() == Severity.INFO) break;
            if (seen.add(finding.id())) result.add(finding);
            if (result.size() >= max) break;
        }
        return result;
    }
}
