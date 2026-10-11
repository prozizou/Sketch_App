package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.blocks.manager.BlockLibrary;
import pro.sketchware.blocks.manager.BlockManagerAdapter;
import pro.sketchware.blocks.manager.BlockStore;
import pro.sketchware.blocks.manager.PaletteListing;
import pro.sketchware.blocks.manager.PaletteListing.Filter;
import pro.sketchware.blocks.manager.PaletteListing.Sort;
import pro.sketchware.databinding.ActivityBlocksManagerBinding;
import pro.sketchware.databinding.DialogBlockConfigurationBinding;
import pro.sketchware.databinding.DialogPaletteBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * The palettes of the user's custom blocks: search, counts, filters, sorting, favorites, folders, drag and drop,
 * multiple selection, import / export and the recycle bin. Blocks of a palette are managed in
 * {@link BlocksManagerDetailsActivity} and edited in {@link BlocksManagerCreatorActivity}, as before.
 */
public class BlocksManager extends BaseAppCompatActivity implements BlockManagerAdapter.Listener {
    private static final String PREFS = "block_manager";
    private static final String KEY_SORT = "sort";
    private static final File EXPORT_DIR = new File(FileUtil.getExternalStorageDir(), ".sketch_nws/resources/block/export/");

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Integer> selected = new LinkedHashSet<>();
    private ActivityBlocksManagerBinding binding;
    private BlockManagerAdapter adapter;
    private ItemTouchHelper dragHelper;
    private BlockStore store;
    /** Null while loading, or when a file can't be read (then nothing is ever written). */
    @Nullable
    private BlockLibrary library;
    private Filter filter = Filter.ALL;
    private Sort sort = Sort.MANUAL;
    private String query = "";
    private int listingGeneration;
    @Nullable
    private ActionMode selectionMode;
    private final Runnable searchLater = this::showList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityBlocksManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        UI.addSystemWindowInsetToPadding(binding.appBar, true, true, true, false);
        UI.addSystemWindowInsetToPadding(binding.list, true, false, true, true);
        UI.addSystemWindowInsetToMargin(binding.fab, false, false, true, true);

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        try {
            sort = Sort.valueOf(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SORT, Sort.MANUAL.name()));
        } catch (IllegalArgumentException ignored) {
            sort = Sort.MANUAL;
        }

        adapter = new BlockManagerAdapter(this);
        binding.list.setLayoutManager(new LinearLayoutManager(this));
        binding.list.setAdapter(adapter);
        dragHelper = new ItemTouchHelper(new DragCallback());
        dragHelper.attachToRecyclerView(binding.list);

        binding.search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString();
                binding.searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                main.removeCallbacks(searchLater);
                main.postDelayed(searchLater, 150);
            }
        });
        binding.searchClear.setOnClickListener(v -> binding.search.setText(""));
        binding.filters.setOnCheckedStateChangeListener((group, checked) -> {
            int id = checked.isEmpty() ? R.id.filter_all : checked.get(0);
            filter = id == R.id.filter_favorites ? Filter.FAVORITES
                    : id == R.id.filter_recent ? Filter.RECENT
                    : id == R.id.filter_custom ? Filter.CUSTOM : Filter.ALL;
            showList();
        });
        binding.statBin.setOnClickListener(v -> openPalette(BlockLibrary.RECYCLE_BIN));
        binding.statBin.setOnLongClickListener(v -> {
            confirmEmptyRecycleBin();
            return true;
        });
        binding.fab.setOnClickListener(this::showCreateMenu);
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onStop() {
        super.onStop();
        BlockLoader.refresh();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    // ---- loading and saving

    private void load() {
        String paletteFile = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(
                ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
        String blockFile = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(
                ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));
        BlockStore newStore = new BlockStore(new File(paletteFile), new File(blockFile), getGson());
        io.execute(() -> {
            try {
                BlockLibrary loaded = newStore.load();
                main.post(() -> {
                    store = newStore;
                    library = loaded;
                    showList();
                });
            } catch (BlockStore.UnreadableFile e) {
                main.post(() -> {
                    store = newStore;
                    library = null;
                    showList();
                    SketchwareUtil.showFailedToParseJsonDialog(this, e.file,
                            e.file.equals(newStore.paletteFile()) ? "Custom Block Palettes" : "Custom Blocks", v -> load());
                });
            } catch (IOException e) {
                main.post(() -> SketchwareUtil.toastError(getString(R.string.bm_save_failed, e.getMessage())));
            }
        });
    }

    /** Writes both files in the background, in order, then refreshes the list. */
    private void save() {
        if (library == null || store == null) return;
        BlockStore target = store;
        String palettes = getGson().toJson(library.palettes());
        String blocks = getGson().toJson(library.blocks());
        io.execute(() -> {
            try {
                BlockStore.write(target.paletteFile(), palettes);
                BlockStore.write(target.blockFile(), blocks);
            } catch (IOException e) {
                main.post(() -> SketchwareUtil.toastError(getString(R.string.bm_save_failed, e.getMessage())));
            }
        });
        showList();
    }

    /** A copy of the current state, to put back with Undo. */
    private BlockLibrary snapshot() {
        return library == null ? null : new BlockLibrary(library.palettes(), library.blocks());
    }

    private void restore(BlockLibrary previous) {
        library = previous;
        save();
    }

    // ---- list

    private void showList() {
        BlockLibrary current = library;
        if (current == null) {
            adapter.submit(List.of());
            binding.statPalettesValue.setText("–");
            binding.statBlocksValue.setText("–");
            binding.statBinValue.setText("–");
            showEmpty(false);
            return;
        }
        binding.statPalettesValue.setText(String.valueOf(current.paletteCount()));
        binding.statBlocksValue.setText(String.valueOf(current.blockCount()));
        binding.statBinValue.setText(String.valueOf(current.recycleBinCount()));

        // Searching large block files happens off the main thread, on a copy.
        BlockLibrary copy = new BlockLibrary(current.palettes(), current.blocks());
        Filter f = filter;
        Sort s = sort;
        String q = query;
        Set<Integer> selection = new LinkedHashSet<>(selected);
        int generation = ++listingGeneration;
        io.execute(() -> {
            PaletteListing.Result result = PaletteListing.build(copy, f, s, q);
            boolean reorderable = PaletteListing.canReorder(f, s, q) && selection.isEmpty();
            List<BlockManagerAdapter.Row> rows = new ArrayList<>();
            boolean searching = !q.trim().isEmpty();
            if (searching && !result.palettes().isEmpty()) rows.add(new BlockManagerAdapter.Section(getString(R.string.bm_section_palettes)));
            for (PaletteListing.PaletteRow row : result.palettes()) {
                rows.add(new BlockManagerAdapter.Palette(row, selection.contains(row.index()), reorderable));
            }
            if (searching && !result.blocks().isEmpty()) {
                rows.add(new BlockManagerAdapter.Section(getString(R.string.bm_section_blocks)));
                for (PaletteListing.BlockRow row : result.blocks()) {
                    Object color = copy.blocks().get(row.blockIndex()).get("color");
                    rows.add(new BlockManagerAdapter.Block(row, color == null ? "#9E9E9E" : color.toString()));
                }
            }
            main.post(() -> {
                if (generation != listingGeneration || binding == null) return;
                adapter.submit(rows);
                showEmpty(rows.isEmpty());
            });
        });
    }

    private void showEmpty(boolean empty) {
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        boolean noPalettes = library != null && library.paletteCount() == 0;
        binding.emptyTitle.setText(noPalettes ? R.string.bm_empty_title : R.string.bm_empty_filter_title);
        binding.emptyMessage.setText(noPalettes ? R.string.bm_empty_message : R.string.bm_empty_filter_message);
    }

    // ---- rows

    @Override
    public void onPaletteClick(PaletteListing.PaletteRow row) {
        if (selectionMode != null) {
            toggleSelection(row.index());
        } else {
            openPalette(BlockLibrary.paletteId(row.index()));
        }
    }

    @Override
    public void onPaletteLongClick(PaletteListing.PaletteRow row) {
        if (selectionMode == null) selectionMode = startSupportActionMode(new SelectionCallback());
        toggleSelection(row.index());
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder holder) {
        dragHelper.startDrag(holder);
    }

    @Override
    public void onBlockClick(PaletteListing.BlockRow row) {
        if (library == null) return;
        if (row.inRecycleBin()) {
            openPalette(BlockLibrary.RECYCLE_BIN);
            return;
        }
        Intent intent = new Intent(this, BlocksManagerCreatorActivity.class);
        intent.putExtra("mode", "edit");
        intent.putExtra("color", library.color(row.paletteIndex()));
        intent.putExtra("path", store.blockFile().getAbsolutePath());
        intent.putExtra("pos", String.valueOf(row.blockIndex()));
        startActivity(intent);
    }

    private void openPalette(int paletteId) {
        if (store == null) return;
        Intent intent = new Intent(this, BlocksManagerDetailsActivity.class);
        intent.putExtra("position", String.valueOf(paletteId));
        intent.putExtra("dirB", store.blockFile().getAbsolutePath());
        intent.putExtra("dirP", store.paletteFile().getAbsolutePath());
        startActivity(intent);
    }

    @Override
    public void onPaletteMenu(PaletteListing.PaletteRow row, View anchor) {
        if (library == null) return;
        int index = row.index();
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add(0, 1, 0, R.string.bm_edit);
        menu.add(0, 2, 1, R.string.bm_duplicate);
        menu.add(0, 3, 2, library.isFavorite(index) ? R.string.bm_favorite_remove : R.string.bm_favorite_add);
        menu.add(0, 4, 3, R.string.bm_move_to_folder);
        menu.add(0, 5, 4, R.string.bm_insert_above);
        menu.add(0, 6, 5, R.string.bm_export);
        menu.add(0, 7, 6, R.string.bm_delete);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> showPaletteDialog(index, null);
                case 2 -> {
                    library.duplicatePalette(index);
                    save();
                }
                case 3 -> {
                    library.setFavorite(index, !library.isFavorite(index));
                    save();
                }
                case 4 -> askFolder(List.of(index));
                case 5 -> showPaletteDialog(null, index);
                case 6 -> export(List.of(index));
                case 7 -> confirmDelete(List.of(index));
                default -> {
                    return false;
                }
            }
            return true;
        });
        popup.show();
    }

    // ---- selection

    private void toggleSelection(int paletteIndex) {
        if (!selected.remove(paletteIndex)) selected.add(paletteIndex);
        if (selected.isEmpty() && selectionMode != null) {
            selectionMode.finish();
            return;
        }
        if (selectionMode != null) {
            selectionMode.setTitle(getResources().getQuantityString(R.plurals.bm_selected_count, selected.size(), selected.size()));
        }
        showList();
    }

    private final class SelectionCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.block_manager_selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (library == null) return false;
            List<Integer> indices = new ArrayList<>(selected);
            int id = item.getItemId();
            if (id == R.id.action_select_all) {
                for (int i = 0; i < library.paletteCount(); i++) selected.add(i);
                mode.setTitle(getResources().getQuantityString(R.plurals.bm_selected_count, selected.size(), selected.size()));
                showList();
                return true;
            } else if (id == R.id.action_favorite) {
                boolean allFavorite = true;
                for (int i : indices) allFavorite &= library.isFavorite(i);
                for (int i : indices) library.setFavorite(i, !allFavorite);
                save();
            } else if (id == R.id.action_folder) {
                askFolder(indices);
            } else if (id == R.id.action_export) {
                export(indices);
            } else if (id == R.id.action_delete) {
                confirmDelete(indices);
            } else {
                return false;
            }
            mode.finish();
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            selectionMode = null;
            selected.clear();
            showList();
        }
    }

    // ---- drag and drop

    private final class DragCallback extends ItemTouchHelper.SimpleCallback {
        private int from = -1;
        private int to = -1;

        DragCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            int position = viewHolder.getBindingAdapterPosition();
            boolean palette = position != RecyclerView.NO_POSITION
                    && adapter.getItemViewType(position) == BlockManagerAdapter.TYPE_PALETTE;
            return palette && PaletteListing.canReorder(filter, sort, query) && selected.isEmpty()
                    ? makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) : 0;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            int fromPos = viewHolder.getBindingAdapterPosition();
            int toPos = target.getBindingAdapterPosition();
            if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false;
            if (from == -1) from = fromPos;
            to = toPos;
            adapter.move(fromPos, toPos);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                viewHolder.itemView.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
                viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            // The full list in manual order is shown while dragging: list positions are palette indices.
            if (from != -1 && to != -1 && from != to && library != null) {
                library.movePalette(from, to);
                save();
            }
            from = -1;
            to = -1;
        }
    }

    // ---- create, edit, delete

    private void showCreateMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.END);
        popup.getMenu().add(0, 1, 0, R.string.bm_new_palette);
        popup.getMenu().add(0, 2, 1, R.string.bm_new_block);
        popup.getMenu().add(0, 3, 2, R.string.bm_import);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> showPaletteDialog(null, null);
                case 2 -> createBlock();
                case 3 -> pickImportFile();
                default -> {
                    return false;
                }
            }
            return true;
        });
        popup.show();
    }

    /**
     * @param editIndex   the palette to edit, or null to create one
     * @param insertAt    where to insert the new palette, or null for the end
     */
    private void showPaletteDialog(@Nullable Integer editIndex, @Nullable Integer insertAt) {
        if (library == null) return;
        DialogPaletteBinding dialogBinding = DialogPaletteBinding.inflate(getLayoutInflater());
        if (editIndex != null) {
            dialogBinding.nameEditText.setText(library.name(editIndex));
            dialogBinding.colorEditText.setText(library.color(editIndex).replace("#", ""));
        }
        dialogBinding.openColorPalette.setOnClickListener(v -> {
            String current = "#" + Helper.getText(dialogBinding.colorEditText);
            ColorPickerDialog picker = new ColorPickerDialog(this,
                    PropertiesUtil.isHexColor(current) ? PropertiesUtil.parseColor(current) : 0xFFFFFFFF, false, false);
            picker.a(new ColorPickerDialog.b() {
                @Override
                public void a(int colorInt) {
                    dialogBinding.colorEditText.setText(String.format("%06X", colorInt & 0x00FFFFFF));
                }

                @Override
                public void a(String name, int colorInt) {
                    dialogBinding.colorEditText.setText(String.format("%06X", colorInt & 0x00FFFFFF));
                }
            });
            picker.showAtLocation(dialogBinding.openColorPalette, Gravity.CENTER, 0, 0);
        });
        var dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(editIndex == null ? R.string.bm_palette_dialog_new : R.string.bm_palette_dialog_edit)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.common_word_save, null)
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = Helper.getText(dialogBinding.nameEditText).trim();
            String color = "#" + Helper.getText(dialogBinding.colorEditText).trim();
            if (name.isEmpty()) {
                dialogBinding.nameEditText.setError(getString(R.string.bm_palette_name_empty));
                return;
            }
            if (!PropertiesUtil.isHexColor(color)) {
                dialogBinding.colorEditText.setError(getString(R.string.bm_palette_color_invalid));
                return;
            }
            if (editIndex != null) {
                library.editPalette(editIndex, name, color);
            } else {
                library.createPalette(name, color, insertAt);
            }
            save();
            dialog.dismiss();
        });
    }

    private void askFolder(List<Integer> indices) {
        if (library == null || indices.isEmpty()) return;
        TextInputLayout layout = new TextInputLayout(this);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setHint(R.string.bm_folder_hint);
        input.setText(library.folder(indices.get(0)));
        layout.addView(input);
        int pad = SketchwareUtil.dpToPx(20);
        layout.setPadding(pad, pad / 2, pad, 0);
        List<String> folders = library.folders();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bm_folder_title)
                .setView(layout)
                .setPositiveButton(R.string.common_word_save, (d, w) -> {
                    for (int i : indices) library.setFolder(i, Helper.getText(input));
                    save();
                })
                .setNegativeButton(R.string.common_word_cancel, null);
        if (!folders.isEmpty()) {
            // Existing folders, to pick one without typing it again.
            builder.setItems(folders.toArray(new String[0]), (d, which) -> {
                for (int i : indices) library.setFolder(i, folders.get(which));
                save();
            });
        }
        builder.show();
    }

    private void confirmDelete(List<Integer> indices) {
        if (library == null || indices.isEmpty()) return;
        int blocks = 0;
        for (int i : indices) blocks += library.blockCount(i);
        String what = indices.size() == 1 ? library.name(indices.get(0))
                : getResources().getQuantityString(R.plurals.bm_selected_count, indices.size(), indices.size());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_delete)
                .setTitle(getString(R.string.bm_delete_title, what))
                .setMessage(getResources().getQuantityString(R.plurals.bm_blocks_count, blocks, blocks))
                .setNegativeButton(R.string.common_word_cancel, null);
        if (blocks > 0) {
            builder.setPositiveButton(R.string.block_move_to_bin, (d, w) -> deletePalettes(indices, true))
                    .setNeutralButton(R.string.bm_delete_permanently, (d, w) -> deletePalettes(indices, false));
        } else {
            builder.setPositiveButton(R.string.bm_delete, (d, w) -> deletePalettes(indices, false));
        }
        builder.show();
    }

    private void deletePalettes(List<Integer> indices, boolean toRecycleBin) {
        BlockLibrary before = snapshot();
        List<Integer> sorted = new ArrayList<>(indices);
        sorted.sort(java.util.Collections.reverseOrder());
        for (int i : sorted) library.deletePalette(i, toRecycleBin);
        save();
        Snackbar.make(binding.root, toRecycleBin ? R.string.bm_deleted_to_bin : R.string.bm_deleted, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.fab)
                .setAction(R.string.bm_undo, v -> restore(before))
                .show();
    }

    private void confirmEmptyRecycleBin() {
        if (library == null || library.recycleBinCount() == 0) return;
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_delete)
                .setTitle(R.string.bm_empty_bin_title)
                .setMessage(R.string.bm_empty_bin_message)
                .setPositiveButton(R.string.bm_empty_bin, (d, w) -> {
                    library.emptyRecycleBin();
                    save();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void createBlock() {
        if (library == null) return;
        if (library.paletteCount() == 0) {
            SketchwareUtil.toast(getString(R.string.bm_need_palette));
            showPaletteDialog(null, null);
            return;
        }
        String[] names = new String[library.paletteCount()];
        for (int i = 0; i < names.length; i++) names[i] = library.name(i);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bm_choose_palette)
                .setItems(names, (d, which) -> {
                    Intent intent = new Intent(this, BlocksManagerCreatorActivity.class);
                    intent.putExtra("mode", "add");
                    intent.putExtra("color", library.color(which));
                    intent.putExtra("path", store.blockFile().getAbsolutePath());
                    intent.putExtra("pallet", String.valueOf(BlockLibrary.paletteId(which)));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    // ---- import / export

    private void pickImportFile() {
        if (library == null) return;
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"json"});
        options.setTitle(getString(R.string.bm_import));
        new FilePickerDialogFragment(options, new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                readImport(file);
            }
        }).show(getSupportFragmentManager(), "filePickerDialog");
    }

    private void readImport(File file) {
        io.execute(() -> {
            List<HashMap<String, Object>> entries;
            try {
                entries = new BlockStore(file, file, getGson()).readList(file);
            } catch (IOException e) {
                main.post(() -> SketchwareUtil.toastError(getString(R.string.bm_import_unreadable)));
                return;
            }
            main.post(() -> {
                if (entries.isEmpty()) {
                    SketchwareUtil.toastError(getString(R.string.bm_import_empty));
                } else {
                    askImportTarget(file, entries);
                }
            });
        });
    }

    private void askImportTarget(File file, List<HashMap<String, Object>> entries) {
        if (library == null) return;
        String newName = file.getName().replaceAll("\\.json$", "");
        String[] targets = new String[library.paletteCount() + 1];
        targets[0] = getString(R.string.bm_import_new_palette, newName);
        for (int i = 0; i < library.paletteCount(); i++) targets[i + 1] = library.name(i);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bm_import_into)
                .setItems(targets, (d, which) -> askConflictPolicy(entries, which == 0 ? null : which - 1, newName))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void askConflictPolicy(List<HashMap<String, Object>> entries, @Nullable Integer paletteIndex, String newName) {
        boolean conflicts = false;
        for (Map<String, Object> entry : entries) {
            if (entry.get("name") != null && library.indexOfBlock(entry.get("name").toString()) >= 0) conflicts = true;
        }
        if (!conflicts) {
            doImport(entries, paletteIndex, newName, BlockLibrary.Conflict.RENAME);
            return;
        }
        AtomicInteger choice = new AtomicInteger(1);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bm_import_conflicts)
                .setSingleChoiceItems(new String[]{getString(R.string.bm_conflict_skip), getString(R.string.bm_conflict_rename),
                        getString(R.string.bm_conflict_replace)}, 1, (d, which) -> choice.set(which))
                .setPositiveButton(R.string.bm_import, (d, w) -> doImport(entries, paletteIndex, newName,
                        BlockLibrary.Conflict.values()[choice.get()]))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void doImport(List<HashMap<String, Object>> entries, @Nullable Integer paletteIndex, String newName, BlockLibrary.Conflict conflict) {
        BlockLibrary before = snapshot();
        List<BlockLibrary.ImportProblem> problems = BlockLibrary.validate(entries);
        int added;
        if (paletteIndex == null) {
            int index = library.importAsPalette(newName, "#" + String.format("%06X", 0x8A55D7), entries, conflict);
            added = library.blockCount(index);
        } else {
            added = library.importBlocks(entries, paletteIndex, conflict).added();
        }
        save();
        String message = getResources().getQuantityString(R.plurals.bm_import_done, added, added);
        if (!problems.isEmpty()) {
            message += "\n" + getResources().getQuantityString(R.plurals.bm_import_invalid, problems.size(), problems.size(),
                    problems.get(0).message());
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.fab)
                .setAction(R.string.bm_undo, v -> restore(before))
                .show();
    }

    private void export(List<Integer> indices) {
        if (library == null) return;
        List<String> written = new ArrayList<>();
        for (int index : indices) {
            String fileName = library.name(index).replaceAll("[\\\\/:*?\"<>|]", "_") + ".json";
            File file = new File(EXPORT_DIR, fileName);
            try {
                BlockStore.write(file, getGson().toJson(library.exportPalette(index)));
                written.add(file.getAbsolutePath());
            } catch (IOException e) {
                SketchwareUtil.toastError(getString(R.string.bm_save_failed, e.getMessage()));
            }
        }
        if (!written.isEmpty()) {
            SketchwareUtil.toast(getString(R.string.bm_exported, written.size() == 1 ? written.get(0) : EXPORT_DIR.getAbsolutePath()));
        }
    }

    // ---- toolbar

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.block_manager_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_sort) {
            showSortChoice();
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            showBlockConfigurationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortChoice() {
        Sort[] sorts = {Sort.MANUAL, Sort.NAME, Sort.DATE, Sort.BLOCKS};
        String[] labels = {getString(R.string.bm_sort_manual), getString(R.string.bm_sort_name),
                getString(R.string.bm_sort_date), getString(R.string.bm_sort_blocks)};
        int current = java.util.Arrays.asList(sorts).indexOf(sort);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bm_sort)
                .setSingleChoiceItems(labels, current, (d, which) -> {
                    sort = sorts[which];
                    SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                    editor.putString(KEY_SORT, sort.name()).apply();
                    showList();
                    d.dismiss();
                })
                .show();
    }

    /** Where palette.json and block.json live (unchanged from the previous Block Manager). */
    private void showBlockConfigurationDialog() {
        if (store == null) return;
        DialogBlockConfigurationBinding dialogBinding = DialogBlockConfigurationBinding.inflate(getLayoutInflater());
        dialogBinding.palettesPath.setText(store.paletteFile().getAbsolutePath().replace(FileUtil.getExternalStorageDir(), ""));
        dialogBinding.blocksPath.setText(store.blockFile().getAbsolutePath().replace(FileUtil.getExternalStorageDir(), ""));
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_folder_48dp)
                .setTitle(R.string.bm_settings)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.common_word_save, (d, w) -> {
                    ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, Objects.requireNonNull(dialogBinding.palettesPath.getText()).toString());
                    ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, Objects.requireNonNull(dialogBinding.blocksPath.getText()).toString());
                    load();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .setNeutralButton("Defaults", (d, w) -> {
                    ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
                    ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));
                    load();
                })
                .show();
    }
}
