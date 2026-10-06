package pro.sketchware.settings;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import a.a.a.wq;
import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import kellinwood.security.zipsigner.optional.KeyStoreFileManager;
import mod.hey.studios.project.backup.BackupFactory;
import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

import static pro.sketchware.utility.GsonUtils.getGson;

/**
 * Dialogs and actions behind the rows of App Settings that are not plain toggles.
 */
public final class AppSettingsDialogs {
    private static final int MAX_SHARE_CHARS = 60_000;

    private AppSettingsDialogs() {
    }

    /* ---------------------------------------------------------------- generic */

    public static void showText(Activity activity, String title, String text, @Nullable String shareSubject) {
        showText(activity, title, text, shareSubject, null, null);
    }

    public static void showText(Activity activity, String title, String text, @Nullable String shareSubject,
                                @Nullable String neutralLabel, @Nullable Runnable neutralAction) {
        float density = activity.getResources().getDisplayMetrics().density;
        int padding = (int) (24 * density);

        TextView textView = new TextView(activity);
        textView.setText(text);
        textView.setTextIsSelectable(true);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setPadding(padding, padding / 2, padding, 0);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(textView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("Close", null);
        if (shareSubject != null) {
            dialog.setNegativeButton("Share", (d, w) -> share(activity, shareSubject, text));
        }
        if (neutralLabel != null && neutralAction != null) {
            dialog.setNeutralButton(neutralLabel, (d, w) -> neutralAction.run());
        }
        dialog.show();
    }

    private static void share(Context context, String subject, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, text.length() > MAX_SHARE_CHARS
                ? text.substring(text.length() - MAX_SHARE_CHARS) : text);
        context.startActivity(Intent.createChooser(intent, subject));
    }

