package pro.sketchware.unityads;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import dev.aldi.sayuti.editor.manage.EmbeddedLibs;
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
     * The given class files that are present in the project's local libraries <em>with</em> compiled code
     * (classes.dex next to classes.jar): those are the classes the built app will contain.
     */
    public static Set<String> availableClasses(String scId, Collection<String> classFiles) {
        Set<String> found = new HashSet<>();
        for (HashMap<String, Object> library : LocalLibrariesUtil.getLocalLibraries(scId)) {
            File folder = new File(LocalLibrariesUtil.getLocalLibsRoot(), String.valueOf(library.get("name")));
            File jar = new File(folder, "classes.jar");
            if (!new File(folder, "classes.dex").isFile() || !jar.isFile()) {
                continue;
            }
            try (ZipFile zip = new ZipFile(jar)) {
                for (String classFile : classFiles) {
                    if (zip.getEntry(classFile) != null) {
                        found.add(classFile);
                    }
                }
            } catch (IOException ignored) {
                // An unreadable jar provides nothing; its classes will be reported missing.
            }
        }
        return found;
    }

    /**
     * SDK libraries that carry JARs in their libs/ folder which were never compiled (Unity Ads embeds the OMID
     * measurement SDK this way): their classes are missing from the app, which crashes when the SDK uses them.
     */
    public static List<String> librariesWithUncompiledJars(String scId) {
        List<String> names = new ArrayList<>();
        for (HashMap<String, Object> library : LocalLibrariesUtil.getLocalLibraries(scId)) {
            if (!UnityAdsSdk.isSdkRelated(library)) {
                continue;
            }
            File folder = new File(LocalLibrariesUtil.getLocalLibsRoot(), String.valueOf(library.get("name")));
            if (EmbeddedLibs.hasUncompiled(folder)) {
                names.add(folder.getName());
            }
        }
        return names;
    }

    /** What the SDK needs and the project doesn't have, empty when it can start. */
    public static List<UnityAdsRequirements.Requirement> missingRequirements(String scId) {
        return UnityAdsRequirements.missing(availableClasses(scId, UnityAdsRequirements.classFiles()));
    }
}
