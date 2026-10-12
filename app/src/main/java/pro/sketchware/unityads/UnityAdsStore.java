package pro.sketchware.unityads;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
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

    /** The AndroidManifest.xml contents of the SDK and of the libraries downloaded with it that the project uses. */
    public static List<String> readSdkManifests(String scId) {
        List<String> manifests = new ArrayList<>();
        for (HashMap<String, Object> library : LocalLibrariesUtil.getLocalLibraries(scId)) {
            if (!UnityAdsSdk.isSdkRelated(library)) {
                continue;
            }
            Object path = library.get("manifestPath");
            File manifest = path != null ? new File(path.toString())
                    : new File(LocalLibrariesUtil.getLocalLibsRoot(), library.get("name") + "/AndroidManifest.xml");
            if (manifest.isFile()) {
                manifests.add(FileUtil.readFile(manifest.getAbsolutePath()));
            }
        }
        return manifests;
    }

    /**
     * Adds the SDK's manifest entries and the helper's start-up provider to a generated manifest, when the project
     * uses Unity Ads; returns {@code manifest} unchanged otherwise.
     */
    public static String applyToManifest(String scId, String packageName, String manifest) {
        if (!isActive(scId)) {
            return manifest;
        }
        return UnityAdsManifest.merge(manifest, readSdkManifests(scId), packageName);
    }
}
