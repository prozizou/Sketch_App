package pro.sketchware.evolve;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.BlockBean;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.auth.AuthGate;
import pro.sketchware.databinding.EvolveItemBinding;
import pro.sketchware.databinding.EvolveSheetBinding;
import pro.sketchware.evolve.EvolveFilter.Mode;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Evolve: what other users shared. Lists it from the {@code Blocks_share} node page by page, lets the user inspect and
 * pick an entry into the screen that opened the sheet, report it, and share something of their own. Entries are block
 * stacks, more blocks or the XML of a layout ({@link SharedKind}); each screen offers the kinds it can use.
 */
public class EvolveSheet extends BottomSheetDialogFragment {

    /** What the sheet needs from the screen that opened it. */
    public interface Host {
        /** @return the kinds this screen can pick and share (the sheet lists only these) */
        EnumSet<SharedKind> kinds();

        /** @return what the user can share from this screen, at this moment */
        List<ShareSource> shareSources();

        /** @return names already used for {@code kind} here, so a picked entry never overwrites one of the user's own */
        default List<String> takenNames(SharedKind kind) {
            return new ArrayList<>();
        }

        /** @return the blocks the user already has for {@code kind}, encoded as shared, to spot a copy of what is picked */
        default List<String> existingJson(SharedKind kind) {
            return new ArrayList<>();
        }

        /** Saves {@code blocks} into My Block Collection under {@code name} and shows them in the drawer. */
        default void pickBlocks(String name, ArrayList<BlockBean> blocks) {
        }

        /** Imports a more block into the open screen (the importer asks about names and missing variables itself). */
        default void pickMoreBlock(String name, String spec, ArrayList<BlockBean> blocks) {
        }

        /** Puts {@code xml} into the XML editor; the screen decides how (replace, insert, copy). */
        default void pickXml(String name, String xml) {
        }
    }

    private static final int PAGE_SIZE = 30;
    /** When a filter leaves few rows, pages are fetched on its own, at most this many in a row. */
    private static final int MAX_AUTO_PAGES = 10;
    private static final int FEW_ROWS = 6;

    private final List<SharedBlock> all = new ArrayList<>();
    private final Map<String, ArrayList<BlockBean>> decoded = new HashMap<>();
    private EvolveSheetBinding binding;
    private ItemAdapter adapter;
    private GridLayoutManager layoutManager;
    private ObjectAnimator skeletonPulse;
    private boolean loading;
    /** The last load failed: the error text and Retry stay up instead of "nothing shared yet". */
    private boolean loadFailed;
    private boolean hasMore;
    private int autoPages;
    private boolean moderator;
    @Nullable
    private SharedKind kindFilter;
    private Mode mode = Mode.ALL;

    public static EvolveSheet newInstance() {
        return new EvolveSheet();
    }

    @Nullable
    private Host host() {
        return getActivity() instanceof Host host ? host : null;
    }

    /** Callbacks can arrive after the sheet was closed: they must touch neither views nor resources then. */
    private boolean alive() {
        return binding != null && isAdded() && getContext() != null;
    }

    private EnumSet<SharedKind> kinds() {
        Host host = host();
        return host == null || host.kinds().isEmpty() ? EnumSet.of(SharedKind.BLOCKS) : host.kinds();
    }

    private boolean available() {
        return alive() && EvolveShare.available(requireContext());
    }

