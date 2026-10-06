package pro.sketchware.settings;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.content.Context;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.project.backup.BackupFactory;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.utility.FileUtil;

/**
 * Automatic backups taken after a project is saved and closed, with per-project retention.
 * Only backups created here are ever pruned; backups made by hand are never deleted.
 */
public final class AutoBackup {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final TypeToken<HashMap<String, ArrayList<String>>> REGISTRY_TYPE = new TypeToken<>() {
    };

    private AutoBackup() {
    }

    private static File getRegistryFile() {
        return new File(FileUtil.getExternalStorageDir(), ".sketch_nws/data/auto_backups.json");
    }

    public static void runIfEnabled(Context context, String scId) {
        if (!ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_AUTO_BACKUP)) {
            return;
        }
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> backUp(appContext, scId));
    }

    private static long minimumIntervalMillis() {
        String frequency = ConfigActivity.getStringSetting(ConfigActivity.SETTING_AUTO_BACKUP_FREQUENCY);
        return switch (frequency) {
            case "daily" -> 24L * 60 * 60 * 1000;
            case "weekly" -> 7L * 24 * 60 * 60 * 1000;
            default -> 0L;
        };
    }

    private static synchronized void backUp(Context context, String scId) {
        String lastKey = "auto-backup-last-" + scId;
        long now = System.currentTimeMillis();
        long last = parseLong(ConfigActivity.DataStore.getInstance().getString(lastKey, null));
        if (now - last < minimumIntervalMillis()) {
            return;
        }

        try {
            HashMap<String, Object> metadata = lC.b(scId);
            String projectName = yB.c(metadata, "my_ws_name");
            if (projectName.isEmpty()) {
                return;
            }
            BackupFactory factory = new BackupFactory(scId);
            factory.setBackupLocalLibs(false);
            factory.setBackupCustomBlocks(false);
            factory.backup(context, projectName);

            File out = factory.getOutFile();
            if (out == null) {
                AppLog.e("AutoBackup", "Backup of " + projectName + " failed: " + factory.getError());
                return;
            }
            ConfigActivity.DataStore.getInstance().putString(lastKey, String.valueOf(now));
            Map<String, ArrayList<String>> registry = loadRegistry();
            ArrayList<String> mine = registry.computeIfAbsent(scId, k -> new ArrayList<>());
            mine.add(out.getAbsolutePath());
            prune(mine, ConfigActivity.getIntSetting(ConfigActivity.SETTING_BACKUP_RETENTION, 5));
            saveRegistry(registry);
            AppLog.i("AutoBackup", "Backed up " + projectName + " to " + out.getName());
        } catch (Throwable t) {
            AppLog.e("AutoBackup", "Backup failed: " + t);
        }
    }

    /**
     * Deletes the oldest backups of {@code paths} (oldest first) until at most {@code keep} remain.
     * {@code keep <= 0} means unlimited.
     */
    private static void prune(List<String> paths, int keep) {
        paths.removeIf(path -> !new File(path).isFile());
        if (keep <= 0) {
            return;
        }
        while (paths.size() > keep) {
            String oldest = paths.remove(0);
            //noinspection ResultOfMethodCallIgnored
            new File(oldest).delete();
            AppLog.i("AutoBackup", "Pruned " + new File(oldest).getName());
        }
    }

    /**
     * Applies the current retention to every project right now.
     */
    public static synchronized void pruneAll() {
        Map<String, ArrayList<String>> registry = loadRegistry();
        int keep = ConfigActivity.getIntSetting(ConfigActivity.SETTING_BACKUP_RETENTION, 5);
        for (ArrayList<String> paths : registry.values()) {
            prune(paths, keep);
        }
        saveRegistry(registry);
    }

    private static Map<String, ArrayList<String>> loadRegistry() {
        try {
            File file = getRegistryFile();
            if (file.isFile()) {
                HashMap<String, ArrayList<String>> map = getGson().fromJson(FileUtil.readFile(file.getAbsolutePath()), REGISTRY_TYPE.getType());
                if (map != null) {
                    return map;
                }
            }
        } catch (Throwable ignored) {
            // Start with an empty registry.
        }
        return new HashMap<>();
    }

    private static void saveRegistry(Map<String, ArrayList<String>> registry) {
        FileUtil.writeFile(getRegistryFile().getAbsolutePath(), getGson().toJson(registry));
    }

    /**
     * @return all .swb files in the backup directory, newest first.
     */
    public static List<File> listBackups() {
        List<File> found = new ArrayList<>();
        collect(new File(BackupFactory.getBackupDir()), found, 0);
        found.sort(Comparator.comparingLong(File::lastModified).reversed());
        return found;
    }

    private static void collect(File dir, List<File> out, int depth) {
        File[] children = dir.listFiles();
        if (children == null || depth > 3) {
            return;
        }
        Arrays.sort(children);
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, out, depth + 1);
            } else if (child.getName().endsWith("." + BackupFactory.EXTENSION)) {
                out.add(child);
            }
        }
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
