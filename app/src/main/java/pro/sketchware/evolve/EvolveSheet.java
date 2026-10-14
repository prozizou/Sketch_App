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
import com.besome.sketch.beans.BlockCollectionBean;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
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
 * Evolve: the blocks other users shared. Lists them from the {@code Blocks_share} node, lets the user pick one into
 * their own block collection, and share one of theirs.
 */
public class EvolveSheet extends BottomSheetDialogFragment {

    /** What the sheet needs from the Logic editor that opened it. */
    public interface Host {
        /** @return the user's block collection (My Block Collection) */
        List<BlockCollectionBean> collection();

        /** Saves {@code blocks} into the collection under {@code name} and shows them in the drawer. */
        void addToCollection(String name, ArrayList<BlockBean> blocks);
    }

    private static final int LOAD_LIMIT = 100;

    private final List<SharedBlock> all = new ArrayList<>();
    private final Map<String, ArrayList<BlockBean>> decoded = new HashMap<>();
    private EvolveSheetBinding binding;
    private ItemAdapter adapter;
    private boolean loading;
    /** The last load failed: the error text stays up instead of "nothing shared yet". */
    private boolean loadFailed;

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
        List<SharedBlock> shown = new ArrayList<>();
        for (SharedBlock block : all) {
            if (query.isEmpty() || matches(block, query)) {
                shown.add(block);
            }
        }
        adapter.submit(shown);
        if (loading) {
            return;
        }
        if (all.isEmpty()) {
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
                || String.join(" ", block.opCodes()).toLowerCase(Locale.ROOT).contains(query);
    }

    @Nullable
    private ArrayList<BlockBean> decodeCached(SharedBlock block) {
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
            case TOO_MANY_BLOCKS -> R.string.evolve_problem_too_many_blocks;
            case TOO_LARGE, NONE -> R.string.evolve_problem_too_large;
        };
    }

    // ---------------------------------------------------------------------------------------------------------
    // Picking
    // ---------------------------------------------------------------------------------------------------------

    private void pick(SharedBlock block) {
        if (!alive()) return;
        // Decoded afresh: the preview may have used (and changed) the cached beans.
        ArrayList<BlockBean> blocks = SharedBlockCodec.decode(block.blocksJson());
        if (blocks == null) {
            SketchwareUtil.toastError(getString(R.string.evolve_corrupt));
            return;
        }
        // The stored flag is the author's word; what the stack really contains decides.
        boolean rawCode = block.usesRawCode() || SharedBlockRules.usesRawCode(SharedBlockCodec.opCodes(blocks));
        if (!rawCode) {
            addPicked(block, blocks);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.evolve_raw_title)
                .setMessage(getString(R.string.evolve_raw_message, authorLabel(block)))
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.evolve_raw_pick, (d, w) -> addPicked(block, blocks))
                .show();
    }

    private void addPicked(SharedBlock block, ArrayList<BlockBean> blocks) {
        Host host = host();
        if (host == null) return;
        List<String> taken = new ArrayList<>();
        List<BlockCollectionBean> collection = host.collection();
        if (collection != null) {
            for (BlockCollectionBean bean : collection) {
                taken.add(bean.name);
            }
        }
        String name = SharedBlockRules.uniqueName(block.name(), taken);
        host.addToCollection(name, blocks);
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
        List<BlockCollectionBean> collection = host.collection();
        if (collection == null || collection.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.evolve_share_title)
                    .setMessage(R.string.evolve_share_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        List<BlockCollectionBean> items = new ArrayList<>(collection);
        String[] names = new String[items.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = items.get(i).name;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.evolve_share_choose)
                .setItems(names, (d, which) -> showShareDetails(items.get(which)))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void showShareDetails(BlockCollectionBean bean) {
        if (!alive() || bean.blocks == null) return;
        EvolveShareDialogBinding form = EvolveShareDialogBinding.inflate(getLayoutInflater());
        form.shareName.setText(bean.name);
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
        publish.setOnClickListener(v -> publish(dialog, publish, bean, String.valueOf(form.shareName.getText()).trim(),
                String.valueOf(form.shareDescription.getText()).trim()));
    }

    private void publish(AlertDialog dialog, Button publishButton, BlockCollectionBean bean, String name, String description) {
        if (!alive()) return;
        FirebaseUser user = AuthGate.currentUser(requireContext());
        if (user == null) {
            SketchwareUtil.toastError(getString(R.string.evolve_unavailable));
            return;
        }
        String json = SharedBlockCodec.encode(bean.blocks);
        Problem problem = SharedBlockRules.checkStack(bean.blocks.size(), json);
        if (problem != Problem.NONE) {
            SketchwareUtil.toastError(getString(problemText(problem)));
            return;
        }
        List<String> opCodes = SharedBlockRules.distinctOpCodes(SharedBlockCodec.opCodes(bean.blocks));
        String author = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? getString(R.string.evolve_anonymous) : user.getDisplayName();
        SharedBlock block = new SharedBlock("", name, description, user.getUid(), author, 0, BuildConfig.VERSION_CODE,
                bean.blocks.size(), opCodes, SharedBlockRules.usesRawCode(opCodes), 0, json);
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
                item.itemName.setText(block.name());
                item.itemAuthor.setText(context.getString(R.string.evolve_by, authorLabel(block)));
                String date = block.createdAt() > 0 ? DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(block.createdAt())) : "";
                item.itemMeta.setText(context.getString(R.string.evolve_meta, block.blockCount(), block.downloads(), date));
                item.itemDescription.setText(block.description());
                item.itemDescription.setVisibility(block.description().isEmpty() ? View.GONE : View.VISIBLE);
                item.itemOpcodes.setText(String.join(" · ", block.opCodes()));
                ArrayList<BlockBean> blocks = decodeCached(block);
                boolean rawCode = block.usesRawCode() || blocks != null && SharedBlockRules.usesRawCode(SharedBlockCodec.opCodes(blocks));
                item.itemRawBadge.setVisibility(rawCode ? View.VISIBLE : View.GONE);
                bindPreview(item.itemPreview, block, blocks);
                item.itemPick.setEnabled(blocks != null);
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
