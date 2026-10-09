package pro.sketchware.releases;

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
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import pro.sketchware.releases.ReleaseArchive.Release;

public class ReleaseArchiveTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private ReleaseArchive archive;

    @Before
    public void setUp() throws Exception {
        archive = new ReleaseArchive(temp.newFolder("releases"));
    }

    private static Release release(long time, String versionCode, String type) {
        Release r = new Release();
        r.projectId = "605";
        r.projectName = "Demo";
        r.packageName = "com.my.demo";
        r.versionName = "1." + versionCode;
        r.versionCode = versionCode;
        r.type = type;
        r.time = time;
        return r;
    }

    private File file(String name, String content) throws Exception {
        File f = temp.newFile(name);
        Files.writeString(f.toPath(), content);
        return f;
    }

    @Test
    public void aReleaseKeepsItsMappingAndAFingerprintOfTheArtifact() throws Exception {
        File apk = file("demo.apk", "apk bytes");
        File mapping = file("mapping.txt", "com.my.A -> a:\n");
        Release stored = archive.record(release(1000, "7", "apk"), apk, mapping);

        assertEquals(9, stored.sizeBytes);
        assertEquals("a SHA-256 is 64 hex characters", 64, stored.sha256.length());
        assertTrue(stored.hasMapping);
        assertEquals("1000-7-apk", stored.folder);

        File kept = archive.mappingFile(stored);
        assertNotNull(kept);
        assertEquals("com.my.A -> a:\n", Files.readString(kept.toPath()));

        List<Release> listed = archive.list("605");
        assertEquals(1, listed.size());
        assertEquals("1.7", listed.get(0).versionName);
        assertEquals(stored.sha256, listed.get(0).sha256);
        assertNotNull(archive.mappingFile(listed.get(0)));
    }

    @Test
    public void theMappingIsKeptEvenWhenTheNextBuildOverwritesTheOriginal() throws Exception {
        File mapping = file("mapping.txt", "first build");
        Release first = archive.record(release(1000, "7", "apk"), file("a.apk", "a"), mapping);
        Files.writeString(mapping.toPath(), "second build");
        archive.record(release(2000, "8", "apk"), file("b.apk", "bb"), mapping);

        assertEquals("first build", Files.readString(archive.mappingFile(first).toPath()));
    }

    @Test
    public void aReleaseWithoutAMappingSaysSo() throws Exception {
        Release noMapping = archive.record(release(1000, "7", "apk"), file("a.apk", "a"), null);
        assertFalse(noMapping.hasMapping);
        assertNull(archive.mappingFile(noMapping));
        Release emptyMapping = archive.record(release(2000, "8", "apk"), file("b.apk", "b"), file("empty.txt", ""));
        assertFalse(emptyMapping.hasMapping);
    }

    @Test
    public void releasesAreListedNewestFirstAndAreIndependentPerProject() throws Exception {
        archive.record(release(1000, "1", "apk"), file("1.apk", "x"), null);
        archive.record(release(3000, "3", "apk"), file("3.apk", "x"), null);
        archive.record(release(2000, "2", "aab"), file("2.aab", "x"), null);
        Release other = release(1500, "9", "apk");
        other.projectId = "700";
        archive.record(other, file("o.apk", "x"), null);

        assertEquals(List.of("3", "2", "1"), archive.list("605").stream().map(r -> r.versionCode).toList());
        assertEquals(1, archive.list("700").size());
        assertEquals(List.of(), archive.list("999"));
    }

    @Test
    public void sizeChangeComparesWithThePreviousReleaseOfTheSameType() throws Exception {
        archive.record(release(1000, "1", "apk"), file("1.apk", "x".repeat(100)), null);
        archive.record(release(2000, "2", "aab"), file("2.aab", "x".repeat(500)), null);
        archive.record(release(3000, "3", "apk"), file("3.apk", "x".repeat(130)), null);
        List<Release> all = archive.list("605");

        assertEquals(Long.valueOf(30), ReleaseArchive.sizeChange(all, all.get(0)));
        assertNull("the first apk has nothing before it", ReleaseArchive.sizeChange(all, all.get(2)));
        assertNull("the only aab has nothing before it", ReleaseArchive.sizeChange(all, all.get(1)));
    }

    @Test
    public void aDamagedRecordDoesNotHideTheOthers() throws Exception {
        archive.record(release(1000, "1", "apk"), file("1.apk", "x"), null);
        File broken = new File(temp.getRoot(), "releases/605/2000-2-apk");
        assertTrue(broken.mkdirs());
        Files.writeString(new File(broken, "release.json").toPath(), "{ not json");
        assertEquals(1, archive.list("605").size());
    }

    @Test
    public void deleteRemovesTheReleaseAndItsMapping() throws Exception {
        Release stored = archive.record(release(1000, "1", "apk"), file("1.apk", "x"), file("m.txt", "map"));
        archive.delete(stored);
        assertEquals(List.of(), archive.list("605"));
        assertFalse(new File(temp.getRoot(), "releases/605/1000-1-apk").exists());
    }

    @Test(expected = IOException.class)
    public void aReleaseNeedsAProject() throws Exception {
        Release r = release(1, "1", "apk");
        r.projectId = "";
        archive.record(r, file("x.apk", "x"), null);
    }
}
