package pro.sketchware.build.settings;

import static mod.hey.studios.build.BuildSettings.SETTING_ANDROID_JAR_PATH;
import static mod.hey.studios.build.BuildSettings.SETTING_CLASSPATH;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER_D8;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER_DX;
import static mod.hey.studios.build.BuildSettings.SETTING_ENABLE_LOGCAT;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION;
import static mod.hey.studios.build.BuildSettings.SETTING_NO_HTTP_LEGACY;
import static mod.hey.studios.build.BuildSettings.SETTING_NO_WARNINGS;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.export.ExportProjectActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.jC;
import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import dev.pranav.filepicker.SelectionMode;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.proguard.ManageProguardActivity;
import mod.hey.studios.project.proguard.ProguardHandler;
import pro.sketchware.R;
import pro.sketchware.build.BuildMemory;
import pro.sketchware.build.MemoryProfile;
import pro.sketchware.databinding.ActivityBuildSettingsBinding;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.settings.AppSettingsDialogs;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * A project's build settings: the dexer, the Java level, paths and diagnostics the compilers read from
 * {@link BuildSettings}, with a compatibility check that keeps settings that cannot build from being saved.
 * Replaces the former bottom sheet; the keys, values and defaults are unchanged.
 */
public class BuildSettingsActivity extends BaseAppCompatActivity {

    private ActivityBuildSettingsBinding binding;
    private String scId;
    private BuildSettings store;
    /** What is saved now, to tell whether the screen has unsaved changes. */
    private ProjectBuildConfig saved;
    private ProjectBuildConfig config;
    private BuildSettingsCheck.Project project;
    /** Set while the views are being filled from {@link #config}, so their listeners don't write back. */
    private boolean filling;

