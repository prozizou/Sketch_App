package pro.sketchware.blocks.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The user's custom blocks and palettes, in the format the Logic editor already reads.
 *
 * <p>{@code palette.json} is a list of palettes ({@code name}, {@code color}); the palette at index {@code i} has the
 * id {@code i + 9}. {@code block.json} is a list of blocks whose {@code palette} entry holds that id as a string,
 * {@code "-1"} for the recycle bin. Every operation keeps those ids consistent (inserting, moving or removing a
 * palette renumbers the blocks of the palettes after it).
 *
 * <p>Manager-only information is kept as extra keys of a palette ({@code favorite}, {@code folder},
 * {@code modified}, {@code origin}); the Logic editor and older versions ignore them.
 */
public final class BlockLibrary {
    public static final int FIRST_PALETTE_ID = 9;
    public static final int RECYCLE_BIN = -1;

    public static final String KEY_NAME = "name";
    public static final String KEY_COLOR = "color";
    public static final String KEY_FAVORITE = "favorite";
    public static final String KEY_FOLDER = "folder";
    public static final String KEY_MODIFIED = "modified";
    public static final String KEY_ORIGIN = "origin";
    public static final String ORIGIN_IMPORT = "import";
    public static final String BLOCK_PALETTE = "palette";

    /** What to do with an imported block whose name (its opcode) is already used. */
    public enum Conflict {SKIP, RENAME, REPLACE}

    public record ImportProblem(int entry, String message) {
    }

    public record ImportResult(int added, int renamed, int replaced, int skipped, List<ImportProblem> invalid) {
    }

    private final List<Map<String, Object>> palettes;
    private final List<Map<String, Object>> blocks;
    private final LongSupplier clock;

    public BlockLibrary(List<? extends Map<String, Object>> palettes, List<? extends Map<String, Object>> blocks) {
        this(palettes, blocks, System::currentTimeMillis);
    }

    public BlockLibrary(List<? extends Map<String, Object>> palettes, List<? extends Map<String, Object>> blocks, LongSupplier clock) {
        this.palettes = new ArrayList<>();
        for (Map<String, Object> p : palettes) this.palettes.add(new LinkedHashMap<>(p));
        this.blocks = new ArrayList<>();
        for (Map<String, Object> b : blocks) this.blocks.add(new LinkedHashMap<>(b));
        this.clock = clock;
    }

    public List<Map<String, Object>> palettes() {
        return palettes;
    }

    public List<Map<String, Object>> blocks() {
        return blocks;
    }

    public static int paletteId(int paletteIndex) {
        return paletteIndex + FIRST_PALETTE_ID;
    }

    /** @return the palette id of a block, or null when its entry is missing or not a number */
    public static Integer paletteIdOf(Map<String, Object> block) {
        Object value = block.get(BLOCK_PALETTE);
        if (value == null) return null;
        try {
            return (int) Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void setPaletteId(Map<String, Object> block, int id) {
        block.put(BLOCK_PALETTE, String.valueOf(id));
    }

    // ---- palette information

    public String name(int paletteIndex) {
        Object name = palettes.get(paletteIndex).get(KEY_NAME);
        return name == null ? "" : name.toString();
    }

    public String color(int paletteIndex) {
        Object color = palettes.get(paletteIndex).get(KEY_COLOR);
        return color == null ? "#FFFFFF" : color.toString();
    }

    public boolean isFavorite(int paletteIndex) {
        return Boolean.TRUE.equals(palettes.get(paletteIndex).get(KEY_FAVORITE));
    }

    public String folder(int paletteIndex) {
        Object folder = palettes.get(paletteIndex).get(KEY_FOLDER);
        return folder == null ? "" : folder.toString();
    }

    /** @return when the palette or its blocks were last changed in the manager, 0 when unknown (older palettes) */
    public long modified(int paletteIndex) {
        Object modified = palettes.get(paletteIndex).get(KEY_MODIFIED);
        return modified instanceof Number number ? number.longValue() : 0L;
    }

    public boolean isImported(int paletteIndex) {
        return ORIGIN_IMPORT.equals(palettes.get(paletteIndex).get(KEY_ORIGIN));
    }

    public int paletteCount() {
        return palettes.size();
    }

    /** Blocks outside the recycle bin. */
    public int blockCount() {
        int count = 0;
        for (Map<String, Object> block : blocks) {
            Integer id = paletteIdOf(block);
            if (id == null || id != RECYCLE_BIN) count++;
        }
        return count;
    }

    public int recycleBinCount() {
        return blockIndicesOfId(RECYCLE_BIN).size();
    }

    public int blockCount(int paletteIndex) {
        return blockIndicesOfId(paletteId(paletteIndex)).size();
    }

    public List<Integer> blockIndicesOf(int paletteIndex) {
        return blockIndicesOfId(paletteId(paletteIndex));
    }

    public List<Integer> blockIndicesOfId(int paletteId) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Integer id = paletteIdOf(blocks.get(i));
            if (id != null && id == paletteId) indices.add(i);
        }
        return indices;
    }

