package pro.sketchware.evolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ImportFilesTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static String component(String name, String id) {
        return "{\"name\":\"" + name + "\",\"id\":\"" + id + "\",\"icon\":\"i\"}";
    }

    @Test
    public void plainFileNamesOnly() {
        assertTrue(ImportFiles.isPlainFileName("Helper.java"));
        assertTrue(ImportFiles.isPlainFileName("login_screen.xml"));
        for (String bad : new String[]{"", ".", "..", "../Evil.java", "a/b.java", "a\\b.java", "C:evil", "a\u0000b", "/etc/passwd"}) {
            assertFalse(bad, ImportFiles.isPlainFileName(bad));
        }
        assertFalse(ImportFiles.isPlainFileName(null));
    }

    @Test
    public void writeCreatesTheFolderAndReportsNoPrevious() throws IOException {
        File dir = new File(folder.getRoot(), "files/java");
        assertNull(ImportFiles.write(dir, "A.java", "class A {}"));
        assertEquals("class A {}", Files.readString(new File(dir, "A.java").toPath()));
        assertFalse(new File(dir, "A.java.evolve-tmp").exists());
    }

    @Test
    public void writeKeepsTheOldContentForUndo() throws IOException {
        File dir = folder.newFolder("java");
        ImportFiles.write(dir, "A.java", "old");
        String previous = ImportFiles.write(dir, "A.java", "new");
        assertEquals("old", previous);
        ImportFiles.restore(dir, "A.java", previous);
        assertEquals("old", Files.readString(new File(dir, "A.java").toPath()));
    }

    @Test
    public void restoringANewFileRemovesIt() throws IOException {
        File dir = folder.newFolder("java");
        String previous = ImportFiles.write(dir, "A.java", "x");
        ImportFiles.restore(dir, "A.java", previous);
        assertFalse(new File(dir, "A.java").exists());
    }

    @Test
    public void aNameThatEscapesTheFolderWritesNothing() throws IOException {
        File dir = folder.newFolder("java");
        try {
            ImportFiles.write(dir, "../Evil.java", "x");
            fail();
        } catch (IOException expected) {
            assertFalse(new File(folder.getRoot(), "Evil.java").exists());
        }
    }

    @Test
    public void componentsAreAppendedAndNotAddedTwice() throws IOException {
        File list = new File(folder.getRoot(), "components/list.json");
        assertNull(ImportFiles.mergeComponents(list, "[" + component("Chart", "c1") + "]", false));
        String previous = ImportFiles.mergeComponents(list, "[" + component("Gauge", "g1") + "," + component("Chart", "c1") + "]", false);
        assertTrue(previous.contains("Chart"));
        String now = Files.readString(list.toPath());
        assertEquals(2, now.split("\"name\"", -1).length - 1);
        assertTrue(now.contains("Gauge"));
    }

    @Test
    public void replaceDropsTheComponentsWithTheSameNameOrId() throws IOException {
        File list = folder.newFile("list.json");
        Files.writeString(list.toPath(), "[" + component("Chart", "c1") + "," + component("Other", "o1") + "]");
        ImportFiles.mergeComponents(list, "[" + component("chart", "zz") + "]", true);
        String now = Files.readString(list.toPath());
        assertFalse(now.contains("\"c1\""));
        assertTrue(now.contains("Other"));
        assertTrue(now.contains("\"zz\""));
    }

    @Test
    public void aDamagedComponentListIsLeftAloneAndUndoRestoresTheOldOne() throws IOException {
        File list = folder.newFile("list.json");
        Files.writeString(list.toPath(), "{ not json");
        try {
            ImportFiles.mergeComponents(list, "[" + component("A", "a") + "]", false);
            fail();
        } catch (IOException expected) {
            assertEquals("{ not json", Files.readString(list.toPath()));
        }
        File fresh = new File(folder.getRoot(), "fresh.json");
        String previous = ImportFiles.mergeComponents(fresh, "[" + component("A", "a") + "]", false);
        ImportFiles.restoreList(fresh, previous);
        assertFalse(fresh.exists());
    }

    @Test
    public void uniqueFileNamesKeepTheExtension() throws IOException {
        File dir = folder.newFolder("dl");
        assertEquals("a.java", ImportFiles.uniqueFileName(dir, "a.java"));
        Files.writeString(new File(dir, "a.java").toPath(), "x");
        assertEquals("a-2.java", ImportFiles.uniqueFileName(dir, "a.java"));
        Files.writeString(new File(dir, "a-2.java").toPath(), "x");
        assertEquals("a-3.java", ImportFiles.uniqueFileName(dir, "a.java"));
    }
}
