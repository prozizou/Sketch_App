package pro.sketchware.analysis.fix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.jbk.editor.manage.library.ExcludeBuiltInLibrariesActivity;
import pro.sketchware.analysis.fix.LibraryFixes.Action;
import pro.sketchware.analysis.fix.LibraryFixes.Kind;
import pro.sketchware.analysis.fix.LibraryFixes.Plan;

/** Writes to the project's local library list and exclusions, and takes it back exactly. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class LibraryFixApplierTest {
    private static final String PROJECT = "9001";

    private static HashMap<String, Object> library(String name) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("name", name);
        return map;
    }

    @Before
    public void setUp() throws Exception {
        // BuiltInLibraries reads the app's files dir when it loads: give it the application, as a launch would
        java.lang.reflect.Field context = pro.sketchware.SketchApplication.class.getDeclaredField("mApplicationContext");
        context.setAccessible(true);
        context.set(null, androidx.test.core.app.ApplicationProvider.getApplicationContext());
        File data = LocalLibrariesUtil.getLocalLibFile(PROJECT).getParentFile();
        data.mkdirs();
        LocalLibrariesUtil.rewriteLocalLibFile(PROJECT, new com.google.gson.Gson().toJson(List.of(library("old-ui"), library("keep-me"))));
        ExcludeBuiltInLibrariesActivity.restoreRawConfig(PROJECT, null);
    }

    private static List<String> localNames() {
        return LocalLibrariesUtil.getLocalLibraries(PROJECT).stream().map(m -> m.get("name").toString()).toList();
    }

    @Test
    public void aLocalLibraryLeavesTheListAndComesBackOnUndo() {
        String before = pro.sketchware.utility.FileUtil.readFile(LocalLibrariesUtil.getLocalLibFile(PROJECT).getAbsolutePath());
        Plan plan = new Plan("deps.support-androidx-mix", List.of(new Action(Kind.REMOVE_LOCAL, "old-ui", "old")), List.of());
        LibraryFixApplier.Result result = LibraryFixApplier.apply(PROJECT, plan);
        assertEquals(1, result.removed());
        assertEquals(List.of("keep-me"), localNames());

        LibraryFixApplier.undo(PROJECT, result.snapshot());
        assertEquals(List.of("old-ui", "keep-me"), localNames());
        assertEquals("the file is exactly what it was", before, pro.sketchware.utility.FileUtil.readFile(LocalLibrariesUtil.getLocalLibFile(PROJECT).getAbsolutePath()));
    }

    @Test
    public void aLibraryThatIsNotInTheListIsNotCounted() {
        Plan plan = new Plan("deps.duplicate-classes", List.of(new Action(Kind.REMOVE_LOCAL, "not-there", "x")), List.of());
        LibraryFixApplier.Result result = LibraryFixApplier.apply(PROJECT, plan);
        assertEquals(0, result.removed());
        assertEquals(List.of("old-ui", "keep-me"), localNames());
    }

    @Test
    public void aBuiltInLibraryIsExcludedAndTheExclusionIsTakenBackOnUndo() {
        assertNull(ExcludeBuiltInLibrariesActivity.readRawConfig(PROJECT));
        String name = mod.jbk.build.BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES[0].getName();
        Plan plan = new Plan("deps.support-androidx-mix", List.of(new Action(Kind.EXCLUDE_BUILT_IN, name, "old")), List.of());
        LibraryFixApplier.Result result = LibraryFixApplier.apply(PROJECT, plan);
        assertEquals(1, result.excluded());
        assertTrue(ExcludeBuiltInLibrariesActivity.isExcludingEnabled(PROJECT));
        assertEquals(List.of(name), ExcludeBuiltInLibrariesActivity.getExcludedLibraries(PROJECT).stream().map(l -> l.getName()).toList());

        LibraryFixApplier.undo(PROJECT, result.snapshot());
        assertNull("there was no exclusion file before", ExcludeBuiltInLibrariesActivity.readRawConfig(PROJECT));
        assertFalse(ExcludeBuiltInLibrariesActivity.isExcludingEnabled(PROJECT));
    }

    @Test
    public void existingExclusionsAreKeptWhenAnotherIsAdded() {
        mod.jbk.build.BuiltInLibraries.BuiltInLibrary[] known = mod.jbk.build.BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES;
        ExcludeBuiltInLibrariesActivity.excludeLibraries(PROJECT, List.of(known[0].getName()));
        LibraryFixApplier.apply(PROJECT, new Plan("x", List.of(new Action(Kind.EXCLUDE_BUILT_IN, known[1].getName(), "r")), List.of()));
        assertEquals(List.of(known[0].getName(), known[1].getName()),
                ExcludeBuiltInLibrariesActivity.getExcludedLibraries(PROJECT).stream().map(l -> l.getName()).toList());
    }
}
