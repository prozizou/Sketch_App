package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.tools.NewKeyStoreActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonParseException;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import a.a.a.wq;
import mod.hey.studios.util.Helper;
import mod.jbk.util.LogUtil;
import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.activities.settings.SettingsActivity;
import pro.sketchware.control.UpdateChecker;
import pro.sketchware.databinding.DialogCreateNewFileLayoutBinding;
import pro.sketchware.databinding.DialogSettingsSearchBinding;
import pro.sketchware.databinding.PreferenceActivityBinding;
import pro.sketchware.settings.AppSettingsDialogs;
import pro.sketchware.settings.AutoBackup;
import pro.sketchware.settings.SettingsIndex;
import pro.sketchware.settings.SettingsSearch;
import pro.sketchware.settings.StorageTools;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class ConfigActivity extends BaseAppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    public static final File SETTINGS_FILE = new File(FileUtil.getExternalStorageDir(), ".sketch_nws/data/settings.json");
    public static final String SETTING_ALWAYS_SHOW_BLOCKS = "always-show-blocks";
    public static final String SETTING_BACKUP_DIRECTORY = "backup-dir";
    public static final String SETTING_ROOT_AUTO_INSTALL_PROJECTS = "root-auto-install-projects";
    public static final String SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING = "root-auto-open-after-installing";
    public static final String SETTING_BACKUP_FILENAME = "backup-filename";
    public static final String SETTING_SHOW_BUILT_IN_BLOCKS = "built-in-blocks";
    public static final String SETTING_SHOW_EVERY_SINGLE_BLOCK = "show-every-single-block";
    public static final String SETTING_USE_NEW_VERSION_CONTROL = "use-new-version-control";
    public static final String SETTING_USE_ASD_HIGHLIGHTER = "use-asd-highlighter";
    public static final String SETTING_CRITICAL_UPDATE_REMINDER = "critical-update-reminder";
    public static final String SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH = "palletteDir";
    public static final String SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH = "blockDir";
    /** Seconds between auto-saves, "0" turns auto-save off. */
    public static final String SETTING_AUTO_SAVE_INTERVAL = "auto-save-interval";
    /** "snapshot" (silent recovery snapshot) or "save" (full project save). */
    public static final String SETTING_AUTO_SAVE_MODE = "auto-save-mode";
    public static final String SETTING_AUTO_VERSION_CODE = "auto-version-code";
    public static final String SETTING_AUTO_BACKUP = "auto-backup";
    /** Restorable copies of a project kept on save and before builds. */
    public static final String SETTING_AUTO_SNAPSHOTS = "auto-snapshots";
    /** "always", "daily" or "weekly". */
    public static final String SETTING_AUTO_BACKUP_FREQUENCY = "auto-backup-frequency";
    /** How many auto-backups to keep per project, "0" means all of them. */
    public static final String SETTING_BACKUP_RETENTION = "backup-retention";
    public static final String SETTING_CRASH_RECOVERY = "crash-recovery";
    /** "debug" or "release". */
    public static final String SETTING_BUILD_MODE = "build-mode";
    /** Absolute path of the signing keystore, empty for the default location. */
    public static final String SETTING_KEYSTORE_PATH = "keystore-path";
    public static final String SETTING_KEYSTORE_ALIAS = "keystore-alias";
    public static final String SETTING_MEMORY_ALERTS = "memory-alerts";
    /** Heap usage in percent from which a memory alert is raised. */
    public static final String SETTING_MEMORY_THRESHOLD = "memory-alert-threshold";
    /** "stable", "beta" or "dev". */
    public static final String SETTING_UPDATE_CHANNEL = "update-channel";

    /** Fragment argument: key of a row to scroll to and flash once the screen is shown. */
    private static final String ARG_HIGHLIGHT = "highlight";

    private static final Map<String, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(SETTING_ALWAYS_SHOW_BLOCKS, false);
        DEFAULTS.put(SETTING_BACKUP_DIRECTORY, "/.sketch_nws/backups/");
        DEFAULTS.put(SETTING_ROOT_AUTO_INSTALL_PROJECTS, false);
        DEFAULTS.put(SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING, true);
        DEFAULTS.put(SETTING_SHOW_BUILT_IN_BLOCKS, false);
        DEFAULTS.put(SETTING_SHOW_EVERY_SINGLE_BLOCK, false);
        DEFAULTS.put(SETTING_USE_NEW_VERSION_CONTROL, false);
        DEFAULTS.put(SETTING_USE_ASD_HIGHLIGHTER, false);
        DEFAULTS.put(SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, "/.sketch_nws/resources/block/My Block/palette.json");
        DEFAULTS.put(SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, "/.sketch_nws/resources/block/My Block/block.json");
        DEFAULTS.put(SETTING_AUTO_SAVE_INTERVAL, "0");
        DEFAULTS.put(SETTING_AUTO_SAVE_MODE, "snapshot");
        DEFAULTS.put(SETTING_AUTO_VERSION_CODE, false);
        DEFAULTS.put(SETTING_AUTO_BACKUP, false);
        DEFAULTS.put(SETTING_AUTO_SNAPSHOTS, true);
        DEFAULTS.put(SETTING_AUTO_BACKUP_FREQUENCY, "daily");
        DEFAULTS.put(SETTING_BACKUP_RETENTION, "5");
        DEFAULTS.put(SETTING_CRASH_RECOVERY, true);
        DEFAULTS.put(SETTING_BUILD_MODE, "debug");
        DEFAULTS.put(SETTING_KEYSTORE_PATH, "");
        DEFAULTS.put(SETTING_KEYSTORE_ALIAS, "");
        DEFAULTS.put(SETTING_MEMORY_ALERTS, true);
        DEFAULTS.put(SETTING_MEMORY_THRESHOLD, "85");
        DEFAULTS.put(SETTING_UPDATE_CHANNEL, "stable");
        for (pro.sketchware.flags.FeatureFlag flag : pro.sketchware.flags.FeatureFlag.values()) {
            DEFAULTS.put(flag.key(), flag.enabledByDefault());
        }
    }

    public static String getBackupPath() {
        return DataStore.getInstance().getString(SETTING_BACKUP_DIRECTORY, "/.sketch_nws/backups/");
    }

    public static String getStringSettingValueOrSetAndGet(String settingKey, String toReturnAndSetIfNotFound) {
        var dataStore = DataStore.getInstance();
        Map<String, Object> settings = dataStore.getSettings();

        Object value = settings.get(settingKey);
        if (value instanceof String s) {
            return s;
        } else {
            dataStore.putString(settingKey, toReturnAndSetIfNotFound);
            dataStore.persist();

            return toReturnAndSetIfNotFound;
        }
    }

    public static String getBackupFileName() {
        return DataStore.getInstance().getString(SETTING_BACKUP_FILENAME, "$projectName v$versionName ($pkgName, $versionCode) $time(yyyy-MM-dd'T'HHmmss)");
    }

    /**
     * @return the stored value of a text setting, or its default.
     */
    public static String getStringSetting(String key) {
        Object fallback = DEFAULTS.get(key);
        return DataStore.getInstance().getString(key, fallback instanceof String d ? d : "");
    }

    public static int getIntSetting(String key, int fallback) {
        try {
            return Integer.parseInt(getStringSetting(key).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * @return the user's keystore path, or an empty string when the default location is used.
     */
    public static String getKeystorePath() {
        return getStringSetting(SETTING_KEYSTORE_PATH).trim();
    }

    public static boolean isSettingEnabled(String keyName) {
        return DataStore.getInstance().getBoolean(keyName, false);
    }

    public static void setSetting(String key, Object value) {
        var dataStore = DataStore.getInstance();
        if (value instanceof String s) {
            dataStore.putString(key, s);
        } else if (value instanceof Boolean b) {
            dataStore.putBoolean(key, b);
        } else {
            throw new IllegalArgumentException("Unhandled data type " + value.getClass());
        }
        dataStore.persist();
    }

    @NonNull
    private static HashMap<String, Object> readSettings() {
        HashMap<String, Object> settings;

        if (SETTINGS_FILE.exists()) {
            Exception toLog;

            try {
                settings = getGson().fromJson(FileUtil.readFile(SETTINGS_FILE.getAbsolutePath()), Helper.TYPE_MAP);

                if (settings != null) {
                    return settings;
                }

                toLog = new NullPointerException("settings == null");
                // fall-through to shared error handler
            } catch (JsonParseException e) {
                toLog = e;
                // fall-through to shared error handler
            }

            SketchwareUtil.toastError("Couldn't parse App Settings! Restoring defaults.");
            LogUtil.e("ConfigActivity", "Failed to parse App Settings.", toLog);
        }
        settings = new HashMap<>();
        restoreDefaultSettings(settings);

        return settings;
    }

    private static void restoreDefaultSettings(HashMap<String, Object> settings) {
        settings.clear();
        settings.putAll(DEFAULTS);
        FileUtil.writeFile(SETTINGS_FILE.getAbsolutePath(), getGson().toJson(settings));
    }

    public static Object getDefaultValue(String key) {
        if (!DEFAULTS.containsKey(key)) {
            throw new IllegalArgumentException("Unknown key '" + key + "'!");
        }
        return DEFAULTS.get(key);
    }

    private PreferenceActivityBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = PreferenceActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.topAppBar.setTitle("App Settings");
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(binding.fragmentContainer.getId(), new PreferenceFragment())
                    .commit();
        }

        {
            View view1 = binding.appBarLayout;
            int left = view1.getPaddingLeft();
            int top = view1.getPaddingTop();
            int right = view1.getPaddingRight();
            int bottom = view1.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top + insets.top, right + insets.right, bottom);
                return i;
            });
        }

        {
            View view1 = binding.fragmentContainer;
            int left = view1.getPaddingLeft();
            int top = view1.getPaddingTop();
            int right = view1.getPaddingRight();
            int bottom = view1.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(view1, (v, i) -> {
                Insets insets = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(left + insets.left, top, right + insets.right, bottom + insets.bottom);
                return i;
            });
        }
    }

    /**
     * Opens a category of the main page in its own screen, with back going to the main page.
     */
    @Override
    public boolean onPreferenceStartScreen(@NonNull PreferenceFragmentCompat caller, @NonNull PreferenceScreen screen) {
        openScreen(screen.getKey(), null);
        return true;
    }

    /**
     * Shows a settings screen and, optionally, scrolls to one of its rows and flashes it.
     *
     * @param screenKey    the category to open, {@code null} for the main page
     * @param highlightKey the row to point out, or {@code null}
     */
    public void openScreen(@Nullable String screenKey, @Nullable String highlightKey) {
        FragmentManager fragments = getSupportFragmentManager();
        if (screenKey == null) {
            fragments.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            if (highlightKey != null
                    && fragments.findFragmentById(binding.fragmentContainer.getId()) instanceof PreferenceFragment main) {
                main.highlight(highlightKey);
            }
            return;
        }
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, screenKey);
        if (highlightKey != null) {
            args.putString(ARG_HIGHLIGHT, highlightKey);
        }
        var fragment = new PreferenceFragment();
        fragment.setArguments(args);
        fragments.beginTransaction()
                .setReorderingAllowed(true)
                .replace(binding.fragmentContainer.getId(), fragment)
                .addToBackStack(screenKey)
                .commit();
    }

    private void showScreen(CharSequence title, boolean keepScroll) {
        binding.topAppBar.setTitle(title);
        if (!keepScroll) {
            binding.contentLayout.scrollTo(0, 0);
            binding.appBarLayout.setExpanded(true, false);
        }
    }

    /**
     * Puts the given settings back to their defaults and persists.
     */
    public static void resetSettings(Collection<String> keys) {
        DataStore.getInstance().resetKeys(keys);
    }

    /**
     * One screen of App Settings: the main page with its search bar and eight categories, or one category.
     * Each category only holds some of the rows, so every set-up step skips the rows it doesn't find.
     */
    public static class PreferenceFragment extends PreferenceFragmentCompat {
        private DataStore dataStore;
        @Nullable
        private String rootKey;
        private boolean highlightPending;

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            this.rootKey = rootKey;
            dataStore = DataStore.getInstance();
            getPreferenceManager().setPreferenceDataStore(dataStore);
            setPreferencesFromResource(R.xml.preferences_config_activity, rootKey);
            applyCompactLayout(getPreferenceScreen());

            setUpAdvanced();
            if (rootKey == null) {
                // The categories' rows are inflated here too, but only shown once a category is opened.
                setUpMainPage();
                return;
            }
            setUpEditor();
            setUpProjects();
            setUpBackupAndRecovery();
            setUpBuildAndSigning();
            setUpStorage();
            setUpUpdates();
            setUpDiagnostics();
            addSectionReset();
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            String highlightKey = getArguments() == null ? null : getArguments().getString(ARG_HIGHLIGHT);
            if (highlightKey != null && savedInstanceState == null) {
                highlight(highlightKey);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (getActivity() instanceof ConfigActivity activity) {
                CharSequence title = rootKey == null ? "App Settings" : getPreferenceScreen().getTitle();
                activity.showScreen(title, highlightPending);
            }
        }

        /**
         * Gives every row its Material 3 layout and lets titles wrap instead of being cut off.
         */
        private void applyCompactLayout(PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference preference = group.getPreference(i);
                String key = preference.getKey() == null ? "" : preference.getKey();
                if (preference instanceof PreferenceCategory) {
                    preference.setLayoutResource(R.layout.preference_category_compact);
                } else if (key.equals("settings-search")) {
                    preference.setLayoutResource(R.layout.preference_search);
                } else if (key.equals("settings-footer")) {
                    preference.setLayoutResource(R.layout.preference_footer);
                } else if (key.equals("experimental-warning")) {
                    preference.setLayoutResource(R.layout.preference_warning);
                } else if (preference instanceof PreferenceScreen || preference.getIcon() != null) {
                    preference.setLayoutResource(R.layout.preference_icon_row);
                    preference.setSingleLineTitle(false);
                    if (preference instanceof PreferenceScreen) {
                        preference.setWidgetLayoutResource(R.layout.preference_widget_chevron);
                    }
                } else {
                    preference.setLayoutResource(R.layout.preference_compact);
                    preference.setSingleLineTitle(false);
                }
                // A category's rows are only shown once it is opened.
                if (preference instanceof PreferenceGroup child && !(preference instanceof PreferenceScreen && rootKey == null)) {
                    applyCompactLayout(child);
                }
            }
        }

        @Nullable
        private <T extends Preference> T find(String key) {
            return findPreference(key);
        }

        /**
         * Sets what a row does when tapped, if this screen shows that row.
         */
        private void onClick(String key, Runnable action) {
            Preference preference = find(key);
            if (preference == null) {
                return;
            }
            preference.setOnPreferenceClickListener(clicked -> {
                action.run();
                return true;
            });
        }

        private View snackbarView() {
            return getActivity() instanceof ConfigActivity activity ? activity.binding.getRoot() : requireView();
        }

        /* ------------------------------------------------------------ Main page */

        private void setUpMainPage() {
            onClick("settings-search", this::showSearch);
            Preference footer = find("settings-footer");
            if (footer != null) {
                footer.setTitle("NWS • Version " + BuildConfig.VERSION_NAME);
                footer.setSummary("Build " + BuildConfig.VERSION_CODE);
            }
        }

        /**
         * Searches every setting, in every category, as the user types.
         */
        private void showSearch() {
            SettingsSearch search = new SettingsSearch(SettingsIndex.read(requireContext(), R.xml.preferences_config_activity));
            List<SettingsSearch.Entry> categories = new ArrayList<>();
            for (SettingsSearch.Entry entry : search.entries()) {
                if (entry.isScreen()) {
                    categories.add(entry);
                }
            }

            DialogSettingsSearchBinding searchBinding = DialogSettingsSearchBinding.inflate(getLayoutInflater());
            List<SettingsSearch.Entry> shown = new ArrayList<>(categories);
            ArrayAdapter<SettingsSearch.Entry> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_2, android.R.id.text1, shown) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View row = super.getView(position, convertView, parent);
                    SettingsSearch.Entry entry = getItem(position);
                    TextView title = row.findViewById(android.R.id.text1);
                    TextView detail = row.findViewById(android.R.id.text2);
                    title.setText(entry.title());
                    detail.setText(entry.isScreen() ? entry.summary() : entry.location());
                    return row;
                }
            };
            searchBinding.searchResults.setAdapter(adapter);
            searchBinding.searchResults.setEmptyView(searchBinding.searchEmpty);

            AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                    .setView(searchBinding.getRoot())
                    .setNegativeButton(R.string.common_word_close, null)
                    .create();
            searchBinding.searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable text) {
                    String query = text.toString();
                    shown.clear();
                    shown.addAll(query.trim().isEmpty() ? categories : search.search(query, 40));
                    searchBinding.searchEmpty.setText("No setting matches \u201c" + query.trim() + "\u201d");
                    adapter.notifyDataSetChanged();
                }
            });
            searchBinding.searchResults.setOnItemClickListener((parent, view, position, id) -> {
                SettingsSearch.Entry entry = shown.get(position);
                dialog.dismiss();
                if (getActivity() instanceof ConfigActivity activity) {
                    if (entry.isScreen()) {
                        activity.openScreen(entry.key(), null);
                    } else {
                        activity.openScreen(entry.screenKey(), entry.key());
                    }
                }
            });
            dialog.setOnShowListener(shownDialog -> {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                searchBinding.searchInput.requestFocus();
            });
            dialog.show();
        }

        /**
         * Scrolls to a row of this screen and flashes it, so a search result is easy to spot.
         */
        void highlight(String key) {
            highlightPending = true;
            RecyclerView list = getListView();
            list.postDelayed(() -> {
                highlightPending = false;
                if (!isAdded() || !(list.getAdapter() instanceof PreferenceGroup.PreferencePositionCallback positions)) {
                    return;
                }
                int position = positions.getPreferenceAdapterPosition(key);
                if (position < 0) {
                    return;
                }
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                if (holder == null) {
                    scrollToPreference(key);
                    return;
                }
                View item = holder.itemView;
                if (getActivity() instanceof ConfigActivity activity) {
                    NestedScrollView scroller = activity.binding.contentLayout;
                    int[] itemAt = new int[2];
                    int[] scrollerAt = new int[2];
                    item.getLocationInWindow(itemAt);
                    scroller.getLocationInWindow(scrollerAt);
                    int target = scroller.getScrollY() + itemAt[1] - scrollerAt[1] - scroller.getHeight() / 3;
                    activity.binding.appBarLayout.setExpanded(false, true);
                    scroller.smoothScrollTo(0, Math.max(0, target));
                }
                item.postDelayed(() -> {
                    if (item.getBackground() != null) {
                        item.getBackground().setHotspot(item.getWidth() / 2f, item.getHeight() / 2f);
                    }
                    item.setPressed(true);
                    item.postDelayed(() -> item.setPressed(false), 700);
                }, 350);
            }, 200);
        }

        /**
         * Adds "Reset this section" at the end of a category, for its own settings only.
         */
        private void addSectionReset() {
            PreferenceScreen screen = getPreferenceScreen();
            List<String> keys = new ArrayList<>();
            collectResettableKeys(screen, keys);
            if (keys.isEmpty()) {
                return;
            }
            PreferenceCategory category = new PreferenceCategory(requireContext());
            category.setKey("category-section-reset");
            category.setTitle("Defaults");
            category.setIconSpaceReserved(false);
            category.setLayoutResource(R.layout.preference_category_compact);
            screen.addPreference(category);

            Preference reset = new Preference(requireContext());
            reset.setKey("reset-section");
            reset.setPersistent(false);
            reset.setIconSpaceReserved(false);
            reset.setLayoutResource(R.layout.preference_compact);
            reset.setSingleLineTitle(false);
            reset.setTitle("Reset " + screen.getTitle());
            reset.setSummary("Put the " + keys.size() + (keys.size() == 1 ? " setting" : " settings")
                    + " of this page back to their defaults. Other pages are left as they are.");
            reset.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Reset " + screen.getTitle() + "?")
                        .setMessage("Only the settings on this page go back to their defaults.")
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .setPositiveButton(R.string.common_word_reset, (dialog, which) -> {
                            resetSettings(keys);
                            reload();
                            Snackbar.make(snackbarView(), "Defaults restored for " + screen.getTitle(),
                                    BaseTransientBottomBar.LENGTH_SHORT).show();
                        })
                        .show();
                return true;
            });
            category.addPreference(reset);
        }

        private static void collectResettableKeys(PreferenceGroup group, List<String> keys) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference preference = group.getPreference(i);
                String key = preference.getKey();
                if (key != null && (DEFAULTS.containsKey(key) || SETTING_BACKUP_FILENAME.equals(key))) {
                    keys.add(key);
                }
                if (preference instanceof PreferenceGroup child) {
                    collectResettableKeys(child, keys);
                }
            }
        }

        private AppCompatActivity host() {
            return (AppCompatActivity) requireActivity();
        }

        /* ------------------------------------------------------------ Editor */

        private void setUpEditor() {
            onClick("app-theme", () -> {
                Intent intent = new Intent(requireContext(), SettingsActivity.class);
                intent.putExtra(SettingsActivity.FRAGMENT_TAG_EXTRA, SettingsActivity.SETTINGS_APPEARANCE_FRAGMENT);
                startActivity(intent);
            });

            ListPreference interval = find(SETTING_AUTO_SAVE_INTERVAL);
            Preference mode = find(SETTING_AUTO_SAVE_MODE);
            if (interval == null || mode == null) {
                return;
            }
            mode.setEnabled(!"0".equals(interval.getValue()));
            interval.setOnPreferenceChangeListener((preference, newValue) -> {
                mode.setEnabled(!"0".equals(newValue));
                return true;
            });
        }

        /* ------------------------------------------------------------ Projects */

        private void setUpProjects() {
            onClick("health-check", () -> AppSettingsDialogs.showHealthCheck(requireActivity()));

            SwitchPreferenceCompat installWithRoot = find(SETTING_ROOT_AUTO_INSTALL_PROJECTS);
            if (installWithRoot == null) {
                return;
            }
            installWithRoot.setOnPreferenceClickListener(preference -> {
                if (installWithRoot.isChecked()) {
                    Shell.getShell(shell -> {
                        if (!shell.isRoot()) {
                            Snackbar.make(snackbarView(), "Couldn't acquire root access", BaseTransientBottomBar.LENGTH_SHORT).show();
                            installWithRoot.setChecked(false);
                        }
                    });
                }
                return true;
            });
        }

        /* ------------------------------------------------------------ Backup & Recovery */

        private String describeBackupDirectory() {
            String path = getBackupPath();
            return "/Internal storage" + (path.startsWith("/") ? "" : "/") + path;
        }

        private void setUpBackupAndRecovery() {
            onClick("signing-keystore", () -> {
                if (getActivity() instanceof ConfigActivity activity) {
                    activity.openScreen("screen-build", SETTING_KEYSTORE_PATH);
                }
            });
            onClick("restore-backup", () ->
                    AppSettingsDialogs.showRestoreBackup(requireActivity(), getParentFragmentManager()));

            Preference backupDir = find(SETTING_BACKUP_DIRECTORY);
            if (backupDir == null) {
                return;
            }
            backupDir.setSummary(describeBackupDirectory());
            backupDir.setOnPreferenceClickListener(preference -> {
                DialogCreateNewFileLayoutBinding binding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
                binding.inputText.setText(getBackupPath());
                binding.chipGroupTypes.setVisibility(View.GONE);
                AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                        .setView(binding.getRoot())
                        .setTitle("Backup directory")
                        .setMessage("Directory inside /Internal storage/, e.g. .sketch_nws/backups")
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .setPositiveButton(R.string.common_word_save, null)
                        .create();

                dialog.setOnShowListener(dialogInterface -> {
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(
                            Helper.getDialogDismissListener(dialogInterface));
                    Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    positiveButton.setOnClickListener(view -> {
                        getDataStore().putString(SETTING_BACKUP_DIRECTORY, Helper.getText(binding.inputText));
                        backupDir.setSummary(describeBackupDirectory());
                        dialog.dismiss();
                    });

                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    binding.inputText.requestFocus();
                });
                dialog.show();
                return true;
            });

            Preference backupFilename = find(SETTING_BACKUP_FILENAME);
            backupFilename.setOnPreferenceClickListener(preference -> {
                DialogCreateNewFileLayoutBinding binding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
                binding.chipGroupTypes.setVisibility(View.GONE);
                binding.inputText.setText(getBackupFileName());

                AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                        .setView(binding.getRoot())
                        .setTitle("Backup filename format")
                        .setMessage("This defines how SWB backup files get named.\n" +
                                "Available variables:\n" +
                                " - $projectName - Project name\n" +
                                " - $versionCode - App version code\n" +
                                " - $versionName - App version name\n" +
                                " - $pkgName - App package name\n" +
                                " - $timeInMs - Time during backup in milliseconds\n" +
                                "\n" +
                                "Additionally, you can format your own time like this using Java's date formatter syntax:\n" +
                                "$time(yyyy-MM-dd'T'HHmmss)\n")
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .setPositiveButton(R.string.common_word_save, null)
                        .setNeutralButton(R.string.common_word_reset, (dialogInterface, which) -> {
                            getDataStore().putString(SETTING_BACKUP_FILENAME, null);
                            Snackbar.make(snackbarView(), "Reset to default complete.", BaseTransientBottomBar.LENGTH_SHORT).show();
                        })
                        .create();

                dialog.setOnShowListener(dialogInterface -> {
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(
                            Helper.getDialogDismissListener(dialog));
                    Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    positiveButton.setOnClickListener(view -> {
                        getDataStore().putString(SETTING_BACKUP_FILENAME, Helper.getText(binding.inputText));
                        dialog.dismiss();
                    });
                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    binding.inputText.requestFocus();
                });
                dialog.show();
                return true;
            });

            // A smaller retention takes effect right away instead of at the next backup.
            Preference retention = find(SETTING_BACKUP_RETENTION);
            retention.setOnPreferenceChangeListener((preference, newValue) -> {
                getDataStore().putString(SETTING_BACKUP_RETENTION, (String) newValue);
                new Thread(AutoBackup::pruneAll).start();
                return true;
            });
        }

        /* ------------------------------------------------------------ Build & Signing */

        private String describeKeystore() {
            String path = getKeystorePath();
            if (path.isEmpty()) {
                return "Default · /Internal storage/" + wq.D;
            }
            return new File(path).isFile() ? path : path + " (missing)";
        }

        private void setUpBuildAndSigning() {
            onClick("build-history", () -> AppSettingsDialogs.showBuildHistory(requireActivity()));
            ListPreference buildMode = find(SETTING_BUILD_MODE);
            if (buildMode == null) {
                return;
            }
            buildMode.setSummaryProvider((Preference.SummaryProvider<ListPreference>) preference ->
                    "release".equals(preference.getValue())
                            ? "Release · exports default to your keystore"
                            : "Debug · exports default to the test key");

            Preference keystore = find(SETTING_KEYSTORE_PATH);
            keystore.setSummary(describeKeystore());
            keystore.setOnPreferenceClickListener(preference -> {
                AppSettingsDialogs.pickKeystore(requireActivity(), getParentFragmentManager(),
                        () -> keystore.setSummary(describeKeystore()));
                return true;
            });

            Preference alias = find(SETTING_KEYSTORE_ALIAS);
            alias.setSummary(describeAlias());
            alias.setOnPreferenceClickListener(preference -> {
                DialogCreateNewFileLayoutBinding binding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
                binding.chipGroupTypes.setVisibility(View.GONE);
                binding.textInputLayout.setHint("Key alias");
                binding.inputText.setText(getStringSetting(SETTING_KEYSTORE_ALIAS));
                new MaterialAlertDialogBuilder(requireContext())
                        .setView(binding.getRoot())
                        .setTitle("Key alias")
                        .setMessage("Pre-filled when you sign an export. The password is never stored.")
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .setPositiveButton(R.string.common_word_save, (dialog, which) -> {
                            getDataStore().putString(SETTING_KEYSTORE_ALIAS, Helper.getText(binding.inputText).trim());
                            alias.setSummary(describeAlias());
                        })
                        .show();
                return true;
            });

            onClick("keystore-verify", () -> {
                if (!new File(wq.getSigningKeystorePath()).isFile()) {
                    SketchwareUtil.toastError("Keystore file not found");
                    return;
                }
                DialogCreateNewFileLayoutBinding binding = DialogCreateNewFileLayoutBinding.inflate(getLayoutInflater());
                binding.chipGroupTypes.setVisibility(View.GONE);
                binding.textInputLayout.setHint("Keystore password");
                binding.inputText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                new MaterialAlertDialogBuilder(requireContext())
                        .setView(binding.getRoot())
                        .setTitle("Verify keystore")
                        .setMessage("Used once to open the file; it isn't stored.")
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .setPositiveButton("Verify", (dialog, which) -> AppSettingsDialogs.verifyKeystore(
                                requireActivity(), Helper.getText(binding.inputText).toCharArray()))
                        .show();
            });

            onClick("keystore-create", () -> {
                Snackbar.make(snackbarView(), "New keystores are saved to the default location.", BaseTransientBottomBar.LENGTH_LONG).show();
                startActivity(new Intent(requireContext(), NewKeyStoreActivity.class));
            });
        }

        private String describeAlias() {
            String alias = getStringSetting(SETTING_KEYSTORE_ALIAS).trim();
            return alias.isEmpty() ? "Not set" : alias;
        }

        /* ------------------------------------------------------------ Storage */

        private void setUpStorage() {
            onClick("clear-cache", () -> AppSettingsDialogs.confirmClearCache(requireActivity(), this::refreshCacheSummary));
            onClick("storage-usage", () -> AppSettingsDialogs.showStorageUsage(requireActivity()));
            refreshCacheSummary();
        }

        private void refreshCacheSummary() {
            Preference clearCache = find("clear-cache");
            if (clearCache == null) {
                return;
            }
            clearCache.setSummary("Calculating…");
            var appContext = requireContext().getApplicationContext();
            new Thread(() -> {
                long size = StorageTools.cacheSize(appContext);
                var activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (isAdded()) {
                            clearCache.setSummary(size == 0 ? "Nothing to clear" : FileUtil.formatFileSize(size) + " can be freed");
                        }
                    });
                }
            }).start();
        }

        /* ------------------------------------------------------------ Updates */

        private void setUpUpdates() {
            Preference version = find("app-version");
            if (version != null) {
                version.setSummary(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
            }
            onClick("check-updates", () -> new UpdateChecker().check(host(), true));
        }

        /* ------------------------------------------------------------ Diagnostics */

        private void setUpDiagnostics() {
            onClick("view-logs", () -> AppSettingsDialogs.showLogs(requireActivity()));
            onClick("diagnostic-report", () -> AppSettingsDialogs.showDiagnosticReport(requireActivity()));
        }

        /* ------------------------------------------------------------ Advanced */

        private void setUpAdvanced() {
            onClick("export-settings", () -> AppSettingsDialogs.exportSettings(requireActivity()));
            onClick("import-settings", () -> AppSettingsDialogs.importSettings(
                    requireActivity(), getParentFragmentManager(), this::reload));
            onClick("reset-settings", () -> AppSettingsDialogs.confirmReset(requireActivity(), this::reload));
        }

        /**
         * Rebuilds the screen so every row shows the values that were just imported or reset.
         */
        private void reload() {
            if (!isAdded()) {
                return;
            }
            setPreferenceScreen(null);
            onCreatePreferences(null, rootKey);
        }

        public DataStore getDataStore() {
            return dataStore;
        }
    }

    /**
     * An in-memory caching store for settings listed in {@link ConfigActivity}.
     * Persists to {@link #SETTINGS_FILE}.
     *
     * @see #persist()
     */
    public static class DataStore extends PreferenceDataStore {
        private static DataStore INSTANCE;
        private final Map<String, Object> settings;

        private DataStore() {
            settings = readSettings();
        }

        public static DataStore getInstance() {
            return INSTANCE == null ? (INSTANCE = new DataStore()) : INSTANCE;
        }

        private Map<String, Object> getSettings() {
            return settings;
        }

        /**
         * Blocking method that writes its data to {@link #SETTINGS_FILE}. Should be called manually,
         * since there's no automatic persist. Meaning, every write, unless they are in batches.
         */
        public void persist() {
            FileUtil.writeFile(SETTINGS_FILE.getAbsolutePath(), getGson().toJson(settings));
        }

        /**
         * Puts every setting back to its default value and persists.
         */
        public synchronized void resetToDefaults() {
            settings.clear();
            settings.putAll(DEFAULTS);
            persist();
        }

        /**
         * Puts the given settings back to their defaults and persists. A setting without a default is removed.
         */
        public synchronized void resetKeys(Collection<String> keys) {
            for (String key : keys) {
                if (DEFAULTS.containsKey(key)) {
                    settings.put(key, DEFAULTS.get(key));
                } else {
                    settings.remove(key);
                }
            }
            persist();
        }

        /**
         * @return a copy of the user-facing settings. Internal bookkeeping such as last-backup times is left out.
         */
        public synchronized Map<String, Object> exportableSettings() {
            Map<String, Object> exported = new LinkedHashMap<>();
            for (String key : DEFAULTS.keySet()) {
                if (settings.containsKey(key)) {
                    exported.put(key, settings.get(key));
                }
            }
            if (settings.get(SETTING_BACKUP_FILENAME) instanceof String format) {
                exported.put(SETTING_BACKUP_FILENAME, format);
            }
            return exported;
        }

        /**
         * Applies the entries of {@code values} that are known settings with the right type, ignoring the rest.
         *
         * @return how many settings were applied.
         */
        public synchronized int importSettings(Map<?, ?> values) {
            int applied = 0;
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    continue;
                }
                Object value = entry.getValue();
                Object expected = SETTING_BACKUP_FILENAME.equals(key) ? "" : DEFAULTS.get(key);
                boolean valid = (expected instanceof Boolean && value instanceof Boolean)
                        || (expected instanceof String && value instanceof String);
                if (valid) {
                    settings.put(key, value);
                    applied++;
                }
            }
            persist();
            return applied;
        }

        @Override
        public void putString(String key, @Nullable String value) {
            if (value == null) {
                settings.remove(key);
            } else {
                settings.put(key, value);
            }
            persist();
        }

        @Nullable
        @Override
        public String getString(String key, @Nullable String defValue) {
            var value = settings.get(key);
            if (value instanceof String s) {
                return s;
            }
            return DEFAULTS.get(key) instanceof String d ? d : defValue;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            settings.put(key, value);
            persist();
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            var value = settings.get(key);
            if (value instanceof Boolean b) {
                return b;
            }
            return DEFAULTS.get(key) instanceof Boolean d ? d : defValue;
        }
    }
}