    private String myUid() {
        if (!alive()) return "";
        FirebaseUser user = AuthGate.currentUser(requireContext());
        return user == null ? "" : user.getUid();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = EvolveSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // A tall sheet even with a short list, so the controls don't jump around.
        binding.evolveRoot.setMinimumHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.95f));
        adapter = new ItemAdapter();
        layoutManager = new GridLayoutManager(requireContext(), spanCount());
        binding.evolveList.setLayoutManager(layoutManager);
        binding.evolveList.setAdapter(adapter);
        binding.evolveList.setItemAnimator(null);
        binding.evolveList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView list, int dx, int dy) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.getItemCount() - 5) {
                    loadMore();
                }
            }
        });
        // Keep the last card clear of the navigation bar.
        int basePadding = binding.evolveList.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.evolveList.setPadding(binding.evolveList.getPaddingLeft(), binding.evolveList.getPaddingTop(),
                    binding.evolveList.getPaddingRight(), basePadding + bars.bottom);
            return insets;
        });
        binding.evolveSearch.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                binding.evolveSearchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                autoPages = 0;
                applyFilter();
            }
        });
        binding.evolveSearch.setOnEditorActionListener((v, actionId, event) -> actionId == EditorInfo.IME_ACTION_SEARCH && hideKeyboard());
        binding.evolveSearchClear.setOnClickListener(v -> binding.evolveSearch.setText(""));
        binding.evolveRefresh.setOnClickListener(v -> reload());
        binding.evolveRetry.setOnClickListener(v -> reload());
        binding.evolveShare.setOnClickListener(v -> startShare());
        setupChips();
        if (!available()) {
            binding.evolveRefresh.setEnabled(false);
            binding.evolveShare.setEnabled(false);
            binding.evolveSearch.setEnabled(false);
            showState(getString(R.string.evolve_unavailable), false);
            return;
        }
        EvolveRepository.isModerator(myUid(), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(Boolean value) {
                if (!alive()) return;
                moderator = value;
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(@NonNull String message) {
            }
        });
        // What was loaded a moment ago shows at once; the refresh behind it replaces it.
        List<SharedBlock> cached = EvolveCache.items(System.currentTimeMillis());
        if (!cached.isEmpty()) {
            all.addAll(cached);
            hasMore = EvolveCache.hasMore();
            applyFilter();
        }
        reload();
    }

    private boolean hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.evolveSearch.getWindowToken(), 0);
        return true;
    }

    /** One column on a phone, more on wider screens. */
    private int spanCount() {
        int width = getResources().getConfiguration().screenWidthDp;
        return width >= 840 ? 3 : width >= 600 ? 2 : 1;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (layoutManager != null) {
            layoutManager.setSpanCount(spanCount());
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Chips
    // ---------------------------------------------------------------------------------------------------------

    private void setupChips() {
        addChip(binding.evolveModes, Mode.ALL, R.string.evolve_filter_all, true);
        addChip(binding.evolveModes, Mode.POPULAR, R.string.evolve_filter_popular, false);
        addChip(binding.evolveModes, Mode.RECENT, R.string.evolve_filter_recent, false);
        addChip(binding.evolveModes, Mode.MINE, R.string.evolve_filter_mine, false);
        binding.evolveModes.setOnCheckedStateChangeListener((group, ids) -> {
            mode = chosen(group, ids, Mode.class, Mode.ALL);
            autoPages = 0;
            applyFilter();
        });
        EnumSet<SharedKind> kinds = kinds();
        if (kinds.size() > 1) {
            binding.evolveKinds.setVisibility(View.VISIBLE);
            binding.evolveKindsDivider.setVisibility(View.VISIBLE);
            addChip(binding.evolveKinds, null, R.string.evolve_kind_all, true);
            for (SharedKind kind : kinds) {
                addChip(binding.evolveKinds, kind, kindName(kind), false);
            }
            binding.evolveKinds.setOnCheckedStateChangeListener((group, ids) -> {
                kindFilter = chosen(group, ids, SharedKind.class, null);
                autoPages = 0;
                applyFilter();
            });
        }
    }

    private void addChip(ChipGroup group, @Nullable Object tag, @StringRes int label, boolean checked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.evolve_kind_chip, group, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(tag);
        chip.setChecked(checked);
        group.addView(chip);
    }

    private static <T> T chosen(ChipGroup group, List<Integer> ids, Class<T> type, T fallback) {
        if (!ids.isEmpty()) {
            Chip chip = group.findViewById(ids.get(0));
            if (chip != null && type.isInstance(chip.getTag())) {
                return type.cast(chip.getTag());
            }
        }
        return fallback;
    }

    @StringRes
    private static int kindName(SharedKind kind) {
        return switch (kind) {
            case BLOCKS -> R.string.evolve_kind_blocks;
            case MOREBLOCK -> R.string.evolve_kind_moreblock;
            case VIEW -> R.string.evolve_kind_view;
        };
    }

    @StringRes
    private static int kindBadge(SharedKind kind) {
        return switch (kind) {
            case BLOCKS -> R.string.evolve_blocks_kind_badge;
            case MOREBLOCK -> R.string.evolve_moreblock_kind_badge;
            case VIEW -> R.string.evolve_xml_kind_badge;
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog sheet) {
            sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            sheet.getBehavior().setSkipCollapsed(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (skeletonPulse != null) {
            skeletonPulse.cancel();
            skeletonPulse = null;
        }
        binding = null;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Loading, paging and filtering
    // ---------------------------------------------------------------------------------------------------------

    private void setLoading(boolean loading) {
        this.loading = loading;
        if (binding != null) {
            binding.evolveProgress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /** Shows the empty or error text (with Retry on errors) over the list, or hides it when {@code text} is null. */
    private void showState(@Nullable String text, boolean retry) {
        binding.evolveState.setText(text);
        binding.evolveStateLayout.setVisibility(text == null ? View.GONE : View.VISIBLE);
        binding.evolveRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void showSkeleton(boolean show) {
        binding.evolveSkeleton.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && skeletonPulse == null) {
            skeletonPulse = ObjectAnimator.ofFloat(binding.evolveSkeleton, View.ALPHA, 1f, 0.45f);
            skeletonPulse.setDuration(700);
            skeletonPulse.setRepeatMode(ObjectAnimator.REVERSE);
            skeletonPulse.setRepeatCount(ObjectAnimator.INFINITE);
            skeletonPulse.start();
        } else if (!show && skeletonPulse != null) {
            skeletonPulse.cancel();
            skeletonPulse = null;
            binding.evolveSkeleton.setAlpha(1f);
        }
    }

    private void reload() {
        if (!available() || loading) {
            return;
        }
        setLoading(true);
        loadFailed = false;
        autoPages = 0;
        showState(null, false);
        applyFilter();
        EvolveRepository.load(PAGE_SIZE, 0, getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(EvolveRepository.Page page) {
                if (!alive()) return;
                setLoading(false);
                all.clear();
                all.addAll(page.items());
                hasMore = page.rawCount() >= PAGE_SIZE;
                decoded.clear();
                EvolveCache.store(all, hasMore, System.currentTimeMillis());
                applyFilter();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!alive()) return;
                setLoading(false);
                loadFailed = true;
                if (all.isEmpty()) {
                    applyFilter();
                    showState(getString(R.string.evolve_error, message), true);
                } else {
                    SketchwareUtil.toastError(getString(R.string.evolve_error, message));
                }
            }
        });
    }

    /** Appends the next, older page. */
    private void loadMore() {
        if (!available() || loading || !hasMore || all.isEmpty()) {
            return;
        }
        long oldest = all.get(all.size() - 1).createdAt();
        for (SharedBlock block : all) {
            oldest = Math.min(oldest, block.createdAt());
        }
        setLoading(true);
        EvolveRepository.load(PAGE_SIZE, oldest, getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(EvolveRepository.Page page) {
                if (!alive()) return;
                setLoading(false);
                for (SharedBlock block : page.items()) {
                    if (all.stream().noneMatch(known -> known.id().equals(block.id()))) {
                        all.add(block);
                    }
                }
                hasMore = page.rawCount() >= PAGE_SIZE;
                EvolveCache.store(all, hasMore, System.currentTimeMillis());
                applyFilter();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!alive()) return;
                setLoading(false);
                hasMore = false;
                SketchwareUtil.toastError(getString(R.string.evolve_error, message));
            }
        });
    }

    private void applyFilter() {
        if (!alive()) return;
        List<SharedBlock> shown = EvolveFilter.apply(all, kinds(), kindFilter, mode,
                String.valueOf(binding.evolveSearch.getText()), myUid(), System.currentTimeMillis());
        adapter.submitList(shown);
        showSkeleton(loading && all.isEmpty());
        if (loading && all.isEmpty()) {
            showState(null, false);
            return;
        }
        if (shown.isEmpty()) {
            if (hasMore && autoPages < MAX_AUTO_PAGES) {
                // Nothing matches yet: look further back before saying so.
                autoPages++;
                loadMore();
                return;
            }
            if (!loadFailed) {
                showState(getString(all.isEmpty() ? R.string.evolve_empty : R.string.evolve_no_results), false);
            }
        } else {
            showState(null, false);
            if (shown.size() < FEW_ROWS && hasMore && autoPages < MAX_AUTO_PAGES) {
                autoPages++;
                loadMore();
            }
        }
    }

    @Nullable
    private ArrayList<BlockBean> decodeCached(SharedBlock block) {
        if (!block.kind().hasBlocks()) {
            return null;
        }
        if (!decoded.containsKey(block.id())) {
            decoded.put(block.id(), SharedBlockCodec.decode(block.blocksJson()));
        }
        return decoded.get(block.id());
    }

    private String authorLabel(SharedBlock block) {
        return EvolveCard.authorName(requireContext(), block);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Inspecting and picking
    // ---------------------------------------------------------------------------------------------------------

    /** The text of the inspection dialog: everything the entry would bring in, shown before anything is added. */
    private String inspection(SharedBlock block, @Nullable ArrayList<BlockBean> blocks) {
        StringBuilder text = new StringBuilder();
        if (!block.description().isEmpty()) {
            text.append(block.description()).append("\n\n");
        }
        if (EvolveChecks.madeWithNewerApp(block.appVersion(), BuildConfig.VERSION_CODE)) {
            text.append(getString(R.string.evolve_preview_newer, block.appVersion(), BuildConfig.VERSION_CODE)).append("\n\n");
        }
        if (block.kind() == SharedKind.VIEW) {
            text.append(getString(R.string.evolve_preview_xml_header)).append('\n').append(block.xml());
            return text.toString();
        }
        if (blocks == null) {
            return text.append(getString(R.string.evolve_corrupt)).toString();
        }
        List<String> variables = EvolveChecks.variables(blocks);
        if (!variables.isEmpty()) {
            text.append(getString(R.string.evolve_preview_needs, String.join(", ", variables))).append("\n\n");
        }
        List<EvolveChecks.RawSnippet> raw = EvolveChecks.rawCode(blocks);
        if (!raw.isEmpty()) {
            text.append(getString(R.string.evolve_preview_raw_header)).append('\n');
            for (EvolveChecks.RawSnippet snippet : raw) {
                text.append("// ").append(snippet.opCode()).append('\n').append(snippet.code()).append("\n\n");
            }
        }
        text.append(getString(R.string.evolve_preview_blocks_header)).append('\n');
        for (BlockBean bean : blocks) {
            text.append("• ").append(bean.opCode);
            if (bean.spec != null && !bean.spec.isBlank()) {
                text.append(": ").append(bean.spec);
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** Shows {@link #inspection}; with {@code onConfirm} it also offers to go on and pick. */
    private void showInspection(SharedBlock block, @Nullable ArrayList<BlockBean> blocks, @Nullable Runnable onConfirm) {
        if (!alive()) return;
        Context context = requireContext();
        TextView body = new TextView(context);
        body.setText(inspection(block, blocks));
        body.setTextIsSelectable(true);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextSize(12);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad / 2, pad, pad / 2);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.evolve_preview_title, block.name()))
                .setView(scroll)
                .setNegativeButton(onConfirm == null ? R.string.evolve_preview_close : R.string.common_word_cancel, null);
        if (onConfirm != null) {
            dialog.setPositiveButton(R.string.evolve_raw_pick, (d, w) -> onConfirm.run());
        }
        dialog.show();
    }

    private void pick(SharedBlock block) {
        if (!alive()) return;
        Host host = host();
        if (host == null) return;
        if (block.kind() == SharedKind.VIEW) {
            if (SharedXml.check(block.xml()).problem() != SharedBlockRules.Problem.NONE) {
                SketchwareUtil.toastError(getString(R.string.evolve_corrupt));
                return;
            }
            host.pickXml(block.name(), block.xml());
            EvolveRepository.countDownload(block.id());
            return;
        }
        // Decoded afresh: the preview may have used (and changed) the cached beans.
        ArrayList<BlockBean> blocks = SharedBlockCodec.decode(block.blocksJson());
        if (blocks == null) {
            SketchwareUtil.toastError(getString(R.string.evolve_corrupt));
            return;
        }
        // The stored flag is the author's word; what the stack really contains decides.
        boolean rawCode = block.usesRawCode() || SharedBlockRules.usesRawCode(SharedBlockCodec.opCodes(blocks));
        boolean newer = EvolveChecks.madeWithNewerApp(block.appVersion(), BuildConfig.VERSION_CODE);
        Runnable checkDuplicates = () -> {
            if (block.kind() == SharedKind.BLOCKS && isDuplicate(host, block)) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.evolve_duplicate_title)
                        .setMessage(R.string.evolve_duplicate_message)
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .setPositiveButton(R.string.evolve_duplicate_add, (d, w) -> addPicked(host, block, blocks))
                        .show();
            } else {
                addPicked(host, block, blocks);
            }
        };
        if (rawCode || newer) {
            // Nothing is added before the user has read the code (and the warnings) in the inspection dialog.
            showInspection(block, blocks, checkDuplicates);
        } else {
            checkDuplicates.run();
        }
    }

    private boolean isDuplicate(Host host, SharedBlock block) {
        for (String name : host.takenNames(SharedKind.BLOCKS)) {
            if (name.equalsIgnoreCase(block.name())) {
                return true;
            }
        }
        return EvolveChecks.alreadyHave(block.blocksJson(), host.existingJson(SharedKind.BLOCKS));
    }

    private void addPicked(Host host, SharedBlock block, ArrayList<BlockBean> blocks) {
        if (block.kind() == SharedKind.MOREBLOCK) {
            host.pickMoreBlock(block.name(), block.spec(), blocks);
            EvolveRepository.countDownload(block.id());
            return;
        }
        String name = SharedBlockRules.uniqueName(block.name(), host.takenNames(SharedKind.BLOCKS));
        host.pickBlocks(name, blocks);
        EvolveRepository.countDownload(block.id());
        if (alive()) {
            SketchwareUtil.toast(getString(R.string.evolve_picked, name));
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Sharing, reporting, deleting
    // ---------------------------------------------------------------------------------------------------------

    private void startShare() {
        Host host = host();
        if (host == null || !available()) return;
        List<ShareSource> sources = new ArrayList<>();
        for (ShareSource source : host.shareSources()) {
            if (kinds().contains(source.kind())) {
                sources.add(source);
            }
        }
        if (sources.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.evolve_share_title)
                    .setMessage(R.string.evolve_share_nothing)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        String[] labels = new String[sources.size()];
        for (int i = 0; i < labels.length; i++) {
            ShareSource source = sources.get(i);
            labels[i] = getString(R.string.evolve_source_label, source.origin(), source.name());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.evolve_share_choose_any)
                .setItems(labels, (d, which) -> EvolveShare.share(requireActivity(), sources.get(which), () -> {
                    if (alive()) reload();
                }))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void shareEntry(SharedBlock block) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.evolve_share_text, block.name(), getString(kindBadge(block.kind())), authorLabel(block)));
        startActivity(Intent.createChooser(send, getString(R.string.evolve_menu_share)));
    }

    private void confirmReport(SharedBlock block) {
        if (!alive()) return;
        String[] reasons = {
                getString(R.string.evolve_report_inappropriate),
                getString(R.string.evolve_report_malicious),
                getString(R.string.evolve_report_spam),
                getString(R.string.evolve_report_other)
        };
        String[] keys = {"inappropriate", "malicious", "spam", "other"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.evolve_report_title)
                .setItems(reasons, (d, which) -> {
                    Context app = requireContext().getApplicationContext();
                    EvolveRepository.report(block.id(), myUid(), keys[which], getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
                        @Override
                        public void onSuccess(Void value) {
                            SketchwareUtil.toast(app.getString(R.string.evolve_report_done));
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            SketchwareUtil.toastError(app.getString(R.string.evolve_report_failed, message));
                        }
                    });
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void confirmDelete(SharedBlock block) {
        if (!alive()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.evolve_delete_title)
                .setMessage(getString(R.string.evolve_delete_message, block.name()))
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.evolve_delete, (d, w) -> {
                    setLoading(true);
                    EvolveRepository.delete(block.id(), getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
                        @Override
                        public void onSuccess(Void value) {
                            EvolveCache.clear();
                            if (!alive()) return;
                            setLoading(false);
                            SketchwareUtil.toast(getString(R.string.evolve_deleted));
                            all.remove(block);
                            applyFilter();
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            if (!alive()) return;
                            setLoading(false);
                            SketchwareUtil.toastError(getString(R.string.evolve_delete_failed, message));
                        }
                    });
                })
                .show();
    }

    private void showMenu(View anchor, SharedBlock block) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(Menu.NONE, 1, 1, R.string.evolve_menu_preview);
        menu.getMenu().add(Menu.NONE, 2, 2, R.string.evolve_menu_share);
        menu.getMenu().add(Menu.NONE, 3, 3, R.string.evolve_menu_report);
        // Only the owner, or a moderator listed in the database, may delete (the rules enforce it as well).
        if (block.authorUid().equals(myUid()) || moderator) {
            menu.getMenu().add(Menu.NONE, 4, 4, R.string.evolve_menu_delete);
        }
        menu.setOnMenuItemClickListener((MenuItem item) -> {
            switch (item.getItemId()) {
                case 1 -> showInspection(block, SharedBlockCodec.decode(block.blocksJson()), null);
                case 2 -> shareEntry(block);
                case 3 -> confirmReport(block);
                case 4 -> confirmDelete(block);
                default -> {
                    return false;
                }
            }
            return true;
        });
        menu.show();
    }

    // ---------------------------------------------------------------------------------------------------------
    // The list
    // ---------------------------------------------------------------------------------------------------------

    private static final DiffUtil.ItemCallback<SharedBlock> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull SharedBlock a, @NonNull SharedBlock b) {
            return a.id().equals(b.id());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SharedBlock a, @NonNull SharedBlock b) {
            return a.equals(b);
        }
    };

    private class ItemAdapter extends ListAdapter<SharedBlock, ItemAdapter.ViewHolder> {
        ItemAdapter() {
            super(DIFF);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(EvolveItemBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(getItem(position));
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final EvolveItemBinding item;

            ViewHolder(EvolveItemBinding item) {
                super(item.getRoot());
                this.item = item;
            }

            void bind(SharedBlock block) {
                EvolveCard.bind(item, block, decodeCached(block), kindBadge(block.kind()),
                        () -> pick(block), anchor -> showMenu(anchor, block));
            }
        }
    }
}
