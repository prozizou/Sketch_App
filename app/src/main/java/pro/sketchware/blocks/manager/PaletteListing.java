package pro.sketchware.blocks.manager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** What the Block Manager list shows: palettes filtered, searched and sorted, plus the blocks a search finds. */
public final class PaletteListing {
    public enum Filter {ALL, FAVORITES, RECENT, CUSTOM}

    public enum Sort {MANUAL, NAME, DATE, BLOCKS}

    /** How many palettes "Recent" shows at most. */
    public static final int RECENT_LIMIT = 10;

    /** One palette row. */
    public record PaletteRow(int index, String name, String color, String folder, int blocks, long modified,
                             boolean favorite) {
    }

    /** One block found by a search. */
    public record BlockRow(int blockIndex, String name, String spec, int paletteIndex, String paletteName,
                           boolean inRecycleBin) {
    }

    public record Result(List<PaletteRow> palettes, List<BlockRow> blocks) {
    }

    private PaletteListing() {
    }

    public static Result build(BlockLibrary library, Filter filter, Sort sort, String query) {
        String q = query == null ? "" : query.trim();
        List<PaletteRow> rows = new ArrayList<>();
        for (int i = 0; i < library.paletteCount(); i++) {
            boolean keep = switch (filter) {
                case ALL -> true;
                case FAVORITES -> library.isFavorite(i);
                case RECENT -> library.modified(i) > 0;
                case CUSTOM -> !library.isImported(i);
            };
            if (keep && library.paletteMatches(i, q)) {
                rows.add(new PaletteRow(i, library.name(i), library.color(i), library.folder(i), library.blockCount(i),
                        library.modified(i), library.isFavorite(i)));
            }
        }
        Sort effective = filter == Filter.RECENT ? Sort.DATE : sort;
        Comparator<PaletteRow> byManual = Comparator.comparingInt(PaletteRow::index);
        switch (effective) {
            case NAME -> rows.sort(Comparator.comparing(PaletteRow::name, String.CASE_INSENSITIVE_ORDER).thenComparing(byManual));
            case DATE -> rows.sort(Comparator.comparingLong(PaletteRow::modified).reversed().thenComparing(byManual));
            case BLOCKS -> rows.sort(Comparator.comparingInt(PaletteRow::blocks).reversed().thenComparing(byManual));
            case MANUAL -> rows.sort(byManual);
        }
        if (filter == Filter.RECENT && rows.size() > RECENT_LIMIT) {
            rows = new ArrayList<>(rows.subList(0, RECENT_LIMIT));
        }

        List<BlockRow> found = new ArrayList<>();
        if (!q.isEmpty()) {
            List<Map<String, Object>> blocks = library.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                Map<String, Object> block = blocks.get(i);
                if (!BlockLibrary.blockMatches(block, q)) continue;
                Integer id = BlockLibrary.paletteIdOf(block);
                boolean bin = id != null && id == BlockLibrary.RECYCLE_BIN;
                int paletteIndex = id == null ? -1 : id - BlockLibrary.FIRST_PALETTE_ID;
                boolean known = paletteIndex >= 0 && paletteIndex < library.paletteCount();
                if (!bin && !known) continue;
                found.add(new BlockRow(i, String.valueOf(block.get(BlockLibrary.KEY_NAME)), String.valueOf(block.get("spec")),
                        bin ? -1 : paletteIndex, bin ? "" : library.name(paletteIndex), bin));
            }
        }
        return new Result(rows, found);
    }

    /** Dragging palettes only makes sense when the list shows all of them in their saved order. */
    public static boolean canReorder(Filter filter, Sort sort, String query) {
        return filter == Filter.ALL && sort == Sort.MANUAL && (query == null || query.trim().isEmpty());
    }
}
