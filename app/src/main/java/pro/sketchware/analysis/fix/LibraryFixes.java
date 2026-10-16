package pro.sketchware.analysis.fix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import pro.sketchware.analysis.DependencyInspector;
import pro.sketchware.analysis.DependencyInspector.Library;

/**
 * Works out how to resolve the library problems the health check finds: the old Support Library mixed with AndroidX,
 * libraries that contain the same classes, and the same library in several versions. A fix only leaves a library out
 * of the project (a local library is taken off the project's list, a built-in one is excluded); no library file is
 * deleted, so Library Manager can bring it back. It only plans a fix that has one safe answer; a pair of libraries
 * where neither contains the other is left for the person.
 */
public final class LibraryFixes {
    public enum Kind {
        /** Take a local library off the project's list. */
        REMOVE_LOCAL,
        /** Exclude a built-in library from the build. */
        EXCLUDE_BUILT_IN
    }

    /** @param reason why this library is the one to leave out */
    public record Action(Kind kind, String library, String reason) {
    }

    /**
     * @param findingId  the finding this answers
     * @param actions    what to change
     * @param unresolved what the plan can't decide, one line each, for the person
     */
    public record Plan(String findingId, List<Action> actions, List<String> unresolved) {
        public boolean isEmpty() {
            return actions.isEmpty();
        }
    }

    /** What the plan needs to know about the project's libraries. */
    public record Libraries(Map<String, Set<String>> classesByLibrary, Set<String> local, Set<String> builtIn,
                            Map<String, Library> builtInGraph, Collection<String> builtInRoots, Set<String> alreadyExcluded) {
    }

    public static final String SUPPORT_MIX = "deps.support-androidx-mix";
    public static final String DUPLICATE_CLASSES = "deps.duplicate-classes";
    public static final String VERSION_CONFLICT = "deps.version-conflict";

    private LibraryFixes() {
    }

    /** Whether a finding can be resolved here. */
    public static boolean handles(String findingId) {
        return SUPPORT_MIX.equals(findingId) || DUPLICATE_CLASSES.equals(findingId) || VERSION_CONFLICT.equals(findingId);
    }

    public static Plan plan(String findingId, Libraries libraries) {
        Map<String, String> remove = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        switch (findingId) {
            case SUPPORT_MIX -> supportMix(libraries, remove);
            case VERSION_CONFLICT -> versionConflicts(libraries, remove);
            case DUPLICATE_CLASSES -> duplicates(libraries, remove, unresolved);
            default -> {
            }
        }
        return build(findingId, libraries, remove, unresolved);
    }

    // ---- the three problems

    private static void supportMix(Libraries libraries, Map<String, String> remove) {
        for (Map.Entry<String, Set<String>> entry : new TreeMap<>(libraries.classesByLibrary()).entrySet()) {
            for (String className : entry.getValue()) {
                if (className.startsWith("android.support.")) {
                    remove.put(entry.getKey(), "It still uses the old Support Library (android.support), which AndroidX replaces.");
                    break;
                }
            }
        }
    }

    private static void versionConflicts(Libraries libraries, Map<String, String> remove) {
        for (Map<String, String> versions : DependencyInspector.versionGroups(allNames(libraries)).values()) {
            if (versions.size() < 2) continue;
            String newest = null;
            for (String name : versions.values()) newest = name; // the map is in version order
            for (String name : versions.values()) {
                if (!name.equals(newest)) remove.put(name, "An older version: " + newest + " stays.");
            }
        }
    }

    private static Set<String> allNames(Libraries libraries) {
        Set<String> names = new TreeSet<>(libraries.local());
        names.addAll(libraries.builtIn());
        return names;
    }

