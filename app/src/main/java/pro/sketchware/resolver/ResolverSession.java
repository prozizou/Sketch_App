package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One run of the resolver on one build log: the missing resources, what the project and the offline library offer for
 * each, what has been chosen so far, and the plan that follows. The screens only show and edit this; the rules live
 * here and in the classes it uses, so they can be tested without a screen.
 */
public final class ResolverSession {
    /** Where a missing resource stands. */
    public enum Status {
        /** Something is chosen (by a person or by "Fix all"). */
        CHOSEN,
        /** A confident suggestion exists but nothing is chosen yet. */
        SUGGESTED,
        /** Written into the project; the next build will tell whether it was enough. */
        APPLIED,
        /** It can be fixed, but only with a file or a value from the person. */
        NEEDS_INPUT,
        /** The resolver can't fix it (framework resource, other type, invalid name). */
        UNFIXABLE
    }

    /** What "Fix all" did. */
    public record FixAllSummary(int selected, @NonNull List<MissingResource> needInput, @NonNull List<MissingResource> unfixable) {
    }

    private final List<MissingResource> problems;
    private final ResourceInventory inventory;
    @Nullable
    private final IconLibrary library;
    private final ResourceValidator validator;
    private final File resDir;
    private final File journalRoot;
    private String iconColour;
    private final Map<MissingResource, Resolution> choices = new LinkedHashMap<>();
    private final java.util.Set<String> applied = new java.util.HashSet<>();
    private final Map<MissingResource, List<Suggestion>> suggestionCache = new HashMap<>();

    public ResolverSession(List<MissingResource> problems, ResourceInventory inventory, @Nullable IconLibrary library,
                           ResourceValidator validator, File resDir, File journalRoot, @Nullable String iconColour) {
        this.problems = List.copyOf(problems);
        this.inventory = inventory;
        this.library = library;
        this.validator = validator;
        this.resDir = resDir;
        this.journalRoot = journalRoot;
        this.iconColour = iconColour;
    }

    /** The colour library and downloaded icons are drawn in, {@code #RRGGBB} or {@code #AARRGGBB}. */
    public String iconColour() {
        return iconColour == null ? "#FF000000" : iconColour;
    }

    public void setIconColour(@Nullable String colour) {
        this.iconColour = colour;
    }

    /** What the current choice for one problem would write, or null when nothing is chosen or it can't be used. */
    @Nullable
    public PlannedChange plannedFor(MissingResource problem) {
        Resolution resolution = choices.get(problem);
        if (resolution == null) return null;
        Map<MissingResource, Resolution> only = new LinkedHashMap<>();
        only.put(problem, resolution);
        PlanBuilder.Plan plan = new PlanBuilder(inventory, library, iconColour).build(only);
        return plan.changes().isEmpty() ? null : plan.changes().get(0);
    }

    public List<MissingResource> problems() {
        return problems;
    }

    public List<MissingResource> fixable() {
        List<MissingResource> fixable = new ArrayList<>();
        for (MissingResource problem : problems) if (problem.fixable()) fixable.add(problem);
        return fixable;
    }

    public ResourceInventory inventory() {
        return inventory;
    }

    /** What the project and the library offer for a problem, project first (computed once). */
    public List<Suggestion> suggestions(MissingResource problem) {
        return suggestionCache.computeIfAbsent(problem, p -> ResourceMatcher.suggest(p, inventory, library));
    }

    public void choose(MissingResource problem, Resolution resolution) {
        if (!problems.contains(problem)) throw new IllegalArgumentException("Not a problem of this log: " + problem);
        choices.put(problem, resolution);
    }

    public void clear(MissingResource problem) {
        choices.remove(problem);
    }

    @Nullable
    public Resolution choice(MissingResource problem) {
        return choices.get(problem);
    }

    public Status status(MissingResource problem) {
        if (applied.contains(problem.key())) return Status.APPLIED;
        if (!problem.fixable()) return Status.UNFIXABLE;
        if (choices.containsKey(problem)) return Status.CHOSEN;
        return ResourceMatcher.confident(suggestions(problem)) != null ? Status.SUGGESTED : Status.NEEDS_INPUT;
    }

    /**
     * Chooses, for every fixable problem that has no choice yet, the best suggestion if it is sure enough. A problem
     * with no sure suggestion is left for the person; choices already made are never replaced.
     */
    public FixAllSummary fixAll() {
        int selected = 0;
        List<MissingResource> needInput = new ArrayList<>();
        List<MissingResource> unfixable = new ArrayList<>();
        for (MissingResource problem : problems) {
            if (!problem.fixable()) {
                unfixable.add(problem);
                continue;
            }
            if (choices.containsKey(problem)) continue;
            Suggestion sure = ResourceMatcher.confident(suggestions(problem));
            if (sure == null) {
                needInput.add(problem);
            } else {
                choices.put(problem, new Resolution.Reuse(sure));
                selected++;
            }
        }
        return new FixAllSummary(selected, needInput, unfixable);
    }

    /** A file read from the device or downloaded, with the name it had. */
    public record NamedFile(@NonNull String fileName, @NonNull byte[] bytes) {
    }

