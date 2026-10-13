package com.besome.sketch.editor.makeblock;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.editor.makeblock.MoreBlockSpec.Draft;
import com.besome.sketch.editor.makeblock.MoreBlockSpec.Param;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import a.a.a.Lx;
import a.a.a.Rs;
import a.a.a.ZB;
import a.a.a.bB;
import a.a.a.mB;
import a.a.a.uq;
import mod.hey.studios.moreblock.MoreblockEditor;
import mod.hey.studios.moreblock.MoreblockValidator;
import mod.hey.studios.moreblock.ReturnMoreblockManager;
import mod.hey.studios.util.Helper;
import mod.jbk.util.BlockUtil;
import pro.sketchware.R;
import pro.sketchware.databinding.MakeBlockLayoutBinding;
import pro.sketchware.databinding.MakeBlockParamEditorBinding;
import pro.sketchware.databinding.MakeBlockParamItemBinding;
import pro.sketchware.lib.base.BaseTextWatcher;

/**
 * The form of the New More Block screen: name, return type, parameters (reorderable) and advanced parameters, with a
 * live preview of the real block and of the Java it generates.
 */
@SuppressLint("ViewConstructor")
public class MoreBlockBuilderView extends LinearLayout {

    /** Chip ids in the order of {@link #CHIP_KEYS}; the keys are what drafts store. */
    private static final int[] CHIP_IDS = {
            R.id.radio_mb_type_void, R.id.radio_mb_type_string, R.id.radio_mb_type_number, R.id.radio_mb_type_boolean,
            R.id.radio_mb_type_map, R.id.radio_mb_type_liststring, R.id.radio_mb_type_listmap, R.id.radio_mb_type_view};
    private static final String[] CHIP_KEYS = {
            "void", "string", "number", "boolean", "map", "liststring", "listmap", "view"};

    /** Told whenever something the screen reacts to changes. */
    public interface Listener {
        /** The form turned valid or invalid: the Create button follows. */
        void onValidityChanged(boolean valid);

        /** Anything in the form changed: the draft is saved. */
        void onChanged();
    }

    private final Activity activity;
    private final MakeBlockLayoutBinding binding;
    private final ArrayList<Param> params = new ArrayList<>();
    private final ParameterAdapter adapter = new ParameterAdapter();
    private final ItemTouchHelper touchHelper;
    private final MoreblockValidator blockNameValidator;
    /** The list the name validator reads: replaced in place, so the validator never has to be swapped. */
    private final ArrayList<String> takenNames;
    private ZB advancedNameValidator;
    private Listener listener;
    private Rs block;
    private boolean advancedCustomTypeValid;
    private boolean lastValidity;
    /** Shown in the preview while the typed name is empty or not valid (yet). */
    private String previewName = MoreBlockSpec.PREVIEW_PLACEHOLDER;
    /** While a draft or a stored MoreBlock is loaded, the form must not report every intermediate change. */
    private boolean loading;

    /** The MoreBlock being edited and how many calls of it the activity has, shown above its new code. */
    private String editedName;
    private String editedSpec;
    private int editedUses;