    public void touch(int paletteIndex) {
        if (paletteIndex >= 0 && paletteIndex < palettes.size()) {
            palettes.get(paletteIndex).put(KEY_MODIFIED, clock.getAsLong());
        }
    }

    /** Marks the palette with this id (not the recycle bin) as just changed. */
    public void touchId(int paletteId) {
        touch(paletteId - FIRST_PALETTE_ID);
    }

    // ---- palettes

    /** @param at where to insert it, null for the end; blocks of the palettes after it are renumbered */
    public int createPalette(String name, String color, Integer at) {
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put(KEY_NAME, name);
        palette.put(KEY_COLOR, color);
        int index = at == null ? palettes.size() : Math.max(0, Math.min(at, palettes.size()));
        shiftIdsFrom(paletteId(index), +1);
        palettes.add(index, palette);
        touch(index);
        return index;
    }

    public void editPalette(int paletteIndex, String name, String color) {
        Map<String, Object> palette = palettes.get(paletteIndex);
        palette.put(KEY_NAME, name);
        palette.put(KEY_COLOR, color);
        touch(paletteIndex);
    }

    public void setFavorite(int paletteIndex, boolean favorite) {
        if (favorite) {
            palettes.get(paletteIndex).put(KEY_FAVORITE, true);
        } else {
            palettes.get(paletteIndex).remove(KEY_FAVORITE);
        }
    }

    public void setFolder(int paletteIndex, String folder) {
        if (folder == null || folder.trim().isEmpty()) {
            palettes.get(paletteIndex).remove(KEY_FOLDER);
        } else {
            palettes.get(paletteIndex).put(KEY_FOLDER, folder.trim());
        }
    }

    /** @return the folders in use, sorted */
    public List<String> folders() {
        Set<String> folders = new HashSet<>();
        for (int i = 0; i < palettes.size(); i++) {
            if (!folder(i).isEmpty()) folders.add(folder(i));
        }
        List<String> sorted = new ArrayList<>(folders);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    /** Copies a palette and its blocks (with new names) right after it. @return the copy's index */
    public int duplicatePalette(int paletteIndex) {
        List<Integer> sourceBlocks = blockIndicesOf(paletteIndex);
        List<Map<String, Object>> copies = new ArrayList<>();
        for (int i : sourceBlocks) copies.add(new LinkedHashMap<>(blocks.get(i)));
        Map<String, Object> source = palettes.get(paletteIndex);
        int index = createPalette(name(paletteIndex) + " copy", color(paletteIndex), paletteIndex + 1);
        if (source.containsKey(KEY_FOLDER)) palettes.get(index).put(KEY_FOLDER, source.get(KEY_FOLDER));
        for (Map<String, Object> copy : copies) {
            copy.put(KEY_NAME, uniqueBlockName(String.valueOf(copy.get(KEY_NAME))));
            setPaletteId(copy, paletteId(index));
            blocks.add(copy);
        }
        return index;
    }

    /** Moves a palette in the list (drag and drop); its blocks and those of the palettes in between follow. */
    public void movePalette(int from, int to) {
        if (from == to) return;
        int count = palettes.size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) order.add(i);
        order.add(to, order.remove(from));
        Map<Integer, Integer> newIdByOldId = new HashMap<>();
        for (int newIndex = 0; newIndex < count; newIndex++) {
            newIdByOldId.put(paletteId(order.get(newIndex)), paletteId(newIndex));
        }
        for (Map<String, Object> block : blocks) {
            Integer id = paletteIdOf(block);
            if (id != null && newIdByOldId.containsKey(id)) setPaletteId(block, newIdByOldId.get(id));
        }
        palettes.add(to, palettes.remove(from));
    }

