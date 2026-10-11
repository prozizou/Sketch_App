package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Rs;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.lib.PCP;
import pro.sketchware.R;
import pro.sketchware.blocks.manager.BlockChecks;
import pro.sketchware.blocks.manager.BlockLibrary;
import pro.sketchware.blocks.manager.BlockStore;
import pro.sketchware.blocks.manager.TextHistory;
import pro.sketchware.databinding.ActivityBlocksManagerCreatorBinding;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.lib.highlighter.SimpleHighlighter;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BlocksManagerCreatorActivity extends BaseAppCompatActivity {

    private static final Pattern PARAM_PATTERN = Pattern.compile("%m(?!\\.[\\w]+)");
    private final ArrayList<String> id_detector = new ArrayList<>();
    private ActivityBlocksManagerCreatorBinding binding;
    private ArrayList<HashMap<String, Object>> blocksList = new ArrayList<>();
    /**
     * Current mode of this activity, "edit" if editing a block, "add" if creating a new block and "insert" if inserting a block above another
     */
    private String mode = "";
    /**
     * Position of current editing/adding/inserting block in palette
     */
    private int blockPosition = 0;
    private String palletColour = "";
    private String path = "";
    /** block.json couldn't be read: saving would replace every block with this one, so it is refused. */
    private boolean blocksUnreadable;
    private TextHistory codeHistory;
    private TextHistory specHistory;
    @Nullable
    private EditText lastEdited;
    private boolean restoringHistory;
    private boolean saved;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityBlocksManagerCreatorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initialize();
        initializeLogic();
    }

    @Override
    public void onDestroy() {
        // Leaving on purpose (Cancel, Back) discards the draft; a crash never gets here, so it stays for next time.
        if (isFinishing() && !saved) clearDraft();
        super.onDestroy();
        binding = null;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(view -> onBackPressed());

        binding.name.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String string = s.toString();
                if (!id_detector.contains(string)) {
                    binding.nameLayout.setError(null);
                    binding.nameLayout.setErrorEnabled(false);
                    binding.save.setEnabled(true);
                } else if (!mode.equals("edit")) {
                    binding.nameLayout.setError("Block name already in use");
                    binding.nameLayout.setErrorEnabled(true);
                    binding.save.setEnabled(false);
                } else {
                    HashMap<String, Object> savedBlocksListBlock = blocksList.get(blockPosition);
                    Object blockNameObject = savedBlocksListBlock.get("name");

                    if (!string.equals(blockNameObject)) {
                        binding.nameLayout.setError("Block name already in use");
                        binding.nameLayout.setErrorEnabled(true);
                        binding.save.setEnabled(false);
                    }
                }
            }
        });

        binding.type.setOnClickListener(view -> binding.selectType.performClick());
        binding.selectType.setOnClickListener(v -> {
            List<String> types = Arrays.asList(
                    "regular", "c", "e", "s", "b",
                    "d", "v", "a", "f", "l", "p", "h"
            );
            List<String> choices = Arrays.asList(
                    "Regular block (regular)",
                    "if block (c)",
                    "if-else block (e)",
                    "String (s)",
                    "Boolean (b)",
                    "Number (d)",
                    "Variable (v)",
                    "Map (a)",
                    "stop block (f)",
                    "List (l)",
                    "Component (p)",
                    "Header (h)"
            );
            AtomicInteger choice = new AtomicInteger();
            new MaterialAlertDialogBuilder(this).setTitle("Block type")
                    .setSingleChoiceItems(choices.toArray(new String[0]),
                            types.indexOf(Helper.getText(binding.type)), (dialog, which) -> choice.set(which))
                    .setPositiveButton(R.string.common_word_save, (dialog, which) -> binding.type.setText(types.get(choice.get())))
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .create().show();
        });

        binding.type.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().equals("e")) {
                    AutoTransition transition = new AutoTransition();
                    transition.setDuration(300L);
                    TransitionManager.beginDelayedTransition(binding.scrollView, transition);
                    binding.spec2Layout.setVisibility(View.VISIBLE);
                } else {
                    AutoTransition transition = new AutoTransition();
                    transition.setDuration(300L);
                    TransitionManager.beginDelayedTransition(binding.scrollView, transition);
                    binding.spec2Layout.setVisibility(View.GONE);
                }
                updateBlockSpec(s.toString(), Helper.getText(binding.colour));
            }
        });

        binding.spec.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateBlockSpec(Helper.getText(binding.type), Helper.getText(binding.colour));
            }
        });

        binding.openColorPalette.setOnClickListener(v -> {
            ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this, 0, false, false);
            colorPickerDialog.a(new PCP(binding.colour));
            colorPickerDialog.showAtLocation(v, Gravity.CENTER, 0, 0);
        });

        binding.colour.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!PropertiesUtil.isHexColor(s.toString())) {
                    binding.colourLayout.setError("Invalid hex color");
                    binding.colourLayout.setErrorEnabled(true);
                } else {
                    binding.colourLayout.setError(null);
                    binding.colourLayout.setErrorEnabled(false);
                }
                updateBlockSpec(Helper.getText(binding.type), s.toString());
            }
        });

        binding.cancel.setOnClickListener(Helper.getBackPressedClickListener(this));
        binding.save.setOnClickListener(v -> {
            if (!PropertiesUtil.isHexColor(Helper.getText(binding.colour))) {
                SketchwareUtil.showMessage(getApplicationContext(), "Invalid hex color");
                return;
            }
            Matcher matcher = PARAM_PATTERN.matcher(Helper.getText(binding.spec));
            if (matcher.find()) {
                SketchwareUtil.showMessage(getApplicationContext(), "Invalid block params");
                return;
            }
            if (Helper.getText(binding.type).isEmpty()) {
                binding.type.setText(" ");
            }
            if (blocksUnreadable) {
                SketchwareUtil.toastError("block.json can't be read: fix or restore it before saving");
                return;
            }
            checkThenSave();
        });

        binding.reset.setOnClickListener(v -> binding.colour.setText(palletColour));
        binding.spec.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;

                case MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            v.onTouchEvent(event);
            return true;
        });
    }

    private void initializeLogic() {
        inputProperties();
        addParameters();
        receiveIntents();
        setUpHistory();
        offerDraft();
        new SimpleHighlighter(binding.code);

        {
            View view = binding.content;
            int left = view.getPaddingLeft();
            int top = view.getPaddingTop();
            int right = view.getPaddingRight();
            int bottom = view.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }

        {
            View view = binding.appBarLayout;
            int left = view.getPaddingLeft();
            int top = view.getPaddingTop();
            int right = view.getPaddingRight();
            int bottom = view.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }
    }

    private View addBlockMenu(String menu, String name) {
        TextView textView = new TextView(this);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        textView.setPadding(
                (int) SketchwareUtil.getDip(8),
                0,
                (int) SketchwareUtil.getDip(8),
                0
        );
        textView.setTextColor(MaterialColors.getColor(textView, R.attr.colorPrimary));
        textView.setText(name);
        textView.setTextSize(14.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder(Helper.getText(binding.spec));
            int selectionStart = binding.spec.getSelectionStart();
            sb.insert(selectionStart, menu);
            binding.spec.setText(sb);
            binding.spec.setSelection(selectionStart + menu.length());
        });
        return textView;
    }

    private void inputProperties() {
        binding.name.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        binding.name.setMaxLines(1);
        binding.type.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        binding.type.setMaxLines(1);
        binding.typename.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        binding.typename.setMaxLines(1);
        binding.spec.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        binding.spec.setMaxLines(1);
        binding.colour.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        binding.colour.setMaxLines(1);
        binding.spec2.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        binding.spec2.setMaxLines(1);
        binding.spec2Layout.setVisibility(View.GONE);
    }

    private void addParameters() {
        binding.parametersHolder.addView(addBlockMenu("%s.inputOnly ", "inputOnly"));
        binding.parametersHolder.addView(addBlockMenu("%s ", "string"));
        binding.parametersHolder.addView(addBlockMenu("%b ", "boolean"));
        binding.parametersHolder.addView(addBlockMenu("%d ", "number"));
        binding.parametersHolder.addView(addBlockMenu("%m.varMap ", "map"));
        binding.parametersHolder.addView(addBlockMenu("%m.view ", "view"));
        binding.parametersHolder.addView(addBlockMenu("%m.textview ", "textView"));
        binding.parametersHolder.addView(addBlockMenu("%m.edittext ", "editText"));
        binding.parametersHolder.addView(addBlockMenu("%m.imageview ", "ImageView"));
        binding.parametersHolder.addView(addBlockMenu("%m.listview ", "listView"));
        binding.parametersHolder.addView(addBlockMenu("%m.list ", "list"));
        binding.parametersHolder.addView(addBlockMenu("%m.listMap ", "listMap"));
        binding.parametersHolder.addView(addBlockMenu("%m.listStr ", "listString"));
        binding.parametersHolder.addView(addBlockMenu("%m.listInt ", "listNumber"));
        binding.parametersHolder.addView(addBlockMenu("%m.intent ", "intent"));
        binding.parametersHolder.addView(addBlockMenu("%m.color ", "color"));
        binding.parametersHolder.addView(addBlockMenu("%m.activity ", "activity"));
        binding.parametersHolder.addView(addBlockMenu("%m.resource ", "resource"));
        binding.parametersHolder.addView(addBlockMenu("%m.customViews ", "custom views"));
        binding.parametersHolder.addView(addBlockMenu("%m.layout ", "layout"));
        binding.parametersHolder.addView(addBlockMenu("%m.anim ", "anim"));
        binding.parametersHolder.addView(addBlockMenu("%m.drawable ", "drawable"));
        binding.parametersHolder.addView(addBlockMenu("%m.ResString ", "ResStrings"));
    }

    private void receiveIntents() {
        mode = getIntent().getStringExtra("mode");
        path = getIntent().getStringExtra("path");
        palletColour = getIntent().getStringExtra("color");
        getBlockList();
        if (mode.equals("add")) {
            blockPosition = Integer.parseInt(getIntent().getStringExtra("pallet"));
            binding.colour.setText(palletColour);
            getSupportActionBar().setTitle("Add a new block");
            return;
        }
        blockPosition = Integer.parseInt(getIntent().getStringExtra("pos"));
        binding.colour.setText(palletColour);
        getSupportActionBar().setTitle("Insert block");
        if (mode.equals("edit")) {
            getSupportActionBar().setTitle("Edit block");
            fillUpInputs(blockPosition);
        }
    }

    private void fillUpInputs(int position) {
        HashMap<String, Object> block = blocksList.get(position);

        Object nameObject = block.get("name");
        if (nameObject instanceof String) {
            binding.name.setText((String) nameObject);
        } else {
            binding.nameLayout.setError("Invalid name block data");
            binding.nameLayout.setErrorEnabled(true);
        }

        Object typeObject = block.get("type");
        if (typeObject instanceof String typeString) {

            if (typeString.equals(" ")) {
                binding.type.setText("regular");
            } else {
                binding.type.setText(typeString);
            }
        } else {
            binding.typeLayout.setError("Invalid type block data");
            binding.typeLayout.setErrorEnabled(true);
        }

        Object typeName = block.get("typeName");
        if (typeName != null) {
            if (typeName instanceof String) {
                binding.typename.setText((String) typeName);
            } else {
                binding.typenameLayout.setError("Invalid typeName block data");
                binding.typenameLayout.setErrorEnabled(true);
            }
        }

        Object specObject = block.get("spec");
        if (specObject instanceof String) {
            binding.spec.setText((String) specObject);
        } else {
            binding.specLayout.setError("Invalid spec block data");
            binding.specLayout.setErrorEnabled(true);
        }

        Object spec2Object = block.get("spec2");
        if (spec2Object != null) {
            if (spec2Object instanceof String) {
                binding.spec2.setText((String) spec2Object);
            } else {
                binding.spec2Layout.setError("Invalid spec2 block data");
                binding.spec2Layout.setErrorEnabled(true);
            }
        }

        Object importsObject = block.get("imports");
        if (importsObject != null) {
            if (importsObject instanceof String) {
                binding.customImport.setText((String) importsObject);
            } else {
                binding.customImportLayout.setError("Invalid imports block data");
                binding.customImportLayout.setErrorEnabled(true);
            }
        }

        Object colorObject = block.get("color");
        if (colorObject != null) {
            if (colorObject instanceof String) {
                binding.colour.setText((String) colorObject);
            } else {
                binding.colourLayout.setError("Invalid color block data");
                binding.colourLayout.setErrorEnabled(true);
            }
        } else {
            binding.colour.setText(palletColour);
        }

        Object codeObject = block.get("code");
        if (codeObject instanceof String) {
            binding.code.setText((String) codeObject);
        } else {
            binding.code.setHint("(Invalid code block data)");
        }
    }

    private void getBlockList() {
        try {
            blocksList = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);

            if (blocksList != null) {
                for (int i = 0, blocksListSize = blocksList.size(); i < blocksListSize; i++) {
                    HashMap<String, Object> block = blocksList.get(i);
                    Object name = block.get("name");

                    if (name instanceof String) {
                        id_detector.add((String) name);
                    } else {
                        SketchwareUtil.toastError("Custom Block #" + i + " in current palette has an invalid name");
                    }
                }
                return;
            }
        } catch (JsonParseException e) {
        }
        SketchwareUtil.showFailedToParseJsonDialog(this, new File(path), "Custom Blocks", v -> getBlockList());
        blocksList = new ArrayList<>();
        blocksUnreadable = true;
    }

    private void updateBlockSpec(String specId, String color) {
        binding.blockArea.removeAllViews();
        var blockType = specId.equalsIgnoreCase("regular") ? " " : specId;
        try {
            var block = new Rs(this, -1, Helper.getText(binding.spec), blockType, Helper.getText(binding.name));
            block.e = PropertiesUtil.isHexColor(color) ? PropertiesUtil.parseColor(color) : Color.parseColor("#F0F0F0");
            binding.blockArea.addView(block);
        } catch (Exception e) {
            var block = new TextView(this);
            block.setTextColor(Color.RED);
            var input = Helper.getText(binding.spec);
            Matcher matcher = PARAM_PATTERN.matcher(input);
            if (matcher.find()) {
                int position = matcher.end();
                //Unable to resolve this error because the Rs class still undecompiled.
                block.setText("Error: '%m' must be followed by '.param' at position " + position);
            } else {
                block.setText(e.toString());
            }
            binding.blockArea.addView(block);
        }
    }

    private void addBlock() {
        HashMap<String, Object> tempMap = new HashMap<>();
        tempMap.put("name", Helper.getText(binding.name));
        if (Helper.getText(binding.type).equals("regular")) {
            tempMap.put("type", " ");
        } else if (Helper.getText(binding.type).isEmpty()) {
            tempMap.put("type", " ");
        } else {
            tempMap.put("type", Helper.getText(binding.type));
        }
        tempMap.put("typeName", Helper.getText(binding.typename));
        tempMap.put("spec", Helper.getText(binding.spec));
        tempMap.put("color", Helper.getText(binding.colour));
        if (Helper.getText(binding.type).equals("e")) {
            tempMap.put("spec2", Helper.getText(binding.spec2));
        }
        if (!TextUtils.isEmpty(Helper.getText(binding.customImport))) {
            tempMap.put("imports", Helper.getText(binding.customImport));
        }
        tempMap.put("code", Helper.getText(binding.code));
        tempMap.put("palette", String.valueOf(blockPosition));
        blocksList.add(tempMap);
        writeBlocks(tempMap.get("palette"));
    }

    private void insertBlockAt(int position) {
        HashMap<String, Object> tempMap = new HashMap<>();
        tempMap.put("name", Helper.getText(binding.name));
        if (Helper.getText(binding.type).equals("regular") || Helper.getText(binding.type).isEmpty()) {
            tempMap.put("type", " ");
        } else {
            tempMap.put("type", Helper.getText(binding.type));
        }
        tempMap.put("typeName", Helper.getText(binding.typename));
        tempMap.put("spec", Helper.getText(binding.spec));
        tempMap.put("color", Helper.getText(binding.colour));
        if (Helper.getText(binding.type).equals("e")) {
            tempMap.put("spec2", Helper.getText(binding.spec2));
        }
        if (!TextUtils.isEmpty(Helper.getText(binding.customImport))) {
            tempMap.put("imports", Helper.getText(binding.customImport));
        }
        tempMap.put("code", Helper.getText(binding.code));
        tempMap.put("palette", blocksList.get(position).get("palette"));
        blocksList.add(position, tempMap);
        writeBlocks(tempMap.get("palette"));
    }

    private void editBlock(int position) {
        HashMap<String, Object> tempMap = blocksList.get(position);
        tempMap.put("name", Helper.getText(binding.name));
        if (Helper.getText(binding.type).equals("regular") || Helper.getText(binding.type).isEmpty()) {
            tempMap.put("type", " ");
        } else {
            tempMap.put("type", Helper.getText(binding.type));
        }
        tempMap.put("typeName", Helper.getText(binding.typename));
        tempMap.put("spec", Helper.getText(binding.spec));
        tempMap.put("color", Helper.getText(binding.colour));
        if (Helper.getText(binding.type).equals("e")) {
            tempMap.put("spec2", Helper.getText(binding.spec2));
        }
        tempMap.put("imports", Helper.getText(binding.customImport));
        tempMap.put("code", Helper.getText(binding.code));
        writeBlocks(tempMap.get("palette"));
    }

    // ---- checks, preview, undo / redo, drafts

    private List<String> otherBlockNames() {
        List<String> names = new ArrayList<>(id_detector);
        if (mode.equals("edit") && blockPosition < blocksList.size()) {
            names.remove(String.valueOf(blocksList.get(blockPosition).get("name")));
        }
        return names;
    }

    private List<BlockChecks.Issue> currentIssues() {
        return BlockChecks.check(Helper.getText(binding.name), Helper.getText(binding.type), Helper.getText(binding.spec),
                Helper.getText(binding.code), Helper.getText(binding.customImport), otherBlockNames());
    }

    /** Errors keep the block from being saved; warnings are shown and can be accepted. */
    private void checkThenSave() {
        List<BlockChecks.Issue> issues = currentIssues();
        boolean errors = issues.stream().anyMatch(i -> i.severity() == BlockChecks.Severity.ERROR);
        if (issues.isEmpty()) {
            save();
            return;
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(errors ? "Fix the block first" : "Check the block")
                .setMessage(describe(issues));
        if (errors) {
            dialog.setPositiveButton(R.string.common_word_ok, null);
        } else {
            dialog.setPositiveButton(R.string.common_word_save, (d, w) -> save())
                    .setNegativeButton(R.string.common_word_cancel, null);
        }
        dialog.show();
    }

    private static String describe(List<BlockChecks.Issue> issues) {
        StringBuilder text = new StringBuilder();
        for (BlockChecks.Issue issue : issues) {
            String mark = switch (issue.severity()) {
                case ERROR -> "✖ ";
                case WARNING -> "⚠ ";
                case INFO -> "ℹ ";
            };
            text.append(mark).append(issue.message()).append("\n\n");
        }
        return text.toString().trim();
    }

    private void save() {
        if (mode.equals("add")) {
            addBlock();
        } else if (mode.equals("insert")) {
            insertBlockAt(blockPosition);
        } else if (mode.equals("edit")) {
            editBlock(blockPosition);
        }
    }

    /** Saves block.json in one step (keeping a .bak) and dates the palette the block belongs to. */
    private void writeBlocks(@Nullable Object paletteId) {
        try {
            BlockStore.write(new File(path), getGson().toJson(blocksList));
        } catch (java.io.IOException e) {
            SketchwareUtil.toastError("Couldn't save the block: " + e.getMessage());
            return;
        }
        touchPalette(paletteId);
        saved = true;
        clearDraft();
        SketchwareUtil.toast("Saved");
        finish();
    }

    private void touchPalette(@Nullable Object paletteId) {
        if (paletteId == null) return;
        try {
            File paletteFile = new File(FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(
                    ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                    (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH)));
            BlockStore store = new BlockStore(paletteFile, new File(path), getGson());
            List<HashMap<String, Object>> palettes = store.readList(paletteFile);
            int index = (int) Double.parseDouble(paletteId.toString()) - BlockLibrary.FIRST_PALETTE_ID;
            if (index >= 0 && index < palettes.size()) {
                palettes.get(index).put(BlockLibrary.KEY_MODIFIED, System.currentTimeMillis());
                BlockStore.write(paletteFile, getGson().toJson(palettes));
            }
        } catch (java.io.IOException | NumberFormatException ignored) {
            // Only the "last modified" date of the palette is missed
        }
    }

    private void setUpHistory() {
        codeHistory = new TextHistory(Helper.getText(binding.code), 100);
        specHistory = new TextHistory(Helper.getText(binding.spec), 100);
        binding.code.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (restoringHistory) return;
                codeHistory.record(s.toString(), binding.code.getSelectionStart());
                lastEdited = binding.code;
                invalidateOptionsMenu();
            }
        });
        binding.spec.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (restoringHistory) return;
                specHistory.record(s.toString(), binding.spec.getSelectionStart());
                lastEdited = binding.spec;
                invalidateOptionsMenu();
            }
        });
    }

    private void applyHistory(boolean undo) {
        EditText field = lastEdited != null ? lastEdited : binding.code;
        TextHistory history = field == binding.spec ? specHistory : codeHistory;
        TextHistory.State state = undo ? history.undo() : history.redo();
        if (state == null) return;
        restoringHistory = true;
        field.setText(state.text());
        field.setSelection(Math.min(state.cursor(), state.text().length()));
        restoringHistory = false;
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 0, "Undo").setIcon(R.drawable.ic_mtrl_undo).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 2, 1, "Redo").setIcon(R.drawable.ic_mtrl_redo).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 3, 2, "Java preview");
        menu.add(0, 4, 3, "Check the block");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        TextHistory history = lastEdited == binding.spec ? specHistory : codeHistory;
        if (history != null) {
            menu.findItem(1).setEnabled(history.canUndo());
            menu.findItem(2).setEnabled(history.canRedo());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        switch (item.getItemId()) {
            case 1 -> applyHistory(true);
            case 2 -> applyHistory(false);
            case 3 -> showJavaPreview();
            case 4 -> {
                List<BlockChecks.Issue> issues = currentIssues();
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Check the block")
                        .setMessage(issues.isEmpty() ? "No problem found." : describe(issues))
                        .setPositiveButton(R.string.common_word_ok, null)
                        .show();
            }
            default -> {
                return super.onOptionsItemSelected(item);
            }
        }
        return true;
    }

    /** The Java a use of this block adds to the app, with its imports. */
    private void showJavaPreview() {
        TextView view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        int pad = SketchwareUtil.dpToPx(20);
        view.setPadding(pad, pad / 2, pad, 0);
        view.setText(BlockChecks.javaPreview(Helper.getText(binding.spec), Helper.getText(binding.code), Helper.getText(binding.customImport)));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(view);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Java preview")
                .setView(scroll)
                .setPositiveButton(R.string.common_word_ok, null)
                .show();
    }

    private String draftKey() {
        String where = mode.equals("add") ? getIntent().getStringExtra("pallet") : getIntent().getStringExtra("pos");
        return path + "|" + mode + "|" + where;
    }

    private android.content.SharedPreferences drafts() {
        return getSharedPreferences("block_editor_drafts", MODE_PRIVATE);
    }

    private HashMap<String, Object> currentFields() {
        HashMap<String, Object> fields = new HashMap<>();
        fields.put("name", Helper.getText(binding.name));
        fields.put("type", Helper.getText(binding.type));
        fields.put("typeName", Helper.getText(binding.typename));
        fields.put("spec", Helper.getText(binding.spec));
        fields.put("spec2", Helper.getText(binding.spec2));
        fields.put("color", Helper.getText(binding.colour));
        fields.put("imports", Helper.getText(binding.customImport));
        fields.put("code", Helper.getText(binding.code));
        return fields;
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kept until the block is saved or the changes are discarded, so a crash or a killed app loses nothing.
        if (!saved && binding != null && !blocksUnreadable) {
            drafts().edit().putString(draftKey(), getGson().toJson(currentFields())).apply();
        }
    }

    private void clearDraft() {
        drafts().edit().remove(draftKey()).apply();
    }

    private void offerDraft() {
        String json = drafts().getString(draftKey(), null);
        if (json == null) return;
        HashMap<String, Object> draft;
        try {
            draft = getGson().fromJson(json, new com.google.gson.reflect.TypeToken<HashMap<String, Object>>() {
            }.getType());
        } catch (RuntimeException e) {
            clearDraft();
            return;
        }
        if (draft == null || draft.equals(currentFields())) {
            clearDraft();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Unsaved changes")
                .setMessage("This block has changes that weren't saved (the app was closed before). Restore them?")
                .setPositiveButton("Restore", (d, w) -> {
                    binding.name.setText(String.valueOf(draft.getOrDefault("name", "")));
                    binding.type.setText(String.valueOf(draft.getOrDefault("type", "")));
                    binding.typename.setText(String.valueOf(draft.getOrDefault("typeName", "")));
                    binding.spec.setText(String.valueOf(draft.getOrDefault("spec", "")));
                    binding.spec2.setText(String.valueOf(draft.getOrDefault("spec2", "")));
                    binding.colour.setText(String.valueOf(draft.getOrDefault("color", "")));
                    binding.customImport.setText(String.valueOf(draft.getOrDefault("imports", "")));
                    binding.code.setText(String.valueOf(draft.getOrDefault("code", "")));
                })
                .setNegativeButton("Discard", (d, w) -> clearDraft())
                .show();
    }
}
