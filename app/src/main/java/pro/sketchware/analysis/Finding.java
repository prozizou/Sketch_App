package pro.sketchware.analysis;

/**
 * One problem found in a project or in a build, with what causes it and how to fix it.
 *
 * @param id       stable identifier of the rule, like {@code compat.target-sdk-old}; the same problem always has the same id
 * @param title    one line saying what is wrong
 * @param cause    why it matters
 * @param solution what to do about it
 * @param evidence  the line, file or value that triggered it, or null
 * @param locations where it was found, to open it; empty when it is about the project as a whole
 */
public record Finding(String id, Category category, Severity severity, String title, String cause, String solution, String evidence,
                      java.util.List<Location> locations) {
    /** Locations beyond this are not kept: the list is for jumping to examples, the evidence gives the count. */
    public static final int MAX_LOCATIONS = 100;

    public Finding {
        if (id == null || category == null || severity == null || title == null || cause == null || solution == null) {
            throw new IllegalArgumentException("A finding needs an id, category, severity, title, cause and solution");
        }
        locations = locations == null ? java.util.List.of()
                : java.util.List.copyOf(locations.size() > MAX_LOCATIONS ? locations.subList(0, MAX_LOCATIONS) : locations);
    }

    public Finding(String id, Category category, Severity severity, String title, String cause, String solution, String evidence) {
        this(id, category, severity, title, cause, solution, evidence, java.util.List.of());
    }

    public Finding withEvidence(String newEvidence) {
        return new Finding(id, category, severity, title, cause, solution, newEvidence, locations);
    }

    public Finding withLocations(java.util.List<Location> newLocations) {
        return new Finding(id, category, severity, title, cause, solution, evidence, newLocations);
    }
}
