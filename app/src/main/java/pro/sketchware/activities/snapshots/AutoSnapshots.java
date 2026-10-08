package pro.sketchware.activities.snapshots;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.activities.snapshots.ProjectSnapshots.Kind;
import pro.sketchware.settings.AppLog;
import pro.sketchware.utility.FileUtil;

/**
 * Takes snapshots in the background at the moments the project is about to change or has just been saved.
 */
public final class AutoSnapshots {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    /** Saves can come every few seconds with auto-save on, so they're spaced out. */
    private static final long MIN_INTERVAL_BETWEEN_SAVES_MS = 5 * 60 * 1000L;

    private AutoSnapshots() {
    }

    public static ProjectSnapshots forProject(String scId) {
        return new ProjectSnapshots(new File(FileUtil.getExternalStorageDir(), ".sketch_nws"), scId);
    }

    public static void afterSave(String scId) {
        run(scId, Kind.SAVE, MIN_INTERVAL_BETWEEN_SAVES_MS);
    }

    public static void beforeBuild(String scId) {
        run(scId, Kind.BUILD, 0);
    }

    private static void run(String scId, Kind kind, long minIntervalMillis) {
        if (!ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_AUTO_SNAPSHOTS)) return;
        EXECUTOR.execute(() -> {
            try {
                forProject(scId).createIfChanged(kind, minIntervalMillis);
            } catch (Throwable t) {
                AppLog.e("Snapshots", "Snapshot of " + scId + " failed: " + t);
            }
        });
    }
}
