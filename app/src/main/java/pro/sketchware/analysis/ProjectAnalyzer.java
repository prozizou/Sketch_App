package pro.sketchware.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runs every project analysis and combines their findings into one report with the health score. */
public final class ProjectAnalyzer {
    private ProjectAnalyzer() {
    }

    /**
     * @param files             text files other than code (resources, assets, configuration), for the secret scan
     * @param libraryNames      libraries that end up in the app
     * @param classesByLibrary  the classes each of those libraries contains; a library missing from the map is not checked
     */
    public static AnalysisReport run(ProjectFacts facts, List<SourceFile> files, List<String> libraryNames, Map<String, Set<String>> classesByLibrary) {
        List<Finding> findings = new ArrayList<>();
        findings.addAll(CompatibilityAnalyzer.analyze(facts));
        findings.addAll(DesignSystemChecker.analyze(facts.views()));
        findings.addAll(SecurityScanner.analyze(facts, files, libraryNames));
        findings.addAll(DependencyInspector.findDuplicateClasses(classesByLibrary));
        findings.addAll(DependencyInspector.findVersionConflicts(libraryNames));
        findings.addAll(DependencyInspector.findSupportLibraryMix(classesByLibrary));
        return new AnalysisReport(findings);
    }
}
