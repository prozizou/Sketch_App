package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.BlockBean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules of the two-way edit of one event, apart from any screen: what applying Java to the blocks would change
 * (without changing it), applying it only when the blocks are still what was reviewed, taking an application back and
 * doing it again, and keeping the unapplied Java so it survives closing the editor.
 */
public final class SyncSession {
    /** What applying a Java text would do. Nothing has been changed when this exists. */
    public static final class Preview {
        public final boolean ok;
        public final List<JavaBody.Problem> problems;
        public final String text;
        public final String currentJava;
        public final String newJava;
        public final TextDiff diff;
        public final ChangeSummary changes;
        public final List<JavaToBlocks.SourceBlock> sources;
        public final ArrayList<BlockBean> blocks;
        /** Fingerprint of the event's blocks when the preview was made; applying checks they are still the same. */
        public final String baseHash;

        Preview(boolean ok, List<JavaBody.Problem> problems, String text, String currentJava, String newJava, TextDiff diff,
                ChangeSummary changes, List<JavaToBlocks.SourceBlock> sources, ArrayList<BlockBean> blocks, String baseHash) {
            this.ok = ok;
            this.problems = problems;
            this.text = text;
            this.currentJava = currentJava;
            this.newJava = newJava;
            this.diff = diff;
            this.changes = changes;
            this.sources = sources;
            this.blocks = blocks;
            this.baseHash = baseHash;
        }

        /** Whether the blocks would stay as they are. */
        public boolean isInSync() {
            return ok && changes.isEmpty();
        }
    }

    /** The outcome of applying, undoing or redoing. */
    public static final class Outcome {
        public enum Status {
            /** Done: {@code blocks} are the event's new blocks. */
            DONE,
            /** The blocks are not what was reviewed (or what the entry expects): nothing was changed. */
            BLOCKS_CHANGED,
            /** There is nothing to take back or do again. */
            NOTHING,
            /** The preview couldn't be applied (it had errors). */
            REFUSED
        }

        public final Status status;
        @Nullable
        public final ArrayList<BlockBean> blocks;
        public final String java;
        public final String message;

        Outcome(Status status, @Nullable ArrayList<BlockBean> blocks, String java, String message) {
            this.status = status;
            this.blocks = blocks;
            this.java = java;
            this.message = message;
        }
    }

    private final SyncStore store;
    private final JavaToBlocks converter;
    private final JavaToBlocks.CodeGenerator generator;
    private final java.util.function.LongSupplier clock;
    private final SyncHistory history;

    public SyncSession(SyncStore store, JavaToBlocks converter, JavaToBlocks.CodeGenerator generator) {
        this(store, converter, generator, System::currentTimeMillis);
    }

    SyncSession(SyncStore store, JavaToBlocks converter, JavaToBlocks.CodeGenerator generator, java.util.function.LongSupplier clock) {
        this.store = store;
        this.converter = converter;
        this.generator = generator;
        this.clock = clock;
        this.history = store.loadHistory();
    }

    public SyncHistory history() {
        return history;
    }

    // ---- preview

    /** Works out what applying {@code text} would do to {@code current}. Changes nothing. */
    public Preview prepare(String text, List<BlockBean> current) {
        return prepare(text, current, converter);
    }

    /** Same, with a converter that knows the screen's declarations as they are now. */
    public Preview prepare(String text, List<BlockBean> current, JavaToBlocks withContext) {
        String baseHash = BlockJson.hash(current);
        String currentJava = generate(current);
        JavaToBlocks.Result result = withContext.convert(text, current);
        if (!result.ok()) {
            return new Preview(false, result.problems(), text, currentJava, currentJava, TextDiff.of(currentJava, currentJava),
                    ChangeSummary.compare(current, current), List.of(), BlockJson.copyOf(current), baseHash);
        }
        String newJava = generate(result.blocks());
        return new Preview(true, List.of(), text, currentJava, newJava, TextDiff.of(currentJava, newJava),
                ChangeSummary.compare(current, result.blocks()), result.sourceBlocks(), result.blocks(), baseHash);
    }

