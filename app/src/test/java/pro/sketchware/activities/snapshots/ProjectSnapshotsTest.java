package pro.sketchware.activities.snapshots;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import pro.sketchware.activities.snapshots.ProjectSnapshots.Kind;
import pro.sketchware.activities.snapshots.ProjectSnapshots.Snapshot;

public class ProjectSnapshotsTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private File root;
    private ProjectSnapshots snapshots;

    private void write(String path, String content) throws Exception {
        File f = new File(root, path);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), content);
    }

    private String read(String path) throws Exception {
        return Files.readString(new File(root, path).toPath());
    }

    @Before
    public void setUp() throws Exception {
        root = temp.newFolder(".sketch_nws");
        snapshots = new ProjectSnapshots(root, "605");
        write("data/605/logic", "blocks v1");
        write("data/605/files/java/Main.java", "class Main {}");
        write("mysc/list/605/project", "meta v1");
        write("resources/images/605/logo.png", "png v1");
        write("data/606/logic", "someone else's project");
        new File(root, "data/605/files/assets").mkdirs();
    }

    @Test
    public void restoreBringsBackTheSnapshottedState() throws Exception {
        Snapshot before = snapshots.create(Kind.MANUAL);

        write("data/605/logic", "blocks v2");
        Files.delete(new File(root, "data/605/files/java/Main.java").toPath());
        write("data/605/files/java/New.java", "class New {}");
        write("resources/sounds/605/beep.mp3", "added later");
        Files.delete(new File(root, "resources/images/605/logo.png").toPath());

        snapshots.restore(before);

        assertEquals("blocks v1", read("data/605/logic"));
        assertEquals("class Main {}", read("data/605/files/java/Main.java"));
        assertFalse(new File(root, "data/605/files/java/New.java").exists());
        assertFalse(new File(root, "resources/sounds/605").exists());
        assertEquals("png v1", read("resources/images/605/logo.png"));
        assertEquals("meta v1", read("mysc/list/605/project"));
        assertTrue("empty folders survive", new File(root, "data/605/files/assets").isDirectory());
    }

    @Test
    public void restoreLeavesOtherProjectsAlone() throws Exception {
        Snapshot s = snapshots.create(Kind.MANUAL);
        write("data/606/logic", "changed");
        snapshots.restore(s);
        assertEquals("changed", read("data/606/logic"));
    }

    @Test
    public void restoreFirstSavesTheCurrentStateSoItCanBeUndone() throws Exception {
        Snapshot old = snapshots.create(Kind.MANUAL);
        write("data/605/logic", "blocks v2");
        snapshots.restore(old);
        assertEquals("blocks v1", read("data/605/logic"));

        Snapshot undo = snapshots.list().stream().filter(s -> s.kind() == Kind.BEFORE_RESTORE).findFirst().orElseThrow();
        snapshots.restore(undo);
        assertEquals("blocks v2", read("data/605/logic"));
    }

    @Test
    public void skipsAutomaticSnapshotWhenNothingChanged() throws Exception {
        assertNotNull(snapshots.createIfChanged(Kind.SAVE, 0));
        assertNull(snapshots.createIfChanged(Kind.BUILD, 0));
        write("data/605/logic", "blocks v2");
        assertNotNull(snapshots.createIfChanged(Kind.BUILD, 0));
        assertEquals(2, snapshots.list().size());
    }

    @Test
    public void honoursMinimumInterval() throws Exception {
        assertNotNull(snapshots.createIfChanged(Kind.SAVE, 0));
        write("data/605/logic", "blocks v2");
        assertNull(snapshots.createIfChanged(Kind.SAVE, 60_000));
    }

    @Test
    public void listsNewestFirstAndPrunesAutomaticOnesButNotManual() throws Exception {
        Snapshot manual = snapshots.create(Kind.MANUAL);
        for (int i = 0; i < ProjectSnapshots.MAX_AUTOMATIC + 5; i++) {
            write("data/605/logic", "v" + i);
            snapshots.create(Kind.SAVE);
        }
        List<Snapshot> all = snapshots.list();
        assertEquals(ProjectSnapshots.MAX_AUTOMATIC + 1, all.size());
        assertTrue(all.contains(manual));
        for (int i = 1; i < all.size(); i++) assertTrue(all.get(i - 1).time() > all.get(i).time());
    }

    @Test
    public void deleteRemovesTheSnapshot() throws Exception {
        Snapshot s = snapshots.create(Kind.MANUAL);
        snapshots.delete(s);
        assertTrue(snapshots.list().isEmpty());
    }

    @Test
    public void refusesArchivesThatTouchOtherPaths() throws Exception {
        File evil = new File(temp.newFolder("snapshots-dir"), "1-manual.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(evil.toPath()))) {
            zip.putNextEntry(new ZipEntry("data/607/logic"));
            zip.write(1);
            zip.closeEntry();
        }
        write("data/605/logic", "keep me");
        try {
            snapshots.restore(new Snapshot(evil, 1, Kind.MANUAL, evil.length()));
            org.junit.Assert.fail("expected IOException");
        } catch (java.io.IOException expected) {
            // fine
        }
        assertEquals("keep me", read("data/605/logic"));
        assertFalse(new File(root, "data/607").exists());
    }
}
