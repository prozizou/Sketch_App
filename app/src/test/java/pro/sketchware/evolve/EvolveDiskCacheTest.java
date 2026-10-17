package pro.sketchware.evolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class EvolveDiskCacheTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static SharedBlock post(String id, long createdAt) {
        PostMeta meta = new PostMeta(List.of("ui"), "1.0", 26, List.of("gson"), List.of(), 5, 3, 2, "class A {}", "A.java");
        return new SharedBlock(id, "Name " + id, "desc", "u1", "Ann", createdAt, 160, 1, List.of(), false, 7, "", SharedKind.JAVA, "", "", meta);
    }

    @Test
    public void whatWasSavedComesBackWithItsCounters() throws IOException {
        File file = folder.newFile("feed.json");
        SharedBlock block = new SharedBlock("b1", "Toast", "d", "u2", "Bob", 11, 150, 2, List.of("if", "doToast"), true, 4, "[{}]");
        EvolveDiskCache.save(file, List.of(post("p1", 20), block));
        List<SharedBlock> back = EvolveDiskCache.load(file);
        assertEquals(2, back.size());
        SharedBlock java = back.get(0);
        assertEquals("p1", java.id());
        assertEquals(20, java.createdAt());
        assertEquals(7, java.downloads());
        assertEquals(3, java.meta().likes());
        assertEquals(2, java.meta().commentCount());
        assertEquals(5, java.meta().updatedAt());
        assertEquals("class A {}", java.meta().code());
        assertEquals(List.of("ui"), java.meta().tags());
        SharedBlock blocks = back.get(1);
        assertEquals(SharedKind.BLOCKS, blocks.kind());
        assertEquals("[{}]", blocks.blocksJson());
        assertTrue(blocks.usesRawCode());
        assertEquals(List.of("if", "doToast"), blocks.opCodes());
    }

    @Test
    public void aMissingOrDamagedFileGivesAnEmptyList() throws IOException {
        assertTrue(EvolveDiskCache.load(new File(folder.getRoot(), "none.json")).isEmpty());
        File damaged = folder.newFile("bad.json");
        Files.writeString(damaged.toPath(), "{ this is not json");
        assertTrue(EvolveDiskCache.load(damaged).isEmpty());
        File empty = folder.newFile("empty.json");
        assertTrue(EvolveDiskCache.load(empty).isEmpty());
    }

    @Test
    public void onlyTheNewestPostsAreKept() throws IOException {
        File file = folder.newFile("many.json");
        List<SharedBlock> posts = new ArrayList<>();
        for (int i = 0; i < 60; i++) posts.add(post("p" + i, 100 - i));
        EvolveDiskCache.save(file, posts);
        List<SharedBlock> back = EvolveDiskCache.load(file);
        assertEquals(EvolveDiskCache.MAX_POSTS, back.size());
        assertEquals("p0", back.get(0).id());
    }

    @Test
    public void bigPostsAreCutByTotalSize() throws IOException {
        File file = folder.newFile("big.json");
        List<SharedBlock> posts = new ArrayList<>();
        String big = "x".repeat(SharedBlockRules.MAX_JSON_LENGTH);
        for (int i = 0; i < 20; i++) {
            PostMeta meta = new PostMeta(List.of(), "", 0, List.of(), List.of(), 0, 0, 0, big, "A.java");
            posts.add(new SharedBlock("p" + i, "n", "", "u", "a", i, 1, 1, List.of(), false, 0, "", SharedKind.JAVA, "", "", meta));
        }
        EvolveDiskCache.save(file, posts);
        int kept = EvolveDiskCache.load(file).size();
        assertTrue(kept >= 1 && kept <= 8);
    }

    @Test
    public void savingLeavesNoTemporaryFileAndClearRemovesTheFile() throws IOException {
        File file = new File(folder.getRoot(), "feed.json");
        EvolveDiskCache.save(file, List.of(post("p1", 1)));
        assertTrue(file.isFile());
        assertFalse(new File(folder.getRoot(), "feed.json.tmp").exists());
        EvolveDiskCache.clear(file);
        assertFalse(file.exists());
    }
}
