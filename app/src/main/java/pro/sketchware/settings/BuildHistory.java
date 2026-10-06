package pro.sketchware.settings;

import static pro.sketchware.utility.GsonUtils.getGson;

import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import a.a.a.yB;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.utility.FileUtil;

/**
 * Persistent list of the most recent project builds, newest first.
 */
public final class BuildHistory {
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELED = "canceled";
    private static final int MAX_ENTRIES = 100;
    private static final TypeToken<ArrayList<Entry>> TYPE = new TypeToken<>() {
    };

    private BuildHistory() {
    }

    private static File getFile() {
        return new File(FileUtil.getExternalStorageDir(), ".sketch_nws/data/build_history.json");
    }

    public static synchronized List<Entry> load() {
        try {
            File file = getFile();
            if (file.isFile()) {
                ArrayList<Entry> list = getGson().fromJson(FileUtil.readFile(file.getAbsolutePath()), TYPE.getType());
                if (list != null) {
                    return list;
                }
            }
        } catch (Throwable ignored) {
            // A corrupt history file just starts over.
        }
        return new ArrayList<>();
    }

    public static synchronized void record(Entry entry) {
        try {
            List<Entry> list = load();
            list.add(0, entry);
            while (list.size() > MAX_ENTRIES) {
                list.remove(list.size() - 1);
            }
            FileUtil.writeFile(getFile().getAbsolutePath(), getGson().toJson(list));
        } catch (Throwable t) {
            AppLog.e("BuildHistory", "Couldn't record build: " + t);
        }
        AppLog.i("Build", entry.project + " " + entry.status + " in " + entry.durationMs + " ms"
                + (entry.detail == null || entry.detail.isEmpty() ? "" : " - " + entry.detail));
    }

    /**
     * Records a finished build and, for a successful one while auto-increment is on, adds 1 to the
     * project's version code so the next build gets a fresh one.
     *
     * @return the new version code, or null if it wasn't changed.
     */
    public static String recordFinishedBuild(Entry entry) {
        record(entry);
        if (!STATUS_SUCCESS.equals(entry.status) || !ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_AUTO_VERSION_CODE)) {
            return null;
        }
        try {
            HashMap<String, Object> metadata = lC.b(entry.scId);
            if (metadata == null) {
                return null;
            }
            int current = Integer.parseInt(yB.c(metadata, "sc_ver_code").trim());
            if (current <= 0 || current >= 2_100_000_000) {
                return null;
            }
            String next = String.valueOf(current + 1);
            metadata.put("sc_ver_code", next);
            lC.b(entry.scId, metadata);
            AppLog.i("Build", entry.project + " version code " + current + " -> " + next);
            return next;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static synchronized void clear() {
        //noinspection ResultOfMethodCallIgnored
        getFile().delete();
    }

    public static class Entry {
        public long time;
        public String scId;
        public String project;
        public String status;
        public String mode;
        public String versionName;
        public String versionCode;
        public long durationMs;
        public String detail;
    }
}
