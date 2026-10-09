package pro.sketchware.analysis;

import java.util.Set;

import pro.sketchware.analysis.DependencyInspector.ExclusionCheck;

/** Checks, for an open project, what excluding some built-in libraries would break. Run it off the main thread. */
public final class ExclusionValidator {
    private ExclusionValidator() {
    }

    public static ExclusionCheck check(String scId, Set<String> excludedLibraryNames) {
        return DependencyInspector.checkExclusion(ProjectFactsLoader.builtInGraph(), ProjectFactsLoader.builtInRoots(scId),
                excludedLibraryNames, ProjectFactsLoader.packagesOfLocalLibraries(scId));
    }
}
