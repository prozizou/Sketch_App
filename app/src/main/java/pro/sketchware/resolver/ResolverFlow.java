package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The steps of the resolver screen as a small state machine: choose, review, write, done or failed, and taking
 * back. The screen only draws the current state and forwards taps, so the order of things (nothing is written before the
 * review is confirmed, a failure changes nothing, a finished operation can be undone) is tested without a screen.
 */
public final class ResolverFlow {
    public enum Page {LIST, REVIEW, WORKING, DONE, FAILED}

    /** Called on the main executor whenever something the screen shows changed. */
    public interface Listener {
        void onFlowChanged();
    }

    /** The change list shown for confirmation. */
    public static final class Review {
        public final List<PlannedChange> changes;
        public final boolean[] checked;
        /** Choices that can't be applied, with the reason (a clash, an invalid value, ...). */
        public final List<Conflict> conflicts;
        /** Fixable problems that have no usable choice yet; they stay unresolved. */
        public final List<MissingResource> unresolved;

        Review(List<PlannedChange> changes, List<Conflict> conflicts, List<MissingResource> unresolved) {
            this.changes = changes;
            this.checked = new boolean[changes.size()];
            java.util.Arrays.fill(checked, true);
            this.conflicts = conflicts;
            this.unresolved = unresolved;
        }

        public List<PlannedChange> selected() {
            List<PlannedChange> selected = new ArrayList<>();
            for (int i = 0; i < changes.size(); i++) if (checked[i]) selected.add(changes.get(i));
            return selected;
        }
    }

    private final ResolverSession session;
    private final Executor background;
    private final Executor main;
    private Listener listener = () -> {
    };
    private Page page = Page.LIST;
    @Nullable
    private Review review;
    @Nullable
    private ResourceTransaction.Result result;
    @Nullable
    private ResourceJournal.UndoResult undone;
    private String message = "";