    /**
     * @param ok         whether the file was chosen for the problem
     * @param message    why not, in one line ("" when ok)
     * @param inspection what the file turned out to be (null only when it couldn't be read at all)
     */
    public record ImportOutcome(boolean ok, @NonNull String message, @Nullable FileInspector.Inspection inspection) {
    }

    /**
     * What an import of several files did.
     *
     * @param matched   {@code file -> type/name} for each file chosen for a missing resource
     * @param unmatched files whose name matches no missing resource
     * @param refused   {@code file: reason} for files that matched but can't be used
     */
    public record BatchResult(@NonNull List<String> matched, @NonNull List<String> unmatched, @NonNull List<String> refused) {
    }

    /**
     * Checks a file and, if the build would take it, chooses it for {@code problem}. This is also how an icon from the
     * library or the network is chosen: its SVG text is the file.
     */
    public ImportOutcome importFile(MissingResource problem, String fileName, byte[] bytes) {
        if (!problem.fixable() || !ResourceTypes.isFile(problem.type())) {
            return new ImportOutcome(false, "A file can't stand for " + problem.key(), null);
        }
        FileInspector.Inspection inspection = FileInspector.inspect(problem.type(), bytes, iconColour);
        if (!inspection.ok()) return new ImportOutcome(false, inspection.problem(), inspection);
        Resolution resolution = new Resolution.Imported(fileName, inspection, null);
        Conflict conflict = check(problem, resolution);
        if (conflict != null) return new ImportOutcome(false, conflict.message(), inspection);
        choose(problem, resolution);
        return new ImportOutcome(true, "", inspection);
    }

    /**
     * Chooses each file for the missing resource it is named after: the same name, or the same name once words like
     * "ic", "black" and "24dp" are set aside, when exactly one resource fits. A file that fits none, or fits several,
     * is reported, not guessed.
     */
    public BatchResult importFiles(List<NamedFile> files) {
        List<String> matched = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (NamedFile file : files) {
            String base = file.fileName().substring(0, file.fileName().indexOf('.') < 0 ? file.fileName().length() : file.fileName().indexOf('.'));
            MissingResource target = null;
            List<MissingResource> exact = new ArrayList<>();
            List<MissingResource> similar = new ArrayList<>();
            for (MissingResource problem : problems) {
                if (!problem.fixable() || !ResourceTypes.isFile(problem.type())) continue;
                if (ResourceNames.foldedKey(problem.name()).equals(ResourceNames.foldedKey(base))) exact.add(problem);
                else if (!NameTokens.key(base).isEmpty() && NameTokens.key(base).equals(NameTokens.key(problem.name()))) similar.add(problem);
            }
            if (exact.size() == 1) target = exact.get(0);
            else if (exact.isEmpty() && similar.size() == 1) target = similar.get(0);
            if (target == null) {
                unmatched.add(file.fileName());
                continue;
            }
            ImportOutcome outcome = importFile(target, file.fileName(), file.bytes());
            if (outcome.ok()) matched.add(file.fileName() + " \u2192 " + target.key());
            else refused.add(file.fileName() + ": " + outcome.message());
        }
        return new BatchResult(matched, unmatched, refused);
    }

    /** @return why {@code resolution} can't be used for {@code problem} (a clash, an invalid value, a missing source), or null */
    @Nullable
    public Conflict check(MissingResource problem, Resolution resolution) {
        Map<MissingResource, Resolution> only = new LinkedHashMap<>();
        only.put(problem, resolution);
        PlanBuilder.Plan plan = new PlanBuilder(inventory, library, iconColour).build(only);
        return plan.conflicts().isEmpty() ? null : plan.conflicts().get(0);
    }

    /** Records that these resources were written: they are no longer chosen, and show as applied. */
    public void markApplied(java.util.Collection<String> keys) {
        applied.addAll(keys);
        choices.keySet().removeIf(problem -> keys.contains(problem.key()));
    }

    /** Records that written resources were taken back: they can be chosen again. */
    public void markNotApplied(java.util.Collection<String> keys) {
        applied.removeAll(keys);
    }

    /** The problems that are not written yet. */
    public List<MissingResource> notApplied() {
        List<MissingResource> rest = new ArrayList<>();
        for (MissingResource problem : problems) if (!applied.contains(problem.key())) rest.add(problem);
        return rest;
    }

    /** The changes the current choices lead to, and the choices that can't be made. */
    public PlanBuilder.Plan plan() {
        return new PlanBuilder(inventory, library, iconColour).build(new LinkedHashMap<>(choices));
    }

    /**
     * Writes the given changes (all of the plan, or the ones the person kept ticked). The files are checked first
     * with {@link #validator}; nothing is written if any is refused.
     */
    public ResourceTransaction.Result apply(List<PlannedChange> changes) {
        return ResourceTransaction.apply(resDir, journalRoot, changes, validator);
    }

    /** The operations that can be taken back, newest first. */
    public List<ResourceJournal.Entry> history() {
        return ResourceJournal.list(journalRoot);
    }

    public ResourceJournal.UndoResult undo(ResourceJournal.Entry entry) throws java.io.IOException {
        return ResourceJournal.undo(resDir, entry);
    }
}
