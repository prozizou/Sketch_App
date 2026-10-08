package dev.aldi.sayuti.editor.manage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Brings the local libraries a project uses back in line with what is really on the device.
 * <p>
 * A project stores absolute paths (res, jar, dex, manifest...) of each library it uses. When a library is
 * re-downloaded, moved, partly deleted or the project comes from elsewhere, those paths point to nothing and
 * the build stops with "Missing directory detected". Repair rebuilds every entry from the files that exist now
 * and drops the libraries that are no longer installed at all.
 */
public class LocalLibrariesRepair {
    private LocalLibrariesRepair() {
    }

    public static class Result {
        public final ArrayList<HashMap<String, Object>> libraries = new ArrayList<>();
        /** Libraries whose stored paths were out of date and have been rebuilt. */
        public final List<String> repaired = new ArrayList<>();
        /** Libraries that are no longer installed: they cannot be used until downloaded again. */
        public final List<String> removed = new ArrayList<>();
        public int healthy;

        public boolean changedAnything() {
            return !repaired.isEmpty() || !removed.isEmpty();
        }
    }

    public static Result repair(File localLibsRoot, List<HashMap<String, Object>> used) {
        Result result = new Result();
        for (HashMap<String, Object> entry : used) {
            Object nameValue = entry.get("name");
            if (nameValue == null) {
                continue;
            }
            String name = nameValue.toString();
            File folder = new File(localLibsRoot, name);
            if (!folder.isDirectory()) {
                result.removed.add(name);
                continue;
            }
            Object dependency = entry.get("dependency");
            HashMap<String, Object> fresh = describe(localLibsRoot, name, dependency == null ? null : dependency.toString());
            if (fresh.equals(entry)) {
                result.healthy++;
            } else {
                result.repaired.add(name);
            }
            result.libraries.add(fresh);
        }
        return result;
    }

    /** What the project must store for a library, from the files that exist right now. */
    public static HashMap<String, Object> describe(File localLibsRoot, String name, String dependency) {
        File folder = new File(localLibsRoot, name);
        HashMap<String, Object> library = new HashMap<>();
        library.put("name", name);
        if (dependency != null) {
            library.put("dependency", dependency);
        }
        File config = new File(folder, "config");
        if (config.exists()) {
            try {
                library.put("packageName", new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8));
            } catch (java.io.IOException ignored) {
                // Unreadable config: the package name is simply left out, as when the file is missing.
            }
        }
        putIfExists(library, "resPath", new File(folder, "res"));
        putIfExists(library, "jarPath", new File(folder, "classes.jar"));
        putIfExists(library, "dexPath", new File(folder, "classes.dex"));
        putIfExists(library, "manifestPath", new File(folder, "AndroidManifest.xml"));
        putIfExists(library, "pgRulesPath", new File(folder, "proguard.txt"));
        putIfExists(library, "assetsPath", new File(folder, "assets"));
        return library;
    }

    private static void putIfExists(HashMap<String, Object> library, String key, File file) {
        if (file.exists()) {
            library.put(key, file.getAbsolutePath());
        }
    }
}
