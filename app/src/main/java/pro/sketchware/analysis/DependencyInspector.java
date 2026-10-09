package pro.sketchware.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Dependency Inspector's engine: shows which library needs which, finds libraries that contain the same classes
 * or exist in two versions, and checks an exclusion before it is applied.
 * <p>
 * It works on names and class lists given to it, so it can be tested without a device. The graph's nodes are
 * library names as the build knows them, with the version in the name ({@code appcompat-1.7.1}).
 */
public final class DependencyInspector {
    /**
     * @param dependencies names of the libraries this one needs directly
     * @param packages     Java packages the library provides; used to see whether an excluded library is replaced by another
     */
    public record Library(String name, List<String> dependencies, Set<String> packages) {
    }

    /** One line of the dependency tree. {@code repeated} nodes were already shown above, so their own needs are not repeated. */
    public record Node(String name, int depth, boolean repeated) {
    }

    /**
     * @param safe          nothing the project needs is lost
     * @param findings      what would be missing or left over
     * @param alsoUnused    libraries only the excluded ones needed; they stay in the app unless excluded too
     */
    public record ExclusionCheck(boolean safe, List<Finding> findings, Set<String> alsoUnused) {
    }

    private static final Pattern VERSIONED = Pattern.compile("^(.+?)-(\\d+(?:\\.\\d+)*(?:[-.][A-Za-z0-9]+)*)$");
    private static final int MAX_PAIRS_REPORTED = 10;

    private DependencyInspector() {
    }

    // ---- the tree

    /** Depth-first tree of everything the roots need. A library shown once is not expanded again, which also stops cycles. */
    public static List<Node> tree(Map<String, Library> graph, Collection<String> roots) {
        List<Node> lines = new ArrayList<>();
        Set<String> shown = new HashSet<>();
        for (String root : new TreeSet<>(roots)) walk(graph, root, 0, shown, lines);
        return lines;
    }

    private static void walk(Map<String, Library> graph, String name, int depth, Set<String> shown, List<Node> lines) {
        boolean repeated = !shown.add(name);
        lines.add(new Node(name, depth, repeated));
        if (repeated) return;
        Library library = graph.get(name);
        if (library == null) return;
        for (String dependency : new TreeSet<>(library.dependencies())) walk(graph, dependency, depth + 1, shown, lines);
    }

