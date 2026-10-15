package pro.sketchware.activities.editor.view;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import a.a.a.cC;
import a.a.a.jC;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.appcompat.ManageAppCompatActivity;
import pro.sketchware.activities.editor.view.xml.XmlDiagnostics;
import pro.sketchware.activities.editor.view.xml.XmlEditorUi;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.code.CodeColorPicker;
import pro.sketchware.databinding.ViewCodeEditorBinding;
import pro.sketchware.evolve.EvolveSheet;
import pro.sketchware.evolve.ShareSource;
import pro.sketchware.evolve.SharedKind;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.relativelayout.CircularDependencyDetector;

public class ViewCodeEditorActivity extends BaseAppCompatActivity implements EvolveSheet.Host {
    private ViewCodeEditorBinding binding;
    private CodeEditor editor;
    private XmlEditorUi ui;

    private SharedPreferences prefs;

    private String sc_id;

    private String content;

    private boolean isEdited = false;

    private ProjectFileBean projectFile;
    private ProjectLibraryBean projectLibrary;

    private InjectRootLayoutManager rootLayoutManager;

    private final OnBackPressedCallback onBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (ui != null && ui.hideFind()) {
                        return;
                    }
                    if (isContentModified()) {
                        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(ViewCodeEditorActivity.this);
                        dialog.setIcon(R.drawable.ic_warning_96dp);
                        dialog.setTitle(Helper.getResString(R.string.common_word_warning));
                        dialog.setMessage(Helper.getResString(
                                R.string
                                        .src_code_editor_unsaved_changes_dialog_warning_message));

                        dialog.setPositiveButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
                            v.dismiss();
                            exitWithEditedContent();
                            finish();
                        });

                        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel),
                                null);
                        dialog.show();
                    } else {
                        if (isEdited) {
                            exitWithEditedContent();
                            finish();
                            return;
                        }
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ViewCodeEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs = getSharedPreferences("dce", Activity.MODE_PRIVATE);
        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }
        rootLayoutManager = new InjectRootLayoutManager(sc_id);
        String title = getIntent().getStringExtra("title");
        projectFile = jC.b(sc_id).b(title);
        projectLibrary = jC.c(sc_id).c();
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setTitle(R.string.xml_editor_title);
        getSupportActionBar().setSubtitle(title);
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (onBackPressedCallback.isEnabled()) {
                onBackPressedCallback.handleOnBackPressed();
            }
        });
        content = getIntent().getStringExtra("content");
        editor = binding.editor;
        editor.setTypefaceText(EditorUtils.getTypeface(this));
        editor.setTextSize(14);
        editor.setText(content);
        EditorUtils.loadXmlConfig(editor);
        editor.setWordwrap(prefs.getBoolean("xml_wrap", false));
        applyInsets();
        ui = new XmlEditorUi(binding);
        ui.attach(this::onTextChanged, this::format);
        if (projectFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                && projectLibrary.isEnabled()) {
            setNote(getString(R.string.xml_note_appcompat));
        }
        binding.close.setOnClickListener(v -> {
            prefs.edit().putInt("note_" + sc_id, 1).apply();
            setNote(null);
        });
        binding.noteCard.setOnClickListener(v -> toAppCompat());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.xml_editor_menu, menu);
        menu.findItem(R.id.xml_appcompat).setVisible(projectFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                && projectLibrary.isEnabled());
        return true;
    }

    /** Undo, Redo and Save are only live when they would do something; word wrap shows its state. */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        setEnabledWithIcon(menu.findItem(R.id.xml_undo), editor.canUndo());
        setEnabledWithIcon(menu.findItem(R.id.xml_redo), editor.canRedo());
        setEnabledWithIcon(menu.findItem(R.id.xml_save), isContentModified());
        menu.findItem(R.id.xml_wrap).setChecked(editor.isWordwrap());
        return super.onPrepareOptionsMenu(menu);
    }

    private static void setEnabledWithIcon(MenuItem item, boolean enabled) {
        if (item == null) return;
        item.setEnabled(enabled);
        if (item.getIcon() != null) {
            item.getIcon().mutate().setAlpha(enabled ? 255 : 97);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.xml_undo) {
            editor.undo();
        } else if (id == R.id.xml_redo) {
            editor.redo();
        } else if (id == R.id.xml_save) {
            save();
        } else if (id == R.id.xml_find) {
            ui.showFind();
        } else if (id == R.id.xml_color) {
            CodeColorPicker.show(this, editor, false, sc_id);
        } else if (id == R.id.xml_format) {
            format();
        } else if (id == R.id.xml_preview) {
            toLayoutPreview();
        } else if (id == R.id.xml_appcompat) {
            toAppCompat();
        } else if (id == R.id.xml_wrap) {
            boolean wrap = !editor.isWordwrap();
            editor.setWordwrap(wrap);
            prefs.edit().putBoolean("xml_wrap", wrap).apply();
            invalidateOptionsMenu();
        } else if (id == R.id.xml_reload_colors) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else if (id == R.id.xml_evolve) {
            if (getSupportFragmentManager().findFragmentByTag("evolve") == null) {
                EvolveSheet.newInstance().show(getSupportFragmentManager(), "evolve");
            }
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /** The text changed: refresh what depends on it, once typing pauses (the menu is rebuilt, so not per keystroke). */
    private void onTextChanged() {
        binding.getRoot().removeCallbacks(refreshChrome);
        binding.getRoot().postDelayed(refreshChrome, 120);
    }

    private final Runnable refreshChrome = this::refreshChrome;

    /** Undo, Redo, Save, and the "unsaved" mark under the title. */
    private void refreshChrome() {
        invalidateOptionsMenu();
        String title = getIntent().getStringExtra("title");
        if (getSupportActionBar() != null && title != null) {
            getSupportActionBar().setSubtitle(isContentModified() ? getString(R.string.xml_editor_subtitle_modified, title) : title);
        }
    }

    private void format() {
        switch (ui.format()) {
            case DONE -> SketchwareUtil.toast(getString(R.string.xml_format_done));
            case UNCHANGED -> SketchwareUtil.toast(getString(R.string.xml_format_unchanged));
            case INVALID -> {
                SketchwareUtil.toastError(getString(R.string.xml_format_invalid));
                ui.goToError();
            }
        }
    }

    /** Keeps the bottom bars above the keyboard and the navigation bar (the window is edge to edge). */
    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            return windowInsets;
        });
    }

    @Override
    public void onDestroy() {
        binding.getRoot().removeCallbacks(refreshChrome);
        if (ui != null) {
            ui.detach();
        }
        super.onDestroy();
    }

    @Override
    public java.util.EnumSet<SharedKind> kinds() {
        return java.util.EnumSet.of(SharedKind.VIEW);
    }

    /** The layout as it is in the editor right now, saved or not. */
    @Override
    public java.util.List<ShareSource> shareSources() {
        java.util.List<ShareSource> sources = new java.util.ArrayList<>();
        String xml = editor.getText().toString();
        if (!xml.isBlank()) {
            String title = getIntent().getStringExtra("title");
            sources.add(ShareSource.ofXml(getString(R.string.evolve_origin_xml), title == null ? "layout" : title, xml));
        }
        return sources;
    }

    /** Never changes the layout without asking: replace, insert at the cursor (both undoable), or just copy. */
    @Override
    public void pickXml(String name, String xml) {
        String[] choices = {
                getString(R.string.evolve_xml_replace),
                getString(R.string.evolve_xml_insert),
                getString(R.string.evolve_xml_copy)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.evolve_xml_title, name))
                .setItems(choices, (dialog, which) -> {
                    Content text = editor.getText();
                    if (which == 0) {
                        int lastLine = text.getLineCount() - 1;
                        text.replace(0, 0, lastLine, text.getColumnCount(lastLine), xml);
                        SketchwareUtil.toast(getString(R.string.evolve_xml_applied));
                    } else if (which == 1) {
                        int line = editor.getCursor().getLeftLine();
                        int column = editor.getCursor().getLeftColumn();
                        text.replace(line, column, line, column, xml);
                        SketchwareUtil.toast(getString(R.string.evolve_xml_applied));
                    } else {
                        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                                .setPrimaryClip(ClipData.newPlainText(name, xml));
                        SketchwareUtil.toast(getString(R.string.evolve_xml_copied));
                    }
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void toAppCompat() {
        var intent = new Intent(getApplicationContext(), ManageAppCompatActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("file_name", getIntent().getStringExtra("title"));
        startActivity(intent);
    }

    private void toLayoutPreview() {
        var intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra("xml", editor.getText().toString());
        startActivity(intent);
    }

    private void setNote(String note) {
        if (prefs.getInt("note_" + sc_id, 0) < 1 && (note != null && !note.isEmpty())) {
            binding.noteCard.setVisibility(View.VISIBLE);
        } else {
            binding.noteCard.setVisibility(View.GONE);
            return;
        }
        binding.noteCard.setVisibility(View.VISIBLE);
        binding.note.setText(note);
        binding.note.setSelected(true);
    }

    private void save() {
        try {
            if (isContentModified()) {
                // The layout has to be well-formed before anything else is looked at.
                XmlDiagnostics.Result syntax = ui.checkNow();
                if (!syntax.valid()) {
                    SketchwareUtil.toastError(getString(R.string.xml_save_invalid, syntax.line() + 1, syntax.message()));
                    ui.goToError();
                    return;
                }
                // Parse content to validate circular dependencies
                var parser = new ViewBeanParser(editor.getText().toString());
                parser.setSkipRoot(true);

                var parsedLayout = parser.parse();
                for (ViewBean viewBean : parsedLayout) {
                    CircularDependencyDetector detector = new CircularDependencyDetector(parsedLayout, viewBean);
                    for (String attr : viewBean.parentAttributes.keySet()) {
                        String targetId = viewBean.parentAttributes.get(attr);
                        if (!detector.isLegalAttribute(targetId, attr)) {
                            SketchwareUtil.toastError(getString(R.string.xml_save_circular, viewBean.name));
                            return;
                        }
                    }
                }

                // Update content only after validation
                content = editor.getText().toString();
                if (!isEdited) {
                    isEdited = true;
                }
                SketchwareUtil.toast(getString(R.string.xml_saved));
                refreshChrome();
            } else {
                SketchwareUtil.toast(getString(R.string.xml_nothing_to_save));
            }
        } catch (Exception e) {
            SketchwareUtil.toastError(e.toString());
        }

    }

    private boolean isContentModified() {
        return !content.equals(editor.getText().toString());
    }

    private void exitWithEditedContent() {
        String filename = getIntent().getStringExtra("title");
        try {
            var parser = new ViewBeanParser(content);
            parser.setSkipRoot(true);
            var parsedLayout = parser.parse();
            var root = parser.getRootAttributes();
            rootLayoutManager.set(filename, InjectRootLayoutManager.toRoot(root));
            HistoryViewBean bean = new HistoryViewBean();
            bean.actionOverride(parsedLayout, jC.a(sc_id).d(filename));
            var cc = cC.c(sc_id);
            if (!cc.c.containsKey(filename)) {
                cc.e(filename);
            }
            cc.a(filename);
            cc.a(filename, bean);
            // Replace the view beans with the parsed layout
            jC.a(sc_id).c.put(filename, parsedLayout);
            setResult(RESULT_OK);
        } catch (Exception e) {
            SketchwareUtil.toastError(e.toString());
        }
    }
}
