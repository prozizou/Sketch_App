package mod.hey.studios.project.proguard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;

import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.hey.studios.code.SrcCodeEditor;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageProguardBinding;

/**
 * Code shrinking, made simple: three levels (off, smaller, smaller and protected) cover what almost everyone
 * needs. The engine, the rules file, local libraries and the crash map stay available under "Advanced options".
 */
public class ManageProguardActivity extends BaseAppCompatActivity
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private ProguardHandler pg;

    private ManageProguardBinding binding;

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == binding.lnPgRules.getId()) {
            Intent intent = new Intent(this, SrcCodeEditor.class);
            intent.putExtra("title", "proguard-rules.pro");
            intent.putExtra("content", pg.getCustomProguardRules());
            startActivity(intent);
        } else if (id == binding.lnPgFm.getId()) {
            fmDialog();
        } else if (id == binding.lnAdvanced.getId()) {
            boolean show = binding.lnAdvancedContent.getVisibility() != View.VISIBLE;
            binding.lnAdvancedContent.setVisibility(show ? View.VISIBLE : View.GONE);
            binding.imgAdvanced.animate().rotation(show ? 180f : 0f).setDuration(150).start();
        } else if (id == binding.cardModeOff.getId()) {
            chooseMode(OptimizationMode.OFF);
        } else if (id == binding.cardModeSafe.getId()) {
            chooseMode(OptimizationMode.SAFE);
        } else if (id == binding.cardModeMax.getId()) {
            chooseMode(OptimizationMode.MAX);
        }
    }

    private void chooseMode(OptimizationMode mode) {
        pg.setMode(mode);
        showMode();
        // Choosing a level can change the engine and the crash map, so the advanced switches follow.
        binding.r8Enabled.setOnCheckedChangeListener(null);
        binding.swPgDebug.setOnCheckedChangeListener(null);
        binding.r8Enabled.setChecked(pg.isR8Enabled());
        binding.swPgDebug.setChecked(pg.isDebugFilesEnabled());
        binding.r8Enabled.setOnCheckedChangeListener(this);
        binding.swPgDebug.setOnCheckedChangeListener(this);
    }

    private void showMode() {
        OptimizationMode mode = pg.getMode();
        select(binding.cardModeOff, mode == OptimizationMode.OFF);
        select(binding.cardModeSafe, mode == OptimizationMode.SAFE);
        select(binding.cardModeMax, mode == OptimizationMode.MAX);
        binding.tvHint.setText(switch (mode) {
            case OFF -> R.string.code_shrinker_hint_off;
            case SAFE -> R.string.code_shrinker_hint_safe;
            case MAX -> R.string.code_shrinker_hint_max;
        });
    }

    private void select(MaterialCardView card, boolean selected) {
        float density = getResources().getDisplayMetrics().density;
        card.setChecked(selected);
        card.setStrokeWidth(Math.round((selected ? 2 : 1) * density));
        card.setStrokeColor(MaterialColors.getColor(card, selected
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOutlineVariant));
    }

    private void fmDialog() {
        ManageLocalLibrary mll = new ManageLocalLibrary(getIntent().getStringExtra("sc_id"));

        String[] libraries = new String[mll.list.size()];
        boolean[] enabledLibraries = new boolean[mll.list.size()];

        for (int i = 0; i < mll.list.size(); i++) {
            HashMap<String, Object> current = mll.list.get(i);

            Object name = current.get("name");
            if (name instanceof String) {
                libraries[i] = (String) name;
                enabledLibraries[i] = pg.libIsProguardFMEnabled(libraries[i]);
            } else {
                libraries[i] = "(broken library configuration)";
                enabledLibraries[i] = false;
            }
        }

        MaterialAlertDialogBuilder bld = new MaterialAlertDialogBuilder(this);
        bld.setTitle("Select Local libraries");
        bld.setMultiChoiceItems(
                libraries,
                enabledLibraries,
                (dialog, which, isChecked) -> enabledLibraries[which] = isChecked);
        bld.setPositiveButton(
                R.string.common_word_save,
                (dialog, which) -> {
                    ArrayList<String> finalList = new ArrayList<>();

                    for (int i = 0; i < libraries.length; i++) {
                        if (enabledLibraries[i]) {
                            finalList.add(libraries[i]);
                        }
                    }

                    pg.setProguardFMLibs(finalList);
                });
        bld.setNegativeButton(R.string.common_word_cancel, null);
        bld.create().show();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        int id = buttonView.getId();
        if (id == binding.r8Enabled.getId()) {
            pg.setR8Enabled(isChecked);
        } else if (id == binding.swPgDebug.getId()) {
            pg.setDebugEnabled(isChecked);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageProguardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialize();
        initializeLogic();
    }

    private void initialize() {
        binding.lnPgRules.setOnClickListener(this);
        binding.lnPgFm.setOnClickListener(this);
        binding.lnAdvanced.setOnClickListener(this);
        binding.cardModeOff.setOnClickListener(this);
        binding.cardModeSafe.setOnClickListener(this);
        binding.cardModeMax.setOnClickListener(this);
    }

    private void initializeLogic() {
        _initToolbar();
        pg = new ProguardHandler(getIntent().getStringExtra("sc_id"));
        showMode();
        binding.swPgDebug.setChecked(pg.isDebugFilesEnabled());
        binding.r8Enabled.setChecked(pg.isR8Enabled());
        binding.r8Enabled.setOnCheckedChangeListener(this);
        binding.swPgDebug.setOnCheckedChangeListener(this);
    }

    private void _initToolbar() {
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.design_drawer_menu_proguard);
        binding.toolbar.setNavigationOnClickListener(view -> onBackPressed());
    }
}
