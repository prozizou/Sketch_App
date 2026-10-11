package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.blocks.manager.BlockLibrary;
import pro.sketchware.blocks.manager.BlockStore;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BlocksManagerDetailsActivity extends BaseAppCompatActivity {

    private static final String BLOCK_EXPORT_PATH = new File(FileUtil.getExternalStorageDir(), ".sketch_nws/resources/block/export/").getAbsolutePath();

    private final ArrayList<HashMap<String, Object>> filtered_list = new ArrayList<>();
    private final ArrayList<Integer> reference_list = new ArrayList<>();
    private ArrayList<HashMap<String, Object>> all_blocks_list = new ArrayList<>();
    private String blocks_path = "";
    private String mode = "normal";
    private ArrayList<HashMap<String, Object>> pallet_list = new ArrayList<>();
    private String pallet_path = "";
    private int palette = 0;
    private Parcelable listViewSavedState;

    private Toolbar toolbar;
    private ListView block_list;
    private LinearLayout background;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fab_button;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        });
        setContentView(R.layout.activity_blocks_manager_details);

        background = findViewById(R.id.background);
        block_list = findViewById(R.id.block_list);
        fab_button = findViewById(R.id.fab_button);

        initialize();
        _receive_intents();
    }

    private void initialize() {

        toolbar = (Toolbar) getLayoutInflater().inflate(R.layout.toolbar_improved, background, false);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        background.addView(toolbar, 0);

        fab_button.setOnClickListener(v -> {
            Object paletteColor = pallet_list.get(palette - 9).get("color");
            if (paletteColor instanceof String) {
                Intent intent = new Intent(getApplicationContext(), BlocksManagerCreatorActivity.class);
                intent.putExtra("mode", "add");
                intent.putExtra("color", (String) paletteColor);
                intent.putExtra("path", blocks_path);
                intent.putExtra("pallet", String.valueOf(palette));
                startActivity(intent);
            } else {
                SketchwareUtil.toastError("Invalid color of palette #" + (palette - 9));
            }
        });
    }

    public void openFileExplorerImport() {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"json"});
        options.setTitle("Select a JSON file");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                if (FileUtil.readFile(file.getAbsolutePath()).isEmpty()) {
                    SketchwareUtil.toastError("The selected file is empty!");
                } else if (FileUtil.readFile(file.getAbsolutePath()).equals("[]")) {
                    SketchwareUtil.toastError("The selected file is empty!");
                } else {
                    try {
                        ArrayList<HashMap<String, Object>> readMap = getGson().fromJson(FileUtil.readFile(file.getAbsolutePath()), Helper.TYPE_MAP_LIST);
                        _importBlocks(readMap);
                    } catch (JsonParseException e) {
                        SketchwareUtil.toastError("Invalid JSON file");
                    }
                }
            }
        };

        FilePickerDialogFragment dialog = new FilePickerDialogFragment(options, callback);

        dialog.show(getSupportFragmentManager(), "filePickerDialog");
    }

    @Override
    public void onStop() {
        super.onStop();
        listViewSavedState = block_list.onSaveInstanceState();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (listViewSavedState != null) {
            block_list.onRestoreInstanceState(listViewSavedState);
            _refreshLists();
        }
    }

    /** Back press handling, registered with the back dispatcher in {@link #onCreate}. */
    private void handleBackPressed() {
        if (mode.equals("editor")) {
            mode = "normal";
            Parcelable savedState = block_list.onSaveInstanceState();
            block_list.setAdapter(new Adapter(filtered_list));
            ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
            block_list.onRestoreInstanceState(savedState);
            fabButtonVisibility(true);
            onCreateOptionsMenu(toolbar.getMenu());
        } else {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        if (Integer.parseInt(getIntent().getStringExtra("position")) == -1) {
            menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Restore blocks");
            menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Empty recycle bin");
        }
        if (Integer.parseInt(getIntent().getStringExtra("position")) != -1) {
            if (mode.equals("normal")) {
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Swap").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_swap_vertical)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Import");
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Export");
            } else {
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Swap").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        String title = menuItem.getTitle().toString();
        switch (title) {
            case "Swap":
                if (mode.equals("normal")) {
                    mode = "editor";
                    fabButtonVisibility(false);
                } else {
                    mode = "normal";
                    fabButtonVisibility(true);
                }
                Parcelable savedInstanceState = block_list.onSaveInstanceState();
                block_list.setAdapter(new Adapter(filtered_list));
                ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
                block_list.onRestoreInstanceState(savedInstanceState);
                onCreateOptionsMenu(toolbar.getMenu());
                break;

            case "Import":
                openFileExplorerImport();
                break;

            case "Restore blocks":
                showRestoreSelected();
                break;

            case "Empty recycle bin":
                confirmEmptyRecycleBin();
                break;

            case "Export":
                Object paletteName = pallet_list.get(palette - 9).get("name");
                if (paletteName instanceof String) {
                    String exportTo = new File(BLOCK_EXPORT_PATH, paletteName + ".json").getAbsolutePath();
                    writeAtomically(exportTo, getGson().toJson(filtered_list));
                    SketchwareUtil.toast("Successfully exported blocks to:\n" + exportTo, Toast.LENGTH_LONG);
                } else {
                    SketchwareUtil.toastError("Invalid name of palette #" + (palette - 9));
                }
                break;

            default:
                return false;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void _receive_intents() {
        palette = Integer.parseInt(getIntent().getStringExtra("position"));
        pallet_path = getIntent().getStringExtra("dirP");
        blocks_path = getIntent().getStringExtra("dirB");
        _refreshLists();
        if (palette == -1) {
            getSupportActionBar().setTitle("Recycle Bin");
            fab_button.setVisibility(View.GONE);
        } else {
            Object paletteName = pallet_list.get(palette - 9).get("name");

            if (paletteName instanceof String) {
                getSupportActionBar().setTitle("Manage Block");
                getSupportActionBar().setSubtitle((String) paletteName);
            }
        }
    }

    private void _refreshLists() {
        filtered_list.clear();
        reference_list.clear();
        String paletteFileContent = FileUtil.readFile(pallet_path);
        String blocksFileContent = FileUtil.readFile(blocks_path);
        if (paletteFileContent.isEmpty()) {
            FileUtil.writeFile(pallet_path, "[]");
            paletteFileContent = "[]";
        }
        if (blocksFileContent.isEmpty()) {
            FileUtil.writeFile(blocks_path, "[]");
            blocksFileContent = "[]";
        }

        parseLists:
        {
            try {
                pallet_list = getGson().fromJson(paletteFileContent, Helper.TYPE_MAP_LIST);

                if (pallet_list != null) {
                    break parseLists;
                }
                // fall-through to shared error handling
            } catch (JsonParseException e) {
                // fall-through to shared error handling
            }

            SketchwareUtil.showFailedToParseJsonDialog(this, new File(pallet_path), "Custom Block Palettes", v -> _refreshLists());
            pallet_list = new ArrayList<>();
        }

        parseBlocks:
        {
            try {
                all_blocks_list = getGson().fromJson(blocksFileContent, Helper.TYPE_MAP_LIST);

                if (all_blocks_list != null) {
                    break parseBlocks;
                }
                // fall-through to shared error handling
            } catch (JsonParseException e) {
                // fall-through to shared error handling
            }

            SketchwareUtil.showFailedToParseJsonDialog(this, new File(blocks_path), "Custom Blocks", v -> _refreshLists());
            all_blocks_list = new ArrayList<>();
        }

        for (int i = 0; i < all_blocks_list.size(); i++) {
            HashMap<String, Object> block = all_blocks_list.get(i);

            Object blockPalette = block.get("palette");
            if (blockPalette instanceof String) {
                try {
                    if (Integer.parseInt((String) blockPalette) == palette) {
                        reference_list.add(i);
                        filtered_list.add(block);
                    }
                } catch (NumberFormatException e) {
                    SketchwareUtil.toastError("Invalid palette entry in block #" + (i + 1));
                }
            }
        }
        Parcelable onSaveInstanceState = block_list.onSaveInstanceState();
        block_list.setAdapter(new Adapter(filtered_list));
        ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
        block_list.onRestoreInstanceState(onSaveInstanceState);
    }

    private void _swapitems(int sourcePosition, int targetPosition) {
        Collections.swap(all_blocks_list, sourcePosition, targetPosition);
        saveBlocks();
        _refreshLists();
    }

    private void _showItemPopup(View view, int position) {
        if (palette == -1) {
            PopupMenu popupMenu = new PopupMenu(this, view);
            Menu menu = popupMenu.getMenu();
            menu.add("Delete permanently");
            menu.add("Restore");
            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getTitle().toString()) {
                    case "Delete permanently":
                        _deleteBlock(position);
                        break;

                    case "Restore":
                        _changePallette(position);
                        break;

                    default:
                        return false;
                }
                return true;
            });
            popupMenu.show();
            return;
        }
        PopupMenu popupMenu = new PopupMenu(this, view);
        Menu menu = popupMenu.getMenu();
        menu.add("Insert above");
        menu.add("Delete");
        menu.add("Duplicate");
        menu.add("Move to palette");
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "Duplicate":
                    _duplicateBlock(position);
                    break;

                case "Insert above":
                    Object paletteColor = pallet_list.get(palette - 9).get("color");
                    if (paletteColor instanceof String) {
                        Intent intent = new Intent(getApplicationContext(), BlocksManagerCreatorActivity.class);
                        intent.putExtra("mode", "insert");
                        intent.putExtra("path", blocks_path);
                        intent.putExtra("color", (String) paletteColor);
                        intent.putExtra("pos", String.valueOf(position));
                        startActivity(intent);
                    } else {
                        SketchwareUtil.toastError("Invalid color of palette #" + (palette - 9));
                    }
                    break;

                case "Move to palette":
                    _changePallette(position);
                    break;

                case "Delete":
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Delete block?")
                            .setMessage("Are you sure you want to delete this block?")
                            .setPositiveButton("Recycle bin", (dialog, which) -> _moveToRecycleBin(position))
                            .setNegativeButton(R.string.common_word_cancel, null)
                            .setNeutralButton("Delete permanently", (dialog, which) -> _deleteBlock(position))
                            .show();
                    break;

                default:
                    return false;
            }
            return true;
        });
        popupMenu.show();
    }

    private void _duplicateBlock(int position) {
        HashMap<String, Object> block = new HashMap<>(all_blocks_list.get(position));
        Object blockName = block.get("name");

        if (blockName instanceof String) {
            if (((String) blockName).matches("(?s).*_copy[0-9][0-9]")) {
                block.put("name", ((String) blockName).replaceAll("_copy[0-9][0-9]", "_copy" + SketchwareUtil.getRandom(11, 99)));
            } else {
                block.put("name", blockName + "_copy" + SketchwareUtil.getRandom(11, 99));
            }
        }
        all_blocks_list.add(position + 1, block);
        saveBlocks();
        _refreshLists();
    }

    private void _deleteBlock(int position) {
        all_blocks_list.remove(position);
        saveBlocks();
        _refreshLists();
    }

    private void _moveToRecycleBin(int position) {
        all_blocks_list.get(position).put("palette", "-1");
        saveBlocks();
        _refreshLists();
    }

    private void _changePallette(int position) {
        ArrayList<String> paletteNames = new ArrayList<>();
        for (int j = 0, pallet_listSize = pallet_list.size(); j < pallet_listSize; j++) {
            HashMap<String, Object> palette = pallet_list.get(j);
            Object name = palette.get("name");

            if (name instanceof String) {
                paletteNames.add((String) name);
            } else {
                SketchwareUtil.toastError("Invalid name of Custom Block palette #" + (j + 1));
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setNegativeButton(R.string.common_word_cancel, null);
        if (palette == -1) {
            AtomicInteger restoreToChoice = new AtomicInteger(-1);
            builder.setTitle("Restore to")
                    .setSingleChoiceItems(paletteNames.toArray(new String[0]), -1, (dialog, which) -> restoreToChoice.set(which))
                    .setPositiveButton("Restore", (dialog, which) -> {
                        if (restoreToChoice.get() != -1) {
                            restoreBlocks(java.util.List.of(position), restoreToChoice.get());
                        }
                    });
        } else {
            AtomicInteger moveToChoice = new AtomicInteger(palette - 9);
            builder.setTitle("Move to")
                    .setSingleChoiceItems(paletteNames.toArray(new String[0]), palette - 9, (dialog, which) -> moveToChoice.set(which))
                    .setPositiveButton("Move", (dialog, which) -> {
                        // Moved to the end of the chosen palette; every other block keeps its place (the
                        // previous swap with the last block reordered another palette).
                        BlockLibrary library = library();
                        library.moveBlocks(java.util.List.of(position), moveToChoice.get());
                        library.touchId(palette);
                        commit(library);
                    });
        }
        builder.show();
    }

    private void _importBlocks(ArrayList<HashMap<String, Object>> rawBlocks) {
        // Entries without a name, a spec or a known type, or repeated in the file, are left out.
        java.util.List<BlockLibrary.ImportProblem> problems = BlockLibrary.validate(rawBlocks);
        java.util.Set<Integer> invalid = new java.util.HashSet<>();
        for (BlockLibrary.ImportProblem problem : problems) invalid.add(problem.entry());
        ArrayList<HashMap<String, Object>> blocks = new ArrayList<>();
        for (int i = 0; i < rawBlocks.size(); i++) {
            if (!invalid.contains(i)) blocks.add(rawBlocks.get(i));
        }
        if (!problems.isEmpty()) {
            SketchwareUtil.toastError(problems.size() + " entries can't be imported, first: " + problems.get(0).message());
        }
        if (blocks.isEmpty()) return;
        try {
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Integer> toAdd = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                Object blockName = blocks.get(i).get("name");

                if (blockName instanceof String) {
                    names.add((String) blockName);
                } else {
                    SketchwareUtil.toastError("Invalid name entry of Custom Block #" + (i + 1) + " in Blocks to import");
                }
            }
            MaterialAlertDialogBuilder import_dialog = new MaterialAlertDialogBuilder(this);
            import_dialog.setTitle("Import blocks")
                    .setMultiChoiceItems(names.toArray(new CharSequence[0]), null, (dialog, which, isChecked) -> {
                        if (isChecked) {
                            toAdd.add(which);
                        } else {
                            toAdd.remove((Integer) which);
                        }
                    })
                    .setPositiveButton("Import", (dialog, which) -> {
                        for (int i = 0; i < blocks.size(); i++) {
                            if (toAdd.contains(i)) {
                                addImported(blocks.get(i));
                            }
                        }
                        saveBlocks();
                        _refreshLists();
                        SketchwareUtil.toast("Imported successfully");
                    })
                    .setNegativeButton("Reverse", (dialog, which) -> {
                        for (int i = 0; i < blocks.size(); i++) {
                            if (!toAdd.contains(i)) {
                                addImported(blocks.get(i));
                            }
                        }
                        saveBlocks();
                        _refreshLists();
                        SketchwareUtil.toast("Imported successfully");
                    })
                    .setNeutralButton("All", (dialog, which) -> {
                        for (int i = 0; i < blocks.size(); i++) {
                            addImported(blocks.get(i));
                        }
                        saveBlocks();
                        _refreshLists();
                        SketchwareUtil.toast("Imported successfully");
                    })
                    .show();
        } catch (Exception e) {
            SketchwareUtil.toastError("An error occurred! [" + e.getMessage() + "]");
        }
    }

    /** Adds an imported block to this palette; a name already used gets a new one, so no opcode is duplicated. */
    private void addImported(HashMap<String, Object> block) {
        BlockLibrary library = library();
        HashMap<String, Object> copy = new HashMap<>(block);
        copy.put("name", library.uniqueBlockName(String.valueOf(copy.get("name"))));
        copy.put("palette", String.valueOf(palette));
        if (!copy.containsKey("type")) copy.put("type", " ");
        all_blocks_list.add(copy);
    }

    private BlockLibrary library() {
        return new BlockLibrary(pallet_list, all_blocks_list);
    }

    /** Takes the result of a library operation as the new state, saves it and shows it. */
    private void commit(BlockLibrary library) {
        pallet_list = new ArrayList<>();
        for (java.util.Map<String, Object> p : library.palettes()) pallet_list.add(new HashMap<>(p));
        all_blocks_list = new ArrayList<>();
        for (java.util.Map<String, Object> b : library.blocks()) all_blocks_list.add(new HashMap<>(b));
        writeAtomically(pallet_path, getGson().toJson(pallet_list));
        writeAtomically(blocks_path, getGson().toJson(all_blocks_list));
        _refreshLists();
    }

    /** Saves the block list (in one step, keeping a .bak) and marks this palette as just changed. */
    private void saveBlocks() {
        writeAtomically(blocks_path, getGson().toJson(all_blocks_list));
        if (palette >= BlockLibrary.FIRST_PALETTE_ID && palette - BlockLibrary.FIRST_PALETTE_ID < pallet_list.size()) {
            pallet_list.get(palette - BlockLibrary.FIRST_PALETTE_ID).put(BlockLibrary.KEY_MODIFIED, System.currentTimeMillis());
            writeAtomically(pallet_path, getGson().toJson(pallet_list));
        }
    }

    private static void writeAtomically(String path, String content) {
        try {
            BlockStore.write(new File(path), content);
        } catch (java.io.IOException e) {
            SketchwareUtil.toastError("Couldn't save " + new File(path).getName() + ": " + e.getMessage());
        }
    }

    /** Restores recycle bin blocks to a palette, at its end, keeping their order. */
    private void restoreBlocks(java.util.List<Integer> blockIndices, int paletteIndex) {
        BlockLibrary library = library();
        library.moveBlocks(blockIndices, paletteIndex);
        commit(library);
    }

    /** Picks recycle bin blocks, then the palette to restore them to. */
    private void showRestoreSelected() {
        if (filtered_list.isEmpty() || pallet_list.isEmpty()) {
            SketchwareUtil.toastError(pallet_list.isEmpty() ? "Create a palette first" : "The recycle bin is empty");
            return;
        }
        String[] names = new String[filtered_list.size()];
        boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) names[i] = String.valueOf(filtered_list.get(i).get("name"));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore blocks")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Next", (dialog, which) -> {
                    java.util.List<Integer> chosen = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.add(reference_list.get(i));
                    if (chosen.isEmpty()) return;
                    String[] paletteNames = new String[pallet_list.size()];
                    for (int j = 0; j < paletteNames.length; j++) paletteNames[j] = String.valueOf(pallet_list.get(j).get("name"));
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Restore to")
                            .setItems(paletteNames, (d, target) -> restoreBlocks(chosen, target))
                            .setNegativeButton(R.string.common_word_cancel, null)
                            .show();
                })
                .setNeutralButton("All", (dialog, which) -> {
                    for (int i = 0; i < checked.length; i++) checked[i] = true;
                    java.util.List<Integer> chosen = new ArrayList<>(reference_list);
                    String[] paletteNames = new String[pallet_list.size()];
                    for (int j = 0; j < paletteNames.length; j++) paletteNames[j] = String.valueOf(pallet_list.get(j).get("name"));
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Restore to")
                            .setItems(paletteNames, (d, target) -> restoreBlocks(chosen, target))
                            .setNegativeButton(R.string.common_word_cancel, null)
                            .show();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void confirmEmptyRecycleBin() {
        if (filtered_list.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Empty the recycle bin?")
                .setMessage("The blocks in it will be deleted permanently.")
                .setPositiveButton("Empty", (dialog, which) -> {
                    BlockLibrary library = library();
                    library.emptyRecycleBin();
                    commit(library);
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void fabButtonVisibility(boolean visible) {
        if (visible) {
            ObjectAnimator.ofFloat(fab_button, "translationX", fab_button.getTranslationX(), -50.0f, 0.0f).setDuration(400L).start();
        } else {
            ObjectAnimator.ofFloat(fab_button, "translationX", fab_button.getTranslationX(), -50.0f, 250.0f).setDuration(400L).start();
        }
    }

    private class Adapter extends BaseAdapter {

        private final ArrayList<HashMap<String, Object>> blocks;

        public Adapter(ArrayList<HashMap<String, Object>> data) {
            blocks = data;
        }

        @Override
        public int getCount() {
            return blocks.size();
        }

        @Override
        public HashMap<String, Object> getItem(int position) {
            return blocks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.block_customview, parent, false);
            }

            HashMap<String, Object> block = blocks.get(position);

            LinearLayout background = convertView.findViewById(R.id.background);
            TextView name = convertView.findViewById(R.id.name);
            TextView spec = convertView.findViewById(R.id.spec);
            CardView upLayout = convertView.findViewById(R.id.up_layout);
            CardView downLayout = convertView.findViewById(R.id.down_layout);
            LinearLayout down = convertView.findViewById(R.id.down);
            LinearLayout up = convertView.findViewById(R.id.up);

            if (mode.equals("normal")) {
                downLayout.setVisibility(View.GONE);
                upLayout.setVisibility(View.GONE);
            } else {
                downLayout.setVisibility(position != blocks.size() - 1 ? View.VISIBLE : View.GONE);
                upLayout.setVisibility(position != 0 ? View.VISIBLE : View.GONE);
            }

            Object blockName = block.get("name");
            if (blockName instanceof String) {
                name.setText((String) blockName);
                spec.setHint("");
            } else {
                name.setText("");
                name.setHint("(Invalid block name entry)");
            }

            Object blockSpec = block.get("spec");
            if (blockSpec instanceof String) {
                spec.setText((String) blockSpec);
                spec.setHint("");
            } else {
                spec.setText("");
                spec.setHint("(Invalid block spec entry)");
            }

            Object blockType = block.get("type");
            if (blockType instanceof String) {
                switch ((String) blockType) {
                    case " ":
                    case "regular":
                        spec.setBackgroundResource(R.drawable.block_ori);
                        break;

                    case "b":
                        spec.setBackgroundResource(R.drawable.block_boolean);
                        break;

                    case "c":
                    case "e":
                        spec.setBackgroundResource(R.drawable.if_else);
                        break;

                    case "d":
                        spec.setBackgroundResource(R.drawable.block_num);
                        break;

                    case "f":
                        spec.setBackgroundResource(R.drawable.block_stop);
                        break;

                    default:
                        spec.setBackgroundResource(R.drawable.block_string);
                        break;
                }
            } else {
                spec.setBackgroundResource(R.drawable.block_string);
            }

            if (palette == -1) {
                spec.getBackground().setColorFilter(new PorterDuffColorFilter(0xff9e9e9e, PorterDuff.Mode.MULTIPLY));
            } else {
                if (block.containsKey("color")) {
                    Object blockColor = block.get("color");

                    if (blockColor instanceof String) {
                        int color = -1;
                        try {
                            color = Color.parseColor((String) blockColor);
                        } catch (IllegalArgumentException e) {
                            SketchwareUtil.toastError("Invalid color entry in block #" + (position + 1));
                        }

                        if (color != -1) {
                            spec.getBackground().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                        }
                    } else {
                        SketchwareUtil.toastError("Invalid color entry in block #" + (position + 1));
                    }
                } else {
                    HashMap<String, Object> paletteObject = pallet_list.get(palette - 9);
                    Object paletteColor = paletteObject.get("color");

                    if (paletteColor instanceof String) {
                        try {
                            spec.getBackground().setColorFilter(new PorterDuffColorFilter(
                                    Color.parseColor((String) paletteColor),
                                    PorterDuff.Mode.MULTIPLY
                            ));
                        } catch (IllegalArgumentException e) {
                            SketchwareUtil.toastError("Invalid color in Custom Block palette #" + (palette - 8));
                        }
                    }
                }
            }
            up.setOnClickListener(v -> {
                if (position > 0) {
                    _swapitems(reference_list.get(position), reference_list.get(position - 1));
                }
            });
            down.setOnClickListener(v -> {
                if (position < filtered_list.size() - 1) {
                    _swapitems(reference_list.get(position), reference_list.get(position + 1));
                }
            });
            if (mode.equals("normal")) {
                background.setOnClickListener(v -> {
                    if (palette == -1) {
                        _showItemPopup(background, reference_list.get(position));
                    } else {
                        Object paletteColor = pallet_list.get(palette - 9).get("color");

                        if (paletteColor instanceof String) {
                            Intent intent = new Intent(getApplicationContext(), BlocksManagerCreatorActivity.class);
                            intent.putExtra("mode", "edit");
                            intent.putExtra("color", (String) paletteColor);
                            intent.putExtra("path", blocks_path);
                            intent.putExtra("pos", String.valueOf(reference_list.get(position)));
                            startActivity(intent);
                        }
                    }
                });
                background.setOnLongClickListener(v -> {
                    _showItemPopup(background, reference_list.get(position));
                    return true;
                });
            }
            return convertView;
        }
    }
}