    private static void duplicates(Libraries libraries, Map<String, String> remove, List<String> unresolved) {
        // two versions of one library: the older one goes
        versionConflicts(libraries, remove);
        List<String> names = new ArrayList<>(new TreeSet<>(libraries.classesByLibrary().keySet()));
        Map<String, Set<String>> classes = new TreeMap<>();
        for (String name : names) {
            Set<String> set = new TreeSet<>(libraries.classesByLibrary().get(name));
            set.removeIf(c -> c.endsWith("module-info") || c.endsWith("package-info"));
            classes.put(name, set);
        }
        // pair -> which one to leave out, when it is clear
        Map<String, String> keeper = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                String a = names.get(i);
                String b = names.get(j);
                Set<String> overlap = new TreeSet<>(classes.get(a));
                overlap.retainAll(classes.get(b));
                if (overlap.isEmpty() || remove.containsKey(a) || remove.containsKey(b)) continue;
                boolean aInB = classes.get(b).containsAll(classes.get(a));
                boolean bInA = classes.get(a).containsAll(classes.get(b));
                String out = null;
                String stays = null;
                if (aInB && bInA) {
                    // the same classes: the copy that is in the project's own list goes, else the later name
                    boolean aLocal = libraries.local().contains(a);
                    boolean bLocal = libraries.local().contains(b);
                    if (aLocal != bLocal) {
                        out = aLocal ? a : b;
                    } else {
                        out = b;
                    }
                    stays = out.equals(a) ? b : a;
                } else if (aInB) {
                    out = a;
                    stays = b;
                } else if (bInA) {
                    out = b;
                    stays = a;
                }
                if (out == null) {
                    unresolved.add(a + " and " + b + " share " + overlap.size() + (overlap.size() == 1 ? " class" : " classes")
                            + " but each has others: choose which one to keep in Library Manager.");
                } else {
                    remove.putIfAbsent(out, "Everything it contains is also in " + stays + ", which stays.");
                    keeper.put(out, stays);
                }
            }
        }
        // a library is only left out when what replaces it stays too (or is itself covered the same way)
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, String> entry : new ArrayList<>(keeper.entrySet())) {
                String out = entry.getKey();
                String stays = entry.getValue();
                if (remove.containsKey(out) && remove.containsKey(stays) && !covered(stays, keeper, remove, 0)) {
                    remove.remove(out);
                    keeper.remove(out);
                    unresolved.add(out + " duplicates " + stays + ", but " + stays + " is also leaving: decide in Library Manager.");
                    changed = true;
                }
            }
        }
    }

    /** Whether a library that is leaving is replaced by one that stays, following the chain of keepers. */
    private static boolean covered(String library, Map<String, String> keeper, Map<String, String> remove, int depth) {
        if (!remove.containsKey(library)) return true;
        String next = keeper.get(library);
        return next != null && depth < 20 && covered(next, keeper, remove, depth + 1);
    }

    // ---- turning names into actions

    private static Plan build(String findingId, Libraries libraries, Map<String, String> remove, List<String> unresolved) {
        List<Action> actions = new ArrayList<>();
        Set<String> exclusions = new TreeSet<>();
        Map<String, String> builtInReasons = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : remove.entrySet()) {
            String name = entry.getKey();
            if (libraries.local().contains(name)) {
                actions.add(new Action(Kind.REMOVE_LOCAL, name, entry.getValue()));
            } else if (libraries.builtIn().contains(name)) {
                exclusions.add(name);
                builtInReasons.put(name, entry.getValue());
            } else {
                unresolved.add(name + " can't be changed from here.");
            }
        }
        if (!exclusions.isEmpty()) {
            Set<String> all = new TreeSet<>(libraries.alreadyExcluded());
            all.addAll(exclusions);
            // a built-in library can be left out when a local library that stays provides its packages
            Set<String> provided = new TreeSet<>();
            for (String local : libraries.local()) {
                if (remove.containsKey(local)) continue;
                for (String className : libraries.classesByLibrary().getOrDefault(local, Set.of())) {
                    int dot = className.lastIndexOf('.');
                    if (dot > 0) provided.add(className.substring(0, dot));
                }
            }
            DependencyInspector.ExclusionCheck check = DependencyInspector.checkExclusion(libraries.builtInGraph(), libraries.builtInRoots(), all, provided);
            if (check.safe()) {
                for (String name : exclusions) actions.add(new Action(Kind.EXCLUDE_BUILT_IN, name, builtInReasons.get(name)));
            } else {
                for (String name : exclusions) {
                    unresolved.add(name + " is built in and something in the project needs it: excluding it would break the build. "
                            + "Use Library Manager to look at what needs it.");
                }
            }
        }
        return new Plan(findingId, actions, unresolved);
    }
}
