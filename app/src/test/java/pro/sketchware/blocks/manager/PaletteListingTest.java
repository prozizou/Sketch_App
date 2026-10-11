package pro.sketchware.blocks.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.blocks.manager.PaletteListing.Filter;
import pro.sketchware.blocks.manager.PaletteListing.Sort;

public class PaletteListingTest {
    private static Map<String, Object> palette(String name) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("color", "#FFFFFF");
        return p;
    }

    private static Map<String, Object> block(String name, String palette) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", name);
        b.put("spec", name);
        b.put("type", " ");
        b.put("palette", palette);
        return b;
    }

    private static BlockLibrary library() {
        long[] now = {100};
        BlockLibrary lib = new BlockLibrary(List.of(palette("beta"), palette("Alpha"), palette("gamma")),
                List.of(block("b1", "9"), block("g1", "11"), block("g2", "11"), block("g3", "11"), block("trash", "-1")),
                () -> now[0]++);
        lib.touch(0);        // beta changed first
        lib.touch(2);        // then gamma
        lib.setFavorite(1, true);
        return lib;
    }

    private static List<String> names(PaletteListing.Result result) {
        List<String> names = new ArrayList<>();
        for (PaletteListing.PaletteRow row : result.palettes()) names.add(row.name());
        return names;
    }

    @Test
    public void sortsByManualNameDateAndBlocks() {
        BlockLibrary lib = library();
        assertEquals(List.of("beta", "Alpha", "gamma"), names(PaletteListing.build(lib, Filter.ALL, Sort.MANUAL, "")));
        assertEquals(List.of("Alpha", "beta", "gamma"), names(PaletteListing.build(lib, Filter.ALL, Sort.NAME, "")));
        assertEquals(List.of("gamma", "beta", "Alpha"), names(PaletteListing.build(lib, Filter.ALL, Sort.DATE, "")));
        assertEquals(List.of("gamma", "beta", "Alpha"), names(PaletteListing.build(lib, Filter.ALL, Sort.BLOCKS, "")));
    }

    @Test
    public void filters() {
        BlockLibrary lib = library();
        assertEquals(List.of("Alpha"), names(PaletteListing.build(lib, Filter.FAVORITES, Sort.MANUAL, "")));
        // Recent: only palettes changed in the manager, newest first, whatever the sort.
        assertEquals(List.of("gamma", "beta"), names(PaletteListing.build(lib, Filter.RECENT, Sort.NAME, "")));
        lib.palettes().get(2).put(BlockLibrary.KEY_ORIGIN, BlockLibrary.ORIGIN_IMPORT);
        assertEquals(List.of("beta", "Alpha"), names(PaletteListing.build(lib, Filter.CUSTOM, Sort.MANUAL, "")));
    }

    @Test
    public void searchFindsPalettesAndBlocksButNotOrphans() {
        BlockLibrary lib = library();
        lib.blocks().add(block("g9", "42")); // palette id with no palette
        PaletteListing.Result result = PaletteListing.build(lib, Filter.ALL, Sort.MANUAL, "g");
        assertEquals(List.of("gamma"), names(result));
        List<String> blocks = new ArrayList<>();
        for (PaletteListing.BlockRow row : result.blocks()) blocks.add(row.name() + "@" + row.paletteName());
        assertEquals(List.of("g1@gamma", "g2@gamma", "g3@gamma"), blocks);
        PaletteListing.Result trash = PaletteListing.build(lib, Filter.ALL, Sort.MANUAL, "trash");
        assertTrue(trash.blocks().get(0).inRecycleBin());
    }

    @Test
    public void reorderingOnlyInTheFullManualList() {
        assertTrue(PaletteListing.canReorder(Filter.ALL, Sort.MANUAL, ""));
        assertFalse(PaletteListing.canReorder(Filter.ALL, Sort.NAME, ""));
        assertFalse(PaletteListing.canReorder(Filter.FAVORITES, Sort.MANUAL, ""));
        assertFalse(PaletteListing.canReorder(Filter.ALL, Sort.MANUAL, "x"));
    }
}
