package pro.sketchware.blocks.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlockLibraryTest {
    private static Map<String, Object> palette(String name, String color) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("color", color);
        return p;
    }

    private static Map<String, Object> block(String name, String palette) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", name);
        b.put("type", " ");
        b.put("typeName", "");
        b.put("spec", name + " %s");
        b.put("color", "#FF0000");
        b.put("code", name + "(%s);");
        b.put("palette", palette);
        return b;
    }

    /** Three palettes (ids 9, 10, 11) and a block in the recycle bin, as written by older versions. */
    private static BlockLibrary sample() {
        long[] now = {1000};
        return new BlockLibrary(
                List.of(palette("Toast", "#FF5722"), palette("Files", "#2196F3"), palette("Math", "#4CAF50")),
                List.of(block("toastA", "9"), block("fileA", "10"), block("toastB", "9"), block("mathA", "11"),
                        block("fileB", "10"), block("old", "-1")),
                () -> now[0]++);
    }

    private static List<String> namesIn(BlockLibrary lib, int paletteIndex) {
        List<String> names = new ArrayList<>();
        for (int i : lib.blockIndicesOf(paletteIndex)) names.add(lib.blocks().get(i).get("name").toString());
        return names;
    }

    @Test
    public void countsAreComputedFromTheFiles() {
        BlockLibrary lib = sample();
        assertEquals(3, lib.paletteCount());
        assertEquals(5, lib.blockCount());
        assertEquals(1, lib.recycleBinCount());
        assertEquals(2, lib.blockCount(0));
        assertEquals(1, lib.blockCount(2));
    }

    @Test
    public void legacyNumericPaletteEntriesAreRead() {
        Map<String, Object> b = block("x", "9");
        b.put("palette", 9.0); // Gson reads numbers as doubles
        assertEquals(Integer.valueOf(9), BlockLibrary.paletteIdOf(b));
        b.put("palette", "abc");
        assertEquals(null, BlockLibrary.paletteIdOf(b));
    }

    @Test
    public void insertingAPaletteRenumbersTheBlocksAfterIt() {
        BlockLibrary lib = sample();
        int index = lib.createPalette("New", "#000000", 1);
        assertEquals(1, index);
        assertEquals(List.of("toastA", "toastB"), namesIn(lib, 0));
        assertEquals(List.of(), namesIn(lib, 1));
        assertEquals(List.of("fileA", "fileB"), namesIn(lib, 2));
        assertEquals(List.of("mathA"), namesIn(lib, 3));
        assertEquals(1, lib.recycleBinCount());
        assertTrue(lib.modified(1) > 0);
    }

    @Test
    public void movingAPaletteKeepsEveryBlockWithItsPalette() {
        BlockLibrary lib = sample();
        lib.movePalette(0, 2); // Toast to the end
        assertEquals("Files", lib.name(0));
        assertEquals("Toast", lib.name(2));
        assertEquals(List.of("fileA", "fileB"), namesIn(lib, 0));
        assertEquals(List.of("mathA"), namesIn(lib, 1));
        assertEquals(List.of("toastA", "toastB"), namesIn(lib, 2));
        lib.movePalette(2, 0);
        assertEquals(List.of("toastA", "toastB"), namesIn(lib, 0));
        assertEquals(1, lib.recycleBinCount());
    }

    @Test
    public void deletingAPaletteToTheBinKeepsItsBlocksAndRenumbersTheRest() {
        BlockLibrary lib = sample();
        lib.deletePalette(0, true);
        assertEquals(2, lib.paletteCount());
        assertEquals(3, lib.recycleBinCount());
        assertEquals(List.of("fileA", "fileB"), namesIn(lib, 0));
        assertEquals(List.of("mathA"), namesIn(lib, 1));
    }

    @Test
    public void deletingAPalettePermanentlyRemovesItsBlocks() {
        BlockLibrary lib = sample();
        lib.deletePalette(1, false);
        assertEquals(4, lib.blocks().size());
        assertEquals(List.of("mathA"), namesIn(lib, 1));
        assertEquals(-1, lib.indexOfBlock("fileA"));
    }

    @Test
    public void restoringFromTheBinDoesNotDisturbOtherBlocks() {
        BlockLibrary lib = sample();
        int old = lib.indexOfBlock("old");
        lib.moveBlocks(List.of(old), 2);
        assertEquals(List.of("mathA", "old"), namesIn(lib, 2));
        // The block that used to be last (old itself here) moved; the order of every palette is unchanged.
        assertEquals(List.of("toastA", "toastB"), namesIn(lib, 0));
        assertEquals(List.of("fileA", "fileB"), namesIn(lib, 1));
        assertEquals(0, lib.recycleBinCount());
    }

    @Test
    public void movingBlocksKeepsTheirOrderAndOthersPlaces() {
        BlockLibrary lib = sample();
        lib.moveBlocks(List.of(lib.indexOfBlock("fileB"), lib.indexOfBlock("fileA")), 0);
        assertEquals(List.of("toastA", "toastB", "fileA", "fileB"), namesIn(lib, 0));
        assertEquals(List.of(), namesIn(lib, 1));
        assertEquals(List.of("mathA"), namesIn(lib, 2));
    }

    @Test
    public void selectiveRestoreAndEmptyBin() {
        BlockLibrary lib = sample();
        lib.moveBlocksToRecycleBin(List.of(lib.indexOfBlock("toastA"), lib.indexOfBlock("mathA")));
        assertEquals(3, lib.recycleBinCount());
        lib.moveBlocks(List.of(lib.indexOfBlock("mathA")), 2);
        assertEquals(2, lib.recycleBinCount());
        assertEquals(2, lib.emptyRecycleBin());
        assertEquals(0, lib.recycleBinCount());
        assertEquals(-1, lib.indexOfBlock("toastA"));
        assertEquals(List.of("mathA"), namesIn(lib, 2));
    }

    @Test
    public void duplicatesGetUniqueNames() {
        BlockLibrary lib = sample();
        int copy = lib.duplicateBlock(lib.indexOfBlock("toastA"));
        assertEquals("toastA_copy", lib.blocks().get(copy).get("name"));
        int copy2 = lib.duplicateBlock(lib.indexOfBlock("toastA"));
        assertEquals("toastA_copy2", lib.blocks().get(copy2).get("name"));
        assertEquals(List.of("toastA", "toastA_copy2", "toastA_copy", "toastB"), namesIn(lib, 0));
    }

    @Test
    public void duplicatingAPaletteCopiesItsBlocks() {
        BlockLibrary lib = sample();
        int copy = lib.duplicatePalette(0);
        assertEquals(1, copy);
        assertEquals("Toast copy", lib.name(1));
        assertEquals(List.of("toastA_copy", "toastB_copy"), namesIn(lib, 1));
        assertEquals(List.of("fileA", "fileB"), namesIn(lib, 2));
    }

    @Test
    public void favoritesFoldersAndExtraKeysOnly() {
        BlockLibrary lib = sample();
        lib.setFavorite(1, true);
        lib.setFolder(1, " IO ");
        assertTrue(lib.isFavorite(1));
        assertEquals("IO", lib.folder(1));
        assertEquals(List.of("IO"), lib.folders());
        lib.setFavorite(1, false);
        lib.setFolder(1, "");
        // Back to exactly the original keys: nothing left behind for older readers.
        assertEquals(List.of("name", "color"), new ArrayList<>(lib.palettes().get(1).keySet()));
    }

    @Test
    public void importValidatesAndResolvesConflicts() {
        BlockLibrary lib = sample();
        Map<String, Object> noSpec = block("bad", "9");
        noSpec.remove("spec");
        Map<String, Object> badType = block("weird", "9");
        badType.put("type", "zz");
        List<Map<String, Object>> file = List.of(block("toastA", "3"), block("fresh", "3"), noSpec, badType,
                block("fresh", "3"), block("1nvalid", "3"));
        assertEquals(4, BlockLibrary.validate(file).size());

        BlockLibrary.ImportResult skip = lib.importBlocks(file, 2, BlockLibrary.Conflict.SKIP);
        assertEquals(1, skip.added());
        assertEquals(1, skip.skipped());
        assertEquals(List.of("mathA", "fresh"), namesIn(lib, 2));

        BlockLibrary renaming = sample();
        BlockLibrary.ImportResult rename = renaming.importBlocks(file, 2, BlockLibrary.Conflict.RENAME);
        assertEquals(2, rename.added());
        assertEquals(List.of("mathA", "toastA_copy", "fresh"), namesIn(renaming, 2));

        BlockLibrary replacing = sample();
        replacing.importBlocks(file, 2, BlockLibrary.Conflict.REPLACE);
        assertEquals(List.of("toastB"), namesIn(replacing, 0));
        assertEquals(List.of("mathA", "toastA", "fresh"), namesIn(replacing, 2));
    }

    @Test
    public void exportIsTheBlockListFormat() {
        BlockLibrary lib = sample();
        List<Map<String, Object>> export = lib.exportPalette(1);
        assertEquals(2, export.size());
        assertEquals("fileA", export.get(0).get("name"));
        assertEquals("10", export.get(0).get("palette"));
        // A copy: changing it doesn't change the library.
        export.get(0).put("name", "changed");
        assertEquals(1, lib.indexOfBlock("fileA"));
    }

    @Test
    public void searchMatchesOpcodeSpecCodeAndKeyword() {
        Map<String, Object> b = block("showToast", "9");
        b.put("imports", "android.widget.Toast");
        assertTrue(BlockLibrary.blockMatches(b, "SHOWTOAST"));
        assertTrue(BlockLibrary.blockMatches(b, "showtoast %s"));
        assertTrue(BlockLibrary.blockMatches(b, "android.widget"));
        assertFalse(BlockLibrary.blockMatches(b, "intent"));
    }
}
