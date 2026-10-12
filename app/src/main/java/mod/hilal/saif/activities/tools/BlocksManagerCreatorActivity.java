package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import a.a.a.Rs;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.EditorSearcher;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.lib.PCP;
import pro.sketchware.R;
import pro.sketchware.blocks.editor.BlockFields;
import pro.sketchware.blocks.editor.BlockParamTypes;
import pro.sketchware.blocks.editor.BlockShapes;
import pro.sketchware.blocks.editor.BlockSpec;
import pro.sketchware.blocks.editor.BlockTemplates;
import pro.sketchware.blocks.editor.BlockValidator;
import pro.sketchware.blocks.editor.CodeDiagnostics;
import pro.sketchware.blocks.editor.ImportList;
import pro.sketchware.blocks.editor.JavaPreview;
import pro.sketchware.blocks.editor.ParamAdapter;
import pro.sketchware.blocks.manager.BlockLibrary;
import pro.sketchware.blocks.manager.BlockStore;
import pro.sketchware.blocks.manager.TextHistory;
import pro.sketchware.databinding.ActivityBlocksManagerCreatorBinding;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Adds, inserts or edits a custom block: General, Parameters, Spec and Code tabs, a live preview, and checks before
 * the block is saved. The block is stored exactly as before (name, type, typeName, spec, spec2, color, imports,
 * code and palette); parameters live in the spec, where the Logic editor reads them from.
 */
public class BlocksManagerCreatorActivity extends BaseAppCompatActivity implements ParamAdapter.Listener {
    private static final String STATE_FIELDS = "fields";
    private static final String STATE_TAB = "tab";
    private static final int[] PRESET_COLORS = {0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3,
            0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFF795548};
    private static final int TAB_GENERAL = 0;
    private static final int TAB_PARAMETERS = 1;
    private static final int TAB_SPEC = 2;
    private static final int TAB_CODE = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> id_detector = new ArrayList<>();
    private ActivityBlocksManagerCreatorBinding binding;
    private ArrayList<HashMap<String, Object>> blocksList = new ArrayList<>();
    /** "edit" when editing a block, "add" when creating one and "insert" when inserting one above another. */
    private String mode = "";
    /** Position of the block being edited or inserted before, or the palette id when adding. */
    private int blockPosition = 0;
    private String palletColour = "";
    private String path = "";
    /** block.json couldn't be read: saving would replace every block with this one, so it is refused. */
    private boolean blocksUnreadable;
    private boolean saved;
    private boolean loading = true;
    private boolean syncing;

    /** The block type as shown ("regular" for the single space it is stored as). */
    private String type = "regular";
    private String imports = "";
    private BlockFields initial = BlockFields.EMPTY;
    private int currentTab = TAB_GENERAL;
    private TextHistory specHistory = new TextHistory("", 200);

    private float zoom = 1f;
    private boolean autoFit = true;

    private ParamAdapter paramAdapter;
    private ItemTouchHelper paramDrag;
    private int dragFrom = -1;
    private int dragTo = -1;
    private final List<MaterialCardView> shapeCards = new ArrayList<>();
    private final List<LinearLayout> shapeHolders = new ArrayList<>();
    private int shapesColor;
    private String shapesType = "";

