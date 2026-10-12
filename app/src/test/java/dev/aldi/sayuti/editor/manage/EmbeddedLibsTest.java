package dev.aldi.sayuti.editor.manage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class EmbeddedLibsTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File library() throws IOException {
        File folder = temp.newFolder("unity-ads-v4.21.0");
        assertTrue(new File(folder, "libs").mkdirs());
        return folder;
    }

    @Test
    public void dexNameIsDerivedFromTheJarAndMatchesWhatTheBuildMerges() throws IOException {
        File folder = library();
        File dex = EmbeddedLibs.dexFor(folder, new File(folder, "libs/omsdk-android-1.4.9-release.jar"), 0);
        assertEquals("classes_omsdk-android-1.4.9-release.dex", dex.getName());
        // ProjectBuilder merges files named classes*.dex other than classes.dex.
        assertTrue(dex.getName().startsWith("classes") && dex.getName().endsWith(".dex") && !dex.getName().equals("classes.dex"));
        assertEquals("classes_a_2.dex", EmbeddedLibs.dexFor(folder, new File("a.jar"), 1).getName());
    }

    @Test
    public void listsOnlyJarsWithoutADex() throws IOException {
        File folder = library();
        assertTrue(new File(folder, "libs/b.jar").createNewFile());
        assertTrue(new File(folder, "libs/a.jar").createNewFile());
        assertTrue(new File(folder, "libs/readme.txt").createNewFile());
        assertTrue(new File(folder, "libs/done.jar").createNewFile());
        assertTrue(new File(folder, "classes_done.dex").createNewFile());

        List<File> jars = EmbeddedLibs.jarsToCompile(folder);

        assertEquals(2, jars.size());
        assertEquals("a.jar", jars.get(0).getName());
        assertEquals("b.jar", jars.get(1).getName());
        assertTrue(EmbeddedLibs.hasUncompiled(folder));
    }

    @Test
    public void nothingToDoWithoutALibsFolderOrWhenAllAreCompiled() throws IOException {
        File noLibs = temp.newFolder("plain");
        assertTrue(EmbeddedLibs.jarsToCompile(noLibs).isEmpty());
        assertFalse(EmbeddedLibs.hasUncompiled(noLibs));

        File folder = library();
        assertTrue(new File(folder, "libs/x.jar").createNewFile());
        assertTrue(new File(folder, "classes_x.dex").createNewFile());
        assertFalse(EmbeddedLibs.hasUncompiled(folder));
    }
}
