package pro.sketchware.evolve;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.BlockBean;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import a.a.a.Us;
import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.auth.AuthGate;
import pro.sketchware.databinding.EvolveItemBinding;
import pro.sketchware.databinding.EvolveSheetBinding;
import pro.sketchware.databinding.EvolveShareDialogBinding;
import pro.sketchware.evolve.SharedBlockRules.Problem;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Evolve: what other users shared. Lists it from the {@code Blocks_share} node, lets the user pick an entry into the
 * screen that opened the sheet, and share something of their own. Entries are block stacks, more blocks or the XML of
 * a layout ({@link SharedKind}); each screen offers the kinds it can use.
 */
public class EvolveSheet extends BottomSheetDialogFragment {

    /** What the sheet needs from the Logic editor that opened it. */
    public interface Host {
        /** @return the kinds this screen can pick and share (the sheet lists only these) */
        EnumSet<SharedKind> kinds();

        /** @return what the user can share from this screen, at this moment */
        List<ShareSource> shareSources();

        /** @return names already used for {@code kind} here, so a picked entry never overwrites one of the user's own */
        default List<String> takenNames(SharedKind kind) {
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

    private static final int LOAD_LIMIT = 100;

    private final List<SharedBlock> all = new ArrayList<>();
    private final Map<String, ArrayList<BlockBean>> decoded = new HashMap<>();
    private EvolveSheetBinding binding;
    private ItemAdapter adapter;
    private boolean loading;
    /** The last load failed: the error text stays up instead of "nothing shared yet". */
    private boolean loadFailed;
    /** The kind the chips selected, or null for all the kinds the host offers. */
    @Nullable
    private SharedKind kindFilter;

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
        return alive() && AuthGate.isFirebaseReady(requireContext()) && AuthGate.currentUser(requireContext()) != null;
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
        // A tall sheet even with a short list, so the search field and the list don't jump around.
        binding.evolveRoot.setMinimumHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.78f));
        adapter = new ItemAdapter();
        binding.evolveList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.evolveList.setAdapter(adapter);
        binding.evolveSearch.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });
        setupKindChips();
        binding.evolveRefresh.setOnClickListener(v -> reload());
        binding.evolveShare.setOnClickListener(v -> startShare());
        if (!available()) {
            binding.evolveRefresh.setEnabled(false);
            binding.evolveShare.setEnabled(false);
            binding.evolveSearchLayout.setEnabled(false);
            showState(getString(R.string.evolve_unavailable));
            return;
        }
        reload();
    }

    /** With more than one kind on offer, chips narrow the list to one of them. */
    private void setupKindChips() {
        EnumSet<SharedKind> kinds = kinds();
        if (kinds.size() < 2) {
            return;
        }
        binding.evolveKindsScroll.setVisibility(View.VISIBLE);
        addKindChip(null, R.string.evolve_kind_all, true);
        for (SharedKind kind : kinds) {
            addKindChip(kind, kindName(kind), false);
        }
        binding.evolveKinds.setOnCheckedStateChangeListener((group, ids) -> {
            kindFilter = null;
            if (!ids.isEmpty()) {
                Chip chip = group.findViewById(ids.get(0));
                if (chip != null && chip.getTag() instanceof SharedKind kind) {
                    kindFilter = kind;
                }
            }
            applyFilter();
        });
    }

    private void addKindChip(@Nullable SharedKind kind, @StringRes int label, boolean checked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.evolve_kind_chip, binding.evolveKinds, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(kind);
        chip.setChecked(checked);
        binding.evolveKinds.addView(chip);
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
        binding = null;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Loading and filtering
    // ---------------------------------------------------------------------------------------------------------

    private void setLoading(boolean loading) {
        this.loading = loading;
        if (binding != null) {
            binding.evolveProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showState(@Nullable String text) {
        binding.evolveState.setText(text);
        binding.evolveState.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void reload() {
        if (!available() || loading) {
            return;
        }
        setLoading(true);
        loadFailed = false;
        showState(null);
        EvolveRepository.load(LOAD_LIMIT, getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(List<SharedBlock> value) {
                if (!alive()) return;
                setLoading(false);
                all.clear();
                all.addAll(value);
                decoded.clear();
                applyFilter();
            }

            @Override
            public void onError(@NonNull String message) {
                if (!alive()) return;
                setLoading(false);
                loadFailed = true;
                all.clear();
                applyFilter();
                showState(getString(R.string.evolve_error, message));
            }
        });
    }

    private void applyFilter() {
        if (!alive()) return;
        String query = String.valueOf(binding.evolveSearch.getText()).trim().toLowerCase(Locale.ROOT);
        EnumSet<SharedKind> offered = kinds();
        List<SharedBlock> shown = new ArrayList<>();
        List<SharedBlock> usable = new ArrayList<>();
        for (SharedBlock block : all) {
            if (!offered.contains(block.kind()) || kindFilter != null && block.kind() != kindFilter) {
                continue;
            }
            usable.add(block);
            if (query.isEmpty() || matches(block, query)) {
                shown.add(block);
            }
        }
        adapter.submit(shown);
        if (loading) {
            return;
        }
        if (usable.isEmpty()) {
            if (!loadFailed) {
                showState(getString(R.string.evolve_empty));
            }
        } else {
            showState(shown.isEmpty() ? getString(R.string.evolve_no_results) : null);
        }
    }

    private static boolean matches(SharedBlock block, String query) {
        return block.name().toLowerCase(Locale.ROOT).contains(query)
                || block.authorName().toLowerCase(Locale.ROOT).contains(query)
                || block.description().toLowerCase(Locale.ROOT).contains(query)
                || String.join(" ", block.opCodes()).toLowerCase(Locale.ROOT).contains(query)
                || block.kind() == SharedKind.VIEW && block.xml().toLowerCase(Locale.ROOT).contains(query);
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
        return block.authorName().isEmpty() ? getString(R.string.evolve_anonymous) : block.authorName();
    }

    @StringRes
    private static int problemText(Problem problem) {
        return switch (problem) {
            case NAME_EMPTY -> R.string.evolve_problem_name_empty;
            case NAME_TOO_LONG -> R.string.evolve_problem_name_too_long;
            case NAME_INVALID -> R.string.evolve_problem_name_invalid;
            case DESCRIPTION_TOO_LONG -> R.string.evolve_problem_description_too_long;
            case NO_BLOCKS -> R.string.evolve_problem_no_blocks;
            case XML_EMPTY -> R.string.evolve_problem_xml_empty;
            case XML_INVALID -> R.string.evolve_problem_xml_invalid;
            case TOO_MANY_BLOCKS -> R.string.evolve_problem_too_many_blocks;
            case TOO_LARGE, NONE -> R.string.evolve_problem_too_large;
        };
    }

    // ---------------------------------------------------------------------------------------------------------
    // Picking
    // ---------------------------------------------------------------------------------------------------------

    private void pick(SharedBlock block) {
        if (!alive()) return;
        Host host = host();
        if (host == null) return;
        if (block.kind() == SharedKind.VIEW) {
            if (SharedXml.check(block.xml()).problem() != Problem.NONE) {
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
        if (!rawCode) {
            addPicked(host, block, blocks);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.evolve_raw_title)
                .setMessage(getString(R.string.evolve_raw_message, authorLabel(block)))
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.evolve_raw_pick, (d, w) -> addPicked(host, block, blocks))
                .show();
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
    // Sharing and deleting
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
                .setItems(labels, (d, which) -> showShareDetails(sources.get(which)))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showShareDetails(ShareSource source) {
        if (!alive()) return;
        EvolveShareDialogBinding form = EvolveShareDialogBinding.inflate(getLayoutInflater());
        form.shareName.setText(source.name());
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.evolve_share_title)
                .setView(form.getRoot())
                .setPositiveButton(R.string.evolve_share_publish, null)
                .setNegativeButton(R.string.common_word_cancel, null)
                .create();
        dialog.show();
        Button publish = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Runnable validate = () -> {
            Problem name = SharedBlockRules.checkName(String.valueOf(form.shareName.getText()));
            Problem description = SharedBlockRules.checkDescription(String.valueOf(form.shareDescription.getText()));
            form.shareNameLayout.setError(name == Problem.NONE ? null : getString(problemText(name)));
            form.shareDescriptionLayout.setError(description == Problem.NONE ? null : getString(problemText(description)));
            publish.setEnabled(name == Problem.NONE && description == Problem.NONE);
        };
        BaseTextWatcher watcher = new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validate.run();
            }
        };
        form.shareName.addTextChangedListener(watcher);
        form.shareDescription.addTextChangedListener(watcher);
        validate.run();
        publish.setOnClickListener(v -> publish(dialog, publish, source, String.valueOf(form.shareName.getText()).trim(),
                String.valueOf(form.shareDescription.getText()).trim()));
    }

    /** @return the entry to publish for {@code source}, or null after telling the user why it can't be shared */
    @Nullable
    private SharedBlock build(ShareSource source, String name, String description, FirebaseUser user) {
        String author = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? getString(R.string.evolve_anonymous) : user.getDisplayName();
        if (source.kind() == SharedKind.VIEW) {
            SharedXml.Result result = SharedXml.check(source.xml());
            if (result.problem() != Problem.NONE) {
                SketchwareUtil.toastError(getString(problemText(result.problem())));
                return null;
            }
            return new SharedBlock("", name, description, user.getUid(), author, 0, BuildConfig.VERSION_CODE,
                    result.elements(), List.of(), false, 0, "", SharedKind.VIEW, "", source.xml());
        }
        ArrayList<BlockBean> blocks = source.blocks();
        String json = SharedBlockCodec.encode(blocks);
        Problem problem = SharedBlockRules.checkStack(blocks.size(), json);
        if (problem != Problem.NONE) {
            SketchwareUtil.toastError(getString(problemText(problem)));
            return null;
        }
        List<String> opCodes = SharedBlockRules.distinctOpCodes(SharedBlockCodec.opCodes(blocks));
        return new SharedBlock("", name, description, user.getUid(), author, 0, BuildConfig.VERSION_CODE,
                blocks.size(), opCodes, SharedBlockRules.usesRawCode(opCodes), 0, json, source.kind(), source.spec(), "");
    }

    private void publish(AlertDialog dialog, Button publishButton, ShareSource source, String name, String description) {
        if (!alive()) return;
        FirebaseUser user = AuthGate.currentUser(requireContext());
        if (user == null) {
            SketchwareUtil.toastError(getString(R.string.evolve_unavailable));
            return;
        }
        SharedBlock block = build(source, name, description, user);
        if (block == null) {
            return;
        }
        publishButton.setEnabled(false);
        setLoading(true);
        EvolveRepository.publish(block, getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(String id) {
                dialog.dismiss();
                if (!alive()) return;
                setLoading(false);
                SketchwareUtil.toast(getString(R.string.evolve_share_done, name));
                reload();
            }

            @Override
            public void onError(@NonNull String message) {
                if (dialog.isShowing()) {
                    publishButton.setEnabled(true);
                }
                if (!alive()) return;
                setLoading(false);
                SketchwareUtil.toastError(getString(R.string.evolve_share_failed, message));
            }
        });
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

    // ---------------------------------------------------------------------------------------------------------
    // The list
    // ---------------------------------------------------------------------------------------------------------

    private class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {
        private List<SharedBlock> items = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged")
        void submit(List<SharedBlock> shown) {
            items = shown;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(EvolveItemBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final EvolveItemBinding item;

            ViewHolder(EvolveItemBinding item) {
                super(item.getRoot());
                this.item = item;
            }

            void bind(SharedBlock block) {
                Context context = itemView.getContext();
                boolean isXml = block.kind() == SharedKind.VIEW;
                item.itemKind.setText(kindBadge(block.kind()));
                item.itemName.setText(block.name());
                item.itemAuthor.setText(context.getString(R.string.evolve_by, authorLabel(block)));
                String date = block.createdAt() > 0 ? DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(block.createdAt())) : "";
                item.itemMeta.setText(context.getString(isXml ? R.string.evolve_meta_xml : R.string.evolve_meta,
                        block.blockCount(), block.downloads(), date));
                item.itemDescription.setText(block.description());
                item.itemDescription.setVisibility(block.description().isEmpty() ? View.GONE : View.VISIBLE);
                item.itemOpcodes.setText(String.join(" \u00b7 ", block.opCodes()));
                item.itemOpcodes.setVisibility(isXml ? View.GONE : View.VISIBLE);
                item.itemXml.setText(isXml ? block.xml() : "");
                item.itemXml.setVisibility(isXml ? View.VISIBLE : View.GONE);
                ArrayList<BlockBean> blocks = decodeCached(block);
                boolean rawCode = block.usesRawCode() || blocks != null && SharedBlockRules.usesRawCode(SharedBlockCodec.opCodes(blocks));
                item.itemRawBadge.setVisibility(rawCode ? View.VISIBLE : View.GONE);
                bindPreview(item.itemPreview, block, blocks);
                item.itemPick.setEnabled(isXml || blocks != null);
                item.itemPick.setOnClickListener(v -> pick(block));
                FirebaseUser user = AuthGate.currentUser(context);
                boolean mine = user != null && user.getUid().equals(block.authorUid());
                item.itemDelete.setVisibility(mine ? View.VISIBLE : View.GONE);
                item.itemDelete.setOnClickListener(v -> confirmDelete(block));
            }
        }
    }

    /** Draws the stack like the drawer does; if the editor can't draw it, the opcode line under it still says what it is. */
    private static void bindPreview(FrameLayout holder, SharedBlock block, @Nullable ArrayList<BlockBean> blocks) {
        holder.removeAllViews();
        if (blocks == null) {
            return;
        }
        try {
            BlockBean first = blocks.get(0);
            holder.addView(new Us(holder.getContext(), first.type, first.typeName, first.opCode, block.name(), blocks));
        } catch (RuntimeException e) {
            holder.removeAllViews();
        }
    }
}
