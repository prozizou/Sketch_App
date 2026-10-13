package com.besome.sketch.editor.makeblock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.editor.makeblock.MoreBlockSpec.Draft;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import a.a.a.bB;
import a.a.a.jC;
import a.a.a.mB;
import mod.hey.studios.moreblock.ReturnMoreblockManager;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;

public class MakeBlockActivity extends BaseAppCompatActivity {

    private static final String DRAFTS = "moreblock_drafts";
    private static final String STATE_DRAFT = "draft";
    private static final long DRAFT_SAVE_DELAY_MS = 400;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveDraft = this::persistDraft;
    private String sc_id;
    private ProjectFileBean project;
    private MoreBlockBuilderView makeBlock;
    private MaterialButton createButton;
    private MaterialToolbar toolbar;
    /** Name (with return type) of the MoreBlock being edited, null when creating one. */
    private String editName;
    private String editSpec;
    /** True when the edited MoreBlock is used: its calls can't change shape, so its return type stays. */
    private boolean editTypeLocked;
    /** Set once the result is on its way, so a second tap can't create the MoreBlock twice. */
    private boolean submitted;
    private boolean finishingWithoutDraft;

    private void goBackDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.logic_editor_more_block_dialog_message_confirm_goback));
        dialog.setIcon(R.drawable.exit_96);
        dialog.setMessage(Helper.getResString(R.string.logic_editor_more_block_dialog_description_goback));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_goback), (v, which) -> {
            if (!mB.a()) {
                v.dismiss();
                discardDraftAndFinish();
            }

        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void discardDraftAndFinish() {
        finishingWithoutDraft = true;
        handler.removeCallbacks(saveDraft);
        drafts().edit().remove(draftKey()).apply();
        finish();
    }

    @Override
    public void onBackPressed() {
        if (submitted) {
            return;
        }
        if (makeBlock.isEmpty() || editName != null && !makeBlock.isModified(editName, editSpec)) {
            discardDraftAndFinish();
        } else {
            goBackDialog();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        if (!isStoragePermissionGranted()) {
            finish();
        }

        setContentView(R.layout.make_block);
        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
            project = getIntent().getParcelableExtra("project_file");
            editName = getIntent().getStringExtra("edit_name");
            editSpec = getIntent().getStringExtra("edit_spec");
            editTypeLocked = getIntent().getBooleanExtra("edit_type_locked", false);
        } else {
            sc_id = savedInstanceState.getString("sc_id");
            project = savedInstanceState.getParcelable("project_file");
            editName = savedInstanceState.getString("edit_name");
            editSpec = savedInstanceState.getString("edit_spec");
            editTypeLocked = savedInstanceState.getBoolean("edit_type_locked");
        }

        applyWindowInsets();

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(Helper.getResString(editName == null
                ? R.string.logic_editor_more_block_actionbar_title_create_more_block
                : R.string.logic_editor_more_block_actionbar_title_edit_more_block));
        toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        toolbar.inflateMenu(R.menu.moreblock_menu);
        MenuItem createItem = toolbar.getMenu().findItem(R.id.moreblock_create);
        createItem.setTitle(Helper.getResString(editName == null ? R.string.common_word_create : R.string.common_word_save));
        createItem.setEnabled(false);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.moreblock_create) {
                submit();
                return true;
            }
            return false;
        });

        createButton = findViewById(R.id.btn_create);
        createButton.setText(editName == null ? R.string.common_word_create : R.string.common_word_save);
        createButton.setOnClickListener(v -> submit());

        ArrayList<String> takenNames = jC.a(sc_id).a(project);
        if (editName != null) {
            // The edited MoreBlock may keep its own name.
            takenNames.remove(editName);
            takenNames.remove(ReturnMoreblockManager.getMbName(editName));
        }
        makeBlock = new MoreBlockBuilderView(this, takenNames);
        makeBlock.setListener(new MoreBlockBuilderView.Listener() {
            @Override
            public void onValidityChanged(boolean valid) {
                setCreateEnabled(valid);
            }

            @Override
            public void onChanged() {
                handler.removeCallbacks(saveDraft);
                handler.postDelayed(saveDraft, DRAFT_SAVE_DELAY_MS);
            }
        });
        ((android.widget.FrameLayout) findViewById(R.id.makeblock_view)).addView(makeBlock);
        if (editName != null && editSpec != null) {
            makeBlock.setEditedMoreBlock(editName, editSpec, getIntent().getIntExtra("edit_uses", 0));
            makeBlock.loadMoreBlock(editName, editSpec);
        }
        restoreDraft(savedInstanceState);
        setCreateEnabled(makeBlock.isValid());
    }

    /** Keeps the form and the Create bar clear of the system bars and, when it is open, of the keyboard. */
    private void applyWindowInsets() {
        View root = findViewById(R.id.make_block_root);
        View bottomBar = findViewById(R.id.btn_create);
        View bottomBarContainer = (View) bottomBar.getParent();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            // With the keyboard up there is little room: the toolbar's Create stays available meanwhile.
            bottomBarContainer.setVisibility(windowInsets.isVisible(WindowInsetsCompat.Type.ime()) ? View.GONE : View.VISIBLE);
            return windowInsets;
        });
    }

    private void setCreateEnabled(boolean enabled) {
        createButton.setEnabled(enabled && !submitted);
        toolbar.getMenu().findItem(R.id.moreblock_create).setEnabled(enabled && !submitted);
    }

    private void submit() {
        if (submitted || mB.a()) {
            return;
        }
        if (!makeBlock.isValid()) {
            bB.b(this, Helper.getResString(R.string.make_block_invalid_form), Toast.LENGTH_SHORT).show();
            makeBlock.focusName();
            return;
        }
        if (editTypeLocked && !makeBlock.getReturnType().equals(ReturnMoreblockManager.getMbType(editName))) {
            bB.b(this, Helper.getResString(R.string.logic_editor_more_block_message_type_locked), Toast.LENGTH_LONG).show();
            return;
        }
        submitted = true;
        setCreateEnabled(false);
        Intent intent = new Intent();
        if (editName != null) {
            intent.putExtra("edit_name", editName);
        }
        Pair<String, String> blockInformation = makeBlock.getBlockInformation();
        intent.putExtra("block_name", blockInformation.first);
        intent.putExtra("block_spec", blockInformation.second);
        setResult(RESULT_OK, intent);
        discardDraftAndFinish();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Draft
    // ---------------------------------------------------------------------------------------------------------

    private SharedPreferences drafts() {
        return getSharedPreferences(DRAFTS, Context.MODE_PRIVATE);
    }

    /** One draft per project, and per edited MoreBlock (or one for a new one). */
    private String draftKey() {
        return sc_id + "|" + (editName == null ? "" : editName);
    }

    private void persistDraft() {
        if (finishingWithoutDraft || submitted || makeBlock == null) {
            return;
        }
        SharedPreferences.Editor editor = drafts().edit();
        Draft draft = makeBlock.captureDraft();
        boolean nothingWorthKeeping = editName == null ? makeBlock.isEmpty() : !makeBlock.isModified(editName, editSpec);
        if (nothingWorthKeeping) {
            editor.remove(draftKey());
        } else {
            editor.putString(draftKey(), draft.encode());
        }
        editor.apply();
    }

    private void restoreDraft(Bundle savedInstanceState) {
        String encoded = savedInstanceState != null ? savedInstanceState.getString(STATE_DRAFT) : null;
        if (encoded == null) {
            encoded = drafts().getString(draftKey(), null);
        }
        Draft draft = Draft.decode(encoded);
        if (draft == null || draft.equals(makeBlock.captureDraft())) {
            return;
        }
        makeBlock.applyDraft(draft);
        Snackbar.make(findViewById(R.id.makeblock_view), R.string.make_block_draft_restored, Snackbar.LENGTH_LONG)
                .setAction(R.string.make_block_draft_discard, v -> {
                    drafts().edit().remove(draftKey()).apply();
                    if (editName != null && editSpec != null) {
                        makeBlock.loadMoreBlock(editName, editSpec);
                    } else {
                        makeBlock.applyDraft(new Draft("", "void", List.of(), false));
                    }
                })
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(saveDraft);
        persistDraft();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(saveDraft);
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isStoragePermissionGranted()) {
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("sc_id", sc_id);
        outState.putParcelable("project_file", project);
        outState.putString("edit_name", editName);
        outState.putString("edit_spec", editSpec);
        outState.putBoolean("edit_type_locked", editTypeLocked);
        if (makeBlock != null && !submitted) {
            outState.putString(STATE_DRAFT, makeBlock.captureDraft().encode());
        }
        super.onSaveInstanceState(outState);
    }
}