    /** Every library reachable from the roots, the roots included. */
    public static Set<String> closure(Map<String, Library> graph, Collection<String> roots, Set<String> doNotEnter) {
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (String root : roots) if (!doNotEnter.contains(root)) pending.add(root);
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (!reached.add(name)) continue;
            Library library = graph.get(name);
            if (library == null) continue;
            for (String dependency : library.dependencies()) if (!doNotEnter.contains(dependency)) pending.add(dependency);
        }
        return reached;
    }

    // ---- problems in the libraries that are in the project

    /** Libraries that contain the same classes. D8 refuses to build when a class is defined twice. */
    public static List<Finding> findDuplicateClasses(Map<String, Set<String>> classesByLibrary) {
        List<String> names = new ArrayList<>(new TreeSet<>(classesByLibrary.keySet()));
        List<String> pairs = new ArrayList<>();
        int totalPairs = 0;
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                Set<String> overlap = new TreeSet<>(classesByLibrary.get(names.get(i)));
                overlap.retainAll(classesByLibrary.get(names.get(j)));
                overlap.removeIf(c -> c.endsWith("module-info") || c.endsWith("package-info"));
                if (overlap.isEmpty()) continue;
                totalPairs++;
                if (pairs.size() < MAX_PAIRS_REPORTED) {
                    pairs.add(names.get(i) + " and " + names.get(j) + ": " + overlap.size() + (overlap.size() == 1 ? " class" : " classes")
                            + ", for example " + overlap.iterator().next());
                }
            }
        }
        if (pairs.isEmpty()) return List.of();
        return List.of(new Finding("deps.duplicate-classes", Category.DEPENDENCIES, Severity.ERROR,
                "Libraries that contain the same classes (" + totalPairs + (totalPairs == 1 ? " pair)" : " pairs)"),
                "When two libraries define the same class the build stops with \"defined multiple times\", or the app picks one at random.",
                "Keep one of each pair: remove the extra library, or exclude the built-in copy in Library Manager > Exclude built-in libraries.",
                String.join("\n", pairs)));
    }

    /** The same library present in several versions, judged from the names ({@code core-1.9.0} and {@code core-1.17.0}). */
    public static List<Finding> findVersionConflicts(Collection<String> libraryNames) {
        Map<String, Set<String>> versionsByBase = new TreeMap<>();
        for (String name : libraryNames) {
            Matcher matcher = VERSIONED.matcher(name);
            if (matcher.matches()) versionsByBase.computeIfAbsent(matcher.group(1), k -> new TreeSet<>()).add(matcher.group(2));
        }
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : versionsByBase.entrySet()) {
            if (entry.getValue().size() > 1) conflicts.add(entry.getKey() + ": " + String.join(", ", entry.getValue()));
        }
        if (conflicts.isEmpty()) return List.of();
        return List.of(new Finding("deps.version-conflict", Category.DEPENDENCIES, Severity.WARNING,
                "The same library in several versions (" + conflicts.size() + ")",
                "Two versions of a library usually contain the same classes, so the build fails or the older one wins, and the code that expects the newer one crashes.",
                "Keep only the newest version of each, and remove or exclude the others.", String.join("\n", conflicts)));
    }

    /** The old Support Library and AndroidX in the same project; they define overlapping classes and cannot be mixed. */
    public static List<Finding> findSupportLibraryMix(Map<String, Set<String>> classesByLibrary) {
        List<String> support = new ArrayList<>();
        boolean hasAndroidX = false;
        for (Map.Entry<String, Set<String>> entry : new TreeMap<>(classesByLibrary).entrySet()) {
            boolean usesSupport = false;
            for (String className : entry.getValue()) {
                if (className.startsWith("android.support.")) usesSupport = true;
                else if (className.startsWith("androidx.")) hasAndroidX = true;
            }
            if (usesSupport) support.add(entry.getKey());
        }
        if (support.isEmpty() || !hasAndroidX) return List.of();
        return List.of(new Finding("deps.support-androidx-mix", Category.DEPENDENCIES, Severity.ERROR,
                "The old Support Library is mixed with AndroidX",
                "AndroidX replaced the Support Library in 2018. Using both defines the same classes twice, and a library written for one cannot work with the other.",
                "Replace these libraries by versions built for AndroidX: " + String.join(", ", support) + ".", String.join(", ", support)));
    }

    // ---- checking an exclusion before it is applied

    /**
     * What happens if {@code excluded} libraries are left out of a project whose features need {@code roots}.
     *
     * @param providedPackages packages another library of the project provides, which can stand in for an excluded one
     */
    public static ExclusionCheck checkExclusion(Map<String, Library> graph, Collection<String> roots, Set<String> excluded, Set<String> providedPackages) {
        List<Finding> findings = new ArrayList<>();
        Set<String> before = closure(graph, roots, Collections.emptySet());
        Set<String> after = closure(graph, roots, excluded);

        List<String> missingRoots = new ArrayList<>();
        List<String> missingNeeds = new ArrayList<>();
        for (String excludedName : new TreeSet<>(excluded)) {
            if (!before.contains(excludedName)) continue;
            Library library = graph.get(excludedName);
            if (library != null && !library.packages().isEmpty() && providedPackages.containsAll(library.packages())) continue;
            if (roots.contains(excludedName)) missingRoots.add(excludedName);
            for (String user : new TreeSet<>(after)) {
                Library other = graph.get(user);
                if (other != null && other.dependencies().contains(excludedName)) missingNeeds.add(user + " needs " + excludedName);
            }
        }

        if (!missingRoots.isEmpty()) {
            findings.add(new Finding("deps.exclude-required", Category.DEPENDENCIES, Severity.ERROR,
                    "A library the project's features need would be excluded",
                    "The project uses a feature (a library enabled in Library Manager) that depends directly on " + String.join(", ", missingRoots)
                            + ". Without it the code that uses the feature does not compile or crashes when it runs.",
                    "Keep it, or turn off the feature that needs it, or provide the same classes with a local library.", String.join(", ", missingRoots)));
        }
        if (!missingNeeds.isEmpty()) {
            findings.add(new Finding("deps.exclude-breaks-dependents", Category.DEPENDENCIES, Severity.WARNING,
                    "Libraries that remain need what is being excluded (" + missingNeeds.size() + ")",
                    "These libraries use classes of the excluded ones. If nothing else provides those classes, the build reports missing classes or the app crashes with NoClassDefFoundError.",
                    "Only continue if a local library provides the same classes. Otherwise exclude the dependents too, or keep the library.",
                    String.join("\n", missingNeeds)));
        }

        Set<String> alsoUnused = new TreeSet<>(before);
        alsoUnused.removeAll(after);
        alsoUnused.removeAll(excluded);
        if (!alsoUnused.isEmpty()) {
            findings.add(new Finding("deps.exclude-leaves-unused", Category.DEPENDENCIES, Severity.INFO,
                    "Libraries that only the excluded ones needed (" + alsoUnused.size() + ")",
                    "Nothing else uses them, but they stay in the app and make it larger unless they are excluded too.",
                    "Add them to the exclusion to keep the app small.", String.join(", ", alsoUnused)));
        }

        boolean safe = findings.stream().noneMatch(f -> f.severity() == Severity.ERROR || f.severity() == Severity.WARNING);
        return new ExclusionCheck(safe, findings, alsoUnused);
    }
}
