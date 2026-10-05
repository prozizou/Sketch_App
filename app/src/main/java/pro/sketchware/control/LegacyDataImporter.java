package pro.sketchware.control;

import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-time import of the data folders of the previous app ({@code .sketchware}, {@code sketchware})
 * into the new ones ({@code .sketch_nws}, {@code sketch_nws}).
 *
 * The old folders are copied, never moved or deleted, so the old app keeps working. Files that already
 * exist in the new folders are left untouched, which makes the import safe to resume after an interruption.
 * Absolute paths to the old folder stored inside small JSON files are rewritten to the new one.
 */
public final class LegacyDataImporter {

    private static final String[][] FOLDERS = {
            {".sketchware", ".sketch_nws"},
            {"sketchware", "sketch_nws"},
    };
    private static final String MARKER = ".legacy_import_done";
    private static final long MAX_REWRITE_SIZE = 1024 * 1024;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private LegacyDataImporter() {
    }

    /** Starts the import when old data exists, it was not imported yet and the app may read it. */
    public static void runIfNeeded(AppCompatActivity activity, boolean storagePermissionGranted, Runnable onDone) {
        if (!storagePermissionGranted) return;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && !Environment.isExternalStorageManager()) return;

        File root = Environment.getExternalStorageDirectory();
        if (new File(root, FOLDERS[0][1] + File.separator + MARKER).exists()) return;
        if (!hasOldData(root)) return;
        if (!RUNNING.compareAndSet(false, true)) return;

        float dip = activity.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) (24 * dip), (int) (8 * dip), (int) (24 * dip), 0);
        TextView message = new TextView(activity);
        message.setText("Copying your projects and settings from the previous version. Your old data is kept untouched.");
        content.addView(message);
        LinearProgressIndicator progress = new LinearProgressIndicator(activity);
        progress.setMax(100);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (16 * dip);
        content.addView(progress, params);
        TextView status = new TextView(activity);
        status.setPadding(0, (int) (6 * dip), 0, 0);
        status.setText("Preparing…");
        content.addView(status);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Importing your projects")
                .setView(content)
                .setCancelable(false)
                .create();
        dialog.show();

        new Thread(() -> {
            String error = null;
            int copied = 0;
            try {
                List<File[]> jobs = new ArrayList<>();
                for (String[] pair : FOLDERS) {
                    File oldDir = new File(root, pair[0]);
                    if (oldDir.isDirectory()) collect(oldDir, new File(root, pair[1]), jobs);
                }
                int total = jobs.size();
                int lastPercent = -1;
                for (int i = 0; i < total; i++) {
                    File[] job = jobs.get(i);
                    if (copyOne(job[0], job[1])) copied++;
                    int percent = total == 0 ? 100 : (i + 1) * 100 / total;
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        int done = i + 1;
                        int p = percent;
                        activity.runOnUiThread(() -> {
                            progress.setIndeterminate(false);
                            progress.setProgressCompat(p, true);
                            status.setText(String.format(Locale.US, "%d%%  (%d / %d files)", p, done, total));
                        });
                    }
                }
                File marker = new File(root, FOLDERS[0][1] + File.separator + MARKER);
                //noinspection ResultOfMethodCallIgnored
                marker.getParentFile().mkdirs();
                //noinspection ResultOfMethodCallIgnored
                marker.createNewFile();
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            String result = error;
            int copiedFiles = copied;
            activity.runOnUiThread(() -> {
                RUNNING.set(false);
                if (!activity.isDestroyed()) dialog.dismiss();
                if (result == null) {
                    Toast.makeText(activity, "Import finished: " + copiedFiles + " files copied", Toast.LENGTH_LONG).show();
                    if (onDone != null) onDone.run();
                } else if (!activity.isDestroyed()) {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("Import incomplete")
                            .setMessage(result + "\n\nIt will resume the next time you open the app.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }, "legacy-data-import").start();
    }

    private static boolean hasOldData(File root) {
        for (String[] pair : FOLDERS) {
            String[] children = new File(root, pair[0]).list();
            if (children != null && children.length > 0) return true;
        }
        return false;
    }

    private static void collect(File source, File target, List<File[]> jobs) {
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File targetChild = new File(target, child.getName());
            if (child.isDirectory()) {
                collect(child, targetChild, jobs);
            } else {
                jobs.add(new File[]{child, targetChild});
            }
        }
    }

    /** @return true when the file was copied, false when it already existed in the new folder. */
    private static boolean copyOne(File source, File target) throws IOException {
        if (target.exists()) return false;
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent.getAbsolutePath());
        }
        File partial = new File(target.getPath() + ".part");
        if (shouldRewritePaths(source)) {
            String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
            Files.write(partial.toPath(), rewritePaths(text).getBytes(StandardCharsets.UTF_8));
        } else {
            Files.copy(source.toPath(), partial.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    private static boolean shouldRewritePaths(File file) {
        String name = file.getName();
        return file.length() <= MAX_REWRITE_SIZE
                && (name.endsWith(".json") || name.equals("local_library") || name.equals("project_config")
                || name.equals("build_config"));
    }

    private static String rewritePaths(String text) {
        return text.replace("/.sketchware/", "/.sketch_nws/")
                .replace("/.sketchware\"", "/.sketch_nws\"")
                .replace("0/sketchware/", "0/sketch_nws/")
                .replace("sdcard/sketchware/", "sdcard/sketch_nws/");
    }
}
