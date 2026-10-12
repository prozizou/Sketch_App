package com.besome.sketch.editor.manage.library.firebase;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ImageViewCompat;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.DB;
import a.a.a.GB;
import a.a.a.bB;
import a.a.a.lC;
import a.a.a.yB;
import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageLibraryManageFirebaseBinding;
import pro.sketchware.firebase.FirebaseChecks;
import pro.sketchware.firebase.FirebaseSdkCheck;
import pro.sketchware.firebase.GoogleServicesConfig;
import pro.sketchware.firebase.HttpsTransport;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Firebase Manager: one editor for the project's {@code google-services.json}, with the values read from it, a status,
 * and separate connection tests.
 *
 * <p>The JSON text is the only state; the summary, the issues and the status are derived from it. What the build reads
 * (database URL or project id, app id, API key, storage bucket) is still written to the same four fields of the
 * project's Firebase library entry, so existing projects and the build are unaffected. The JSON itself is kept next to
 * them in the entry's {@code configurations}, and trusted on opening only while it still agrees with those four fields
 * (the step-by-step wizard or an import from another project may have changed them); otherwise it is rebuilt from them.
 *
 * <p>The app id and API key are hidden in the editor and in the summary until asked for, hidden again when the screen
 * stops, and never logged.
 */
public class ManageFirebaseActivity extends BaseAppCompatActivity {
    public static final String CONFIG_JSON = "google_services_json";
    public static final String CONFIG_CLIENT = "google_services_client_package";
    private static final String EXTRA_PROJECT_PACKAGE = "project_package";
    private static final long REFRESH_DELAY_MS = 250;
    private static final int MAX_FILE_BYTES = 512 * 1024;
    private static final int MAX_ISSUE_ROWS = 10;
    private static final int MENU_HELP = 1;
    private static final int MENU_SETUP = 2;
    private static final int MENU_DELETE = 3;
    private static final int[] TEST_TITLES = {R.string.fb_test_sdk, R.string.fb_test_auth, R.string.fb_test_database, R.string.fb_test_storage};

    public ManageLibraryManageFirebaseBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private DB s;
    private String sc_id;
    private String projectPackage = "";
    private ProjectLibraryBean firebaseLibraryBean;

    // The state: everything on screen is derived from these.
    private String rawJson = "";
    private boolean enabled;
    private boolean masked = true;
    private String preferredPackage = "";
    private boolean appIdRevealed;
    private boolean apiKeyRevealed;

    // What was loaded, to know what changed.
    private String initialJson = "";
    private boolean initialEnabled;
    private String initialPreferred = "";
    private boolean hasStoredJson;

    private GoogleServicesConfig config = GoogleServicesConfig.parse("", "");
    private boolean ignoreEditorChanges;
    private boolean emptyDismissed;
    private int testRun;

    private ImageView[] testIcons;
    private ProgressBar[] testSpinners;
    private TextView[] testDetails;

    private final Runnable refresh = this::refresh;

