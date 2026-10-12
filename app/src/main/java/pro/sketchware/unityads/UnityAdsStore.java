package pro.sketchware.unityads;

import java.io.File;

import pro.sketchware.utility.FileUtil;

/** Reads and writes a project's Unity Ads settings, next to its other per-project data. */
public final class UnityAdsStore {
    private static final String FILE_NAME = "unity_ads";

    private UnityAdsStore() {
    }

    public static File getFile(String scId) {
        return new File(FileUtil.getExternalStorageDir() + "/.sketch_nws/data/" + scId + "/" + FILE_NAME);
    }

    public static UnityAdsConfig read(String scId) {
        File file = getFile(scId);
        if (!file.isFile()) {
            return new UnityAdsConfig();
        }
        return UnityAdsConfig.fromJson(FileUtil.readFile(file.getAbsolutePath()));
    }

    public static void write(String scId, UnityAdsConfig config) {
        FileUtil.writeFile(getFile(scId).getAbsolutePath(), config.toJson());
    }

    /** True when the project has Unity Ads switched on with valid settings. */
    public static boolean isActive(String scId) {
        return read(scId).isActive();
    }
}
