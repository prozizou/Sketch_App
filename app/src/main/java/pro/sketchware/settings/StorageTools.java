package pro.sketchware.settings;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import a.a.a.wq;
import mod.hey.studios.project.backup.BackupFactory;
import pro.sketchware.utility.FileUtil;

/**
 * Cache cleanup and disk usage reporting.
 */
public final class StorageTools {
    private StorageTools() {
    }

    /**
     * Everything that is regenerated on demand: app caches, temp files and per-project build output.
     * Project data, resources and backups are never part of this list.
     */
    private static List<File> cacheTargets(Context context) {
        List<File> targets = new ArrayList<>();
        targets.add(context.getCacheDir());
        File external = context.getExternalCacheDir();
        if (external != null) {
            targets.add(external);
        }
        targets.add(new File(wq.q(), "temp"));

        File[] projectBuildFolders = new File(wq.q(), "mysc").listFiles();
        if (projectBuildFolders != null) {
            for (File folder : projectBuildFolders) {
                // "list" holds the project files themselves, everything else is build output.
                if (folder.isDirectory() && !folder.getName().equals("list")) {
                    targets.add(folder);
                }
            }
        }
        return targets;
    }

    public static long cacheSize(Context context) {
        long total = 0;
        for (File target : cacheTargets(context)) {
            total += FileUtil.getFileSize(target);
        }
        return total;
    }

    /**
     * Deletes the contents of every cache target.
     *
     * @return the number of bytes freed.
     */
    public static long clearCache(Context context) {
        long before = cacheSize(context);
        for (File target : cacheTargets(context)) {
            File[] children = target.listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                FileUtil.deleteFile(child.getAbsolutePath());
            }
        }
        long freed = Math.max(0, before - cacheSize(context));
        AppLog.i("Storage", "Cache cleared, freed " + FileUtil.formatFileSize(freed));
        return freed;
    }

    /**
     * @return label to size in bytes. Walks the disk, so call it off the main thread.
     */
    public static Map<String, Long> usage(Context context) {
        Map<String, Long> usage = new LinkedHashMap<>();
        usage.put("Project data", FileUtil.getFileSize(new File(wq.q(), "data")));
        usage.put("Resources", FileUtil.getFileSize(new File(wq.q(), "resources")));
        usage.put("Local libraries", FileUtil.getFileSize(new File(wq.q(), "libs")));
        usage.put("Backups", FileUtil.getFileSize(new File(BackupFactory.getBackupDir())));
        usage.put("Cache", cacheSize(context));
        usage.put("Logs", FileUtil.getFileSize(new File(wq.q(), "logs")));
        return usage;
    }
}