    private final Runnable refreshPreviewLater = this::refreshPreview;
    private final Runnable refreshDetailsLater = this::refreshDetails;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityBlocksManagerCreatorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leave();
            }
        });

        setUpTabs();
        setUpGeneral();
        setUpParameters();
        setUpSpec();
        setUpCode();
        binding.cancel.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.save.setOnClickListener(v -> trySave());

        receiveIntents();
        initial = fields();
        loading = false;

        String restored = savedInstanceState == null ? null : savedInstanceState.getString(STATE_FIELDS);
        if (restored != null) {
            apply(getGson().fromJson(restored, BlockFields.class));
            showTab(savedInstanceState.getInt(STATE_TAB, TAB_GENERAL));
            TabLayout.Tab tab = binding.tabs.getTabAt(currentTab);
            if (tab != null) tab.select();
        } else {
            offerDraft();
        }
        refreshAll();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_FIELDS, getGson().toJson(fields()));
        outState.putInt(STATE_TAB, currentTab);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kept until the block is saved or the changes are discarded, so a crash or a killed app loses nothing.
        if (!saved && binding != null && !blocksUnreadable && !fields().equals(initial)) {
            drafts().edit().putString(draftKey(), getGson().toJson(fields())).apply();
        }
    }

    @Override
    public void onDestroy() {
        // Leaving on purpose (Cancel, Back) discards the draft; a crash never gets here, so it stays for next time.
        if (isFinishing() && !saved && binding != null) clearDraft();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
        binding = null;
    }

    private void applyInsets() {
        View view = binding.content;
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        // The bottom bar rides above the navigation bar and the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, i) -> {
            Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(left + insets.left, top, right + insets.right, bottom + insets.bottom);
            return i;
        });
        View appBar = binding.appBarLayout;
        int appLeft = appBar.getPaddingLeft();
        int appTop = appBar.getPaddingTop();
        int appRight = appBar.getPaddingRight();
        int appBottom = appBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(appBar, (v, i) -> {
            Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(appLeft + insets.left, appTop + insets.top, appRight + insets.right, appBottom);
            return i;
        });
    }

    // ---- toolbar

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.be_help).setIcon(R.drawable.ic_mtrl_help).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, 2, 1, R.string.be_undo).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 3, 2, R.string.be_redo).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 4, 3, R.string.be_java_preview).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 5, 4, R.string.be_check_block).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean code = currentTab == TAB_CODE;
        MenuItem undo = menu.findItem(2);
        MenuItem redo = menu.findItem(3);
        if (undo != null) undo.setEnabled(code ? binding.code.canUndo() : specHistory.canUndo());
        if (redo != null) redo.setEnabled(code ? binding.code.canRedo() : specHistory.canRedo());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 1 -> new MaterialAlertDialogBuilder(this).setTitle(R.string.be_help_title)
                    .setMessage(R.string.be_help_message).setPositiveButton(R.string.common_word_ok, null).show();
            case 2 -> undo();
            case 3 -> redo();
            case 4 -> showJavaPreview();
            case 5 -> showCheck();
            default -> {
                return super.onOptionsItemSelected(item);
            }
        }
        return true;
    }

    private void undo() {
        if (currentTab == TAB_CODE) {
            if (binding.code.canUndo()) binding.code.undo();
        } else {
            restoreSpec(specHistory.undo());
        }
        invalidateOptionsMenu();
    }

    private void redo() {
        if (currentTab == TAB_CODE) {
            if (binding.code.canRedo()) binding.code.redo();
        } else {
            restoreSpec(specHistory.redo());
        }
        invalidateOptionsMenu();
    }

    private void restoreSpec(@Nullable TextHistory.State state) {
        if (state == null) return;
        syncing = true;
        binding.spec.setText(state.text());
        binding.spec.setSelection(Math.min(state.cursor(), state.text().length()));
        syncing = false;
        onSpecChanged();
    }

    // ---- tabs

    private void setUpTabs() {
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void showTab(int tab) {
        currentTab = tab;
        hideKeyboard();
        binding.pageGeneral.setVisibility(tab == TAB_GENERAL ? View.VISIBLE : View.GONE);
        binding.pageParameters.setVisibility(tab == TAB_PARAMETERS ? View.VISIBLE : View.GONE);
        binding.pageSpec.setVisibility(tab == TAB_SPEC ? View.VISIBLE : View.GONE);
        binding.scrollView.setVisibility(tab == TAB_CODE ? View.GONE : View.VISIBLE);
        binding.pageCode.setVisibility(tab == TAB_CODE ? View.VISIBLE : View.GONE);
        if (tab != TAB_CODE) binding.scrollView.scrollTo(0, 0);
        if (tab == TAB_GENERAL || tab == TAB_SPEC) refreshPreview();
        invalidateOptionsMenu();
    }

    private void selectTab(int tab) {
        TabLayout.Tab target = binding.tabs.getTabAt(tab);
        if (target != null) target.select();
        showTab(tab);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    // ---- General

    private void setUpGeneral() {
        binding.name.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (loading) return;
                validateName();
                scheduleRefresh();
            }
        });
        binding.typename.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!loading) scheduleRefresh();
            }
        });
        binding.colour.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean valid = BlockValidator.isValidColor(s.toString());
                binding.colourLayout.setError(valid || s.length() == 0 ? null : getString(R.string.be_color_invalid));
                if (valid) binding.colorSwatch.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(s.toString().trim())));
                if (!loading) scheduleRefresh();
            }
        });
        binding.openColorPalette.setOnClickListener(v -> {
            ColorPickerDialog picker = new ColorPickerDialog(this, currentColor() & 0xFFFFFF, false, false);
            picker.a(new PCP(binding.colour));
            picker.showAtLocation(v, Gravity.CENTER, 0, 0);
        });
        binding.reset.setOnClickListener(v -> binding.colour.setText(palletColour));
        buildPresets();
        buildShapes();
        binding.zoomOut.setOnClickListener(v -> zoomBy(-0.25f));
        binding.zoomIn.setOnClickListener(v -> zoomBy(0.25f));
        binding.zoomLabel.setOnClickListener(v -> {
            autoFit = true;
            refreshPreview();
        });
        // The block is fitted to the width the card really has: again whenever that width changes (rotation, first
        // layout), never in a loop of its own.
        binding.previewScroll.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft && binding != null) applyZoom();
        });
    }

    private void buildPresets() {
        binding.colorPresets.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(40 * density);
        List<Integer> colors = new ArrayList<>();
        if (BlockValidator.isValidColor(palletColour)) colors.add(Color.parseColor(palletColour));
        for (int color : PRESET_COLORS) colors.add(color);
        for (int color : colors) {
            View swatch = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, Math.round(4 * density), Math.round(8 * density), Math.round(4 * density));
            swatch.setLayoutParams(params);
            swatch.setBackgroundResource(R.drawable.bg_block_palette_swatch);
            swatch.setBackgroundTintList(ColorStateList.valueOf(color));
            String hex = String.format("#%06X", color & 0xFFFFFF);
            swatch.setContentDescription(getString(R.string.be_color_pick) + " " + hex);
            swatch.setOnClickListener(v -> binding.colour.setText(hex));
            binding.colorPresets.addView(swatch);
        }
    }

    private void buildShapes() {
        float density = getResources().getDisplayMetrics().density;
        for (BlockShapes.Shape shape : BlockShapes.ALL) {
            MaterialCardView card = new MaterialCardView(this, null, com.google.android.material.R.attr.materialCardViewOutlinedStyle);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, Math.round(8 * density), 0);
            card.setLayoutParams(cardParams);
            card.setCheckable(true);
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription(shape.label());
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = Math.round(10 * density);
            column.setPadding(pad, pad, pad, pad);
            column.setMinimumWidth(Math.round(88 * density));
            LinearLayout holder = new LinearLayout(this);
            holder.setGravity(Gravity.CENTER);
            holder.setMinimumHeight(Math.round(48 * density));
            holder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            TextView label = new TextView(this);
            label.setText(shape.label());
            label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            label.setTextColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface));
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, Math.round(6 * density), 0, 0);
            column.addView(holder);
            column.addView(label);
            card.addView(column);
            card.setOnClickListener(v -> setType(shape.type()));
            binding.typeShapes.addView(card);
            shapeCards.add(card);
            shapeHolders.add(holder);
        }
    }

    /** Draws a small block of each shape the Logic editor supports, in the block's color. */
    private void refreshShapes() {
        int color = currentColor();
        if (color == shapesColor && type.equals(shapesType)) return;
        shapesColor = color;
        shapesType = type;
        for (int i = 0; i < BlockShapes.ALL.size(); i++) {
            BlockShapes.Shape shape = BlockShapes.ALL.get(i);
            LinearLayout holder = shapeHolders.get(i);
            holder.removeAllViews();
            View block = makeBlock(shape.sampleSpec(), shape.stored(), shape.type(), color);
            if (block != null) {
                block.setClickable(false);
                block.setFocusable(false);
                holder.addView(block);
            }
            shapeCards.get(i).setChecked(shape.type().equals(type));
            shapeCards.get(i).setContentDescription(shape.label() + (shape.type().equals(type) ? ", " + getString(R.string.common_word_select) : ""));
        }
    }

    private void setType(String newType) {
        String normalized = newType == null || newType.trim().isEmpty() || newType.equals(" ") ? "regular" : newType.trim();
        if (normalized.equals(type)) return;
        type = normalized;
        onTypeChanged();
    }

    private void onTypeChanged() {
        BlockShapes.Shape shape = BlockShapes.of(type);
        binding.spec2Layout.setVisibility(shape != null && shape.type().equals("e") ? View.VISIBLE : View.GONE);
        updateTypeNameVisibility();
        if (!loading) scheduleRefresh();
        refreshShapes();
    }

    private void updateTypeNameVisibility() {
        boolean show = BlockShapes.usesTypeName(type) || !Helper.getText(binding.typename).isEmpty();
        binding.typenameLayout.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void validateName() {
        String name = Helper.getText(binding.name).trim();
        String error = null;
        if (!name.isEmpty() && !BlockValidator.isValidName(name)) {
            error = getString(R.string.be_name_invalid);
        } else if (otherBlockNames().contains(name)) {
            error = getString(R.string.be_name_in_use);
        }
        binding.nameLayout.setError(error);
        binding.nameLayout.setErrorEnabled(error != null);
    }

    private List<String> otherBlockNames() {
        List<String> names = new ArrayList<>(id_detector);
        if (mode.equals("edit") && blockPosition < blocksList.size()) {
            names.remove(String.valueOf(blocksList.get(blockPosition).get("name")));
        }
        return names;
    }

    private int currentColor() {
        String color = Helper.getText(binding.colour);
        return BlockValidator.isValidColor(color) ? PropertiesUtil.parseColor(color) : Color.parseColor("#F0F0F0");
    }

    // ---- preview

    private void scheduleRefresh() {
        handler.removeCallbacks(refreshPreviewLater);
        handler.postDelayed(refreshPreviewLater, 120);
        handler.removeCallbacks(refreshDetailsLater);
        handler.postDelayed(refreshDetailsLater, 300);
    }

    private void refreshAll() {
        validateName();
        onTypeChanged();
        refreshPreview();
        refreshDetails();
    }

    /** What depends on the block's content, not on how fast the user types. */
    private void refreshDetails() {
        if (binding == null) return;
        refreshParams();
        refreshSpecUi();
        refreshImports();
        refreshDiagnostics();
        refreshTemplates();
        binding.codeUndo.setEnabled(binding.code.canUndo());
        binding.codeRedo.setEnabled(binding.code.canRedo());
        invalidateOptionsMenu();
    }

    private void refreshPreview() {
        if (binding == null) return;
        refreshShapes();
        fill(binding.blockArea);
        fill(binding.blockAreaSpec);
        applyZoom();
    }

    private void fill(LinearLayout area) {
        area.removeAllViews();
        String spec = Helper.getText(binding.spec);
        List<BlockSpec.Token> tokens = BlockSpec.parse(spec);
        List<BlockSpec.Invalid> errors = new ArrayList<>();
        for (BlockSpec.Invalid invalid : BlockSpec.problems(tokens)) {
            if (invalid.error()) errors.add(invalid);
        }
        if (tokens.isEmpty()) {
            area.addView(message(getString(R.string.be_spec_empty), false));
        } else if (!errors.isEmpty()) {
            area.addView(message(errors.get(0).problem(), true));
        } else {
            View block = makeBlock(spec, type.equals("regular") ? " " : type, Helper.getText(binding.name), currentColor());
            area.addView(block != null ? block : message(getString(R.string.be_check_block), true));
        }
    }

    private TextView message(String text, boolean error) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(MaterialColors.getColor(view, error ? androidx.appcompat.R.attr.colorError : com.google.android.material.R.attr.colorOnSurfaceVariant));
        return view;
    }

    @Nullable
    private View makeBlock(String spec, String storedType, String opcode, int color) {
        try {
            Rs block = new Rs(this, -1, spec, storedType, opcode);
            block.e = color;
            return block;
        } catch (Exception e) {
            return null;
        }
    }

    private void zoomBy(float delta) {
        autoFit = false;
        zoom = Math.max(0.5f, Math.min(2f, Math.round((zoom + delta) * 4) / 4f));
        applyZoom();
    }

    private void applyZoom() {
        layoutPreview(binding.blockArea, binding.previewScroll);
        layoutPreview(binding.blockAreaSpec, null);
        binding.zoomLabel.setText(Math.round(zoom * 100) + "%");
    }

    /** Scales a preview around its top-left corner and sizes its holder to match, so scrolling stays right. */
    private void layoutPreview(LinearLayout area, @Nullable View scroll) {
        ViewGroup.LayoutParams natural = area.getLayoutParams();
        natural.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        natural.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        area.setLayoutParams(natural);
        area.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = Math.max(1, area.getMeasuredWidth());
        int height = Math.max(1, area.getMeasuredHeight());
        View holder = (View) area.getParent();
        View container = scroll != null ? scroll : (View) holder.getParent();
        int available = container.getWidth() - holder.getPaddingLeft() - holder.getPaddingRight();
        if (autoFit && scroll != null) {
            // Shrinks a wide block to fit (never below half size); a short one stays at full size.
            zoom = available > 0 ? Math.max(0.5f, Math.min(1f, available / (float) width)) : 1f;
        }
        ViewGroup.LayoutParams exact = area.getLayoutParams();
        exact.width = width;
        exact.height = height;
        area.setLayoutParams(exact);
        area.setPivotX(0);
        area.setPivotY(0);
        area.setScaleX(zoom);
        area.setScaleY(zoom);
        ViewGroup.LayoutParams holderParams = holder.getLayoutParams();
        holderParams.width = Math.round(width * zoom) + holder.getPaddingLeft() + holder.getPaddingRight();
        holderParams.height = Math.max(Math.round(72 * getResources().getDisplayMetrics().density),
                Math.round(height * zoom) + holder.getPaddingTop() + holder.getPaddingBottom());
        holder.setLayoutParams(holderParams);
    }

    // ---- templates

    private void refreshTemplates() {
        boolean empty = Helper.getText(binding.spec).trim().isEmpty() && code().trim().isEmpty();
        binding.templateChips.removeAllViews();
        for (BlockTemplates.Template template : BlockTemplates.compatible(type, empty)) {
            Chip chip = new Chip(this);
            chip.setText(BlockTemplates.groupLabel(template.group()) + " · " + template.title());
            chip.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            chip.setOnClickListener(v -> applyTemplate(template));
            binding.templateChips.addView(chip);
        }
    }

    private void applyTemplate(BlockTemplates.Template template) {
        boolean empty = Helper.getText(binding.spec).trim().isEmpty() && code().trim().isEmpty();
        Runnable apply = () -> {
            BlockShapes.Shape shape = BlockShapes.of(template.type());
            if (shape != null) setType(shape.type());
            if (!template.spec().isEmpty() || empty) setSpecText(template.spec());
            if (!template.code().isEmpty() || empty) replaceCode(template.code());
            if (template.needs() != null) SketchwareUtil.toast(getString(R.string.be_template_needs, template.needs()));
            refreshDetails();
        };
        if (empty) {
            apply.run();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.be_template_replace_title)
                    .setMessage(R.string.be_template_replace_message)
                    .setPositiveButton(R.string.common_word_yes, (d, w) -> apply.run())
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .show();
        }
    }

    // ---- Parameters

    private void setUpParameters() {
        paramAdapter = new ParamAdapter(this);
        binding.paramsList.setLayoutManager(new LinearLayoutManager(this));
        binding.paramsList.setAdapter(paramAdapter);
        paramDrag = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                if (dragFrom == -1) dragFrom = from;
                dragTo = to;
                paramAdapter.move(from, to);
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
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                int from = dragFrom;
                int to = dragTo;
                dragFrom = -1;
                dragTo = -1;
                if (from != -1 && to != -1 && from != to) moveParam(from, to);
            }
        });
        paramDrag.attachToRecyclerView(binding.paramsList);
        binding.addParam.setOnClickListener(v -> pickParamType(-1, null, BlockSpec.paramCount(Helper.getText(binding.spec))));
    }

    private List<BlockSpec.Token> tokens() {
        return BlockSpec.parse(Helper.getText(binding.spec));
    }

    private void refreshParams() {
        List<BlockSpec.Param> params = BlockSpec.params(tokens());
        Set<Integer> used = CodeDiagnostics.referencedInputs(code());
        List<ParamAdapter.Item> items = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            BlockSpec.Param param = params.get(i);
            items.add(new ParamAdapter.Item(i + 1, BlockParamTypes.labelOf(param), param.text(), used.contains(i + 1)));
        }
        paramAdapter.submit(items);
        binding.paramsEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        // Inputs the code refers to that the block doesn't have, or never uses.
        BlockShapes.Shape shape = BlockShapes.of(type);
        int slots = shape != null && shape.hasSlots() ? 2 : 0;
        StringBuilder issues = new StringBuilder();
        if (!code().trim().isEmpty()) {
            List<Integer> refs = new ArrayList<>(used);
            Collections.sort(refs);
            for (int ref : refs) {
                if (ref > params.size() + slots) {
                    issues.append("✖ ").append("The code refers to input ").append(ref).append(", which the block doesn't have\n");
                }
            }
        }
        binding.paramsIssues.setText(issues.toString().trim());
        binding.paramsIssues.setVisibility(issues.length() == 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onChangeType(int index) {
        List<BlockSpec.Param> params = BlockSpec.params(tokens());
        if (index >= 0 && index < params.size()) pickParamType(index, params.get(index), params.size());
    }

    @Override
    public void onMenu(int index, View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.be_param_change_type);
        popup.getMenu().add(0, 2, 1, R.string.be_param_move_up).setEnabled(index > 0);
        popup.getMenu().add(0, 3, 2, R.string.be_param_move_down).setEnabled(index < paramAdapter.getItemCount() - 1);
        popup.getMenu().add(0, 4, 3, R.string.be_param_delete);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> onChangeType(index);
                case 2 -> moveParam(index, index - 1);
                case 3 -> moveParam(index, index + 1);
                case 4 -> deleteParam(index);
                default -> {
                    return false;
                }
            }
            return true;
        });
        popup.show();
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder holder) {
        paramDrag.startDrag(holder);
    }

    /** Adds a parameter ({@code index} -1) or changes the type of one. */
    private void pickParamType(int index, @Nullable BlockSpec.Param current, int count) {
        String[] labels = new String[BlockParamTypes.ALL.size()];
        for (int i = 0; i < labels.length; i++) {
            BlockParamTypes.Type t = BlockParamTypes.ALL.get(i);
            labels[i] = t.label() + "   " + t.spec();
        }
        int checked = current == null ? -1 : BlockParamTypes.indexOf(current);
        int[] choice = {checked};
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.be_param_type_title)
                .setNegativeButton(R.string.common_word_cancel, null);
        if (index < 0) {
            builder.setItems(labels, (d, which) -> addParam(BlockParamTypes.ALL.get(which).param()));
        } else {
            builder.setSingleChoiceItems(labels, checked, (d, which) -> choice[0] = which)
                    .setPositiveButton(R.string.common_word_save, (d, w) -> {
                        if (choice[0] >= 0) retypeParam(index, BlockParamTypes.ALL.get(choice[0]).param());
                    });
        }
        builder.show();
    }

    private void addParam(BlockSpec.Param param) {
        int count = BlockSpec.paramCount(Helper.getText(binding.spec));
        int[] same = new int[count];
        for (int i = 0; i < count; i++) same[i] = i + 1;
        changeSpec(BlockSpec.addParam(tokens(), param), same, +1);
    }

    private void retypeParam(int index, BlockSpec.Param param) {
        int count = BlockSpec.paramCount(Helper.getText(binding.spec));
        int[] same = new int[count];
        for (int i = 0; i < count; i++) same[i] = i + 1;
        changeSpec(BlockSpec.retypeParam(tokens(), index, param), same, 0);
    }

    private void deleteParam(int index) {
        int count = BlockSpec.paramCount(Helper.getText(binding.spec));
        int[] mapping = new int[count];
        for (int j = 0; j < count; j++) mapping[j] = j < index ? j + 1 : j == index ? 0 : j;
        changeSpec(BlockSpec.removeParam(tokens(), index), mapping, -1);
    }

    private void moveParam(int from, int to) {
        int count = BlockSpec.paramCount(Helper.getText(binding.spec));
        if (from < 0 || to < 0 || from >= count || to >= count || from == to) {
            refreshParams();
            return;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) order.add(i);
        order.add(to, order.remove(from));
        int[] mapping = new int[count];
        for (int newPosition = 0; newPosition < count; newPosition++) mapping[order.get(newPosition)] = newPosition + 1;
        changeSpec(BlockSpec.moveParam(tokens(), from, to), mapping, 0);
    }

    /**
     * Writes a new spec. When the code refers to inputs by number and those numbers would now point elsewhere, it
     * offers to renumber them; nothing in the code changes without that answer.
     *
     * @param mapping   new number of each old input (index = old number - 1), 0 when it was removed
     * @param slotShift how far the numbers of the block slots of if blocks move
     */
    private void changeSpec(List<BlockSpec.Token> newTokens, int[] mapping, int slotShift) {
        setSpecText(BlockSpec.toSpec(newTokens));
        boolean identity = slotShift == 0;
        for (int i = 0; i < mapping.length; i++) identity &= mapping[i] == i + 1;
        String code = code();
        if (identity || !code.matches("(?s).*%\\d+\\$.*")) {
            refreshDetails();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.be_update_refs_title)
                .setMessage(R.string.be_update_refs_message)
                .setPositiveButton(R.string.be_update_refs_yes, (d, w) -> replaceCode(CodeDiagnostics.renumber(code, mapping, slotShift)))
                .setNegativeButton(R.string.be_update_refs_no, null)
                .setOnDismissListener(d -> refreshDetails())
                .show();
    }

    // ---- Spec

    private void setUpSpec() {
        binding.spec.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncing || loading) return;
                specHistory.record(s.toString(), binding.spec.getSelectionStart());
                onSpecChanged();
            }
        });
        binding.spec2.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!loading) scheduleRefresh();
            }
        });
        binding.specAddText.setOnClickListener(v -> askText(getString(R.string.be_spec_text_title), "", text -> setSpecFromTokens(BlockSpec.addText(tokens(), text))));
        binding.specAddParam.setOnClickListener(v -> pickParamForSpec());
    }

    private void pickParamForSpec() {
        String[] labels = new String[BlockParamTypes.ALL.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = BlockParamTypes.ALL.get(i).label() + "   " + BlockParamTypes.ALL.get(i).spec();
        }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.be_param_type_title)
                .setItems(labels, (d, which) -> addParam(BlockParamTypes.ALL.get(which).param()))
                .setNegativeButton(R.string.common_word_cancel, null).show();
    }

    private interface TextAnswer {
        void accept(String text);
    }

    private void askText(String title, String initialText, TextAnswer answer) {
        TextInputLayout layout = new TextInputLayout(this);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setText(initialText);
        layout.addView(input);
        int pad = SketchwareUtil.dpToPx(20);
        layout.setPadding(pad, pad / 2, pad, 0);
        new MaterialAlertDialogBuilder(this).setTitle(title).setView(layout)
                .setPositiveButton(R.string.common_word_save, (d, w) -> {
                    String text = Helper.getText(input).trim();
                    if (!text.isEmpty()) answer.accept(text);
                })
                .setNegativeButton(R.string.common_word_cancel, null).show();
    }

    private void setSpecFromTokens(List<BlockSpec.Token> newTokens) {
        setSpecText(BlockSpec.toSpec(newTokens));
        refreshDetails();
    }

    /** Puts a spec in the text field (the builder and the Parameters tab write through it) and records it for undo. */
    private void setSpecText(String text) {
        syncing = true;
        binding.spec.setText(text);
        binding.spec.setSelection(text.length());
        syncing = false;
        specHistory.record(text, text.length());
        onSpecChanged();
    }

    private void onSpecChanged() {
        if (binding == null) return;
        refreshSpecUi();
        refreshParams();
        scheduleRefresh();
    }

    /** The builder chips and the problems of the spec text, rebuilt from the text (which always wins). */
    private void refreshSpecUi() {
        List<BlockSpec.Token> tokens = tokens();
        binding.specChips.removeAllViews();
        binding.specEmpty.setVisibility(tokens.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tokens.size(); i++) {
            BlockSpec.Token token = tokens.get(i);
            Chip chip = new Chip(this);
            chip.setText(token.text());
            chip.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            if (token instanceof BlockSpec.Param) {
                chip.setChipBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorPrimaryContainer)));
                chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnPrimaryContainer));
            } else if (token instanceof BlockSpec.Invalid) {
                chip.setChipBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorErrorContainer)));
                chip.setTextColor(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnErrorContainer));
            }
            int index = i;
            chip.setOnClickListener(v -> showTokenMenu(index, v));
            binding.specChips.addView(chip);
        }
        StringBuilder problems = new StringBuilder();
        for (BlockSpec.Invalid invalid : BlockSpec.problems(tokens)) {
            problems.append(invalid.error() ? "✖ " : "⚠ ").append(invalid.problem()).append('\n');
        }
        binding.specIssues.setText(problems.toString().trim());
        binding.specIssues.setVisibility(problems.length() == 0 ? View.GONE : View.VISIBLE);
        binding.specLayout.setError(null);
    }

    private void showTokenMenu(int index, View anchor) {
        List<BlockSpec.Token> tokens = tokens();
        if (index >= tokens.size()) return;
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.bm_edit);
        popup.getMenu().add(0, 2, 1, R.string.be_spec_move_left).setEnabled(index > 0);
        popup.getMenu().add(0, 3, 2, R.string.be_spec_move_right).setEnabled(index < tokens.size() - 1);
        popup.getMenu().add(0, 4, 3, R.string.be_param_delete);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1 -> {
                    BlockSpec.Token token = tokens.get(index);
                    if (token instanceof BlockSpec.Param param) {
                        int n = 0;
                        for (int i = 0; i < index; i++) if (tokens.get(i) instanceof BlockSpec.Param) n++;
                        pickParamType(n, param, BlockSpec.paramCount(Helper.getText(binding.spec)));
                    } else {
                        askText(getString(R.string.be_spec_text_title), token.text(),
                                text -> setSpecFromTokens(BlockSpec.replace(tokens, index, BlockSpec.parseToken(text.replaceAll("\\s+", "")))));
                    }
                }
                case 2 -> moveToken(tokens, index, -1);
                case 3 -> moveToken(tokens, index, +1);
                case 4 -> deleteToken(tokens, index);
                default -> {
                    return false;
                }
            }
            return true;
        });
        popup.show();
    }

    /** Words and parameters move freely; moving parameters past each other changes the inputs' order. */
    private void moveToken(List<BlockSpec.Token> tokens, int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= tokens.size()) return;
        List<BlockSpec.Token> moved = BlockSpec.move(tokens, index, delta);
        int count = BlockSpec.paramCount(BlockSpec.toSpec(tokens));
        int[] mapping = new int[count];
        List<BlockSpec.Param> before = BlockSpec.params(tokens);
        List<BlockSpec.Param> after = BlockSpec.params(moved);
        List<Integer> unused = new ArrayList<>();
        for (int i = 0; i < count; i++) unused.add(i);
        for (int i = 0; i < count; i++) {
            int newPosition = -1;
            for (int k : unused) {
                if (after.get(k) == before.get(i)) {
                    newPosition = k;
                    break;
                }
            }
            if (newPosition < 0) newPosition = i;
            unused.remove((Integer) newPosition);
            mapping[i] = newPosition + 1;
        }
        changeSpec(moved, mapping, 0);
    }

    private void deleteToken(List<BlockSpec.Token> tokens, int index) {
        if (tokens.get(index) instanceof BlockSpec.Param) {
            int n = 0;
            for (int i = 0; i < index; i++) if (tokens.get(i) instanceof BlockSpec.Param) n++;
            deleteParam(n);
        } else {
            setSpecFromTokens(BlockSpec.remove(tokens, index));
        }
    }

    // ---- Code

    private void setUpCode() {
        EditorUtils.loadJavaConfig(binding.code);
        binding.code.setTypefaceText(EditorUtils.getTypeface(this));
        binding.code.setTextSize(13);
        binding.code.setWordwrap(false);
        binding.code.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (loading || binding == null) return;
            binding.codeUndo.setEnabled(binding.code.canUndo());
            binding.codeRedo.setEnabled(binding.code.canRedo());
            scheduleRefresh();
        });
        binding.codeUndo.setOnClickListener(v -> undo());
        binding.codeRedo.setOnClickListener(v -> redo());
        binding.codeUndo.setEnabled(false);
        binding.codeRedo.setEnabled(false);

        binding.codeSearchToggle.setOnClickListener(v -> toggleSearch(binding.codeSearchBar.getVisibility() != View.VISIBLE));
        binding.codeSearchClose.setOnClickListener(v -> toggleSearch(false));
        binding.codeSearchQuery.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                search(s.toString());
            }
        });
        binding.codeSearchNext.setOnClickListener(v -> {
            binding.code.getSearcher().gotoNext();
            updateSearchCount();
        });
        binding.codeSearchPrevious.setOnClickListener(v -> {
            binding.code.getSearcher().gotoPrevious();
            updateSearchCount();
        });

        binding.importAdd.setOnClickListener(v -> addImportFromField());
        binding.importInput.setOnEditorActionListener((v, actionId, event) -> {
            addImportFromField();
            return true;
        });
    }

    private String code() {
        return binding.code.getText().toString();
    }

    /** Replaces the whole code in a way the editor can undo. */
    private void replaceCode(String text) {
        Content content = binding.code.getText();
        int lastLine = content.getLineCount() - 1;
        content.replace(0, 0, lastLine, content.getColumnCount(lastLine), text);
    }

    private void toggleSearch(boolean show) {
        binding.codeSearchBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.codeSearchQuery.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(binding.codeSearchQuery, InputMethodManager.SHOW_IMPLICIT);
            search(Helper.getText(binding.codeSearchQuery));
        } else {
            binding.code.getSearcher().stopSearch();
            binding.codeSearchCount.setText("");
            hideKeyboard();
        }
    }

    private void search(String query) {
        EditorSearcher searcher = binding.code.getSearcher();
        if (query.isEmpty()) {
            searcher.stopSearch();
            binding.codeSearchCount.setText("");
            return;
        }
        searcher.search(query, new EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true));
        handler.postDelayed(() -> {
            if (binding != null) {
                searcher.gotoNext();
                updateSearchCount();
            }
        }, 150);
    }

    private void updateSearchCount() {
        EditorSearcher searcher = binding.code.getSearcher();
        int total = searcher.getMatchedPositionCount();
        binding.codeSearchCount.setText(total == 0 ? getString(R.string.be_code_search_none)
                : getString(R.string.be_code_search_count, Math.max(searcher.getCurrentMatchedPositionIndex(), 0) + 1, total));
    }

    private void goToLine(int line) {
        selectTab(TAB_CODE);
        if (line > 0) {
            binding.code.requestFocus();
            binding.code.setSelection(Math.min(line, binding.code.getText().getLineCount()) - 1, 0);
        }
    }

    // ---- imports

    private void refreshImports() {
        binding.importsRows.removeAllViews();
        List<ImportList.Entry> entries = ImportList.parse(imports);
        float density = getResources().getDisplayMetrics().density;
        for (ImportList.Entry entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView text = new TextView(this);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            text.setTypeface(android.graphics.Typeface.MONOSPACE);
            text.setText(entry.valid() ? entry.text() : entry.text() + "\n" + ImportList.describe(entry));
            text.setTextColor(MaterialColors.getColor(text, entry.valid() ? com.google.android.material.R.attr.colorOnSurface : androidx.appcompat.R.attr.colorError));
            row.addView(text);
            ImageButton edit = iconButton(R.drawable.ic_mtrl_edit, R.string.be_import_edit, density);
            edit.setOnClickListener(v -> askText(getString(R.string.be_imports_title), entry.text(), newText -> {
                imports = ImportList.replaceLine(imports, entry.line(), newText);
                importsChanged();
            }));
            row.addView(edit);
            ImageButton remove = iconButton(R.drawable.ic_mtrl_close, R.string.be_import_remove, density);
            remove.setOnClickListener(v -> {
                imports = ImportList.removeLine(imports, entry.line());
                importsChanged();
            });
            row.addView(remove);
            binding.importsRows.addView(row);
        }
        binding.importSuggestions.removeAllViews();
        Set<String> already = ImportList.classes(imports);
        for (String full : ImportList.usedKnownClasses(code())) {
            if (already.contains(full)) continue;
            Chip chip = new Chip(this);
            chip.setText(full.substring(full.lastIndexOf('.') + 1) + " · " + full);
            chip.setMinHeight(Math.round(48 * density));
            chip.setOnClickListener(v -> addImport(full));
            binding.importSuggestions.addView(chip);
        }
        binding.sectionImports.setTitle(getString(R.string.be_imports_title) + (entries.isEmpty() ? "" : " (" + entries.size() + ")"));
    }

    private ImageButton iconButton(int icon, int description, float density) {
        ImageButton button = new ImageButton(this);
        button.setLayoutParams(new LinearLayout.LayoutParams(Math.round(48 * density), Math.round(48 * density)));
        button.setBackgroundResource(android.R.color.transparent);
        button.setImageResource(icon);
        button.setColorFilter(MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurfaceVariant));
        button.setContentDescription(getString(description));
        return button;
    }

    private void addImportFromField() {
        String text = Helper.getText(binding.importInput).trim();
        if (text.isEmpty()) return;
        List<ImportList.Entry> parsed = ImportList.parse(text);
        if (parsed.size() != 1 || !parsed.get(0).valid()) {
            binding.importLayout.setError(parsed.isEmpty() ? null : parsed.get(0).problem());
            return;
        }
        binding.importLayout.setError(null);
        binding.importInput.setText("");
        addImport(parsed.get(0).type());
    }

    private void addImport(String fullName) {
        imports = ImportList.add(imports, fullName);
        importsChanged();
    }

    private void importsChanged() {
        refreshImports();
        refreshDiagnostics();
        invalidateOptionsMenu();
    }

    // ---- diagnostics

    private void refreshDiagnostics() {
        binding.diagnosticsRows.removeAllViews();
        List<CodeDiagnostics.Diagnostic> found = CodeDiagnostics.analyze(code(), type, BlockSpec.paramCount(Helper.getText(binding.spec)), imports);
        int errors = 0;
        int warnings = 0;
        float density = getResources().getDisplayMetrics().density;
        for (CodeDiagnostics.Diagnostic d : found) {
            if (d.severity() == CodeDiagnostics.Severity.ERROR) errors++;
            else if (d.severity() == CodeDiagnostics.Severity.WARNING) warnings++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Math.round(48 * density));
            row.setClickable(true);
            row.setFocusable(true);
            TextView text = new TextView(this);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            String where = d.line() > 0 ? getString(R.string.be_diagnostic_line, d.line()) : getString(R.string.be_diagnostic_block);
            String mark = d.severity() == CodeDiagnostics.Severity.ERROR ? "✖" : d.severity() == CodeDiagnostics.Severity.WARNING ? "⚠" : "ℹ";
            text.setText(mark + " " + where + " · " + d.message()
                    + (d.kind() == CodeDiagnostics.Kind.NEEDS_COMPILATION ? " (" + getString(R.string.be_diagnostic_compile) + ")" : ""));
            text.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            text.setTextColor(MaterialColors.getColor(text, d.severity() == CodeDiagnostics.Severity.ERROR ? androidx.appcompat.R.attr.colorError
                    : com.google.android.material.R.attr.colorOnSurfaceVariant));
            row.addView(text);
            if (d.fixImport() != null) {
                com.google.android.material.button.MaterialButton add = new com.google.android.material.button.MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                add.setText(R.string.be_import_add);
                add.setMinHeight(Math.round(48 * density));
                String full = d.fixImport();
                add.setOnClickListener(v -> addImport(full));
                row.addView(add);
            }
            int line = d.line();
            row.setOnClickListener(v -> {
                if (line > 0) goToLine(line);
            });
            binding.diagnosticsRows.addView(row);
        }
        if (found.isEmpty()) binding.diagnosticsRows.addView(message(getString(R.string.be_diagnostics_none), false));
        binding.sectionDiagnostics.setTitle(getString(R.string.be_diagnostics) + (errors + warnings == 0 ? "" : " (" + errors + " ✖ · " + warnings + " ⚠)"));
        // The list scrolls inside a limited height, so the editor keeps its room.
        binding.diagnosticsRows.measure(View.MeasureSpec.makeMeasureSpec(binding.diagnosticsScroll.getWidth() > 0
                ? binding.diagnosticsScroll.getWidth() : getResources().getDisplayMetrics().widthPixels, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        ViewGroup.LayoutParams params = binding.diagnosticsScroll.getLayoutParams();
        params.height = Math.min(binding.diagnosticsRows.getMeasuredHeight(), Math.round(140 * density));
        binding.diagnosticsScroll.setLayoutParams(params);
    }

    // ---- fields, loading, saving

    private BlockFields fields() {
        return new BlockFields(Helper.getText(binding.name), type, Helper.getText(binding.typename), Helper.getText(binding.spec),
                Helper.getText(binding.spec2), Helper.getText(binding.colour), imports, code());
    }

    /** Puts a state in every control. */
    private void apply(BlockFields f) {
        boolean wasLoading = loading;
        loading = true;
        binding.name.setText(f.name());
        type = f.type() == null || f.type().isEmpty() || f.type().equals(" ") ? "regular" : f.type();
        binding.typename.setText(f.typeName());
        syncing = true;
        binding.spec.setText(f.spec());
        syncing = false;
        specHistory = new TextHistory(f.spec(), 200);
        binding.spec2.setText(f.spec2());
        binding.colour.setText(f.color());
        imports = f.imports() == null ? "" : f.imports();
        binding.code.setText(f.code());
        loading = wasLoading;
        onTypeChanged();
    }

    private void receiveIntents() {
        mode = getIntent().getStringExtra("mode");
        path = getIntent().getStringExtra("path");
        palletColour = getIntent().getStringExtra("color");
        if (mode == null || path == null) {
            finish();
            return;
        }
        getBlockList();
        buildPresets();
        if (mode.equals("add")) {
            blockPosition = Integer.parseInt(getIntent().getStringExtra("pallet"));
            binding.colour.setText(palletColour);
            getSupportActionBar().setTitle(R.string.be_title_add);
            return;
        }
        blockPosition = Integer.parseInt(getIntent().getStringExtra("pos"));
        binding.colour.setText(palletColour);
        getSupportActionBar().setTitle(R.string.be_title_insert);
        if (mode.equals("edit")) {
            getSupportActionBar().setTitle(R.string.be_title_edit);
            if (blockPosition >= 0 && blockPosition < blocksList.size()) {
                fillUpInputs(blockPosition);
            } else {
                SketchwareUtil.toastError("This block no longer exists");
                finish();
            }
        }
    }

    private void fillUpInputs(int position) {
        HashMap<String, Object> block = blocksList.get(position);
        String name = stringOrNull(block.get("name"));
        if (name != null) {
            binding.name.setText(name);
        } else {
            binding.nameLayout.setError("Invalid name block data");
        }
        Object typeObject = block.get("type");
        if (typeObject instanceof String typeString) {
            type = typeString.equals(" ") || typeString.isEmpty() ? "regular" : typeString;
        }
        Object typeName = block.get("typeName");
        if (typeName instanceof String s) binding.typename.setText(s);
        Object specObject = block.get("spec");
        if (specObject instanceof String s) {
            syncing = true;
            binding.spec.setText(s);
            syncing = false;
            specHistory = new TextHistory(s, 200);
        } else {
            binding.specLayout.setError("Invalid spec block data");
        }
        Object spec2Object = block.get("spec2");
        if (spec2Object instanceof String s) binding.spec2.setText(s);
        Object importsObject = block.get("imports");
        if (importsObject instanceof String s) imports = s;
        Object colorObject = block.get("color");
        if (colorObject instanceof String s) {
            binding.colour.setText(s);
        } else if (colorObject != null) {
            binding.colourLayout.setError("Invalid color block data");
        } else {
            binding.colour.setText(palletColour);
        }
        Object codeObject = block.get("code");
        if (codeObject instanceof String s) {
            binding.code.setText(s);
        } else if (codeObject != null) {
            SketchwareUtil.toastError("Invalid code block data");
        }
    }

    @Nullable
    private static String stringOrNull(Object value) {
        return value instanceof String s ? s : null;
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
            // fall through to the shared handler
        }
        SketchwareUtil.showFailedToParseJsonDialog(this, new File(path), "Custom Blocks", v -> getBlockList());
        blocksList = new ArrayList<>();
        blocksUnreadable = true;
    }

    // ---- checks and saving

    private String describe(List<BlockValidator.Issue> issues) {
        String[] tabs = {getString(R.string.be_tab_general), getString(R.string.be_tab_parameters), getString(R.string.be_tab_spec), getString(R.string.be_tab_code)};
        StringBuilder text = new StringBuilder();
        for (BlockValidator.Issue issue : issues) {
            String mark = switch (issue.severity()) {
                case ERROR -> "✖ ";
                case WARNING -> "⚠ ";
                case INFO -> "ℹ ";
            };
            text.append(mark).append(tabs[issue.tab().ordinal()]);
            if (issue.line() > 0) text.append(" · ").append(getString(R.string.be_diagnostic_line, issue.line()));
            text.append(": ").append(issue.message());
            if (issue.kind() == CodeDiagnostics.Kind.NEEDS_COMPILATION) text.append(" (").append(getString(R.string.be_diagnostic_compile)).append(')');
            text.append("\n\n");
        }
        return text.toString().trim();
    }

    private void showCheck() {
        List<BlockValidator.Issue> issues = BlockValidator.validate(fields(), otherBlockNames());
        new MaterialAlertDialogBuilder(this).setTitle(R.string.be_check_block)
                .setMessage(issues.isEmpty() ? getString(R.string.be_diagnostics_none) : describe(issues))
                .setPositiveButton(R.string.common_word_ok, null).show();
    }

    private void showJavaPreview() {
        TextView view = new TextView(this);
        view.setTypeface(android.graphics.Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        int pad = SketchwareUtil.dpToPx(20);
        view.setPadding(pad, pad / 2, pad, 0);
        view.setText(JavaPreview.build(Helper.getText(binding.spec), code(), imports));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(view);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.be_java_preview).setView(scroll)
                .setPositiveButton(R.string.common_word_ok, null).show();
    }

    private void trySave() {
        if (blocksUnreadable) {
            SketchwareUtil.toastError(getString(R.string.be_blocks_unreadable));
            return;
        }
        List<BlockValidator.Issue> issues = BlockValidator.validate(fields(), otherBlockNames());
        boolean errors = BlockValidator.hasErrors(issues);
        List<BlockValidator.Issue> blocking = BlockValidator.blocking(issues, true);
        if (blocking.isEmpty()) {
            save();
            return;
        }
        BlockValidator.Issue first = blocking.get(0);
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(errors ? R.string.be_fix_first : R.string.be_check_first)
                .setMessage(describe(blocking))
                .setNeutralButton(R.string.be_diagnostic_block, (d, w) -> showIssue(first));
        if (errors) {
            dialog.setPositiveButton(R.string.common_word_ok, null);
        } else {
            dialog.setPositiveButton(R.string.common_word_save, (d, w) -> save()).setNegativeButton(R.string.common_word_cancel, null);
        }
        dialog.show();
    }

    private void showIssue(BlockValidator.Issue issue) {
        switch (issue.tab()) {
            case GENERAL -> selectTab(TAB_GENERAL);
            case PARAMETERS -> selectTab(TAB_PARAMETERS);
            case SPEC -> selectTab(TAB_SPEC);
            case CODE -> goToLine(issue.line());
        }
    }

    private void save() {
        BlockFields f = fields();
        HashMap<String, Object> map;
        Object paletteId;
        switch (mode) {
            case "add" -> {
                map = new HashMap<>();
                writeFields(map, f, false);
                map.put("palette", String.valueOf(blockPosition));
                blocksList.add(map);
                paletteId = map.get("palette");
            }
            case "insert" -> {
                map = new HashMap<>();
                writeFields(map, f, false);
                map.put("palette", blocksList.get(blockPosition).get("palette"));
                blocksList.add(blockPosition, map);
                paletteId = map.get("palette");
            }
            default -> {
                map = blocksList.get(blockPosition);
                writeFields(map, f, true);
                paletteId = map.get("palette");
            }
        }
        writeBlocks(paletteId);
    }

    /** Writes the block's fields as the Block Manager has always stored them; other keys of an edited block stay. */
    private static void writeFields(HashMap<String, Object> map, BlockFields f, boolean editing) {
        map.put("name", f.name().trim());
        map.put("type", f.type().equals("regular") || f.type().isEmpty() ? " " : f.type());
        map.put("typeName", f.typeName());
        map.put("spec", f.spec());
        map.put("color", f.color().trim());
        if (f.type().equals("e")) map.put("spec2", f.spec2());
        if (editing) {
            map.put("imports", f.imports());
        } else if (!TextUtils.isEmpty(f.imports())) {
            map.put("imports", f.imports());
        }
        map.put("code", f.code());
    }

    /** Saves block.json in one step (keeping a .bak) and dates the palette the block belongs to. */
    private void writeBlocks(@Nullable Object paletteId) {
        try {
            BlockStore.write(new File(path), getGson().toJson(blocksList));
        } catch (java.io.IOException e) {
            SketchwareUtil.toastError(getString(R.string.be_save_failed, e.getMessage()));
            return;
        }
        touchPalette(paletteId);
        saved = true;
        clearDraft();
        SketchwareUtil.toast(getString(R.string.be_saved));
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

    // ---- leaving, drafts

    private boolean isDirty() {
        return !fields().equals(initial);
    }

    private void leave() {
        if (!isDirty()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.be_discard_title)
                .setMessage(R.string.be_discard_message)
                .setPositiveButton(R.string.be_discard, (d, w) -> finish())
                .setNegativeButton(R.string.be_keep_editing, null)
                .show();
    }

    private String draftKey() {
        String where = "add".equals(mode) ? getIntent().getStringExtra("pallet") : getIntent().getStringExtra("pos");
        return path + "|" + mode + "|" + where;
    }

    private SharedPreferences drafts() {
        return getSharedPreferences("block_editor_drafts", MODE_PRIVATE);
    }

    private void clearDraft() {
        drafts().edit().remove(draftKey()).apply();
    }

    private void offerDraft() {
        String json = drafts().getString(draftKey(), null);
        if (json == null) return;
        BlockFields draft;
        try {
            draft = getGson().fromJson(json, BlockFields.class);
        } catch (RuntimeException e) {
            clearDraft();
            return;
        }
        if (draft == null || draft.equals(fields())) {
            clearDraft();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.be_draft_title)
                .setMessage(R.string.be_draft_message)
                .setPositiveButton(R.string.be_draft_restore, (d, w) -> {
                    apply(draft);
                    refreshAll();
                })
                .setNegativeButton(R.string.be_draft_discard, (d, w) -> clearDraft())
                .show();
    }
}
