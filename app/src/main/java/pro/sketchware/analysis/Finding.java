package pro.sketchware.analysis;

/**
 * One problem found in a project or in a build, with what causes it and how to fix it.
 *
 * @param id       stable identifier of the rule, like {@code compat.target-sdk-old}; the same problem always has the same id
 * @param title    one line saying what is wrong
 * @param cause    why it matters
 * @param solution what to do about it
 * @param evidence the line, file or value that triggered it, or null
 */
public record Finding(String id, Category category, Severity severity, String title, String cause, String solution, String evidence) {
    public Finding {
        if (id == null || category == null || severity == null || title == null || cause == null || solution == null) {
            throw new IllegalArgumentException("A finding needs an id, category, severity, title, cause and solution");
        }
    }

    public Finding withEvidence(String newEvidence) {
        return new Finding(id, category, severity, title, cause, solution, newEvidence);
    }
}
