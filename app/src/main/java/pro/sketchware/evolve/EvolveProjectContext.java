package pro.sketchware.evolve;

import android.content.Context;

import com.besome.sketch.beans.BlockCollectionBean;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import a.a.a.Mp;
import a.a.a.Pp;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.utility.FileUtil;

/**
 * Reads what the user already has, as the {@link ImportPlanner.Context} the planner compares a post with: a
 * project's package, minimum Android, Java and layout files and libraries, and the collections and custom components
 * that belong to the app. Disk reads only; call it off the main thread.
 */
public final class EvolveProjectContext {

    private EvolveProjectContext() {
    }

    /** @return the project's folder for Java and Kotlin files */
    public static File javaDir(String scId) {
        return new File(FileUtil.getExternalStorageDir() + "/.sketch_nws/data/" + scId + "/files/java");
    }

    /** @return the project's folder for custom layout files */
    public static File layoutDir(String scId) {
        return new File(FileUtil.getExternalStorageDir() + "/.sketch_nws/data/" + scId + "/files/resource/layout");
    }

    /** @return the file that holds the app's custom components */
    public static File componentList() {
        return new File(wq.getCustomComponent());
    }

    /** @param scId the chosen project, or null when the post doesn't go into a project */
    public static ImportPlanner.Context load(Context context, String scId) {
        String projectName = "";
        String packageName = "";
        int minSdk = 0;
        Set<String> javaFiles = new HashSet<>();
        Set<String> layoutFiles = new HashSet<>();
        Set<String> libraries = new HashSet<>();
        if (scId != null) {
            HashMap<String, Object> project = lC.b(scId);
            projectName = yB.c(project, "my_ws_name");
            packageName = yB.c(project, "my_sc_pkg_name");
            try {
                minSdk = new ProjectSettings(scId).getMinSdkVersion();
            } catch (RuntimeException ignored) {
                // unknown: no check
            }
            names(javaDir(scId), javaFiles);
            names(layoutDir(scId), layoutFiles);
            try {
                for (HashMap<String, Object> library : LocalLibrariesUtil.getLocalLibraries(scId)) {
                    Object name = library.get("name");
                    if (name != null) libraries.add(name.toString());
                }
            } catch (RuntimeException ignored) {
                // no libraries read: the check lists more than it should rather than less
            }
        }
        Set<String> blockNames = new HashSet<>();
        List<String> blocksJson = new ArrayList<>();
        try {
            blockNames.addAll(Mp.h().g());
            for (BlockCollectionBean bean : Mp.h().f()) {
                if (bean.blocks != null) blocksJson.add(SharedBlockCodec.encode(bean.blocks));
            }
        } catch (RuntimeException ignored) {
            // collections not available: nothing to compare with
        }
        Set<String> moreBlockNames = new HashSet<>();
        try {
            moreBlockNames.addAll(Pp.h().g());
        } catch (RuntimeException ignored) {
            // as above
        }
        Set<String> componentNames = new HashSet<>();
        Set<String> componentIds = new HashSet<>();
        readComponents(componentList(), componentNames, componentIds);
        return new ImportPlanner.Context(projectName, packageName, minSdk, javaFiles, layoutFiles, libraries, blockNames, moreBlockNames,
                componentNames, componentIds, blocksJson);
    }

    private static void names(File dir, Set<String> into) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile()) into.add(file.getName());
        }
    }

    private static void readComponents(File file, Set<String> names, Set<String> ids) {
        if (!file.isFile()) return;
        try {
            JsonElement parsed = JsonParser.parseString(FileUtil.readFile(file.getAbsolutePath()));
            if (!parsed.isJsonArray()) return;
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                if (element.getAsJsonObject().has("name")) names.add(element.getAsJsonObject().get("name").getAsString());
                if (element.getAsJsonObject().has("id")) ids.add(element.getAsJsonObject().get("id").getAsString());
            }
        } catch (RuntimeException ignored) {
            // a damaged list: nothing to compare with, and merging it later refuses to touch it
        }
    }

    static String json(Object value) {
        return new Gson().toJson(value);
    }
}