    /**
     * @param takenNames names of blocks, variables and lists the new MoreBlock can't use
     */
    public MoreBlockBuilderView(Activity activity, ArrayList<String> takenNames) {
        super(activity);
        this.activity = activity;
        this.takenNames = takenNames;
        binding = MakeBlockLayoutBinding.inflate(activity.getLayoutInflater(), this, true);

        blockNameValidator = new MoreblockValidator(activity, binding.tiName, uq.b, uq.a(), takenNames);
        binding.edName.setPrivateImeOptions("defaultInputmode=english;");
        binding.edName.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String typed = s.toString().trim();
                if (typed.isEmpty()) {
                    previewName = MoreBlockSpec.PREVIEW_PLACEHOLDER;
                } else if (blockNameValidator.d) {
                    previewName = ReturnMoreblockManager.getMbName(typed);
                }
                refreshPreview();
                notifyChanged();
            }
        });
        binding.edName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus();
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
            return false;
        });

        binding.radioMbType.setOnCheckedStateChangeListener((chipGroup, ids) -> {
            block = newPreviewBlock();
            refreshPreview();
            notifyChanged();
        });

        binding.parameterList.setLayoutManager(new LinearLayoutManager(activity));
        binding.parameterList.setAdapter(adapter);
        binding.parameterList.setItemAnimator(null);
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return move(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // The order is final once the item is dropped: refresh the preview and the other positions' labels.
                adapter.notifyItemRangeChanged(0, params.size());
                onParametersChanged();
            }
        });
        touchHelper.attachToRecyclerView(binding.parameterList);
        binding.addParameter.setOnClickListener(v -> {
            if (!mB.a()) {
                showParameterEditor(-1);
            }
        });

        initAdvancedSection();

        block = newPreviewBlock();
        binding.blockArea.addView(block);
        refreshPreview();
        updateParametersSection();
    }

    /** For screens that embed the form and learn the taken names later, see {@link #setFuncNameValidator}. */
    public MoreBlockBuilderView(Activity activity) {
        this(activity, new ArrayList<>());
    }

    /** Replaces the names the new MoreBlock can't use. */
    public void setFuncNameValidator(ArrayList<String> names) {
        takenNames.clear();
        takenNames.addAll(names);
    }

    /** @return whether nothing was entered yet; same as {@link #isEmpty()}. */
    public boolean a() {
        return isEmpty();
    }

    /** @return whether the block can be created; if not, tells the user and focuses the name. */
    public boolean b() {
        if (isValid()) {
            return true;
        }
        bB.b(getContext(), Helper.getResString(R.string.logic_editor_message_name_requied), Toast.LENGTH_SHORT).show();
        focusName();
        return false;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Advanced parameters
    // ---------------------------------------------------------------------------------------------------------

    private void initAdvancedSection() {
        advancedNameValidator = new ZB(activity, binding.tiVariable, uq.b, reservedParameterNames(-1), new ArrayList<>());
        binding.advancedHeader.setOnClickListener(v -> setAdvancedExpanded(binding.advancedBody.getVisibility() != View.VISIBLE));
        binding.advancedHeader.setContentDescription(Helper.getResString(R.string.make_block_advanced));

        binding.parameter.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence sequence, int start, int before, int count) {
                String typed = sequence.toString();
                advancedCustomTypeValid = MoreBlockSpec.isValidCustomType(typed);
                boolean showError = !typed.isEmpty() && !advancedCustomTypeValid;
                binding.tiParameter.setError(showError ? Helper.getResString(R.string.make_block_custom_type_invalid) : null);
                binding.tiParameter.setErrorEnabled(showError);
                updateAdvancedPreview();
            }
        });
        binding.name.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateAdvancedPreview();
            }
        });
        binding.add.setOnClickListener(v -> {
            if (mB.a()) {
                return;
            }
            if (advancedCustomTypeValid && advancedNameValidator.d) {
                params.add(new Param(Helper.getText(binding.parameter), Helper.getText(binding.name)));
                binding.parameter.setText("");
                binding.name.setText("");
                adapter.notifyItemInserted(params.size() - 1);
                onParametersChanged();
            } else if (!advancedCustomTypeValid) {
                binding.parameter.requestFocus();
            } else {
                binding.name.requestFocus();
            }
        });
    }

    private void updateAdvancedPreview() {
        boolean ready = advancedCustomTypeValid && advancedNameValidator.d;
        binding.tvCustomPreview.setVisibility(ready ? View.VISIBLE : View.GONE);
        if (ready) {
            binding.tvCustomPreview.setText(Helper.getResString(R.string.make_block_custom_will_add,
                    "%" + Helper.getText(binding.parameter) + "." + Helper.getText(binding.name)));
        }
    }

    public void setAdvancedExpanded(boolean expanded) {
        binding.advancedBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        binding.advancedChevron.setRotation(expanded ? 180f : 0f);
        binding.advancedHeader.setSelected(expanded);
        if (!loading) {
            notifyChanged();
        }
    }

    public boolean isAdvancedExpanded() {
        return binding.advancedBody.getVisibility() == View.VISIBLE;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Parameters
    // ---------------------------------------------------------------------------------------------------------

    private String[] reservedParameterNames(int ignoredIndex) {
        List<String> reserved = new ArrayList<>(Arrays.asList(uq.a()));
        reserved.addAll(MoreBlockSpec.takenParameterNames(params, ignoredIndex));
        return reserved.toArray(new String[0]);
    }

    private boolean move(int from, int to) {
        if (from < 0 || to < 0 || from >= params.size() || to >= params.size()) {
            return false;
        }
        Collections.swap(params, from, to);
        adapter.notifyItemMoved(from, to);
        return true;
    }

    /** Everything that depends on the parameters, after any change to them. */
    private void onParametersChanged() {
        advancedNameValidator.a(reservedParameterNames(-1));
        updateParametersSection();
        refreshPreview();
        notifyChanged();
    }

    private void updateParametersSection() {
        binding.tvNoParameters.setVisibility(params.isEmpty() ? View.VISIBLE : View.GONE);
        binding.tvParametersTitle.setText(params.isEmpty()
                ? Helper.getResString(R.string.make_block_parameters)
                : Helper.getResString(R.string.make_block_parameters_count, params.size()));
    }

    private void removeParameter(int index) {
        if (index < 0 || index >= params.size()) {
            return;
        }
        Param removed = params.remove(index);
        adapter.notifyItemRemoved(index);
        adapter.notifyItemRangeChanged(index, params.size() - index);
        onParametersChanged();
        Snackbar.make(this, R.string.make_block_param_removed, Snackbar.LENGTH_LONG)
                .setAction(R.string.make_block_undo, v -> {
                    int at = Math.min(index, params.size());
                    params.add(at, removed);
                    adapter.notifyItemInserted(at);
                    adapter.notifyItemRangeChanged(at, params.size() - at);
                    onParametersChanged();
                })
                .show();
    }

    /**
     * Adds a parameter or label ({@code index == -1}), or edits the one at {@code index}, in a bottom sheet.
     */
    private void showParameterEditor(int index) {
        Param existing = index >= 0 ? params.get(index) : null;
        BottomSheetDialog sheet = new BottomSheetDialog(activity);
        MakeBlockParamEditorBinding editor = MakeBlockParamEditorBinding.inflate(activity.getLayoutInflater());
        sheet.setContentView(editor.getRoot());
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);

        editor.editorTitle.setText(existing == null ? R.string.make_block_add_parameter : R.string.make_block_edit_parameter);
        editor.editorConfirm.setText(existing == null ? R.string.common_word_add : R.string.common_word_save);

        VariableItemView typeView = new VariableItemView(activity);
        editor.typeContainer.addView(typeView);
        editor.edParamName.setPrivateImeOptions("defaultInputmode=english;");
        editor.edLabel.setPrivateImeOptions("defaultInputmode=english;");
        ZB nameValidator = new ZB(activity, editor.tiParamName, uq.b, reservedParameterNames(index), new ArrayList<>());
        ZB labelValidator = new ZB(activity, editor.tiLabel, uq.b, uq.a(), new ArrayList<>());
        boolean[] customType = {false};
        Runnable update = () -> {
            boolean label = editor.kindToggle.getCheckedButtonId() == R.id.kind_label;
            editor.groupParameter.setVisibility(label ? View.GONE : View.VISIBLE);
            editor.groupLabel.setVisibility(label ? View.VISIBLE : View.GONE);
            boolean valid;
            if (label) {
                valid = labelValidator.d;
            } else {
                valid = nameValidator.d && (!customType[0] || MoreBlockSpec.isValidCustomType(Helper.getText(editor.edCustomType)));
            }
            editor.editorConfirm.setEnabled(valid);
        };
        editor.kindToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> update.run());
        BaseTextWatcher refresh = new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                update.run();
            }
        };
        editor.edParamName.addTextChangedListener(refresh);
        editor.edLabel.addTextChangedListener(refresh);
        editor.edCustomType.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                boolean bad = s.length() > 0 && !MoreBlockSpec.isValidCustomType(s.toString());
                editor.tiCustomType.setError(bad ? Helper.getResString(R.string.make_block_custom_type_invalid) : null);
                editor.tiCustomType.setErrorEnabled(bad);
                update.run();
            }
        });

        if (existing == null) {
            editor.kindToggle.check(R.id.kind_parameter);
        } else if (existing.isLabel()) {
            editor.kindToggle.check(R.id.kind_label);
            editor.edLabel.setText(existing.name());
        } else {
            editor.kindToggle.check(R.id.kind_parameter);
            if (!typeView.select(existing.type())) {
                // A type the selector doesn't list (written in the advanced section): edit it as text.
                customType[0] = true;
                editor.typeContainer.setVisibility(View.GONE);
                editor.tiCustomType.setVisibility(View.VISIBLE);
                editor.edCustomType.setText(existing.type());
            }
            editor.edParamName.setText(existing.name());
        }
        update.run();

        editor.editorCancel.setOnClickListener(v -> sheet.dismiss());
        editor.editorConfirm.setOnClickListener(v -> {
            if (mB.a() || !editor.editorConfirm.isEnabled()) {
                return;
            }
            Param param;
            if (editor.kindToggle.getCheckedButtonId() == R.id.kind_label) {
                param = new Param(MoreBlockSpec.LABEL, Helper.getText(editor.edLabel));
            } else {
                String fullSpec;
                if (customType[0]) {
                    fullSpec = Helper.getText(editor.edCustomType);
                } else {
                    Pair<String, String> selected = typeView.getSelectedItem();
                    fullSpec = selected.second.isEmpty() ? selected.first : selected.first + "." + selected.second;
                }
                param = new Param(fullSpec, Helper.getText(editor.edParamName));
            }
            if (index >= 0) {
                params.set(index, param);
                adapter.notifyItemChanged(index);
            } else {
                params.add(param);
                adapter.notifyItemInserted(params.size() - 1);
            }
            onParametersChanged();
            sheet.dismiss();
        });
        sheet.show();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Preview
    // ---------------------------------------------------------------------------------------------------------

    private Rs newPreviewBlock() {
        return new Rs(activity, 0, "", ReturnMoreblockManager.getPreviewType(getType()), "definedFunc");
    }

    /** Redraws the real Sketchware block from the name, return type and parameters, and the generated Java. */
    private void refreshPreview() {
        ViewGroup blockArea = binding.blockArea;
        blockArea.removeAllViews();
        blockArea.addView(block);
        String spec = MoreBlockSpec.buildSpec(previewName, params);
        block.setSpec(spec);
        BlockUtil.loadPreviewBlockVariables(blockArea, block, spec);
        block.k();
        updateCodePreview();
    }

    /**
     * Shows the method the MoreBlock generates, from the same generator the build uses. When editing, the current
     * method is shown first, so the effect of a change on the code is visible before saving.
     */
    private void updateCodePreview() {
        String name = Helper.getText(binding.edName).trim();
        StringBuilder code = new StringBuilder();
        if (editedName != null && editedSpec != null) {
            code.append("// Now").append(editedUses > 0 ? " (" + editedUses + (editedUses == 1 ? " call" : " calls") + " will follow the change)" : "").append('\n')
                    .append(signature(editedName, editedSpec)).append("\n\n// After saving\n");
        }
        if (name.isEmpty()) {
            code.append("// Type a block name");
        } else {
            Pair<String, String> information = getBlockInformation();
            code.append(signature(information.first, information.second));
        }
        binding.codePreview.setText(code);
    }

    private static String signature(String nameWithType, String spec) {
        try {
            return Lx.getMoreBlockCode(nameWithType, spec, "    // blocks").replace("\r\n", "\n").trim();
        } catch (RuntimeException e) {
            return "// " + ReturnMoreblockManager.getMbName(nameWithType);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------------------------

    private void notifyChanged() {
        if (loading || listener == null) {
            return;
        }
        listener.onChanged();
        boolean valid = isValid();
        if (valid != lastValidity) {
            lastValidity = valid;
            listener.onValidityChanged(valid);
        }
    }

    /** @return whether nothing was entered yet. */
    public boolean isEmpty() {
        return Helper.getText(binding.edName).isEmpty() && params.isEmpty();
    }

    /** @return whether the block can be created: its name is valid, not taken and not empty. */
    public boolean isValid() {
        return !Helper.getText(binding.edName).trim().isEmpty() && blockNameValidator.d;
    }

    /** Moves the focus to the name field, whose error says what is wrong. */
    public void focusName() {
        binding.edName.requestFocus();
    }

    public Pair<String, String> getBlockInformation() {
        String name = Helper.getText(binding.edName).trim();
        String type = getType();
        return new Pair<>(ReturnMoreblockManager.injectMbType(name, name, type),
                ReturnMoreblockManager.injectMbType(MoreBlockSpec.buildSpec(name, params), name, type));
    }

    private String getType() {
        return ReturnMoreblockManager.getMbTypeFromChipGroup(binding.radioMbType);
    }

    /** The parameters, with a defensive copy: the form owns the list. */
    public List<Param> getParameters() {
        return List.copyOf(params);
    }

    /**
     * Fills the builder with an existing MoreBlock, so it can be edited.
     *
     * @param nameWithType the MoreBlock name, with its return type, e.g. {@code uniqueID[String]}
     * @param spec         its spec, e.g. {@code uniqueID[String] %s.prefix}
     */
    public void loadMoreBlock(String nameWithType, String spec) {
        loading = true;
        try {
            params.clear();
            for (Pair<String, String> part : MoreblockEditor.parts(spec)) {
                params.add(new Param(part.first, part.second));
            }
            adapter.notifyDataSetChanged();
            advancedNameValidator.a(reservedParameterNames(-1));

            int chipId = switch (ReturnMoreblockManager.getMbType(nameWithType)) {
                case "String" -> R.id.radio_mb_type_string;
                case "double" -> R.id.radio_mb_type_number;
                case "boolean" -> R.id.radio_mb_type_boolean;
                case "a|Map" -> R.id.radio_mb_type_map;
                case "l|List String" -> R.id.radio_mb_type_liststring;
                case "l|List Map" -> R.id.radio_mb_type_listmap;
                case "v|View" -> R.id.radio_mb_type_view;
                default -> R.id.radio_mb_type_void;
            };
            // A custom return type has no chip: keep it in the name, where it is written, so saving never drops it.
            boolean customReturnType = chipId == R.id.radio_mb_type_void && !ReturnMoreblockManager.getMbType(nameWithType).equals("void");
            binding.edName.setText(customReturnType ? nameWithType : ReturnMoreblockManager.getMbName(nameWithType));
            binding.radioMbType.check(chipId);
            block = newPreviewBlock();
            updateParametersSection();
            refreshPreview();
        } finally {
            loading = false;
        }
        lastValidity = isValid();
        if (listener != null) {
            listener.onValidityChanged(lastValidity);
        }
    }

    public void setEditedMoreBlock(String nameWithType, String spec, int uses) {
        editedName = nameWithType;
        editedSpec = spec;
        editedUses = uses;
        updateCodePreview();
    }

    /** @return whether the builder no longer describes the MoreBlock {@code (nameWithType, spec)}. */
    public boolean isModified(String nameWithType, String spec) {
        Pair<String, String> current = getBlockInformation();
        return !current.first.equals(nameWithType) || !current.second.equals(spec);
    }

    /** @return the selected return type, as stored in a MoreBlock name (e.g. {@code String}, {@code a|Map}); "void" for none. */
    public String getReturnType() {
        return ReturnMoreblockManager.getMbType(getBlockInformation().first);
    }

    public Draft captureDraft() {
        int checked = binding.radioMbType.getCheckedChipId();
        String key = CHIP_KEYS[0];
        for (int i = 0; i < CHIP_IDS.length; i++) {
            if (CHIP_IDS[i] == checked) {
                key = CHIP_KEYS[i];
            }
        }
        return new Draft(Helper.getText(binding.edName), key, List.copyOf(params), isAdvancedExpanded());
    }

    public void applyDraft(Draft draft) {
        loading = true;
        try {
            params.clear();
            params.addAll(draft.params());
            adapter.notifyDataSetChanged();
            advancedNameValidator.a(reservedParameterNames(-1));
            binding.edName.setText(draft.name());
            for (int i = 0; i < CHIP_KEYS.length; i++) {
                if (CHIP_KEYS[i].equals(draft.returnTypeKey())) {
                    binding.radioMbType.check(CHIP_IDS[i]);
                }
            }
            setAdvancedExpanded(draft.advancedExpanded());
            block = newPreviewBlock();
            updateParametersSection();
            refreshPreview();
        } finally {
            loading = false;
        }
        lastValidity = isValid();
        if (listener != null) {
            listener.onValidityChanged(lastValidity);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Parameter list
    // ---------------------------------------------------------------------------------------------------------

    private class ParameterAdapter extends RecyclerView.Adapter<ParameterAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(MakeBlockParamItemBinding.inflate(activity.getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Param param = params.get(position);
            String title = param.name();
            holder.item.paramName.setText(title);
            holder.item.paramType.setText(param.isLabel()
                    ? Helper.getResString(R.string.make_block_label_title)
                    : VariableItemView.typeTitle(activity, param.type()));
            holder.item.dragHandle.setContentDescription(Helper.getResString(R.string.make_block_cd_drag, title));
            holder.item.paramEdit.setContentDescription(Helper.getResString(R.string.make_block_cd_edit, title));
            holder.item.paramDelete.setContentDescription(Helper.getResString(R.string.make_block_cd_delete, title));
        }

        @Override
        public int getItemCount() {
            return params.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            final MakeBlockParamItemBinding item;

            @SuppressLint("ClickableViewAccessibility")
            ViewHolder(MakeBlockParamItemBinding item) {
                super(item.getRoot());
                this.item = item;
                item.dragHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        touchHelper.startDrag(this);
                    }
                    return false;
                });
                item.paramEdit.setOnClickListener(v -> {
                    if (!mB.a() && getBindingAdapterPosition() >= 0) {
                        showParameterEditor(getBindingAdapterPosition());
                    }
                });
                item.paramDelete.setOnClickListener(v -> {
                    if (!mB.a()) {
                        removeParameter(getBindingAdapterPosition());
                    }
                });
                // Reordering without a drag, for screen readers and switch access.
                ViewCompat.addAccessibilityAction(item.getRoot(), Helper.getResString(R.string.make_block_move_up), (view, arguments) -> {
                    shift(-1);
                    return true;
                });
                ViewCompat.addAccessibilityAction(item.getRoot(), Helper.getResString(R.string.make_block_move_down), (view, arguments) -> {
                    shift(1);
                    return true;
                });
            }

            private void shift(int by) {
                int from = getBindingAdapterPosition();
                if (move(from, from + by)) {
                    adapter.notifyItemRangeChanged(Math.min(from, from + by), 2);
                    onParametersChanged();
                }
            }
        }
    }
}