    private final ActivityResultLauncher<Intent> openSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            ProjectLibraryBean bean = result.getData().getParcelableExtra("firebase");
            if (bean != null) {
                firebaseLibraryBean = bean;
                loadFromBean();
                renderAll();
            }
        }
    });

    // ---- lifecycle

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageLibraryManageFirebaseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Toolbar toolbar = binding.toolbar.toolbar;
        setSupportActionBar(toolbar);
        binding.toolbar.layoutMainLogo.setVisibility(View.GONE);
        getSupportActionBar().setTitle(Helper.getResString(R.string.design_library_firebase_title_firebase_manager));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leave();
            }
        });

        s = new DB(getApplicationContext(), "P1");
        sc_id = savedInstanceState != null ? savedInstanceState.getString("sc_id") : getIntent().getStringExtra("sc_id");
        firebaseLibraryBean = getIntent().getParcelableExtra("firebase");
        if (firebaseLibraryBean == null) {
            firebaseLibraryBean = new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE);
        }
        projectPackage = resolveProjectPackage();

        binding.tvEnable.setText(Helper.getResString(R.string.design_library_settings_title_enabled));
        binding.layoutSwitch.setOnClickListener(v -> toggleEnabled());
        binding.btnConsole.setOnClickListener(v -> goToConsole());
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnImport.setOnClickListener(v -> showImportJsonDialog());
        binding.btnPaste.setOnClickListener(v -> pasteFromClipboard());
        binding.btnFormat.setOnClickListener(v -> formatJson());
        binding.btnReveal.setOnClickListener(v -> {
            masked = !masked;
            renderEditor();
        });
        binding.btnRunTests.setOnClickListener(v -> runTests());
        binding.editorEmpty.setOnClickListener(v -> {
            emptyDismissed = true;
            binding.editorEmpty.setVisibility(View.GONE);
            binding.jsonEditor.requestFocus();
        });

        setUpEditor();
        buildTestRows();
        applyLayoutMetrics();
        loadFromBean();
        renderAll();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Keys are not left on screen when the app goes to the background.
        boolean changed = !masked || appIdRevealed || apiKeyRevealed;
        masked = true;
        appIdRevealed = false;
        apiKeyRevealed = false;
        if (changed && binding != null) {
            renderEditor();
            renderSummary();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        testRun++;
        executor.shutdownNow();
        super.onDestroy();
        binding = null;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyLayoutMetrics();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    // ---- setup

    private void setUpEditor() {
        EditorUtils.loadJsonConfig(binding.jsonEditor);
        binding.jsonEditor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.jsonEditor.setTextSize(13);
        binding.jsonEditor.setWordwrap(false);
        binding.jsonEditor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (ignoreEditorChanges || binding == null) return;
            rawJson = editorText();
            // Typing into an empty editor shows the real text, so the keys are not hidden from the person typing them.
            masked = false;
            handler.removeCallbacks(refresh);
            handler.postDelayed(refresh, REFRESH_DELAY_MS);
        });
        // The editor scrolls on its own inside the page's scroll view.
        binding.jsonEditor.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL);
            }
            return false;
        });
    }

    /** One column no wider than a tablet reads comfortably, and the action bar goes side by side when there is room. */
    private void applyLayoutMetrics() {
        float density = getResources().getDisplayMetrics().density;
        int widthDp = getResources().getConfiguration().screenWidthDp;
        ViewGroup.LayoutParams column = binding.column.getLayoutParams();
        column.width = widthDp > 640 ? Math.round(640 * density) : ViewGroup.LayoutParams.MATCH_PARENT;
        binding.column.setLayoutParams(column);

        boolean wide = widthDp >= 560;
        binding.bottomBar.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        setBarButton(binding.btnConsole, wide, 0);
        setBarButton(binding.btnSave, wide, wide ? Math.round(8 * density) : Math.round(8 * density));
    }

    private static void setBarButton(View button, boolean wide, int margin) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) button.getLayoutParams();
        lp.width = wide ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
        lp.weight = wide ? 1 : 0;
        lp.setMargins(wide ? margin : 0, wide ? 0 : margin, 0, 0);
        if (button.getId() == R.id.btn_console) lp.setMargins(0, 0, 0, 0);
        button.setLayoutParams(lp);
    }

    private String resolveProjectPackage() {
        String fromIntent = getIntent().getStringExtra(EXTRA_PROJECT_PACKAGE);
        if (fromIntent != null) return fromIntent;
        try {
            String pkg = yB.c(lC.b(sc_id), "my_sc_pkg_name");
            return pkg == null ? "" : pkg;
        } catch (Throwable t) {
            return "";
        }
    }

    // ---- state in and out of the library entry

    private void loadFromBean() {
        ProjectLibraryBean bean = firebaseLibraryBean;
        enabled = bean.isEnabled();
        initialEnabled = enabled;
        HashMap<String, Object> configurations = bean.configurations;
        preferredPackage = stringOf(configurations, CONFIG_CLIENT);
        initialPreferred = preferredPackage;

        String stored = stringOf(configurations, CONFIG_JSON);
        GoogleServicesConfig parsed = GoogleServicesConfig.parse(stored, projectPackage, preferredPackage);
        boolean agrees = !stored.isEmpty() && parsed.isValid()
                && parsed.libraryData().equals(nz(bean.data))
                && parsed.appId().equals(nz(bean.reserved1))
                && parsed.apiKey().equals(nz(bean.reserved2))
                && parsed.storageBucket().equals(nz(bean.reserved3));
        if (agrees) {
            rawJson = stored;
            hasStoredJson = true;
        } else {
            // Opened for the first time, or the four fields changed elsewhere: rebuild the file from what the build reads.
            rawJson = GoogleServicesConfig.fromStoredValues(bean.data, bean.reserved1, bean.reserved2, bean.reserved3, projectPackage);
            hasStoredJson = false;
        }
        initialJson = rawJson;
        masked = true;
        appIdRevealed = false;
        apiKeyRevealed = false;
        config = GoogleServicesConfig.parse(rawJson, projectPackage, preferredPackage);
    }

    private static String stringOf(HashMap<String, Object> map, String key) {
        if (map == null) return "";
        Object value = map.get(key);
        return value instanceof String string ? string : "";
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private boolean jsonChanged() {
        return !rawJson.equals(initialJson) || !preferredPackage.equals(initialPreferred);
    }

    private boolean isDirty() {
        return jsonChanged() || enabled != initialEnabled;
    }

    /** Writes the state into the library entry, leaving the four fields alone when the file was only rebuilt from them. */
    private void applyToBean() {
        ProjectLibraryBean bean = firebaseLibraryBean;
        if (jsonChanged() || hasStoredJson) {
            bean.data = config.libraryData();
            bean.reserved1 = config.appId();
            bean.reserved2 = config.apiKey();
            bean.reserved3 = config.storageBucket();
            if (bean.configurations == null) bean.configurations = new HashMap<>();
            bean.configurations.put(CONFIG_JSON, rawJson);
            if (preferredPackage.isEmpty()) {
                bean.configurations.remove(CONFIG_CLIENT);
            } else {
                bean.configurations.put(CONFIG_CLIENT, preferredPackage);
            }
        }
        bean.useYn = enabled ? ProjectLibraryBean.LIB_USE_Y : ProjectLibraryBean.LIB_USE_N;
    }

    private void finishWithResult() {
        Intent result = new Intent();
        result.putExtra("firebase", firebaseLibraryBean);
        setResult(RESULT_OK, result);
        finish();
    }

    // ---- actions

    private void save() {
        if (!config.isValid()) {
            SketchwareUtil.toast(getString(R.string.fb_toast_configure_first), Toast.LENGTH_LONG);
            return;
        }
        if (hasIssue("PACKAGE_MISMATCH") && jsonChanged()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.fb_mismatch_title)
                    .setMessage(R.string.fb_mismatch_message)
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .setPositiveButton(R.string.fb_save_anyway, (d, w) -> commitAndLeave())
                    .show();
            return;
        }
        commitAndLeave();
    }

    private void commitAndLeave() {
        applyToBean();
        finishWithResult();
    }

    private boolean hasIssue(String code) {
        for (GoogleServicesConfig.Issue issue : config.issues()) if (issue.code.equals(code)) return true;
        return false;
    }

    private void leave() {
        if (!isDirty()) {
            // Nothing changed: hand the entry back as it was, as this screen always did.
            finishWithResult();
            return;
        }
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fb_unsaved_title)
                .setMessage(R.string.fb_unsaved_message)
                .setNeutralButton(R.string.common_word_cancel, null)
                .setNegativeButton(R.string.fb_discard, (d, w) -> {
                    setResult(RESULT_CANCELED);
                    finish();
                });
        if (config.isValid()) {
            dialog.setPositiveButton(R.string.common_word_save, (d, w) -> commitAndLeave());
        }
        dialog.show();
    }

    private boolean canEnable() {
        return config.isValid() || (!jsonChanged() && !nz(firebaseLibraryBean.data).isEmpty());
    }

    private void toggleEnabled() {
        if (!enabled) {
            if (!canEnable()) {
                SketchwareUtil.toast(getString(R.string.fb_toast_configure_first), Toast.LENGTH_LONG);
                return;
            }
            enabled = true;
            renderStatus();
            return;
        }
        if (initialEnabled) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(Helper.getResString(R.string.common_word_warning))
                    .setIcon(R.drawable.delete_96)
                    .setMessage(Helper.getResString(R.string.design_library_firebase_dialog_description_confirm_uncheck_firebase))
                    .setCancelable(false)
                    .setPositiveButton(Helper.getResString(R.string.common_word_delete), (d, w) -> {
                        enabled = false;
                        renderStatus();
                    })
                    .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                    .show();
        } else {
            enabled = false;
            renderStatus();
        }
    }

    private void goToConsole() {
        if (GB.h(getApplicationContext())) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse("https://console.firebase.google.com"));
                startActivity(intent);
            } catch (Exception e) {
                downloadChromeDialog();
            }
        } else {
            bB.a(getApplicationContext(), Helper.getResString(R.string.common_message_check_network), bB.TOAST_NORMAL).show();
        }
    }

    private void openDoc() {
        try {
            s.a("P1I15", true);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse("https://sketchware-pro.vercel.app/docs/components/Firebase/connect-to-firebase"));
            startActivity(intent);
        } catch (Exception e) {
            downloadChromeDialog();
        }
    }

    private void downloadChromeDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.chrome_96);
        dialog.setTitle(Helper.getResString(R.string.title_compatible_chrome_browser));
        dialog.setMessage(Helper.getResString(R.string.message_compatible_chrome_brower));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), (v, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=com.android.chrome"));
            startActivity(intent);
            v.dismiss();
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void openWizard() {
        if (jsonChanged()) {
            SketchwareUtil.toast(getString(R.string.fb_toast_save_first), Toast.LENGTH_LONG);
            return;
        }
        applyToBean();
        Intent intent = new Intent(getApplicationContext(), FirebaseActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("firebase", firebaseLibraryBean);
        openSettings.launch(intent);
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fb_delete_title)
                .setMessage(R.string.fb_delete_message)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.fb_delete, (d, w) -> {
                    ProjectLibraryBean bean = firebaseLibraryBean;
                    bean.data = "";
                    bean.reserved1 = "";
                    bean.reserved2 = "";
                    bean.reserved3 = "";
                    bean.useYn = ProjectLibraryBean.LIB_USE_N;
                    if (bean.configurations != null) {
                        bean.configurations.remove(CONFIG_JSON);
                        bean.configurations.remove(CONFIG_CLIENT);
                    }
                    SketchwareUtil.toast(getString(R.string.fb_toast_deleted));
                    finishWithResult();
                })
                .show();
    }

    // ---- loading the text

    /** Replaces the text with a new file; asks first when there already is one. */
    private void replaceJson(String text) {
        Runnable apply = () -> {
            rawJson = text;
            preferredPackage = "";
            masked = true;
            emptyDismissed = false;
            renderAll();
        };
        if (rawJson.trim().isEmpty() || rawJson.equals(text)) {
            apply.run();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fb_replace_title)
                .setMessage(R.string.fb_replace_message)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.fb_replace, (d, w) -> apply.run())
                .show();
    }

    private void showImportJsonDialog() {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"json"});
        options.setTitle("Select your google-services.json");
        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                if (file.length() > MAX_FILE_BYTES) {
                    SketchwareUtil.toastError(getString(R.string.fb_toast_file_too_big));
                    return;
                }
                String content = FileUtil.readFile(file.getAbsolutePath());
                if (content == null) {
                    SketchwareUtil.toastError(getString(R.string.fb_toast_file_unreadable));
                    return;
                }
                replaceJson(content);
            }

            @Override
            public boolean onFileSelectionCancelled() {
                return true;
            }
        };
        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "filePicker");
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        CharSequence text = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).coerceToText(this);
        if (text == null || text.toString().trim().isEmpty()) {
            SketchwareUtil.toast(getString(R.string.fb_toast_clipboard_empty));
            return;
        }
        if (text.length() > MAX_FILE_BYTES) {
            SketchwareUtil.toastError(getString(R.string.fb_toast_file_too_big));
            return;
        }
        replaceJson(text.toString());
    }

    private void formatJson() {
        String formatted = GoogleServicesConfig.format(rawJson);
        if (formatted == null) {
            SketchwareUtil.toast(getString(R.string.fb_toast_cannot_format));
            return;
        }
        rawJson = formatted;
        renderAll();
    }

    // ---- rendering

    private String editorText() {
        return binding.jsonEditor.getText().toString();
    }

    private void refresh() {
        if (binding == null) return;
        config = GoogleServicesConfig.parse(rawJson, projectPackage, preferredPackage);
        renderEditorChrome();
        renderStatus();
        renderIssues();
        renderSummary();
        renderSaveState();
        resetTests();
    }

    private void renderAll() {
        handler.removeCallbacks(refresh);
        config = GoogleServicesConfig.parse(rawJson, projectPackage, preferredPackage);
        renderEditor();
        renderStatus();
        renderIssues();
        renderSummary();
        renderSaveState();
        resetTests();
    }

    private void renderEditor() {
        boolean empty = rawJson.isEmpty();
        boolean editable = empty || !masked;
        String shown = editable ? rawJson : GoogleServicesConfig.maskSecrets(rawJson);
        if (!shown.equals(editorText())) {
            ignoreEditorChanges = true;
            binding.jsonEditor.setText(shown);
            ignoreEditorChanges = false;
        }
        binding.jsonEditor.setEditable(editable);
        renderEditorChrome();
    }

    /** The buttons and notes around the editor, which follow the text without replacing it. */
    private void renderEditorChrome() {
        boolean empty = rawJson.isEmpty();
        binding.editorEmpty.setVisibility(empty && !emptyDismissed ? View.VISIBLE : View.GONE);
        binding.btnReveal.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.btnReveal.setText(masked ? R.string.fb_edit : R.string.fb_hide);
        binding.btnReveal.setIconResource(masked ? R.drawable.ic_eye : R.drawable.ic_eye_off);
        binding.editorNote.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.editorNote.setText(masked ? R.string.fb_note_masked : R.string.fb_note_editable);
    }

    private void renderStatus() {
        binding.libSwitch.setChecked(enabled);
        binding.layoutSwitch.setContentDescription(getString(R.string.design_library_settings_title_enabled));
        int icon;
        @AttrRes int tint;
        String text;
        if (config.isEmpty()) {
            icon = R.drawable.ic_mtrl_info;
            tint = com.google.android.material.R.attr.colorOnSurfaceVariant;
            text = getString(R.string.fb_status_empty);
        } else if (config.hasErrors()) {
            icon = R.drawable.ic_mtrl_cancel;
            tint = androidx.appcompat.R.attr.colorError;
            text = getString(R.string.fb_status_invalid, config.count(GoogleServicesConfig.Severity.ERROR), config.count(GoogleServicesConfig.Severity.WARNING));
        } else if (config.count(GoogleServicesConfig.Severity.WARNING) > 0) {
            icon = R.drawable.ic_mtrl_warning;
            tint = com.google.android.material.R.attr.colorTertiary;
            text = getString(R.string.fb_status_valid_warnings, config.count(GoogleServicesConfig.Severity.WARNING));
        } else {
            icon = R.drawable.ic_mtrl_check;
            tint = androidx.appcompat.R.attr.colorPrimary;
            text = getString(R.string.fb_status_valid, config.packageName());
        }
        binding.statusIcon.setImageResource(icon);
        ImageViewCompat.setImageTintList(binding.statusIcon, ColorStateList.valueOf(MaterialColors.getColor(binding.statusIcon, tint)));
        binding.statusText.setText(text);
    }

    private void renderSaveState() {
        boolean canSave = config.isValid();
        binding.btnSave.setEnabled(canSave);
        binding.btnSave.setAlpha(canSave ? 1f : 0.5f);
        binding.btnRunTests.setEnabled(canSave);
    }

    private void renderIssues() {
        LinearLayout list = binding.issuesList;
        list.removeAllViews();
        List<GoogleServicesConfig.Issue> issues = config.issues();
        int shown = Math.min(issues.size(), MAX_ISSUE_ROWS);
        for (int i = 0; i < shown; i++) {
            list.addView(issueRow(issues.get(i)));
        }
        if (issues.size() > shown) {
            TextView more = text(getString(R.string.fb_issues_more, issues.size() - shown), com.google.android.material.R.attr.textAppearanceBodySmall);
            more.setPadding(0, dp(4), 0, 0);
            list.addView(more);
        }
        list.setVisibility(issues.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private View issueRow(GoogleServicesConfig.Issue issue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        @DrawableRes int icon;
        @AttrRes int tint;
        switch (issue.severity) {
            case ERROR -> {
                icon = R.drawable.ic_mtrl_cancel;
                tint = androidx.appcompat.R.attr.colorError;
            }
            case WARNING -> {
                icon = R.drawable.ic_mtrl_warning;
                tint = com.google.android.material.R.attr.colorTertiary;
            }
            default -> {
                icon = R.drawable.ic_mtrl_info;
                tint = com.google.android.material.R.attr.colorOnSurfaceVariant;
            }
        }
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(MaterialColors.getColor(binding.getRoot(), tint)));
        row.addView(image, new LinearLayout.LayoutParams(dp(18), dp(18)));
        String label = issue.line > 0 ? getString(R.string.fb_issue_line, issue.line, issue.message) : issue.message;
        TextView message = text(label, com.google.android.material.R.attr.textAppearanceBodySmall);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(dp(8));
        row.addView(message, lp);
        if (issue.line > 0) {
            row.setClickable(true);
            row.setOnClickListener(v -> goToLine(issue.line));
        }
        return row;
    }

    private void goToLine(int line) {
        int count = binding.jsonEditor.getText().getLineCount();
        if (count == 0) return;
        binding.jsonEditor.setSelection(Math.min(line, count) - 1, 0);
        binding.jsonEditor.requestFocus();
    }

    private void renderSummary() {
        LinearLayout rows = binding.summaryRows;
        rows.removeAllViews();
        boolean show = config.isValid() || !config.clients().isEmpty() || !config.projectId().isEmpty();
        binding.summaryEmpty.setVisibility(show ? View.GONE : View.VISIBLE);
        renderClientChips();
        if (!show) return;

        addRow(rows, R.string.fb_row_project_id, config.projectId(), null);
        addRow(rows, R.string.fb_row_project_number, config.projectNumber(), null);
        String pkg = config.packageName();
        String pkgText = pkg;
        if (!pkg.isEmpty() && !projectPackage.isEmpty()) {
            pkgText = pkg.equals(projectPackage) ? getString(R.string.fb_package_matches, pkg) : getString(R.string.fb_package_differs, pkg, projectPackage);
        }
        addRow(rows, R.string.fb_row_package, pkgText, null);
        addSecretRow(rows, R.string.fb_row_app_id, config.appId(), appIdRevealed, () -> {
            appIdRevealed = !appIdRevealed;
            renderSummary();
        });
        addSecretRow(rows, R.string.fb_row_api_key, config.apiKey(), apiKeyRevealed, () -> {
            apiKeyRevealed = !apiKeyRevealed;
            renderSummary();
        });
        addRow(rows, R.string.fb_row_database, config.databaseUrl(), null);
        addRow(rows, R.string.fb_row_storage, config.storageBucket(), null);
        GoogleServicesConfig.Client client = config.selectedClient();
        if (client != null && !client.oauthClients.isEmpty()) {
            addRow(rows, R.string.fb_row_oauth, getString(R.string.fb_oauth_value, client.oauthClients.size(),
                    getString(config.webClientId().isEmpty() ? R.string.fb_oauth_absent : R.string.fb_oauth_present)), null);
        }
    }

    private void renderClientChips() {
        List<GoogleServicesConfig.Client> clients = config.clients();
        boolean several = clients.size() > 1;
        binding.clientScroll.setVisibility(several ? View.VISIBLE : View.GONE);
        binding.clientChips.setOnCheckedStateChangeListener(null);
        binding.clientChips.removeAllViews();
        if (!several) return;
        for (int i = 0; i < clients.size(); i++) {
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId());
            chip.setCheckable(true);
            chip.setText(getString(R.string.fb_client_chip, clients.get(i).packageName));
            chip.setTag(clients.get(i).packageName);
            chip.setChecked(i == config.selectedIndex());
            binding.clientChips.addView(chip);
        }
        binding.clientChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            View chip = group.findViewById(ids.get(0));
            if (chip != null && chip.getTag() instanceof String chosen && !chosen.equals(config.packageName())) {
                preferredPackage = chosen;
                refresh();
            }
        });
    }

    private void addRow(LinearLayout parent, @androidx.annotation.StringRes int label, String value, @Nullable View trailing) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(0, dp(6), 0, dp(6));
        TextView name = text(getString(label), com.google.android.material.R.attr.textAppearanceLabelMedium);
        name.setTextColor(MaterialColors.getColor(name, com.google.android.material.R.attr.colorOnSurfaceVariant));
        TextView content = text(value.isEmpty() ? getString(R.string.fb_value_none) : value, com.google.android.material.R.attr.textAppearanceBodyMedium);
        content.setTextIsSelectable(false);
        if (value.isEmpty()) content.setTextColor(MaterialColors.getColor(content, com.google.android.material.R.attr.colorOnSurfaceVariant));
        texts.addView(name);
        texts.addView(content);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (trailing != null) row.addView(trailing, new LinearLayout.LayoutParams(dp(48), dp(48)));
        parent.addView(row);
    }

    private void addSecretRow(LinearLayout parent, @androidx.annotation.StringRes int label, String value, boolean revealed, Runnable toggle) {
        String shown = value.isEmpty() ? "" : revealed ? value : GoogleServicesConfig.mask(value);
        ImageButton eye = null;
        if (!value.isEmpty()) {
            eye = new ImageButton(this);
            eye.setImageResource(revealed ? R.drawable.ic_eye_off : R.drawable.ic_eye);
            eye.setBackgroundResource(selectableBorderless());
            eye.setContentDescription(getString(R.string.firebase_reveal_value));
            ImageViewCompat.setImageTintList(eye, ColorStateList.valueOf(MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurfaceVariant)));
            eye.setOnClickListener(v -> toggle.run());
            eye.setPadding(dp(12), dp(12), dp(12), dp(12));
        }
        addRow(parent, label, shown, eye);
    }

    private int selectableBorderless() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(androidx.appcompat.R.attr.selectableItemBackgroundBorderless, value, true);
        return value.resourceId;
    }

    // ---- tests

    private void buildTestRows() {
        testIcons = new ImageView[TEST_TITLES.length];
        testSpinners = new ProgressBar[TEST_TITLES.length];
        testDetails = new TextView[TEST_TITLES.length];
        for (int i = 0; i < TEST_TITLES.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(6), 0, dp(6));

            FrameLayout marker = new FrameLayout(this);
            testIcons[i] = new ImageView(this);
            testIcons[i].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            marker.addView(testIcons[i], new FrameLayout.LayoutParams(dp(20), dp(20), android.view.Gravity.CENTER));
            testSpinners[i] = new ProgressBar(this);
            testSpinners[i].setIndeterminate(true);
            testSpinners[i].setVisibility(View.GONE);
            marker.addView(testSpinners[i], new FrameLayout.LayoutParams(dp(20), dp(20), android.view.Gravity.CENTER));
            row.addView(marker, new LinearLayout.LayoutParams(dp(24), dp(24)));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView title = text(getString(TEST_TITLES[i]), com.google.android.material.R.attr.textAppearanceTitleSmall);
            testDetails[i] = text("", com.google.android.material.R.attr.textAppearanceBodySmall);
            testDetails[i].setTextColor(MaterialColors.getColor(testDetails[i], com.google.android.material.R.attr.colorOnSurfaceVariant));
            texts.addView(title);
            texts.addView(testDetails[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMarginStart(dp(12));
            row.addView(texts, lp);
            binding.testsList.addView(row);
        }
        resetTests();
    }

    private void resetTests() {
        if (testIcons == null) return;
        testRun++;
        for (int i = 0; i < TEST_TITLES.length; i++) {
            showTest(i, null);
        }
        binding.btnRunTests.setText(R.string.fb_run_tests);
        binding.btnRunTests.setEnabled(config.isValid());
    }

    /** {@code null} shows "not run yet". */
    private void showTest(int index, @Nullable FirebaseChecks.Result result) {
        testSpinners[index].setVisibility(View.GONE);
        testIcons[index].setVisibility(View.VISIBLE);
        @DrawableRes int icon = R.drawable.ic_mtrl_info;
        @AttrRes int tint = com.google.android.material.R.attr.colorOnSurfaceVariant;
        String detail = getString(R.string.fb_test_not_run);
        if (result != null) {
            detail = result.detail;
            switch (result.status) {
                case PASS -> {
                    icon = R.drawable.ic_mtrl_check;
                    tint = androidx.appcompat.R.attr.colorPrimary;
                }
                case WARN -> {
                    icon = R.drawable.ic_mtrl_warning;
                    tint = com.google.android.material.R.attr.colorTertiary;
                }
                case FAIL -> {
                    icon = R.drawable.ic_mtrl_cancel;
                    tint = androidx.appcompat.R.attr.colorError;
                }
                case UNREACHABLE -> icon = R.drawable.ic_mtrl_wifi;
                case SKIPPED -> {
                    // keeps the neutral icon
                }
            }
        }
        testIcons[index].setImageResource(icon);
        ImageViewCompat.setImageTintList(testIcons[index], ColorStateList.valueOf(MaterialColors.getColor(testIcons[index], tint)));
        testDetails[index].setText(detail);
    }

    private void showTestRunning(int index) {
        testIcons[index].setVisibility(View.INVISIBLE);
        testSpinners[index].setVisibility(View.VISIBLE);
        testDetails[index].setText(R.string.fb_running);
    }

    private boolean isOnline() {
        try {
            ConnectivityManager manager = getSystemService(ConnectivityManager.class);
            if (manager == null) return true;
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (SecurityException e) {
            return true;
        }
    }

    private void runTests() {
        if (!config.isValid()) {
            SketchwareUtil.toast(getString(R.string.fb_test_fix_first));
            return;
        }
        final GoogleServicesConfig tested = config;
        final int run = ++testRun;
        final boolean online = isOnline();
        binding.btnRunTests.setEnabled(false);
        binding.btnRunTests.setText(R.string.fb_running);
        for (int i = 0; i < TEST_TITLES.length; i++) showTestRunning(i);

        // Each test runs and reports on its own; one failing never hides another.
        executor.execute(() -> {
            report(run, 0, FirebaseSdkCheck.run(getApplicationContext(), tested));
            HttpsTransport transport = new HttpsTransport();
            FirebaseChecks.Result offline = new FirebaseChecks.Result(FirebaseChecks.Status.UNREACHABLE, getString(R.string.fb_test_offline));
            report(run, 1, online ? FirebaseChecks.authentication(tested, transport) : offline);
            report(run, 2, online ? FirebaseChecks.database(tested, transport) : offline);
            report(run, 3, online ? FirebaseChecks.storage(tested, transport) : offline);
            runOnUiThread(() -> {
                if (binding != null && run == testRun) {
                    binding.btnRunTests.setText(R.string.fb_run_tests);
                    binding.btnRunTests.setEnabled(config.isValid());
                }
            });
        });
    }

    private void report(int run, int index, FirebaseChecks.Result result) {
        runOnUiThread(() -> {
            // A result for a configuration that has since been edited is dropped.
            if (binding != null && run == testRun) showTest(index, result);
        });
    }

    // ---- toolbar

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_HELP, 0, R.string.fb_menu_help).setIcon(R.drawable.ic_mtrl_help).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_SETUP, 1, R.string.fb_menu_setup).setIcon(R.drawable.ic_mtrl_settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_DELETE, 2, R.string.fb_menu_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case MENU_HELP -> openDoc();
            case MENU_SETUP -> openWizard();
            case MENU_DELETE -> confirmDelete();
            default -> {
                return super.onOptionsItemSelected(item);
            }
        }
        return true;
    }

    // ---- small view helpers

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(CharSequence content, @AttrRes int appearance) {
        TextView view = new TextView(this);
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(appearance, value, true)) view.setTextAppearance(value.resourceId);
        view.setTextColor(MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOnSurface));
        view.setText(content);
        return view;
    }
}