    /**
     * @param background runs the work that touches files (writing, taking back)
     * @param main       delivers results to the screen's thread
     */
    public ResolverFlow(ResolverSession session, Executor background, Executor main) {
        this.session = session;
        this.background = background;
        this.main = main;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public ResolverSession session() {
        return session;
    }

    public Page page() {
        return page;
    }

    @Nullable
    public Review review() {
        return review;
    }

    @Nullable
    public ResourceTransaction.Result result() {
        return result;
    }

    @Nullable
    public ResourceJournal.UndoResult undone() {
        return undone;
    }

    /** A line to show once (why nothing happened, what "Fix all" did); empty when there is none. */
    public String message() {
        return message;
    }

    public void clearMessage() {
        message = "";
    }

    public int selectedCount() {
        if (review == null) return 0;
        int count = 0;
        for (boolean checked : review.checked) if (checked) count++;
        return count;
    }

    // ---- choosing ----

    /** "Fix all": chooses what is sure, then shows the review. A problem with no sure answer stays for the person. */
    public void fixAll() {
        ResolverSession.FixAllSummary summary = session.fixAll();
        message = summary.selected() == 0 && session.plan().changes().isEmpty()
                ? "Nothing here can be fixed without your help: choose a file, an icon or a value for each."
                : "";
        openReview();
    }

    /** Shows what the current choices would change; with no usable choice it stays on the list and says so. */
    public void openReview() {
        PlanBuilder.Plan plan = session.plan();
        if (plan.changes().isEmpty()) {
            if (message.isEmpty()) {
                message = plan.conflicts().isEmpty() ? "Choose a file, an icon or a value for at least one resource first."
                        : plan.conflicts().get(0).message();
            }
            page = Page.LIST;
            listener.onFlowChanged();
            return;
        }
        List<MissingResource> unresolved = new ArrayList<>();
        java.util.Set<String> planned = new java.util.HashSet<>();
        for (PlannedChange change : plan.changes()) planned.add(change.key());
        for (MissingResource problem : session.notApplied()) if (problem.fixable() && !planned.contains(problem.key())) unresolved.add(problem);
        review = new Review(plan.changes(), plan.conflicts(), unresolved);
        page = Page.REVIEW;
        listener.onFlowChanged();
    }

    public void setChecked(int index, boolean checked) {
        if (review != null && index >= 0 && index < review.checked.length) {
            review.checked[index] = checked;
            listener.onFlowChanged();
        }
    }

    /** Goes back one step without changing anything. */
    public void back() {
        if (page == Page.REVIEW || page == Page.FAILED && review == null) {
            page = Page.LIST;
        } else if (page == Page.FAILED) {
            page = Page.REVIEW;
        }
        listener.onFlowChanged();
    }

    // ---- writing ----

    /** Writes the ticked changes. Nothing is written when none is ticked; a failure leaves the project as it was. */
    public void apply() {
        if (page != Page.REVIEW || review == null) return;
        List<PlannedChange> selected = review.selected();
        if (selected.isEmpty()) {
            message = "Tick at least one change to apply.";
            listener.onFlowChanged();
            return;
        }
        page = Page.WORKING;
        listener.onFlowChanged();
        background.execute(() -> {
            ResourceTransaction.Result outcome = session.apply(selected);
            main.execute(() -> {
                result = outcome;
                if (outcome.success()) {
                    List<String> keys = new ArrayList<>();
                    for (PlannedChange change : selected) keys.add(change.key());
                    session.markApplied(keys);
                    page = Page.DONE;
                } else {
                    page = Page.FAILED;
                }
                listener.onFlowChanged();
            });
        });
    }

    // ---- taking back ----

    /** Takes back the operation that just finished and returns to the list, with those resources open again. */
    public void undoLast() {
        ResourceTransaction.Result finished = result;
        if (page != Page.DONE || finished == null || finished.journal() == null) return;
        ResourceJournal.Entry entry = finished.journal();
        List<String> keys = new ArrayList<>();
        if (review != null) for (PlannedChange change : review.selected()) keys.add(change.key());
        background.execute(() -> {
            ResourceJournal.UndoResult outcome = null;
            String problem = null;
            try {
                outcome = session.undo(entry);
            } catch (IOException e) {
                problem = e.getMessage();
            }
            ResourceJournal.UndoResult finalOutcome = outcome;
            String finalProblem = problem;
            main.execute(() -> {
                if (finalProblem != null) {
                    message = "Couldn't take the changes back: " + finalProblem;
                } else {
                    undone = finalOutcome;
                    session.markNotApplied(keys);
                    message = finalOutcome.kept().isEmpty() ? "The changes were taken back."
                            : "Taken back, except " + finalOutcome.kept().size() + " that you changed since.";
                    page = Page.LIST;
                }
                listener.onFlowChanged();
            });
        });
    }

    /** Takes back an earlier operation from the history. */
    public void undo(ResourceJournal.Entry entry, Runnable afterwards) {
        background.execute(() -> {
            String problem = null;
            ResourceJournal.UndoResult outcome = null;
            try {
                outcome = session.undo(entry);
            } catch (IOException e) {
                problem = e.getMessage();
            }
            ResourceJournal.UndoResult finalOutcome = outcome;
            String finalProblem = problem;
            main.execute(() -> {
                if (finalProblem != null) {
                    message = "Couldn't take the changes back: " + finalProblem;
                } else {
                    // anything that was applied by that operation can be chosen again
                    List<String> keys = new ArrayList<>();
                    for (ResourceJournal.Created created : entry.created()) keys.add(keyOf(created.path()));
                    for (ResourceJournal.Modified modified : entry.modified()) {
                        for (String line : modified.addedLines()) keys.add(keyOfValueLine(line));
                    }
                    session.markNotApplied(keys);
                    message = finalOutcome.kept().isEmpty() ? "The changes were taken back."
                            : "Taken back, except " + finalOutcome.kept().size() + " that you changed since.";
                }
                afterwards.run();
                listener.onFlowChanged();
            });
        });
    }

    /** {@code drawable-xhdpi/ic_add.png} gives {@code drawable/ic_add}. */
    static String keyOf(String path) {
        int slash = path.indexOf('/');
        String folder = path.substring(0, slash);
        String file = path.substring(slash + 1);
        int dash = folder.indexOf('-');
        int dot = file.indexOf('.');
        return (dash < 0 ? folder : folder.substring(0, dash)) + "/" + (dot < 0 ? file : file.substring(0, dot));
    }

    /** {@code <color name="a">#111</color>} gives {@code color/a}; {@code <item name="a" type="id">} gives {@code id/a}. */
    static String keyOfValueLine(String line) {
        java.util.regex.Matcher tag = java.util.regex.Pattern.compile("<(\\w+) name=\"([^\"]+)\"(?: type=\"(\\w+)\")?").matcher(line);
        if (!tag.find()) return "";
        String type = tag.group(3) != null ? tag.group(3) : tag.group(1);
        return type + "/" + tag.group(2);
    }

    @NonNull
    @Override
    public String toString() {
        return "ResolverFlow[" + page + "]";
    }
}