    private String generate(List<BlockBean> blocks) {
        if (blocks.isEmpty()) return "";
        // the generator writes Windows line ends; the panel and the comparisons use plain ones
        return generator.generate(BlockJson.copyOf(blocks)).replace("\r\n", "\n").replace('\r', '\n');
    }

    // ---- applying

    /**
     * Makes the previewed blocks the event's blocks. Nothing is done when the preview has errors or the event's blocks
     * are no longer the ones it was made from: the caller shows a new preview. On success the step is recorded to be taken
     * back. The unapplied Java is kept until the caller has stored the new blocks and calls {@link #dropDraft()}, so a
     * crash in between never loses what the person wrote.
     */
    public Outcome apply(Preview preview, List<BlockBean> current) {
        if (!preview.ok) return new Outcome(Outcome.Status.REFUSED, null, preview.currentJava, "The Java has errors");
        if (!BlockJson.hash(current).equals(preview.baseHash)) {
            return new Outcome(Outcome.Status.BLOCKS_CHANGED, null, preview.currentJava, "The blocks changed since the review");
        }
        ArrayList<BlockBean> blocks = BlockJson.copyOf(preview.blocks);
        history.push(new SyncHistory.Entry(clock.getAsLong(), "Applied Java", BlockJson.toJson(current), preview.currentJava,
                BlockJson.toJson(blocks), preview.newJava));
        persistHistory();
        return new Outcome(Outcome.Status.DONE, blocks, preview.newJava, "");
    }

    /**
     * Takes back the last applied step.
     *
     * @param force go ahead even if the blocks were changed by hand since (the step's result is replaced)
     */
    public Outcome undo(List<BlockBean> current, boolean force) {
        SyncHistory.Entry entry = history.nextUndo();
        if (entry == null) return new Outcome(Outcome.Status.NOTHING, null, "", "Nothing to undo");
        if (!force && !BlockJson.hash(current).equals(BlockJson.hashOfJson(entry.afterBlocks()))) {
            return new Outcome(Outcome.Status.BLOCKS_CHANGED, null, "", "The blocks were changed since this step");
        }
        history.undone();
        persistHistory();
        return new Outcome(Outcome.Status.DONE, BlockJson.fromJson(entry.beforeBlocks()), entry.beforeJava(), "");
    }

    public Outcome redo(List<BlockBean> current, boolean force) {
        SyncHistory.Entry entry = history.nextRedo();
        if (entry == null) return new Outcome(Outcome.Status.NOTHING, null, "", "Nothing to redo");
        if (!force && !BlockJson.hash(current).equals(BlockJson.hashOfJson(entry.beforeBlocks()))) {
            return new Outcome(Outcome.Status.BLOCKS_CHANGED, null, "", "The blocks were changed since this step");
        }
        history.redone();
        persistHistory();
        return new Outcome(Outcome.Status.DONE, BlockJson.fromJson(entry.afterBlocks()), entry.afterJava(), "");
    }

    // ---- the Java not applied yet

    /**
     * Keeps {@code text} as the person's work in progress, started from {@code current}; a text equal to what the blocks
     * make is not kept (there is nothing to lose).
     */
    public void keepDraft(String text, List<BlockBean> current) {
        if (text.replace("\r\n", "\n").equals(generate(current))) {
            persist(null);
            return;
        }
        persist(new SyncStore.Draft(text, BlockJson.hash(current), clock.getAsLong()));
    }

    /** The Java kept from an earlier session, if any. */
    @Nullable
    public SyncStore.Draft draft() {
        return store.loadDraft();
    }

    /** Whether a kept draft was started from the blocks the event has now. */
    public boolean draftMatches(SyncStore.Draft draft, List<BlockBean> current) {
        return draft.baseHash().equals(BlockJson.hash(current));
    }

    public void dropDraft() {
        persist(null);
    }

    private void persist(@Nullable SyncStore.Draft draft) {
        try {
            store.saveDraft(draft);
        } catch (IOException e) {
            // Keeping the draft is best effort: the screen still holds the text
        }
    }

    private void persistHistory() {
        try {
            store.saveHistory(history);
        } catch (IOException e) {
            // The step is still undoable in this session
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "SyncSession[" + history.size() + " steps]";
    }
}