    public static Intent intent(android.content.Context context, String scId) {
        return new Intent(context, BuildSettingsActivity.class).putExtra("sc_id", scId);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityBuildSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        UI.addSystemWindowInsetToPadding(binding.toolbar, true, true, true, false);
        UI.addSystemWindowInsetToPadding(binding.scroll, true, false, true, false);
        UI.addSystemWindowInsetToPadding(binding.bottomBar, true, false, true, true);

        scId = getIntent().getStringExtra("sc_id");
        store = new BuildSettings(scId);
        saved = load(store);
        config = savedInstanceState != null && savedInstanceState.getSerializable("config") instanceof HashMap<?, ?> map
                ? fromMap(map) : saved;
        project = describeProject(scId);

        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.toolbar.setNavigationContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leave();
            }
        });

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
        int tab = savedInstanceState != null ? savedInstanceState.getInt("tab") : 0;
        TabLayout.Tab selected = binding.tabs.getTabAt(tab);
        if (selected != null) selected.select();
        showTab(tab);

        setUpListeners();
        setUpAdvancedRows();
        bind();

        binding.btnCancel.setOnClickListener(v -> leave());
        binding.btnSave.setOnClickListener(v -> save());
    }

    @Override
    public void onResume() {
        super.onResume();
        // The code shrinking screen may have changed its mode meanwhile.
        if (binding != null) setUpAdvancedRows();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("config", toMap(config));
        outState.putInt("tab", binding.tabs.getSelectedTabPosition());
    }

    private void showTab(int position) {
        binding.simplePage.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        binding.advancedPage.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
    }

    /* ------------------------------------------------------------------ settings ↔ views */

    private void setUpListeners() {
        binding.profileGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (filling || !checked) return;
            if (id == R.id.profile_debug) update(ProjectBuildConfig.Profile.DEBUG.applyTo(config));
            else if (id == R.id.profile_release) update(ProjectBuildConfig.Profile.RELEASE.applyTo(config));
            else bind(); // Custom is whatever the settings are; it only describes them.
        });
        binding.dexerGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (filling || !checked) return;
            update(config.withDexer(id == R.id.dexer_d8 ? SETTING_DEXER_D8 : SETTING_DEXER_DX));
        });
        binding.switchWarnings.setOnCheckedChangeListener((b, on) -> {
            if (!filling) update(config.withNoWarnings(!on));
        });
        binding.switchLogcat.setOnCheckedChangeListener((b, on) -> {
            if (!filling) update(config.withLogcat(on));
        });
        binding.switchHttpLegacy.setOnCheckedChangeListener((b, on) -> {
            if (!filling) update(config.withNoHttpLegacy(!on));
        });
        binding.androidJar.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!filling) update(config.withAndroidJar(s.toString()), false);
            }
        });
        binding.classpath.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!filling) update(config.withClasspath(s.toString().replace('\n', ':')), false);
            }
        });
        binding.tilAndroidJar.setEndIconOnClickListener(v -> pickFile(false));
        binding.tilClasspath.setEndIconOnClickListener(v -> pickFile(true));
        binding.restoreDefaults.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.build_settings_restore_defaults)
                .setMessage(R.string.build_settings_restore_confirm)
                .setPositiveButton(R.string.common_word_continue, (d, w) -> update(ProjectBuildConfig.DEFAULTS))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show());
    }

    private void update(ProjectBuildConfig next) {
        update(next, true);
    }

    /** @param rebindFields false while the user types in a text field, so the cursor isn't moved */
    private void update(ProjectBuildConfig next, boolean rebindFields) {
        config = next;
        if (rebindFields) {
            bind();
        } else {
            bindProfile();
            showCheck();
        }
    }

    /** Fills every view from {@link #config}. */
    private void bind() {
        filling = true;
        bindProfile();
        binding.dexerGroup.check(SETTING_DEXER_D8.equals(config.dexer()) ? R.id.dexer_d8 : R.id.dexer_dx);
        binding.dexerNote.setText(SETTING_DEXER_D8.equals(config.dexer())
                ? R.string.build_settings_dexer_d8_note : R.string.build_settings_dexer_dx_note);
        bindJavaVersions();
        binding.switchWarnings.setChecked(!config.noWarnings());
        binding.switchLogcat.setChecked(config.logcat());
        binding.switchHttpLegacy.setChecked(!config.noHttpLegacy());
        setTextKeepingCursor(binding.androidJar, config.androidJar());
        setTextKeepingCursor(binding.classpath, config.classpath().replace(':', '\n'));
        filling = false;
        showCheck();
    }

    private void bindProfile() {
        boolean wasBinding = filling;
        filling = true;
        ProjectBuildConfig.Profile profile = config.profile();
        binding.profileGroup.check(switch (profile) {
            case DEBUG -> R.id.profile_debug;
            case RELEASE -> R.id.profile_release;
            case CUSTOM -> R.id.profile_custom;
        });
        binding.profileDescription.setText(switch (profile) {
            case DEBUG -> R.string.build_settings_profile_debug_desc;
            case RELEASE -> R.string.build_settings_profile_release_desc;
            case CUSTOM -> R.string.build_settings_profile_custom_desc;
        });
        filling = wasBinding;
    }

    /** One chip per Java level; those the selected dexer can't build are shown but disabled. */
    private void bindJavaVersions() {
        binding.javaVersions.removeAllViews();
        List<String> possible = ProjectBuildConfig.javaVersionsFor(config.dexer());
        for (String version : ProjectBuildConfig.JAVA_VERSIONS) {
            Chip chip = new Chip(this, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setText(version.startsWith("1.") ? "Java " + version.substring(2) : "Java " + version);
            chip.setCheckable(true);
            chip.setChecked(version.equals(config.javaVersion()));
            chip.setEnabled(possible.contains(version));
            chip.setContentDescription("Java " + version);
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (!filling && checked) update(config.withJavaVersion(version));
            });
            binding.javaVersions.addView(chip);
        }
        binding.javaNote.setText(SETTING_DEXER_D8.equals(config.dexer())
                ? R.string.build_settings_java_note_d8 : R.string.build_settings_java_note_dx);
    }

    private static void setTextKeepingCursor(TextView view, String text) {
        if (!TextUtils.equals(view.getText(), text)) view.setText(text);
    }

    /* ------------------------------------------------------------------ check & save */

    private List<BuildSettingsCheck.Issue> showCheck() {
        List<BuildSettingsCheck.Issue> issues = BuildSettingsCheck.check(config, project);
        binding.tilAndroidJar.setError(null);
        binding.tilClasspath.setError(null);
        if (issues.isEmpty()) {
            binding.checkResult.setText(R.string.build_settings_compat_ok);
            binding.checkResult.setTextColor(com.google.android.material.color.MaterialColors.getColor(binding.checkResult,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            return issues;
        }
        StringBuilder text = new StringBuilder();
        for (BuildSettingsCheck.Issue issue : issues) {
            if (text.length() > 0) text.append("\n\n");
            text.append(issue.severity() == BuildSettingsCheck.Severity.ERROR ? "✕ " : "⚠ ")
                    .append(issue.title()).append('\n').append(issue.detail());
            if (issue.title().startsWith("android.jar") || issue.title().contains("android.jar")) {
                binding.tilAndroidJar.setError(issue.title());
            } else if (issue.title().startsWith("Classpath")) {
                binding.tilClasspath.setError(issue.title());
            }
        }
        binding.checkResult.setText(text);
        binding.checkResult.setTextColor(com.google.android.material.color.MaterialColors.getColor(binding.checkResult,
                BuildSettingsCheck.hasErrors(issues) ? androidx.appcompat.R.attr.colorError
                        : com.google.android.material.R.attr.colorOnSurface));
        return issues;
    }

    private void save() {
        List<BuildSettingsCheck.Issue> issues = showCheck();
        if (BuildSettingsCheck.hasErrors(issues)) {
            // Nothing is written: settings that cannot build would only fail later, halfway through a build.
            binding.scroll.post(() -> binding.scroll.smoothScrollTo(0, binding.checkCard.getTop()));
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.build_settings_errors_title)
                    .setMessage(binding.checkResult.getText())
                    .setPositiveButton(R.string.common_word_ok, null)
                    .show();
            return;
        }
        write(store, config);
        saved = load(new BuildSettings(scId));
        if (!saved.equals(config)) {
            SketchwareUtil.toastError("The build settings could not be written");
            return;
        }
        long warnings = issues.size();
        SketchwareUtil.toast(warnings == 0 ? getString(R.string.build_settings_saved)
                : getString(R.string.build_settings_saved_warnings, warnings));
        finish();
    }

    private void leave() {
        if (config.equals(saved)) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.build_settings_discard)
                .setPositiveButton(R.string.build_settings_discard_button, (d, w) -> finish())
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    /* ------------------------------------------------------------------ advanced rows */

    private void setUpAdvancedRows() {
        String mode = switch (new ProguardHandler(scId).getMode()) {
            case OFF -> "off";
            case SAFE -> "safe";
            case MAX -> "maximum";
        };
        binding.rowShrinking.setText(getString(R.string.build_settings_shrinking, mode));
        binding.rowShrinking.setOnClickListener(v -> startActivity(new Intent(this, ManageProguardActivity.class)
                .putExtra("sc_id", scId)));
        binding.rowExport.setOnClickListener(v -> startActivity(new Intent(this, ExportProjectActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("sc_id", scId)));
        binding.rowHistory.setOnClickListener(v -> AppSettingsDialogs.showBuildHistory(this));
        MemoryProfile.Profile memory = BuildMemory.profile();
        binding.rowMemory.setText(getString(R.string.build_settings_memory,
                memory.tier().name().toLowerCase(java.util.Locale.ROOT), memory.compilerThreads()));
    }

    private void pickFile(boolean classpath) {
        FilePickerOptions options = new FilePickerOptions();
        options.setTitle(getString(classpath ? R.string.build_settings_classpath : R.string.build_settings_android_jar));
        if (classpath) {
            options.setSelectionMode(SelectionMode.BOTH);
            options.setMultipleSelection(true);
        } else {
            options.setExtensions(new String[]{"jar"});
        }
        new FilePickerDialogFragment(options, new FilePickerCallback() {
            @Override
            public void onFileSelected(@NonNull File file) {
                if (classpath) {
                    onFilesSelected(List.of(file));
                } else {
                    update(config.withAndroidJar(file.getAbsolutePath()));
                }
            }

            @Override
            public void onFilesSelected(@NonNull List<? extends File> files) {
                if (!classpath) {
                    if (!files.isEmpty()) onFileSelected(files.get(0));
                    return;
                }
                List<String> entries = new ArrayList<>();
                for (String entry : config.classpath().split(":")) {
                    if (!entry.trim().isEmpty()) entries.add(entry.trim());
                }
                for (File file : files) {
                    if (!entries.contains(file.getAbsolutePath())) entries.add(file.getAbsolutePath());
                }
                update(config.withClasspath(String.join(":", entries)));
            }
        }).show(getSupportFragmentManager(), "build_settings_file_picker");
    }

    /* ------------------------------------------------------------------ storage */

    static ProjectBuildConfig load(BuildSettings settings) {
        ProjectBuildConfig d = ProjectBuildConfig.DEFAULTS;
        return new ProjectBuildConfig(
                settings.getValue(SETTING_ANDROID_JAR_PATH, d.androidJar()),
                settings.getValue(SETTING_CLASSPATH, d.classpath()),
                settings.getValue(SETTING_DEXER, d.dexer()),
                settings.getValue(SETTING_JAVA_VERSION, d.javaVersion()),
                settings.getValue(SETTING_NO_WARNINGS, String.valueOf(d.noWarnings())).equals("true"),
                settings.getValue(SETTING_NO_HTTP_LEGACY, String.valueOf(d.noHttpLegacy())).equals("true"),
                settings.getValue(SETTING_ENABLE_LOGCAT, String.valueOf(d.logcat())).equals("true"));
    }

    /** Writes with the same keys and values the former bottom sheet did. */
    static void write(BuildSettings settings, ProjectBuildConfig config) {
        settings.setValue(SETTING_ANDROID_JAR_PATH, config.androidJar());
        settings.setValue(SETTING_CLASSPATH, config.classpath());
        settings.setValue(SETTING_DEXER, config.dexer());
        settings.setValue(SETTING_JAVA_VERSION, config.javaVersion());
        settings.setValue(SETTING_NO_WARNINGS, String.valueOf(config.noWarnings()));
        settings.setValue(SETTING_NO_HTTP_LEGACY, String.valueOf(config.noHttpLegacy()));
        settings.setValue(SETTING_ENABLE_LOGCAT, String.valueOf(config.logcat()));
    }

    private static HashMap<String, String> toMap(ProjectBuildConfig c) {
        HashMap<String, String> map = new HashMap<>();
        map.put(SETTING_ANDROID_JAR_PATH, c.androidJar());
        map.put(SETTING_CLASSPATH, c.classpath());
        map.put(SETTING_DEXER, c.dexer());
        map.put(SETTING_JAVA_VERSION, c.javaVersion());
        map.put(SETTING_NO_WARNINGS, String.valueOf(c.noWarnings()));
        map.put(SETTING_NO_HTTP_LEGACY, String.valueOf(c.noHttpLegacy()));
        map.put(SETTING_ENABLE_LOGCAT, String.valueOf(c.logcat()));
        return map;
    }

    private static ProjectBuildConfig fromMap(HashMap<?, ?> map) {
        return new ProjectBuildConfig(String.valueOf(map.get(SETTING_ANDROID_JAR_PATH)), String.valueOf(map.get(SETTING_CLASSPATH)),
                String.valueOf(map.get(SETTING_DEXER)), String.valueOf(map.get(SETTING_JAVA_VERSION)),
                "true".equals(map.get(SETTING_NO_WARNINGS)), "true".equals(map.get(SETTING_NO_HTTP_LEGACY)),
                "true".equals(map.get(SETTING_ENABLE_LOGCAT)));
    }

    /** What the compatibility check needs to know about the project. */
    private static BuildSettingsCheck.Project describeProject(String scId) {
        boolean usesRequestNetwork = false;
        try {
            for (ArrayList<ComponentBean> components : jC.a(scId).h.values()) {
                if (components == null) continue;
                for (ComponentBean component : components) {
                    if (component.type == ComponentBean.COMPONENT_TYPE_REQUEST_NETWORK) {
                        usesRequestNetwork = true;
                        break;
                    }
                }
            }
        } catch (RuntimeException projectNotLoaded) {
            // Opened outside a loaded project: the RequestNetwork hint is just left out.
        }
        List<String> libraries = new ArrayList<>();
        try {
            ManageLocalLibrary local = new ManageLocalLibrary(scId);
            if (local.list != null) {
                for (HashMap<String, Object> library : local.list) {
                    Object name = library.get("name");
                    if (name != null) libraries.add(name.toString());
                }
            }
        } catch (RuntimeException unreadable) {
            // A broken local library list is reported by the library screen itself.
        }
        return new BuildSettingsCheck.Project(usesRequestNetwork, libraries);
    }
}
