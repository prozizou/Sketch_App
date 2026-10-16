package pro.sketchware.logic.sync.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.besome.sketch.beans.BlockBean;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import a.a.a.jq;
import dev.aldi.sayuti.block.ExtraBlockFile;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle;
import pro.sketchware.R;
import pro.sketchware.databinding.LogicEditorBinding;
import pro.sketchware.databinding.LogicSyncReviewBinding;
import pro.sketchware.logic.sync.BlockCatalog;
import pro.sketchware.logic.sync.BlockJson;
import pro.sketchware.logic.sync.BlockSpecs;
import pro.sketchware.logic.sync.JavaBody;
import pro.sketchware.logic.sync.JavaToBlocks;
import pro.sketchware.logic.sync.ProjectContext;
import pro.sketchware.logic.sync.SyncSession;
import pro.sketchware.logic.sync.SyncStore;
import pro.sketchware.logic.sync.TextDiff;
import pro.sketchware.settings.AppLog;

/**
 * The editable Java under the blocks. It shows the code the blocks make, lets the person change it, says whether it still
 * matches the blocks, checks it while it is typed, keeps it between sessions, and applies it to the blocks only when
 * asked and after a review. Nothing the person typed is replaced without asking, and nothing reaches the blocks unless the
 * Java is valid and the blocks are still what was reviewed.
 */
public final class JavaPanel {
    /** What the panel needs from the editor around it. */
    public interface Host {
        Activity activity();

        /** A view to show messages over. */
        View messageAnchor();

        /** Names the event for what is kept between sessions, like {@code MainActivity_textview1_onClick}. */
        String eventKey();

        /** Where the project keeps this. */
        File syncFolder();

        /** A copy of the event's blocks as they are now. */
        ArrayList<BlockBean> blocks();

        /** The Java the blocks make, formatted like the generator does. Safe to call from any thread. */
        String generate(ArrayList<BlockBean> blocks);

        /** What the screen declares now; read on the main thread. */
        ProjectContext projectContext();

        /** The build settings the generator reads. */
        jq buildConfig();

        BlockSpecs.Strings strings();

        /** Makes {@code blocks} the event's blocks and shows them, and saves the project. */
        void replaceBlocks(ArrayList<BlockBean> blocks);
    }

    private static final long CHECK_DELAY_MS = 600;
    private static final long SAVE_DELAY_MS = 1200;
    private static final int MAX_DIFF_LINES = 300;
    private static final int MAX_SOURCES_LISTED = 20;

    private final LogicEditorBinding binding;
    private final Host host;
    private final CodeEditor editor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Nullable
    private SyncSession session;
    @Nullable
    private JavaToBlocks converter;
    private boolean ready;
    private boolean preparing;
    private boolean draftOffered;
    private boolean destroyed;

    /** The Java the blocks made when the panel last matched them. */
    private String baseGenerated = "";
    private boolean dirty;
    private boolean programmatic;
    private boolean busy;
    private List<JavaBody.Problem> problems = List.of();
    private boolean checking;
    private int checkGeneration;
    @Nullable
    private AlertDialogHolder openDialog;

    private final Runnable checkNow = this::check;
    private final Runnable saveNow = this::saveDraftNow;

    private static final class AlertDialogHolder {
        final androidx.appcompat.app.AlertDialog dialog;

        AlertDialogHolder(androidx.appcompat.app.AlertDialog dialog) {
            this.dialog = dialog;
        }
    }

    public JavaPanel(LogicEditorBinding binding, Host host) {
        this.binding = binding;
        this.host = host;
        this.editor = binding.codeEditor;
    }

    // ---- life of the panel

