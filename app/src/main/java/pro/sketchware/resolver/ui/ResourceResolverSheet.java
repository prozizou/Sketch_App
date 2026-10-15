package pro.sketchware.resolver.ui;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.databinding.ResolverSheetBinding;
import pro.sketchware.resolver.Conflict;
import pro.sketchware.resolver.IconLibrary;
import pro.sketchware.resolver.MissingResource;
import pro.sketchware.resolver.ResolverFlow;
import pro.sketchware.resolver.ResolverSession;
import pro.sketchware.resolver.Resolution;
import pro.sketchware.resolver.ResourceJournal;
import pro.sketchware.resolver.ResourceTransaction;
import pro.sketchware.resolver.ResourceTypes;
import pro.sketchware.resolver.Suggestion;
import pro.sketchware.utility.ThemeUtils;

/**
 * The resolver: every missing resource as a card, "Fix all", the review of what will be written, the outcome with a
 * countdown to the next build, and the history to take changes back. It draws what {@link ResolverFlow} says and
 * forwards taps; the rules are in the flow and the session.
 */
public class ResourceResolverSheet extends BottomSheetDialogFragment implements IconPickerSheet.Listener {
    /** The screen that owns the flow (it outlives the sheet). */
    public interface Host {
        ResolverFlow resolverFlow();

        @Nullable
        IconLibrary resolverIcons();

        /** The person wants a build now ({@code rebuild}), or closed the sheet after the files were written. */
        void onResolverFinished(boolean rebuild, List<String> keysBefore);

        /** Opens the file and line that asked for a resource. */
        void onResolverOpenLocation(MissingResource problem, MissingResource.Occurrence occurrence);
    }

    private static final String ARG_FOCUS = "focus";
    private static final int COUNTDOWN_SECONDS = 4;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private ResolverSheetBinding binding;
    private ProblemAdapter problems;
    private ReviewAdapter review;
    private int countdown = -1;
    private boolean countdownStopped;
    @Nullable
    private MissingResource pending;
    private ActivityResultLauncher<String[]> pickOne;
    private ActivityResultLauncher<String[]> pickMany;

