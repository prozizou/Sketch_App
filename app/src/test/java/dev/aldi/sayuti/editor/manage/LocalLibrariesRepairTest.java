package dev.aldi.sayuti.editor.manage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class LocalLibrariesRepairTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File library(File root, String name, String... files) throws Exception {
        File folder = new File(root, name);
        for (String file : files) {
            File f = new File(folder, file);
            if (file.endsWith("/")) {
                assertTrue(f.mkdirs());
            } else {
                assertTrue(f.getParentFile().exists() || f.getParentFile().mkdirs());
                assertTrue(f.createNewFile());
            }
        }
        folder.mkdirs();
        return folder;
    }

    private HashMap<String, Object> entry(String name, String resPath) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("dependency", "a:b:1");
        map.put("resPath", resPath);
        return map;
    }

    @Test
    public void aStaleResPathIsDroppedWhenTheLibraryHasNoRes() throws Exception {
        File root = temp.newFolder("local_libs");
        library(root, "zoomage", "classes.jar");
        ArrayList<HashMap<String, Object>> used = new ArrayList<>();
        used.add(entry("zoomage", new File(root, "zoomage/res").getAbsolutePath()));

        LocalLibrariesRepair.Result result = LocalLibrariesRepair.repair(root, used);

        assertEquals(1, result.libraries.size());
        assertFalse(result.libraries.get(0).containsKey("resPath"));
        assertEquals(new File(root, "zoomage/classes.jar").getAbsolutePath(), result.libraries.get(0).get("jarPath"));
        assertEquals("a:b:1", result.libraries.get(0).get("dependency"));
        assertEquals(java.util.List.of("zoomage"), result.repaired);
    }

    @Test
    public void aMovedLibraryGetsItsCurrentPaths() throws Exception {
        File root = temp.newFolder("local_libs");
        library(root, "lib", "res/", "classes.dex");
        ArrayList<HashMap<String, Object>> used = new ArrayList<>();
        used.add(entry("lib", "/storage/emulated/0/.sketchware/libs/local_libs/lib/res"));

        LocalLibrariesRepair.Result result = LocalLibrariesRepair.repair(root, used);

        assertEquals(new File(root, "lib/res").getAbsolutePath(), result.libraries.get(0).get("resPath"));
        assertEquals(new File(root, "lib/classes.dex").getAbsolutePath(), result.libraries.get(0).get("dexPath"));
        assertEquals(1, result.repaired.size());
    }

    @Test
    public void aLibraryThatIsNotInstalledAnymoreIsRemoved() throws Exception {
        File root = temp.newFolder("local_libs");
        ArrayList<HashMap<String, Object>> used = new ArrayList<>();
        used.add(entry("gone", new File(root, "gone/res").getAbsolutePath()));

        LocalLibrariesRepair.Result result = LocalLibrariesRepair.repair(root, used);

        assertTrue(result.libraries.isEmpty());
        assertEquals(java.util.List.of("gone"), result.removed);
        assertTrue(result.changedAnything());
    }

    @Test
    public void healthyLibrariesAreLeftAsTheyAre() throws Exception {
        File root = temp.newFolder("local_libs");
        library(root, "ok", "res/", "classes.jar");
        ArrayList<HashMap<String, Object>> used = new ArrayList<>();
        used.add(LocalLibrariesRepair.describe(root, "ok", "x:y:2"));

        LocalLibrariesRepair.Result result = LocalLibrariesRepair.repair(root, used);

        assertEquals(1, result.healthy);
        assertFalse(result.changedAnything());
        assertEquals(used, result.libraries);
    }
}
