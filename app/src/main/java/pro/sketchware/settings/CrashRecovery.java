package pro.sketchware.settings;

import android.app.Activity;
import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.utility.FileUtil;

/**
 * Remembers that the previous session crashed and, at the next launch, offers a way back.
 * Unsaved editor changes are restored by the project editor itself when the project is reopened.
 */
public final class CrashRecovery {
    private CrashRecovery() {
    }

    private static File marker(Context context) {
        return new File(context.getFilesDir(), "last_crash.txt");
    }

    /**
     * Called from the uncaught exception handler: must be quick and must never throw.
     */
    public static void record(Context context, String stackTrace) {
        try {
            FileUtil.writeFile(marker(context).getAbsolutePath(), System.currentTimeMillis() + "\n" + stackTrace);
            AppLog.e("Crash", stackTrace);
        } catch (Throwable ignored) {
            // The process is dying anyway.
        }
    }

    public static void showIfNeeded(Activity activity) {
        File marker = marker(activity);
        if (!marker.isFile()) {
            return;
        }
        String content = FileUtil.readFile(marker.getAbsolutePath());
        //noinspection ResultOfMethodCallIgnored
        marker.delete();
        if (!ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_CRASH_RECOVERY)) {
            return;
        }

        int newline = content.indexOf('\n');
        long time = 0;
        try {
            time = Long.parseLong(newline < 0 ? content.trim() : content.substring(0, newline).trim());
        } catch (NumberFormatException ignored) {
            // Keep 0, shown as unknown time below.
        }
        String stack = newline < 0 ? "" : content.substring(newline + 1);
        String when = time == 0 ? "last time"
                : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(time));

        List<File> backups = AutoBackup.listBackups();
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Recovered from a crash")
                .setMessage("The app closed unexpectedly (" + when + ").\n\n" +
                        "Unsaved editor changes are offered when you reopen the project.")
                .setPositiveButton("OK", null)
                .setNeutralButton("View log", (d, w) -> new MaterialAlertDialogBuilder(activity)
                        .setTitle("Crash log")
                        .setMessage(stack.isEmpty() ? "No details were recorded." : stack)
                        .setPositiveButton("Close", null)
                        .show());
        if (!backups.isEmpty()) {
            File latest = backups.get(0);
            dialog.setNegativeButton("Restore latest backup", (d, w) ->
                    new BackupRestoreManager(activity).doRestore(latest.getAbsolutePath(), false));
        }
        dialog.show();
    }
}