    /**
     * Removes a palette. Its blocks go to the recycle bin, or are deleted when {@code toRecycleBin} is false; the
     * blocks of the palettes after it are renumbered.
     */
    public void deletePalette(int paletteIndex, boolean toRecycleBin) {
        int id = paletteId(paletteIndex);
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Integer blockId = paletteIdOf(blocks.get(i));
            if (blockId != null && blockId == id) {
                if (toRecycleBin) {
                    setPaletteId(blocks.get(i), RECYCLE_BIN);
                } else {
                    blocks.remove(i);
                }
            }
        }
        palettes.remove(paletteIndex);
        shiftIdsFrom(id + 1, -1);
    }

    /** Adds {@code delta} to every block palette id at or after {@code fromId}. */
    private void shiftIdsFrom(int fromId, int delta) {
        for (Map<String, Object> block : blocks) {
            Integer id = paletteIdOf(block);
            if (id != null && id >= fromId) setPaletteId(block, id + delta);
        }
    }

    // ---- blocks

    public String uniqueBlockName(String base) {
        Set<String> used = new HashSet<>();
        for (Map<String, Object> block : blocks) used.add(String.valueOf(block.get(KEY_NAME)));
        if (!used.contains(base)) return base;
        String stem = base.replaceAll("_copy\\d*$", "");
        for (int n = 1; ; n++) {
            String candidate = stem + "_copy" + (n == 1 ? "" : String.valueOf(n));
            if (!used.contains(candidate)) return candidate;
        }
    }

    /** Copies a block right after it, under a new name. @return the copy's index */
    public int duplicateBlock(int blockIndex) {
        Map<String, Object> copy = new LinkedHashMap<>(blocks.get(blockIndex));
        copy.put(KEY_NAME, uniqueBlockName(String.valueOf(copy.get(KEY_NAME))));
        blocks.add(blockIndex + 1, copy);
        touchOwner(copy);
        return blockIndex + 1;
    }

    /**
     * Puts blocks in a palette (moving, or restoring from the recycle bin): they go to the end of that palette in
     * their current order, and every other block keeps its place.
     */
    public void moveBlocks(List<Integer> blockIndices, int paletteIndex) {
        moveBlocksToId(blockIndices, paletteId(paletteIndex));
        touch(paletteIndex);
    }

    public void moveBlocksToRecycleBin(List<Integer> blockIndices) {
        for (int i : blockIndices) touchOwner(blocks.get(i));
        moveBlocksToId(blockIndices, RECYCLE_BIN);
    }

    private void moveBlocksToId(List<Integer> blockIndices, int paletteId) {
        List<Integer> sorted = new ArrayList<>(new HashSet<>(blockIndices));
        Collections.sort(sorted);
        List<Map<String, Object>> moving = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) moving.add(0, blocks.remove((int) sorted.get(i)));
        for (Map<String, Object> block : moving) {
            setPaletteId(block, paletteId);
            blocks.add(block);
        }
    }

    public void deleteBlocks(List<Integer> blockIndices) {
        List<Integer> sorted = new ArrayList<>(new HashSet<>(blockIndices));
        Collections.sort(sorted, Collections.reverseOrder());
        for (int i : sorted) {
            touchOwner(blocks.get(i));
            blocks.remove(i);
        }
    }

    /** @return how many blocks were deleted */
    public int emptyRecycleBin() {
        List<Integer> bin = blockIndicesOfId(RECYCLE_BIN);
        deleteBlocks(bin);
        return bin.size();
    }

    private void touchOwner(Map<String, Object> block) {
        Integer id = paletteIdOf(block);
        if (id != null && id >= FIRST_PALETTE_ID) touchId(id);
    }

    // ---- import / export

    /** @return the problems that keep entries from being imported (a name, a spec and a known type are needed) */
    public static List<ImportProblem> validate(List<? extends Map<String, Object>> entries) {
        List<ImportProblem> problems = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> entry = entries.get(i);
            if (entry == null) {
                problems.add(new ImportProblem(i, "not a block"));
                continue;
            }
            Object name = entry.get(KEY_NAME);
            Object spec = entry.get("spec");
            Object type = entry.get("type");
            if (!(name instanceof String n) || n.trim().isEmpty()) {
                problems.add(new ImportProblem(i, "no name"));
            } else if (!n.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                problems.add(new ImportProblem(i, "\"" + n + "\" isn't a valid block name"));
            } else if (!names.add(n)) {
                problems.add(new ImportProblem(i, "\"" + n + "\" appears twice in the file"));
            } else if (!(spec instanceof String s) || s.trim().isEmpty()) {
                problems.add(new ImportProblem(i, "\"" + n + "\" has no spec"));
            } else if (type != null && !BlockTypes.isKnown(type.toString())) {
                problems.add(new ImportProblem(i, "\"" + n + "\" has an unknown type \"" + type + "\""));
            }
        }
        return problems;
    }

    /** Imports the valid entries into a palette; names already used are handled by {@code conflict}. */
    public ImportResult importBlocks(List<? extends Map<String, Object>> entries, int paletteIndex, Conflict conflict) {
        List<ImportProblem> invalid = validate(entries);
        Set<Integer> invalidEntries = new HashSet<>();
        for (ImportProblem problem : invalid) invalidEntries.add(problem.entry());
        int added = 0, renamed = 0, replaced = 0, skipped = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (invalidEntries.contains(i)) continue;
            Map<String, Object> block = new LinkedHashMap<>(entries.get(i));
            String name = block.get(KEY_NAME).toString();
            int existing = indexOfBlock(name);
            if (existing >= 0) {
                switch (conflict) {
                    case SKIP -> {
                        skipped++;
                        continue;
                    }
                    case RENAME -> {
                        block.put(KEY_NAME, uniqueBlockName(name));
                        renamed++;
                    }
                    case REPLACE -> {
                        touchOwner(blocks.get(existing));
                        blocks.remove(existing);
                        replaced++;
                    }
                }
            }
            if (!block.containsKey("type")) block.put("type", " ");
            setPaletteId(block, paletteId(paletteIndex));
            blocks.add(block);
            added++;
        }
        touch(paletteIndex);
        return new ImportResult(added, renamed, replaced, skipped, invalid);
    }

    /** Imports into a new palette, marked as imported. @return its index */
    public int importAsPalette(String name, String color, List<? extends Map<String, Object>> entries, Conflict conflict) {
        int index = createPalette(name, color, null);
        palettes.get(index).put(KEY_ORIGIN, ORIGIN_IMPORT);
        importBlocks(entries, index, conflict);
        return index;
    }

    public int indexOfBlock(String name) {
        for (int i = 0; i < blocks.size(); i++) {
            if (name.equals(String.valueOf(blocks.get(i).get(KEY_NAME)))) return i;
        }
        return -1;
    }

    /** @return copies of the palette's blocks, in the export format (the same list format as block.json) */
    public List<Map<String, Object>> exportPalette(int paletteIndex) {
        List<Map<String, Object>> export = new ArrayList<>();
        for (int i : blockIndicesOf(paletteIndex)) export.add(new LinkedHashMap<>(blocks.get(i)));
        return export;
    }

    // ---- search

    public static boolean contains(Object value, String query) {
        return value != null && value.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    /** @return whether the block matches the query by name (opcode), spec, type name, code or imports */
    public static boolean blockMatches(Map<String, Object> block, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return true;
        for (String key : new String[]{KEY_NAME, "spec", "spec2", "typeName", "code", "imports"}) {
            if (contains(block.get(key), q)) return true;
        }
        return false;
    }

    public boolean paletteMatches(int paletteIndex, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        return q.isEmpty() || contains(name(paletteIndex), q) || contains(folder(paletteIndex), q);
    }
}