    /** Makes the editor editable and wires the buttons. Call once, when the editor is created. */
    public void attach() {
        editor.setEditable(true);
        editor.setWordwrap(false);
        editor.setDiagnosticIndicatorStyle(DiagnosticIndicatorStyle.WAVY_LINE);
        editor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (!programmatic) onTyped();
        });
        binding.btnCodeApply.setOnClickListener(v -> apply());
        binding.btnCodeUndo.setOnClickListener(v -> step(true, false));
        binding.btnCodeRedo.setOnClickListener(v -> step(false, false));
        binding.btnCodeSave.setOnClickListener(v -> {
            saveDraftNow();
            Toast.makeText(host.activity(), R.string.logic_sync_saved, Toast.LENGTH_SHORT).show();
        });
        binding.codeStatus.setOnClickListener(v -> {
            if (!problems.isEmpty()) goTo(problems.get(0));
        });
        binding.codeBannerAction.setOnClickListener(v -> confirmReplace());
        render();
    }

    /** The panel became visible: shows the code and, once, offers what was kept from an earlier session. */
    public void onShown() {
        prepare();
        refresh();
        if (ready && !draftOffered) offerDraft();
    }

    /** Blocks changed. The code is refreshed unless it holds edits that were not applied; then a banner says so. */
    public void onBlocksChanged() {
        if (binding.codePanel.getVisibility() != View.VISIBLE) return;
        refresh();
    }

    /** The editor is paused or closed: what was typed is written down now. */
    public void flush() {
        main.removeCallbacks(saveNow);
        if (ready && dirty) saveDraftNow();
    }

    public void destroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        if (openDialog != null && openDialog.dialog.isShowing()) openDialog.dialog.dismiss();
    }

    public boolean isDirty() {
        return dirty;
    }

    // ---- showing the code

    private String generated() {
        ArrayList<BlockBean> blocks = host.blocks();
        if (blocks.isEmpty()) return "";
        try {
            return host.generate(blocks).replace("\r\n", "\n").replace('\r', '\n');
        } catch (RuntimeException e) {
            AppLog.e("JavaPanel", "Couldn't generate the code: " + e);
            return null;
        }
    }

    private void refresh() {
        String now = generated();
        if (now == null) {
            binding.codeMessage.setText(R.string.logic_code_error);
            binding.codeMessage.setVisibility(View.VISIBLE);
            editor.setVisibility(View.INVISIBLE);
            return;
        }
        binding.codeMessage.setVisibility(View.GONE);
        editor.setVisibility(View.VISIBLE);
        if (!dirty) {
            baseGenerated = now;
            if (!now.equals(text())) setText(now);
            binding.codeBanner.setVisibility(View.GONE);
        } else {
            binding.codeBanner.setVisibility(now.equals(baseGenerated) ? View.GONE : View.VISIBLE);
        }
        render();
    }

    /** Puts {@code text} in the editor as if it were the code of the blocks, keeping the reader's place. */
    private void setText(String text) {
        int offsetX = editor.getOffsetX();
        int offsetY = editor.getOffsetY();
        programmatic = true;
        try {
            editor.setText(text);
        } finally {
            programmatic = false;
        }
        if (offsetX != 0 || offsetY != 0) editor.post(() -> editor.getScroller().startScroll(0, 0, offsetX, offsetY, 0));
        problems = List.of();
        showSquiggles(List.of());
    }

    private String text() {
        return editor.getText().toString().replace("\r\n", "\n");
    }

    // ---- typing

    private void onTyped() {
        boolean wasDirty = dirty;
        dirty = !text().equals(baseGenerated);
        if (!dirty) {
            problems = List.of();
            showSquiggles(List.of());
            checking = false;
            binding.codeBanner.setVisibility(View.GONE);
            main.removeCallbacks(checkNow);
            main.removeCallbacks(saveNow);
            dropDraftInBackground();
        } else {
            checking = true;
            main.removeCallbacks(checkNow);
            main.postDelayed(checkNow, CHECK_DELAY_MS);
            main.removeCallbacks(saveNow);
            main.postDelayed(saveNow, SAVE_DELAY_MS);
        }
        if (wasDirty != dirty || checking) render();
    }

    private void check() {
        if (destroyed) return;
        String text = text();
        int generation = ++checkGeneration;
        try {
            worker.execute(() -> {
                JavaBody body = JavaBody.parse(text);
                main.post(() -> {
                    if (destroyed || generation != checkGeneration) return;
                    checking = false;
                    problems = body.problems();
                    showSquiggles(problems);
                    render();
                });
            });
        } catch (RejectedExecutionException closed) {
            // the editor is gone
        }
    }

    private void showSquiggles(List<JavaBody.Problem> list) {
        DiagnosticsContainer container = new DiagnosticsContainer();
        int length = editor.getText().length();
        long id = 0;
        for (JavaBody.Problem problem : list) {
            if (length == 0) break;
            int start = Math.max(0, Math.min(problem.start(), length - 1));
            int end = Math.max(start + 1, Math.min(problem.end(), length));
            container.addDiagnostic(new DiagnosticRegion(start, end, DiagnosticRegion.SEVERITY_ERROR, id++,
                    new DiagnosticDetail(problem.message(), problem.message(), null, null)));
        }
        editor.setDiagnostics(container);
    }

    private void goTo(JavaBody.Problem problem) {
        int length = editor.getText().length();
        int offset = Math.max(0, Math.min(problem.start(), length));
        CharPosition position = editor.getText().getIndexer().getCharPosition(offset);
        editor.setSelection(position.line, position.column);
        editor.ensurePositionVisible(position.line, position.column);
        editor.requestFocus();
    }

    // ---- the status row

    private void render() {
        Context context = host.activity();
        int error = MaterialColors.getColor(binding.getRoot(), R.attr.colorError);
        int variant = MaterialColors.getColor(binding.getRoot(), R.attr.colorOnSurfaceVariant);
        int primary = MaterialColors.getColor(binding.getRoot(), R.attr.colorPrimary);
        String text;
        int color;
        int icon;
        if (!ready) {
            text = context.getString(R.string.logic_sync_preparing);
            color = variant;
            icon = R.drawable.ic_mtrl_sync;
        } else if (!dirty) {
            text = context.getString(R.string.logic_sync_status_synced);
            color = variant;
            icon = R.drawable.ic_mtrl_check;
        } else if (checking) {
            text = context.getString(R.string.logic_sync_status_checking);
            color = variant;
            icon = R.drawable.ic_mtrl_sync;
        } else if (!problems.isEmpty()) {
            JavaBody.Problem first = problems.get(0);
            text = problems.size() == 1 ? context.getString(R.string.logic_sync_status_error, first.line(), first.message())
                    : context.getString(R.string.logic_sync_status_errors, first.line(), first.message(), problems.size() - 1);
            color = error;
            icon = R.drawable.ic_mtrl_warning;
        } else {
            text = context.getString(R.string.logic_sync_status_edited);
            color = primary;
            icon = R.drawable.ic_mtrl_edit;
        }
        binding.codeStatus.setText(text);
        binding.codeStatus.setTextColor(color);
        Drawable drawable = AppCompatResources.getDrawable(context, icon);
        if (drawable != null) {
            drawable = drawable.mutate();
            int size = (int) (14 * context.getResources().getDisplayMetrics().density);
            drawable.setBounds(0, 0, size, size);
            drawable.setTint(color);
        }
        binding.codeStatus.setCompoundDrawablesRelative(drawable, null, null, null);
        binding.codeStatus.setClickable(!problems.isEmpty() && dirty);

        binding.btnCodeApply.setEnabled(ready && dirty && !busy);
        boolean canUndo = session != null && session.history().canUndo() && !busy;
        boolean canRedo = session != null && session.history().canRedo() && !busy;
        binding.btnCodeUndo.setEnabled(canUndo);
        binding.btnCodeUndo.setAlpha(canUndo ? 1f : 0.38f);
        binding.btnCodeRedo.setEnabled(canRedo);
        binding.btnCodeRedo.setAlpha(canRedo ? 1f : 0.38f);
        binding.btnCodeSave.setEnabled(dirty);
        binding.btnCodeSave.setAlpha(dirty ? 1f : 0.38f);
    }

    // ---- getting ready

    /** Builds the conversion (slow) in the background, once. */
    private void prepare() {
        if (ready || preparing || destroyed) return;
        preparing = true;
        render();
        ProjectContext context = host.projectContext();
        jq config = host.buildConfig();
        BlockSpecs.Strings strings = host.strings();
        try {
            worker.execute(() -> {
                try {
                    JavaToBlocks built = JavaToBlocks.create(BlockCatalog.with(ExtraBlockFile.getExtraBlockData()), context, config, host::generate, strings);
                    SyncSession opened = new SyncSession(new SyncStore(host.syncFolder(), host.eventKey()), built, host::generate);
                    main.post(() -> {
                        if (destroyed) return;
                        converter = built;
                        session = opened;
                        ready = true;
                        preparing = false;
                        render();
                        if (binding.codePanel.getVisibility() == View.VISIBLE && !draftOffered) offerDraft();
                    });
                } catch (RuntimeException | LinkageError e) {
                    AppLog.e("JavaPanel", "Couldn't prepare the conversion: " + e);
                    main.post(() -> {
                        preparing = false;
                        binding.codeStatus.setText(host.activity().getString(R.string.logic_sync_failed, String.valueOf(e.getMessage())));
                    });
                }
            });
        } catch (RejectedExecutionException closed) {
            preparing = false;
        }
    }

    // ---- what was kept from a session before

    private void offerDraft() {
        if (session == null || destroyed) return;
        draftOffered = true;
        SyncStore.Draft draft = session.draft();
        if (draft == null) return;
        ArrayList<BlockBean> blocks = host.blocks();
        if (session.draftMatches(draft, blocks)) {
            restore(draft);
            say(R.string.logic_sync_draft_restored);
            return;
        }
        AlertDialogHolder holder = new AlertDialogHolder(new MaterialAlertDialogBuilder(host.activity())
                .setTitle(R.string.logic_sync_draft_found_title)
                .setMessage(R.string.logic_sync_draft_found_message)
                .setPositiveButton(R.string.logic_sync_draft_restore, (dialog, which) -> restore(draft))
                .setNegativeButton(R.string.logic_sync_draft_discard, (dialog, which) -> {
                    if (session != null) session.dropDraft();
                })
                .setCancelable(false)
                .show());
        openDialog = holder;
    }

    /** A short message over the editor; a plain toast when the editor has no place to show one. */
    private void say(int message) {
        try {
            Snackbar.make(host.messageAnchor(), message, Snackbar.LENGTH_SHORT).show();
        } catch (IllegalArgumentException noParent) {
            Toast.makeText(host.activity(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void restore(SyncStore.Draft draft) {
        String now = generated();
        baseGenerated = now == null ? "" : now;
        setText(draft.text());
        dirty = !text().equals(baseGenerated);
        binding.codeBanner.setVisibility(View.GONE);
        if (dirty) {
            checking = true;
            main.removeCallbacks(checkNow);
            main.post(checkNow);
        }
        render();
    }

    // ---- keeping the draft

    private void saveDraftNow() {
        if (session == null || destroyed) return;
        SyncSession kept = session;
        String text = text();
        ArrayList<BlockBean> blocks = host.blocks();
        try {
            worker.execute(() -> kept.keepDraft(text, blocks));
        } catch (RejectedExecutionException closed) {
            kept.keepDraft(text, blocks);
        }
    }

    private void dropDraftInBackground() {
        if (session == null) return;
        SyncSession kept = session;
        try {
            worker.execute(kept::dropDraft);
        } catch (RejectedExecutionException closed) {
            // the editor is gone
        }
    }

    // ---- replacing what the blocks make with the Java

    private void confirmReplace() {
        new MaterialAlertDialogBuilder(host.activity())
                .setTitle(R.string.logic_sync_replace_title)
                .setMessage(R.string.logic_sync_replace_message)
                .setPositiveButton(R.string.logic_sync_replace_action, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) host.activity().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("java", text()));
                        Toast.makeText(host.activity(), R.string.logic_sync_copied_old, Toast.LENGTH_SHORT).show();
                    }
                    dirty = false;
                    problems = List.of();
                    checking = false;
                    main.removeCallbacks(checkNow);
                    main.removeCallbacks(saveNow);
                    dropDraftInBackground();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---- applying the Java to the blocks

    private void apply() {
        if (!ready || session == null || converter == null) {
            Toast.makeText(host.activity(), R.string.logic_sync_preparing, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!dirty) {
            Toast.makeText(host.activity(), R.string.logic_sync_nothing_to_apply, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = text();
        ArrayList<BlockBean> current = host.blocks();
        JavaToBlocks withContext;
        try {
            withContext = converter.withContext(host.projectContext());
        } catch (RuntimeException e) {
            withContext = converter;
        }
        JavaToBlocks use = withContext;
        SyncSession kept = session;
        busy = true;
        render();
        try {
            worker.execute(() -> {
                SyncSession.Preview preview = null;
                String failure = null;
                try {
                    preview = kept.prepare(text, current, use);
                } catch (RuntimeException e) {
                    AppLog.e("JavaPanel", "Couldn't read the Java: " + e);
                    failure = String.valueOf(e.getMessage());
                }
                SyncSession.Preview result = preview;
                String why = failure;
                main.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    render();
                    if (result == null) {
                        Toast.makeText(host.activity(), host.activity().getString(R.string.logic_sync_failed, why), Toast.LENGTH_LONG).show();
                    } else {
                        review(result);
                    }
                });
            });
        } catch (RejectedExecutionException closed) {
            busy = false;
        }
    }

    private void review(SyncSession.Preview preview) {
        if (!preview.ok) {
            showErrors(preview.problems);
            return;
        }
        Activity activity = host.activity();
        LogicSyncReviewBinding view = LogicSyncReviewBinding.inflate(LayoutInflater.from(activity));
        view.reviewSummary.setText(activity.getString(R.string.logic_sync_review_summary, preview.changes.added(), preview.changes.changed(), preview.changes.removed()));

        if (preview.isInSync()) {
            view.reviewNote.setText(R.string.logic_sync_review_in_sync);
            view.reviewNote.setVisibility(View.VISIBLE);
        }
        if (!preview.sources.isEmpty()) {
            view.reviewSourcesTitle.setText(activity.getString(R.string.logic_sync_review_sources, preview.sources.size()));
            view.reviewSourcesTitle.setVisibility(View.VISIBLE);
            StringBuilder list = new StringBuilder(activity.getString(R.string.logic_sync_review_sources_hint)).append("\n\n");
            for (int i = 0; i < Math.min(MAX_SOURCES_LISTED, preview.sources.size()); i++) {
                JavaToBlocks.SourceBlock source = preview.sources.get(i);
                String code = source.code().replace('\n', ' ').trim();
                list.append(activity.getString(R.string.logic_sync_review_source_line, source.line(), code.length() > 60 ? code.substring(0, 60) + "…" : code)).append('\n');
            }
            if (preview.sources.size() > MAX_SOURCES_LISTED) {
                list.append(activity.getString(R.string.logic_sync_review_more, preview.sources.size() - MAX_SOURCES_LISTED));
            }
            view.reviewSources.setText(list.toString().trim());
            view.reviewSources.setVisibility(View.VISIBLE);
        }
        if (!preview.diff.isEmpty()) {
            view.reviewDiffTitle.setText(R.string.logic_sync_review_diff);
            view.reviewDiffTitle.setVisibility(View.VISIBLE);
            view.reviewDiff.setText(diffText(preview.diff));
        } else {
            view.reviewDiff.setText("");
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity).setTitle(R.string.logic_sync_review_title).setView(view.getRoot());
        if (preview.isInSync()) {
            builder.setPositiveButton(R.string.logic_sync_review_reformat, (dialog, which) -> {
                dirty = false;
                problems = List.of();
                main.removeCallbacks(saveNow);
                dropDraftInBackground();
                setText(preview.newJava);
                baseGenerated = preview.newJava;
                binding.codeBanner.setVisibility(View.GONE);
                render();
            }).setNegativeButton(android.R.string.cancel, null);
        } else {
            builder.setPositiveButton(R.string.logic_sync_review_apply, (dialog, which) -> commit(preview)).setNegativeButton(android.R.string.cancel, null);
        }
        openDialog = new AlertDialogHolder(builder.show());
    }

    private CharSequence diffText(TextDiff diff) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        int removed = MaterialColors.compositeARGBWithAlpha(MaterialColors.getColor(binding.getRoot(), R.attr.colorError), 60);
        int added = MaterialColors.compositeARGBWithAlpha(MaterialColors.getColor(binding.getRoot(), R.attr.colorCoolGreen), 60);
        boolean[] show = new boolean[diff.lines().size()];
        for (int i = 0; i < show.length; i++) {
            if (diff.lines().get(i).kind() == TextDiff.Kind.SAME) continue;
            for (int j = Math.max(0, i - 1); j <= Math.min(show.length - 1, i + 1); j++) show[j] = true;
        }
        int shown = 0;
        int hidden = 0;
        for (int i = 0; i < show.length; i++) {
            if (!show[i]) continue;
            if (shown >= MAX_DIFF_LINES) {
                hidden++;
                continue;
            }
            TextDiff.Line line = diff.lines().get(i);
            int start = out.length();
            out.append(switch (line.kind()) {
                case ADDED -> "+ ";
                case REMOVED -> "- ";
                case SAME -> "  ";
            }).append(line.text()).append('\n');
            if (line.kind() != TextDiff.Kind.SAME) {
                out.setSpan(new BackgroundColorSpan(line.kind() == TextDiff.Kind.ADDED ? added : removed), start, out.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            shown++;
        }
        if (hidden > 0) out.append(host.activity().getString(R.string.logic_sync_review_more, hidden));
        return out;
    }

    private void showErrors(List<JavaBody.Problem> list) {
        StringBuilder message = new StringBuilder(host.activity().getString(R.string.logic_sync_errors_intro)).append("\n");
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            message.append("\n• ").append(host.activity().getString(R.string.logic_sync_status_error, list.get(i).line(), list.get(i).message()));
        }
        JavaBody.Problem first = list.get(0);
        problems = list;
        showSquiggles(list);
        render();
        openDialog = new AlertDialogHolder(new MaterialAlertDialogBuilder(host.activity())
                .setTitle(R.string.logic_sync_errors_title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.logic_sync_errors_goto, (dialog, which) -> goTo(first))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    /** The person confirmed the review: record the step, then put the new blocks in place. */
    private void commit(SyncSession.Preview preview) {
        if (session == null) return;
        SyncSession.Outcome outcome = session.apply(preview, host.blocks());
        switch (outcome.status) {
            case DONE -> {
                Toast.makeText(host.activity(), R.string.logic_sync_applied, Toast.LENGTH_SHORT).show();
                host.replaceBlocks(outcome.blocks);
                // the new blocks are stored: only now is the Java that was typed no longer needed
                session.dropDraft();
                dirty = false;
            }
            case BLOCKS_CHANGED -> {
                Toast.makeText(host.activity(), R.string.logic_sync_blocks_changed_meanwhile, Toast.LENGTH_LONG).show();
                apply();
            }
            default -> Toast.makeText(host.activity(), host.activity().getString(R.string.logic_sync_failed, outcome.message), Toast.LENGTH_LONG).show();
        }
    }

    // ---- taking back and doing again

    private void step(boolean undo, boolean force) {
        if (session == null) return;
        SyncSession.Outcome outcome = undo ? session.undo(host.blocks(), force) : session.redo(host.blocks(), force);
        switch (outcome.status) {
            case DONE -> {
                Toast.makeText(host.activity(), undo ? R.string.logic_sync_undone : R.string.logic_sync_redone, Toast.LENGTH_SHORT).show();
                host.replaceBlocks(outcome.blocks);
                dirty = false;
            }
            case BLOCKS_CHANGED -> openDialog = new AlertDialogHolder(new MaterialAlertDialogBuilder(host.activity())
                    .setTitle(R.string.logic_sync_undo_changed_title)
                    .setMessage(R.string.logic_sync_undo_changed_message)
                    .setPositiveButton(R.string.logic_sync_undo_changed_action, (dialog, which) -> step(undo, true))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
            default -> Toast.makeText(host.activity(), undo ? R.string.logic_sync_nothing_to_undo : R.string.logic_sync_nothing_to_redo, Toast.LENGTH_SHORT).show();
        }
        render();
    }

    @NonNull
    @Override
    public String toString() {
        return "JavaPanel[ready=" + ready + ", dirty=" + dirty + ", hash=" + BlockJson.hash(baseGenerated).substring(0, 8) + "]";
    }
}