    private static void copy(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            SketchwareUtil.toast("Copied to clipboard");
        }
    }

    private static void runOnUi(Activity activity, Runnable action) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                action.run();
            }
        });
    }

    private static String formatTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
    }

    /* ---------------------------------------------------------------- projects */

    public static void showHealthCheck(Activity activity) {
        SketchwareUtil.toast("Checking projects…");
        new Thread(() -> {
            ProjectHealthCheck.Report report;
            try {
                report = ProjectHealthCheck.run();
            } catch (Throwable t) {
                AppLog.e("HealthCheck", t.toString());
                runOnUi(activity, () -> SketchwareUtil.toastError("Health check failed: " + t.getMessage()));
                return;
            }
            StringBuilder text = new StringBuilder();
            int errors = report.errorCount();
            int warnings = report.issues.size() - errors;
            text.append(report.projectsChecked).append(" projects checked · ")
                    .append(errors).append(errors == 1 ? " error · " : " errors · ")
                    .append(warnings).append(warnings == 1 ? " warning" : " warnings");
            if (report.issues.isEmpty()) {
                text.append("\n\nNo problems found.");
            } else {
                text.append("\n");
                for (ProjectHealthCheck.Issue issue : report.issues) {
                    text.append("\n").append(issue.error() ? "[Error] " : "[Warn]  ")
                            .append(issue.project()).append(": ").append(issue.message());
                }
            }
            AppLog.i("HealthCheck", report.projectsChecked + " projects, " + errors + " errors, " + warnings + " warnings");
            runOnUi(activity, () -> showText(activity, "Project health check", text.toString(), null));
        }).start();
    }

    /* ---------------------------------------------------------------- backup */

    public static void showRestoreBackup(Activity activity, FragmentManager fragmentManager) {
        List<File> backups = AutoBackup.listBackups();
        List<String> items = new ArrayList<>();
        if (!backups.isEmpty()) {
            File latest = backups.get(0);
            items.add("Latest backup · " + latest.getName());
        }
        items.add("Choose a file…");

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Restore a backup")
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (!backups.isEmpty() && which == 0) {
                        confirmRestore(activity, backups.get(0));
                    } else {
                        pickBackup(activity, fragmentManager);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void confirmRestore(Activity activity, File backup) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Restore this backup?")
                .setMessage(backup.getName() + "\n" + formatTime(backup.lastModified()) + " · "
                        + FileUtil.formatFileSize(backup.length())
                        + "\n\nIt is restored as a new project; existing projects aren't touched.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (d, w) ->
                        new BackupRestoreManager(activity).doRestore(backup.getAbsolutePath(), false))
                .show();
    }

    private static void pickBackup(Activity activity, FragmentManager fragmentManager) {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{BackupFactory.EXTENSION});
        options.setTitle("Select a backup (" + BackupFactory.EXTENSION + ")");
        options.setInitialDirectory(BackupFactory.getBackupDir());
        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                confirmRestore(activity, file);
            }
        };
        new FilePickerDialogFragment(options, callback).show(fragmentManager, "file_picker");
    }

    /* ---------------------------------------------------------------- build & signing */

    public static void showBuildHistory(Activity activity) {
        List<BuildHistory.Entry> entries = BuildHistory.load();
        if (entries.isEmpty()) {
            SketchwareUtil.toast("No builds recorded yet");
            return;
        }
        long okCount = entries.stream().filter(e -> BuildHistory.STATUS_SUCCESS.equals(e.status)).count();
        StringBuilder text = new StringBuilder();
        text.append(entries.size()).append(" recent builds · ").append(okCount).append(" succeeded\n");
        for (BuildHistory.Entry entry : entries) {
            text.append("\n").append(entry.status == null ? "?" : entry.status.toUpperCase(Locale.ROOT))
                    .append("  ").append(entry.project);
            if (entry.versionName != null && !entry.versionName.isEmpty()) {
                text.append("  v").append(entry.versionName).append(" (").append(entry.versionCode).append(")");
            }
            text.append("\n").append(formatTime(entry.time)).append(" · ")
                    .append(entry.mode == null ? "debug" : entry.mode).append(" · ")
                    .append(String.format(Locale.US, "%.1f s", entry.durationMs / 1000f));
            if (entry.detail != null && !entry.detail.isEmpty()) {
                text.append("\n").append(entry.detail);
            }
            text.append("\n");
        }
        showText(activity, "Build history", text.toString(), null, "Clear", () ->
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("Clear build history?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear", (d, w) -> {
                            BuildHistory.clear();
                            SketchwareUtil.toast("Build history cleared");
                        })
                        .show());
    }

    public static void pickKeystore(Activity activity, FragmentManager fragmentManager, Runnable onChanged) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Keystore file")
                .setItems(new String[]{"Choose a file…", "Use the default location"}, (dialog, which) -> {
                    if (which == 1) {
                        ConfigActivity.setSetting(ConfigActivity.SETTING_KEYSTORE_PATH, "");
                        onChanged.run();
                        return;
                    }
                    FilePickerOptions options = new FilePickerOptions();
                    options.setExtensions(new String[]{"jks", "keystore", "bks"});
                    options.setTitle("Select a keystore");
                    FilePickerCallback callback = new FilePickerCallback() {
                        @Override
                        public void onFileSelected(File file) {
                            ConfigActivity.setSetting(ConfigActivity.SETTING_KEYSTORE_PATH, file.getAbsolutePath());
                            onChanged.run();
                        }
                    };
                    new FilePickerDialogFragment(options, callback).show(fragmentManager, "file_picker");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Opens the configured keystore with the given password (never stored) and reports its aliases and expiry.
     */
    public static void verifyKeystore(Activity activity, char[] password) {
        String path = wq.getSigningKeystorePath();
        String wantedAlias = ConfigActivity.getStringSetting(ConfigActivity.SETTING_KEYSTORE_ALIAS).trim();
        new Thread(() -> {
            StringBuilder text = new StringBuilder();
            boolean ok = false;
            try {
                if (!new File(path).isFile()) {
                    throw new IllegalStateException("File not found: " + path);
                }
                KeyStore keyStore = KeyStoreFileManager.loadKeyStore(path, password);
                List<String> aliases = Collections.list(keyStore.aliases());
                text.append("Keystore opened. ").append(aliases.size()).append(aliases.size() == 1 ? " alias:" : " aliases:");
                boolean aliasFound = wantedAlias.isEmpty();
                for (String alias : aliases) {
                    aliasFound |= alias.equalsIgnoreCase(wantedAlias);
                    text.append("\n\n").append(alias);
                    Certificate certificate = keyStore.getCertificate(alias);
                    if (certificate instanceof X509Certificate x509) {
                        Date expires = x509.getNotAfter();
                        text.append("\n  ").append(x509.getSubjectX500Principal().getName())
                                .append("\n  Valid until ")
                                .append(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(expires))
                                .append(expires.before(new Date()) ? " (EXPIRED)" : "");
                    }
                }
                if (!aliasFound) {
                    text.append("\n\nThe alias \"").append(wantedAlias).append("\" isn't in this keystore.");
                }
                ok = aliasFound;
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                text.append("Couldn't open the keystore.\n").append(cause.getMessage() == null ? cause.toString() : cause.getMessage());
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
            boolean finalOk = ok;
            AppLog.i("Keystore", "Verification " + (finalOk ? "passed" : "failed") + " for " + new File(path).getName());
            runOnUi(activity, () -> showText(activity, finalOk ? "Keystore OK" : "Keystore check", text.toString(), null));
        }).start();
    }

    /* ---------------------------------------------------------------- storage */

    public static void confirmClearCache(Activity activity, Runnable onDone) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Clear cache?")
                .setMessage("Removes temporary files and generated build output. " +
                        "Projects, resources and backups are kept. The next build will take longer.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> new Thread(() -> {
                    long freed = StorageTools.clearCache(activity.getApplicationContext());
                    runOnUi(activity, () -> {
                        SketchwareUtil.toast("Freed " + FileUtil.formatFileSize(freed));
                        onDone.run();
                    });
                }).start())
                .show();
    }

    public static void showStorageUsage(Activity activity) {
        SketchwareUtil.toast("Measuring…");
        new Thread(() -> {
            Map<String, Long> usage = StorageTools.usage(activity.getApplicationContext());
            long total = 0;
            StringBuilder text = new StringBuilder();
            for (Map.Entry<String, Long> entry : usage.entrySet()) {
                total += entry.getValue();
                text.append(String.format(Locale.US, "%-16s %s%n", entry.getKey(), FileUtil.formatFileSize(entry.getValue())));
            }
            text.append("\n").append(String.format(Locale.US, "%-16s %s", "Total", FileUtil.formatFileSize(total)));
            long freeMegabytes = a.a.a.GB.c();
            if (freeMegabytes > 0) {
                text.append("\n").append(String.format(Locale.US, "%-16s %d MB", "Free on device", freeMegabytes));
            }
            runOnUi(activity, () -> showText(activity, "Storage usage", text.toString(), null));
        }).start();
    }

    /* ---------------------------------------------------------------- diagnostics */

    public static void showLogs(Activity activity) {
        String appLog = AppLog.readTail(20_000);
        File debugFile = new File(FileUtil.getExternalStorageDir(), ".sketch_nws/debug.txt");
        String debugLog = debugFile.isFile() ? tail(FileUtil.readFile(debugFile.getAbsolutePath()), 10_000) : "";

        StringBuilder text = new StringBuilder();
        text.append("== App events ==\n").append(appLog.isEmpty() ? "(empty)\n" : appLog);
        text.append("\n== Compiler/debug output ==\n").append(debugLog.isEmpty() ? "(empty)\n" : debugLog);
        showText(activity, "Logs", text.toString(), "Sketchware logs", "Clear", () -> {
            AppLog.clear();
            SketchwareUtil.toast("App log cleared");
        });
    }

    private static String tail(String text, int maxChars) {
        return text.length() > maxChars ? "…" + text.substring(text.length() - maxChars) : text;
    }

    public static void showDiagnosticReport(Activity activity) {
        StringBuilder text = new StringBuilder(ProjectHealthCheck.describeEnvironment());
        text.append("\nChannel ").append(ConfigActivity.getStringSetting(ConfigActivity.SETTING_UPDATE_CHANNEL));
        text.append("\nBuild mode ").append(ConfigActivity.getStringSetting(ConfigActivity.SETTING_BUILD_MODE));
        text.append("\nAuto-save ").append(ConfigActivity.getStringSetting(ConfigActivity.SETTING_AUTO_SAVE_INTERVAL)).append(" s");
        text.append("\nAuto-backup ").append(ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_AUTO_BACKUP));
        List<BuildHistory.Entry> builds = BuildHistory.load();
        long failed = builds.stream().filter(e -> BuildHistory.STATUS_FAILED.equals(e.status)).count();
        text.append("\nBuilds ").append(builds.size()).append(" recorded, ").append(failed).append(" failed");
        String report = text.toString();
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Diagnostic report")
                .setMessage(report)
                .setPositiveButton("Close", null)
                .setNeutralButton("Copy", (d, w) -> copy(activity, "Diagnostic report", report))
                .setNegativeButton("Share", (d, w) -> share(activity, "Sketchware diagnostic report", report))
                .show();
    }

    /* ---------------------------------------------------------------- advanced */

    public static void exportSettings(Activity activity) {
        try {
            File dir = new File(wq.s(), "settings");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("Couldn't create " + dir);
            }
            String name = "app-settings-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".json";
            File file = new File(dir, name);

            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("format", "sketchware-app-settings");
            wrapper.put("version", 1);
            wrapper.put("settings", ConfigActivity.DataStore.getInstance().exportableSettings());
            FileUtil.writeFile(file.getAbsolutePath(), getGson().toJson(wrapper));
            AppLog.i("Settings", "Exported to " + file.getName());

            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Settings exported")
                    .setMessage("Saved to\n/Internal storage/sketch_nws/settings/" + name)
                    .setPositiveButton("Close", null)
                    .setNegativeButton("Share", (d, w) -> {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("application/json");
                        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(activity,
                                activity.getPackageName() + ".provider", file));
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        activity.startActivity(Intent.createChooser(intent, "Share settings"));
                    })
                    .show();
        } catch (Throwable t) {
            SketchwareUtil.toastError("Couldn't export settings: " + t.getMessage(), android.widget.Toast.LENGTH_LONG);
        }
    }

    public static void importSettings(Activity activity, FragmentManager fragmentManager, Runnable onImported) {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"json"});
        options.setTitle("Select an exported settings file");
        File dir = new File(wq.s(), "settings");
        if (dir.isDirectory()) {
            options.setInitialDirectory(dir.getAbsolutePath());
        }
        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                try {
                    Map<String, Object> wrapper = getGson().fromJson(FileUtil.readFile(file.getAbsolutePath()), mod.hey.studios.util.Helper.TYPE_MAP);
                    Object inner = wrapper == null ? null : wrapper.get("settings");
                    if (!(inner instanceof Map<?, ?> values)) {
                        throw new IllegalArgumentException("This isn't a settings export.");
                    }
                    int applied = ConfigActivity.DataStore.getInstance().importSettings(values);
                    AppLog.i("Settings", "Imported " + applied + " settings from " + file.getName());
                    SketchwareUtil.toast("Imported " + applied + " settings");
                    onImported.run();
                } catch (Throwable t) {
                    SketchwareUtil.toastError("Couldn't import: " + t.getMessage(), android.widget.Toast.LENGTH_LONG);
                }
            }
        };
        new FilePickerDialogFragment(options, callback).show(fragmentManager, "file_picker");
    }

    public static void confirmReset(Activity activity, Runnable onReset) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Reset settings?")
                .setMessage("Every App Settings option goes back to its default. " +
                        "Projects, backups and your keystore file aren't touched.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> {
                    ConfigActivity.DataStore.getInstance().resetToDefaults();
                    AppLog.i("Settings", "Reset to defaults");
                    SketchwareUtil.toast("Settings reset");
                    onReset.run();
                })
                .show();
    }
}
