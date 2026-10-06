package mod.hey.studios.project.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Importing a .swb backup must never write outside the folder it is extracted to (Zip Slip).
 */
public class BackupFactoryUnzipTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File swb(String name, Map<String, String> entries) throws IOException {
        File file = temp.newFile(name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                if (!entry.getKey().endsWith("/")) {
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        return file;
    }

    @Test
    public void legitimateBackupIsExtracted() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("project", "encrypted-project-file");
        entries.put("data/", "");
        entries.put("data/view", "view-data");
        entries.put("resources/images/pic.png", "png");
        File archive = swb("good.swb", entries);
        File out = new File(temp.getRoot(), "restore");

        assertTrue(BackupFactory.unzip(archive, out));

        assertEquals("encrypted-project-file", new String(Files.readAllBytes(new File(out, "project").toPath()), StandardCharsets.UTF_8));
        assertEquals("view-data", new String(Files.readAllBytes(new File(out, "data/view").toPath()), StandardCharsets.UTF_8));
        assertTrue(new File(out, "resources/images/pic.png").isFile());
    }

    @Test
    public void entryClimbingOutOfTheFolderIsRefusedAndNothingIsWritten() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("project", "innocent first entry");
        entries.put("../evil.txt", "pwned");
        File archive = swb("evil.swb", entries);
        File out = new File(temp.getRoot(), "restore");

        assertFalse(BackupFactory.unzip(archive, out));

        assertFalse("file escaped the extraction folder", new File(temp.getRoot(), "evil.txt").exists());
        // All names are checked before anything is extracted, so no partial restore is left behind.
        assertFalse(new File(out, "project").exists());
    }

    @Test
    public void deepTraversalIsRefused() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("data/view", "x"); // creates data/, so the "data/.." hop is resolvable as in a real backup
        entries.put("data/../../../.sketch_nws/data/settings.json", "{}");
        File archive = swb("deep.swb", entries);
        File out = new File(temp.getRoot(), "a/b/restore");

        assertFalse(BackupFactory.unzip(archive, out));
        // restore/data/../../../ is "a/", two levels above the extraction folder.
        assertFalse(new File(temp.getRoot(), "a/.sketch_nws/data/settings.json").exists());
    }

    @Test
    public void directoryEntryTraversalIsRefused() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("../outside-dir/", "");
        entries.put("../outside-dir/file", "x");
        File archive = swb("dir.swb", entries);
        File out = new File(temp.getRoot(), "restore");

        assertFalse(BackupFactory.unzip(archive, out));
        assertFalse(new File(temp.getRoot(), "outside-dir").exists());
    }

    @Test
    public void siblingFolderSharingThePrefixIsRefused() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("../restore-evil/file", "x");
        File archive = swb("sibling.swb", entries);
        File out = new File(temp.getRoot(), "restore");

        assertFalse(BackupFactory.unzip(archive, out));
        assertFalse(new File(temp.getRoot(), "restore-evil").exists());
    }

    @Test
    public void notAZipFileFailsCleanly() throws IOException {
        File notZip = temp.newFile("garbage.swb");
        Files.write(notZip.toPath(), "this is not a zip".getBytes(StandardCharsets.UTF_8));
        assertFalse(BackupFactory.unzip(notZip, new File(temp.getRoot(), "restore")));
    }
}
