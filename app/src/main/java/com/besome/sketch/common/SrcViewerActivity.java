package com.besome.sketch.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.SrcCodeBean;
import com.besome.sketch.ctrls.CommonSpinnerItem;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import a.a.a.ProjectBuilder;
import a.a.a.bB;
import a.a.a.hC;
import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.SrcViewerBinding;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class SrcViewerActivity extends BaseAppCompatActivity {

    private SrcViewerBinding binding;
    private String sc_id;
    private ArrayList<SrcCodeBean> sourceCodeBeans;

    private String currentFileName;
    private int editorFontSize = 12;

    private static final String MANIFEST_NAME = "AndroidManifest.xml";
    /** Generated helper classes that are replaced when a file of the same name exists in the project's Java folder. */
    private static final Set<String> REPLACEABLE_HELPERS = new HashSet<>(Arrays.asList(
            "SketchwareUtil.java", "FileUtil.java", "RequestNetwork.java", "RequestNetworkController.java",
            "BluetoothConnect.java", "BluetoothController.java", "GoogleMapController.java"));
    private final FilePathUtil filePathUtil = new FilePathUtil();
    /** Generated Java files the user may take over by hand (activities and the helper classes above). */
    private final Set<String> replaceableJava = new HashSet<>();
    /** Names of replaceable files that currently have a hand-edited version. */
    private final Set<String> overriddenJava = new HashSet<>();
    private boolean manifestOverridden;
    private boolean returningFromEditor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = SrcViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        currentFileName = getIntent().hasExtra("current") ? getIntent().getStringExtra("current") : "";
        sc_id = savedInstanceState != null ? savedInstanceState.getString("sc_id") : getIntent().getStringExtra("sc_id");

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        configureEditor();

        binding.changeFontSize.setOnClickListener(v -> showChangeFontSizeDialog());
        binding.editFile.setOnClickListener(v -> editCurrentFile());
        binding.restoreGenerated.setOnClickListener(v -> confirmRestoreGenerated());

        binding.filesListSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SrcCodeBean bean = sourceCodeBeans.get(position);
                binding.editor.setText(bean.source);
                currentFileName = bean.srcFileName;
                if (currentFileName.endsWith(".xml")) {
                    EditorUtils.loadXmlConfig(binding.editor);
                } else {
                    EditorUtils.loadJavaConfig(binding.editor);
                }
                updateEditControls();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        loadSources();
    }

    private void loadSources() {
        k(); // show loading

        new Thread(() -> {
            var yq = new yq(getBaseContext(), sc_id);
            var fileManager = jC.b(sc_id);
            var dataManager = jC.a(sc_id);
            var libraryManager = jC.c(sc_id);
            yq.a(libraryManager, fileManager, dataManager, a.a.a.yq.ExportType.SOURCE_CODE_VIEWING);
            ProjectBuilder builder = new ProjectBuilder(this, yq);
            builder.buildBuiltInLibraryInformation();
            sourceCodeBeans = yq.a(fileManager, dataManager, builder.getBuiltInLibraryManager());
            collectReplaceableFiles(fileManager);

            try {
                runOnUiThread(() -> {
                    if (sourceCodeBeans == null) {
                        bB.b(getApplicationContext(), Helper.getResString(R.string.common_error_unknown), bB.TOAST_NORMAL).show();
                    } else {
                        binding.filesListSpinner.setAdapter(new FilesListSpinnerAdapter());
                        for (SrcCodeBean src : sourceCodeBeans) {
                            if (src.srcFileName.equals(currentFileName)) {
                                binding.filesListSpinner.setSelection(sourceCodeBeans.indexOf(src));
                                break;
                            }
                        }
                        binding.editor.setText(sourceCodeBeans.get(binding.filesListSpinner.getSelectedItemPosition()).source);
                        h(); // hide loading
                    }
                });
            } catch (Exception ignored) {
                // May occur if the activity is killed
            }
        }).start();
    }

    /**
     * Finds out which generated files the user may edit by hand, which of them already were, and lists the hand-edited
     * Java files too (the generator leaves a Java file out when the project's Java folder has a file with that name).
     */
    private void collectReplaceableFiles(hC fileManager) {
        replaceableJava.clear();
        overriddenJava.clear();
        for (ProjectFileBean projectFile : fileManager.b()) {
            replaceableJava.add(projectFile.getJavaName());
        }
        replaceableJava.addAll(REPLACEABLE_HELPERS);

        File[] customFiles = new File(filePathUtil.getPathJava(sc_id)).listFiles();
        if (customFiles != null && sourceCodeBeans != null) {
            for (File file : customFiles) {
                if (file.isFile() && replaceableJava.contains(file.getName())) {
                    overriddenJava.add(file.getName());
                    sourceCodeBeans.add(new SrcCodeBean(file.getName(), FileUtil.readFile(file.getAbsolutePath())));
                }
            }
        }
        manifestOverridden = FileUtil.isExistFile(filePathUtil.getPathManifestOverride(sc_id));
    }

    private boolean isCurrentFileOverridden() {
        return MANIFEST_NAME.equals(currentFileName) ? manifestOverridden : overriddenJava.contains(currentFileName);
    }

    private void updateEditControls() {
        boolean editable = MANIFEST_NAME.equals(currentFileName) || replaceableJava.contains(currentFileName);
        binding.editFile.setVisibility(editable ? View.VISIBLE : View.GONE);
        binding.overrideBanner.setVisibility(editable && isCurrentFileOverridden() ? View.VISIBLE : View.GONE);
    }

    private String targetPathOf(String fileName) {
        return MANIFEST_NAME.equals(fileName)
                ? filePathUtil.getPathManifestOverride(sc_id)
                : filePathUtil.getPathJava(sc_id) + File.separator + fileName;
    }

    private void editCurrentFile() {
        if (isCurrentFileOverridden()) {
            openInEditor(currentFileName);
            return;
        }
        String fileName = currentFileName;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.src_edit_title)
                .setMessage(getString(R.string.src_edit_message, fileName))
                .setPositiveButton(R.string.src_edit_action, (dialog, which) -> {
                    int position = binding.filesListSpinner.getSelectedItemPosition();
                    String target = targetPathOf(fileName);
                    new File(target).getParentFile().mkdirs();
                    FileUtil.writeFile(target, sourceCodeBeans.get(position).source);
                    openInEditor(fileName);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openInEditor(String fileName) {
        returningFromEditor = true;
        Intent intent = new Intent(this, SrcCodeEditor.class);
        intent.putExtra(fileName.endsWith(".xml") ? "xml" : "java", "");
        intent.putExtra("title", fileName);
        intent.putExtra("content", targetPathOf(fileName));
        startActivity(intent);
    }

    private void confirmRestoreGenerated() {
        String fileName = currentFileName;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.src_restore_title)
                .setMessage(getString(R.string.src_restore_message, fileName))
                .setPositiveButton(R.string.src_restore_action, (dialog, which) -> {
                    FileUtil.deleteFile(targetPathOf(fileName));
                    loadSources();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (returningFromEditor) {
            returningFromEditor = false;
            loadSources();
        }
    }

    private void configureEditor() {
        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setEditable(false);
        binding.editor.setTextSize(editorFontSize);
        binding.editor.setPinLineNumber(true);

        if (currentFileName.endsWith(".xml")) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else {
            EditorUtils.loadJavaConfig(binding.editor);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    private void showChangeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(8);
        picker.setMaxValue(30);
        picker.setWrapSelectorWheel(false);
        picker.setValue(editorFontSize);

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select font size")
                .setIcon(R.drawable.ic_mtrl_formattext)
                .setView(layout)
                .setPositiveButton("Apply", (dialog, which) -> {
                    editorFontSize = picker.getValue();
                    binding.editor.setTextSize(editorFontSize);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public class FilesListSpinnerAdapter extends BaseAdapter {

        private View getCustomSpinnerView(int position, View view, boolean isCurrentlyViewingFile) {
            CommonSpinnerItem spinnerItem = view != null ? (CommonSpinnerItem) view :
                    new CommonSpinnerItem(SrcViewerActivity.this);
            spinnerItem.a(sourceCodeBeans.get(position).srcFileName, isCurrentlyViewingFile);
            return spinnerItem;
        }

        @Override
        public int getCount() {
            return sourceCodeBeans.size();
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            boolean isCheckmarkVisible = binding.filesListSpinner.getSelectedItemPosition() == position;
            return getCustomSpinnerView(position, convertView, isCheckmarkVisible);
        }

        @Override
        public Object getItem(int position) {
            return sourceCodeBeans.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomSpinnerView(position, convertView, false);
        }
    }
}