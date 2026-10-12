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

    /**
     * Names of the SDK's libraries in the project that have no compiled code (classes.dex): their classes would be
     * missing from the app, and the SDK fails to start. Usually a download whose dexing step failed.
     */
    public static List<String> librariesWithoutCode(String scId) {
        List<String> names = new ArrayList<>();
        for (HashMap<String, Object> library : LocalLibrariesUtil.getLocalLibraries(scId)) {
            if (!UnityAdsSdk.isSdkRelated(library)) {
                continue;
            }
            Object dexPath = library.get("dexPath");
            Object jarPath = library.get("jarPath");
            boolean hasCode = dexPath != null && new File(dexPath.toString()).isFile();
            // A library with no jar has no classes at all (resources only): nothing to miss.
            boolean hasClasses = jarPath != null && new File(jarPath.toString()).isFile();
            if (hasClasses && !hasCode) {
                names.add(String.valueOf(library.get("name")));
            }
        }
        return names;
    }
}
