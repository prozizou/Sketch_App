package pro.sketchware.blocks.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

public class BlockStoreTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void roundTripKeepsEveryKeyAndKeepsABackup() throws Exception {
        File palettes = temp.newFile("palette.json");
        File blocks = temp.newFile("block.json");
        String paletteJson = "[{\"name\":\"Toast\",\"color\":\"#FF5722\"}]";
        String blockJson = "[{\"name\":\"t\",\"type\":\" \",\"typeName\":\"\",\"spec\":\"t\",\"color\":\"#FF5722\","
                + "\"code\":\"t();\",\"palette\":\"9\",\"spec2\":\"x\",\"imports\":\"a.b\",\"unknownKey\":\"kept\"}]";
        Files.writeString(palettes.toPath(), paletteJson);
        Files.writeString(blocks.toPath(), blockJson);
        BlockStore store = new BlockStore(palettes, blocks, new Gson());
        BlockLibrary lib = store.load();
        lib.setFavorite(0, true);
        store.save(lib);

        BlockLibrary again = store.load();
        assertTrue(again.isFavorite(0));
        assertEquals("kept", again.blocks().get(0).get("unknownKey"));
        assertEquals("9", again.blocks().get(0).get("palette"));
        assertEquals(paletteJson, Files.readString(new File(temp.getRoot(), "palette.json.bak").toPath()));
        assertTrue(!new File(temp.getRoot(), "palette.json.tmp").exists());
    }

    @Test
    public void unreadableFileIsNeverOverwritten() throws Exception {
        File palettes = temp.newFile("palette.json");
        File blocks = temp.newFile("block.json");
        Files.writeString(palettes.toPath(), "{ broken");
        BlockStore store = new BlockStore(palettes, blocks, new Gson());
        try {
            store.load();
            fail("A broken palette.json must not load as an empty list");
        } catch (BlockStore.UnreadableFile expected) {
            assertEquals(palettes, expected.file);
        }
        assertEquals("{ broken", Files.readString(palettes.toPath()));
    }

    @Test
    public void missingFilesAreEmpty() throws Exception {
        BlockStore store = new BlockStore(new File(temp.getRoot(), "a/palette.json"), new File(temp.getRoot(), "a/block.json"), new Gson());
        BlockLibrary lib = store.load();
        assertEquals(0, lib.paletteCount());
        lib.createPalette("First", "#000000", null);
        store.save(lib);
        assertEquals(1, store.load().paletteCount());
    }
}