    /** @param focusKey the resource to show first ({@code drawable/ic_add}), or null */
    public static ResourceResolverSheet newInstance(@Nullable String focusKey) {
        ResourceResolverSheet sheet = new ResourceResolverSheet();
        Bundle args = new Bundle();
        args.putString(ARG_FOCUS, focusKey);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    private Host host() {
        return getActivity() instanceof Host h ? h : null;
    }

    private ResolverFlow flow() {
        return host().resolverFlow();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickOne = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);
        pickMany = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::onFilesPicked);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ResolverSheetBinding.inflate(inflater, container, false);
        binding.getRoot().setMinimumHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.85f));
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (host() == null) {
            dismissAllowingStateLoss();
            return;
        }
        ResolverFlow flow = flow();
        problems = new ProblemAdapter(flow.session(), host().resolverIcons(), new ProblemActions());
        problems.setItems(flow.session().problems());
        binding.resolverList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.resolverList.setAdapter(problems);
        binding.resolverList.setItemAnimator(null);
        String focus = getArguments() == null ? null : getArguments().getString(ARG_FOCUS);
        int at = focus == null ? -1 : problems.indexOf(focus);
        if (at > 0) binding.resolverList.scrollToPosition(at);

        review = new ReviewAdapter((index, checked) -> flow().setChecked(index, checked));
        binding.resolverReviewList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.resolverReviewList.setAdapter(review);

        binding.resolverClose.setOnClickListener(v -> dismiss());
        binding.resolverHistory.setOnClickListener(v -> showHistory());
        binding.resolverImport.setOnClickListener(v -> pickMany.launch(new String[]{"*/*"}));

        flow.setListener(this::render);
        render();
    }

    @Override
    public void onDestroyView() {
        main.removeCallbacksAndMessages(null);
        if (host() != null) flow().setListener(() -> {
        });
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        main.removeCallbacksAndMessages(null);
        super.onDismiss(dialog);
    }

    // ---- drawing ----

    private void render() {
        if (binding == null || host() == null) return;
        ResolverFlow flow = flow();
        ResolverSession session = flow.session();
        ResolverFlow.Page page = flow.page();
        boolean list = page == ResolverFlow.Page.LIST;
        boolean reviewing = page == ResolverFlow.Page.REVIEW;
        boolean status = page == ResolverFlow.Page.WORKING || page == ResolverFlow.Page.DONE || page == ResolverFlow.Page.FAILED;

        binding.resolverList.setVisibility(list ? View.VISIBLE : View.GONE);
        binding.resolverReviewList.setVisibility(reviewing ? View.VISIBLE : View.GONE);
        binding.resolverStatus.setVisibility(status ? View.VISIBLE : View.GONE);
        binding.resolverImport.setVisibility(list ? View.VISIBLE : View.GONE);
        binding.resolverHistory.setVisibility(page == ResolverFlow.Page.WORKING ? View.GONE : View.VISIBLE);
        binding.resolverClose.setEnabled(page != ResolverFlow.Page.WORKING);

        String message = flow.message();
        binding.resolverMessage.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        binding.resolverMessage.setText(message);

        switch (page) {
            case LIST -> renderList(flow, session);
            case REVIEW -> renderReview(flow);
            case WORKING -> renderWorking();
            case DONE -> renderDone(flow);
            case FAILED -> renderFailed(flow);
        }
        if (page != ResolverFlow.Page.DONE) {
            stopCountdown();
            countdownStopped = false;
        }
    }

    private void renderList(ResolverFlow flow, ResolverSession session) {
        List<MissingResource> all = session.problems();
        int fixable = session.fixable().size();
        binding.resolverTitle.setText(R.string.resolver_title);
        binding.resolverSubtitle.setText(getString(R.string.resolver_subtitle, all.size(), fixable));
        problems.setItems(all);

        int chosen = 0;
        for (MissingResource problem : all) if (session.status(problem) == ResolverSession.Status.CHOSEN) chosen++;
        boolean anyOpen = false;
        for (MissingResource problem : session.notApplied()) if (problem.fixable()) anyOpen = true;

        binding.resolverBar.setVisibility(View.VISIBLE);
        binding.resolverSummary.setText("");
        binding.resolverSecondary.setText(R.string.resolver_fix_all);
        binding.resolverSecondary.setVisibility(anyOpen ? View.VISIBLE : View.GONE);
        binding.resolverSecondary.setOnClickListener(v -> flow().fixAll());
        binding.resolverPrimary.setText(getString(R.string.resolver_review_count, chosen));
        binding.resolverPrimary.setEnabled(chosen > 0);
        binding.resolverPrimary.setAlpha(chosen > 0 ? 1f : 0.4f);
        binding.resolverPrimary.setVisibility(View.VISIBLE);
        binding.resolverPrimary.setOnClickListener(v -> flow().openReview());
    }

    private void renderReview(ResolverFlow flow) {
        binding.resolverTitle.setText(R.string.resolver_review_title);
        binding.resolverSubtitle.setText(getResources().getQuantityString(R.plurals.resolver_review_selected, flow.selectedCount(), flow.selectedCount()));
        review.setReview(requireContext(), flow.review());
        binding.resolverBar.setVisibility(View.VISIBLE);
        binding.resolverSummary.setText("");
        binding.resolverSecondary.setText(R.string.resolver_back);
        binding.resolverSecondary.setVisibility(View.VISIBLE);
        binding.resolverSecondary.setOnClickListener(v -> flow().back());
        binding.resolverPrimary.setText(getString(R.string.resolver_apply, flow.selectedCount()));
        binding.resolverPrimary.setEnabled(flow.selectedCount() > 0);
        binding.resolverPrimary.setAlpha(flow.selectedCount() > 0 ? 1f : 0.4f);
        binding.resolverPrimary.setVisibility(View.VISIBLE);
        binding.resolverPrimary.setOnClickListener(v -> flow().apply());
    }

    private void renderWorking() {
        binding.resolverTitle.setText(R.string.resolver_review_title);
        binding.resolverSubtitle.setText("");
        showStatus(0, getString(R.string.resolver_working), "");
        binding.resolverBar.setVisibility(View.GONE);
    }

    private void renderDone(ResolverFlow flow) {
        ResourceTransaction.Result result = flow.result();
        int count = result == null ? 0 : result.created().size() + result.modified().size();
        int added = flow.review() == null ? count : flow.selectedCount();
        binding.resolverTitle.setText(R.string.resolver_title);
        binding.resolverSubtitle.setText("");
        if (countdown < 0 && !countdownStopped) countdown = COUNTDOWN_SECONDS;
        showStatus(R.drawable.ic_mtrl_check, getResources().getQuantityString(R.plurals.resolver_done_title, added, added), countdownText());
        binding.resolverBar.setVisibility(View.VISIBLE);
        binding.resolverSummary.setText("");
        binding.resolverSecondary.setText(R.string.resolver_undo);
        binding.resolverSecondary.setVisibility(View.VISIBLE);
        binding.resolverSecondary.setOnClickListener(v -> {
            stopCountdown();
            countdownStopped = true;
            flow().undoLast();
        });
        binding.resolverPrimary.setText(R.string.resolver_rebuild_now);
        binding.resolverPrimary.setEnabled(true);
        binding.resolverPrimary.setAlpha(1f);
        binding.resolverPrimary.setVisibility(View.VISIBLE);
        binding.resolverPrimary.setOnClickListener(v -> finish(true));
        if (!countdownStopped && countdown >= 0) {
            main.removeCallbacks(tick);
            main.postDelayed(tick, 1000);
        }
    }

    private void renderFailed(ResolverFlow flow) {
        ResourceTransaction.Result result = flow.result();
        StringBuilder detail = new StringBuilder(result == null ? "" : result.message());
        if (result != null) for (ResourceTransaction.Failure failure : result.failures()) detail.append('\n').append(failure.key()).append(": ").append(failure.message());
        binding.resolverTitle.setText(R.string.resolver_title);
        binding.resolverSubtitle.setText("");
        showStatus(R.drawable.ic_mtrl_warning, getString(R.string.resolver_failed_title), detail.toString());
        binding.resolverBar.setVisibility(View.VISIBLE);
        binding.resolverSummary.setText("");
        binding.resolverSecondary.setVisibility(View.GONE);
        binding.resolverPrimary.setText(R.string.resolver_back);
        binding.resolverPrimary.setEnabled(true);
        binding.resolverPrimary.setAlpha(1f);
        binding.resolverPrimary.setVisibility(View.VISIBLE);
        binding.resolverPrimary.setOnClickListener(v -> flow().back());
    }

    private void showStatus(int icon, String title, String detail) {
        binding.resolverStatusProgress.setVisibility(icon == 0 ? View.VISIBLE : View.GONE);
        binding.resolverStatusIcon.setVisibility(icon == 0 ? View.GONE : View.VISIBLE);
        if (icon != 0) {
            binding.resolverStatusIcon.setImageResource(icon);
            int color = icon == R.drawable.ic_mtrl_check ? ThemeUtils.getColor(binding.getRoot(), R.attr.colorCoolGreen)
                    : ThemeUtils.getColor(binding.getRoot(), R.attr.colorError);
            binding.resolverStatusIcon.setImageTintList(ColorStateList.valueOf(color));
        }
        binding.resolverStatusTitle.setText(title);
        binding.resolverStatusDetail.setText(detail);
        binding.resolverStatusDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---- countdown to the next build ----

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (binding == null || countdownStopped || flow().page() != ResolverFlow.Page.DONE) return;
            countdown--;
            if (countdown <= 0) {
                finish(true);
            } else {
                binding.resolverStatusDetail.setText(countdownText());
                main.postDelayed(this, 1000);
            }
        }
    };

    private String countdownText() {
        return countdown > 0 ? getString(R.string.resolver_done_rebuild, countdown) : "";
    }

    private void stopCountdown() {
        main.removeCallbacks(tick);
        countdown = -1;
    }

    private void finish(boolean rebuild) {
        Host host = host();
        if (host == null) return;
        List<String> keys = new ArrayList<>();
        for (MissingResource problem : flow().session().problems()) keys.add(problem.key());
        main.removeCallbacksAndMessages(null);
        dismissAllowingStateLoss();
        host.onResolverFinished(rebuild, keys);
    }

    // ---- cards ----

    private final class ProblemActions implements ProblemAdapter.Actions {
        @Override
        public void onUseSuggestion(MissingResource problem, Suggestion suggestion) {
            flow().session().choose(problem, new Resolution.Reuse(suggestion));
            problems.showError(problem, null);
            changed(problem);
        }

        @Override
        public void onLocal(MissingResource problem) {
            pending = problem;
            pickOne.launch(new String[]{"*/*"});
        }

        @Override
        public void onOnline(MissingResource problem) {
            pending = problem;
            IconPickerSheet.newInstance(problem.key(), problem.name()).show(getChildFragmentManager(), "icons");
        }

        @Override
        public void onTyped(MissingResource problem, String text) {
            Resolution resolution = new Resolution.Typed(text.trim());
            Conflict conflict = flow().session().check(problem, resolution);
            if (conflict != null) {
                problems.showError(problem, conflict.message());
                return;
            }
            flow().session().choose(problem, resolution);
            problems.showError(problem, null);
            changed(problem);
        }

        @Override
        public void onRemove(MissingResource problem) {
            flow().session().clear(problem);
            changed(problem);
        }

        @Override
        public void onOpenLocation(MissingResource problem, MissingResource.Occurrence occurrence) {
            if (host() != null) host().onResolverOpenLocation(problem, occurrence);
        }
    }

    private void changed(MissingResource problem) {
        problems.refresh(problem);
        // the Review button counts the chosen ones
        if (flow().page() == ResolverFlow.Page.LIST) renderList(flow(), flow().session());
    }

    // ---- local files ----

    private void onFilePicked(@Nullable Uri uri) {
        MissingResource problem = pending;
        pending = null;
        if (uri == null || problem == null || binding == null) return;
        android.content.ContentResolver resolver = requireContext().getContentResolver();
        ResolverSession session = flow().session();
        IO.execute(() -> {
            String name = LocalFiles.displayName(resolver, uri);
            ResolverSession.ImportOutcome outcome;
            try {
                byte[] bytes = LocalFiles.read(resolver, uri);
                outcome = session.importFile(problem, name, bytes);
            } catch (IOException e) {
                outcome = new ResolverSession.ImportOutcome(false, getString(R.string.resolver_local_failed, name) + ": " + e.getMessage(), null);
            }
            ResolverSession.ImportOutcome result = outcome;
            main.post(() -> {
                if (binding == null) return;
                problems.showError(problem, result.ok() ? null : result.message());
                changed(problem);
            });
        });
    }

    private void onFilesPicked(@Nullable List<Uri> uris) {
        if (uris == null || uris.isEmpty() || binding == null) return;
        android.content.ContentResolver resolver = requireContext().getContentResolver();
        ResolverSession session = flow().session();
        IO.execute(() -> {
            List<ResolverSession.NamedFile> files = new ArrayList<>();
            List<String> unreadable = new ArrayList<>();
            for (Uri uri : uris) {
                String name = LocalFiles.displayName(resolver, uri);
                try {
                    files.add(new ResolverSession.NamedFile(name, LocalFiles.read(resolver, uri)));
                } catch (IOException e) {
                    unreadable.add(name + ": " + e.getMessage());
                }
            }
            ResolverSession.BatchResult batch = session.importFiles(files);
            main.post(() -> {
                if (binding == null) return;
                problems.setItems(session.problems());
                renderList(flow(), session);
                showBatch(batch, unreadable);
            });
        });
    }

    private void showBatch(ResolverSession.BatchResult batch, List<String> unreadable) {
        StringBuilder text = new StringBuilder();
        section(text, R.string.resolver_batch_matched, batch.matched());
        section(text, R.string.resolver_batch_unmatched, batch.unmatched());
        List<String> refused = new ArrayList<>(batch.refused());
        refused.addAll(unreadable);
        section(text, R.string.resolver_batch_refused, refused);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.resolver_batch_title)
                .setMessage(text.toString().trim())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void section(StringBuilder text, int title, List<String> lines) {
        if (lines.isEmpty()) return;
        text.append(getString(title)).append('\n');
        for (String line : lines) text.append("• ").append(line).append('\n');
        text.append('\n');
    }

    // ---- icon picker ----

    @Override
    public void onIconChosen(String problemKey, String label, byte[] svg, String colour) {
        ResolverSession session = flow().session();
        MissingResource target = null;
        for (MissingResource problem : session.problems()) if (problem.key().equals(problemKey)) target = problem;
        if (target == null || binding == null) return;
        MissingResource problem = target;
        session.setIconColour(colour);
        IO.execute(() -> {
            ResolverSession.ImportOutcome outcome = session.importFile(problem, label + ".svg", svg);
            main.post(() -> {
                if (binding == null) return;
                problems.showError(problem, outcome.ok() ? null : getString(R.string.resolver_picker_unusable, outcome.message()));
                problems.setItems(session.problems());
                renderList(flow(), session);
            });
        });
    }

    @Nullable
    @Override
    public IconLibrary resolverIcons() {
        return host() == null ? null : host().resolverIcons();
    }

    @Override
    public String resolverIconColour() {
        return flow().session().iconColour();
    }

    // ---- history ----

    private void showHistory() {
        List<ResourceJournal.Entry> entries = new ArrayList<>(flow().session().history());
        if (entries.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.resolver_history_title)
                    .setMessage(R.string.resolver_history_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        String[] labels = new String[entries.size()];
        for (int i = 0; i < labels.length; i++) {
            ResourceJournal.Entry entry = entries.get(i);
            String line = getResources().getQuantityString(R.plurals.resolver_history_item, entry.changeCount(), format.format(new Date(entry.time())), entry.changeCount());
            labels[i] = switch (entry.state()) {
                case UNDONE -> getString(R.string.resolver_history_undone, line);
                case PENDING -> getString(R.string.resolver_history_interrupted, line);
                default -> line;
            };
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.resolver_history_title)
                .setItems(labels, (dialog, which) -> confirmUndo(entries.get(which), format))
                .setNegativeButton(R.string.resolver_close, null)
                .show();
    }

    private void confirmUndo(ResourceJournal.Entry entry, DateFormat format) {
        if (entry.state() == ResourceJournal.State.UNDONE) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.resolver_history_undo_title)
                .setMessage(getString(R.string.resolver_history_undo_message, format.format(new Date(entry.time()))))
                .setPositiveButton(R.string.resolver_history_undo_action, (dialog, which) -> flow().undo(entry, () -> {
                    if (binding != null) {
                        problems.setItems(flow().session().problems());
                        render();
                    }
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
