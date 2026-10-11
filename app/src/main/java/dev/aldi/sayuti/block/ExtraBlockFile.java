package dev.aldi.sayuti.block;

import android.os.Environment;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import mod.hey.studios.util.Helper;
import mod.hilal.saif.blocks.BlocksHandler;
import pro.sketchware.utility.FileUtil;

public class ExtraBlockFile {

    public static final File EXTRA_BLOCKS_DATA_FILE = new File(Environment.getExternalStorageDirectory(),
            ".sketch_nws/resources/block/My Block/block.json");
    public static final File EXTRA_BLOCKS_PALETTE_FILE = new File(Environment.getExternalStorageDirectory(),
            ".sketch_nws/resources/block/My Block/palette.json");

    public static ArrayList<HashMap<String, Object>> buildInBlocks = new ArrayList<>();

    /*
     * The block file can hold thousands of custom blocks and was read and parsed again on every tap on a custom
     * palette, on the main thread: with many palettes, browsing them froze the list and once ran out of memory.
     * The parsed content is now kept until the file changes (other size or modification time).
     */
    private static ArrayList<HashMap<String, Object>> cachedExtraBlocks;
    private static String cachedExtraBlocksStamp;
    private static String cachedPaletteFile;
    private static String cachedPaletteStamp;

    private static String stamp(File file) {
        return file.exists() ? file.lastModified() + ":" + file.length() : "missing";
    }

    /** @return the custom blocks followed by the built-in ones; a new list each call, safe to change. */
    public static synchronized ArrayList<HashMap<String, Object>> getExtraBlockData() {
        String stamp = stamp(EXTRA_BLOCKS_DATA_FILE);
        if (cachedExtraBlocks == null || !stamp.equals(cachedExtraBlocksStamp)) {
            ArrayList<HashMap<String, Object>> extraBlocks = new Gson().fromJson(getExtraBlockFile(), Helper.TYPE_MAP_LIST);
            if (extraBlocks == null) {
                extraBlocks = new ArrayList<>();
            }

            buildInBlocks.clear();
            BlocksHandler.builtInBlocks(buildInBlocks);
            extraBlocks.addAll(buildInBlocks);

            cachedExtraBlocks = extraBlocks;
            cachedExtraBlocksStamp = stamp;
        }
        return new ArrayList<>(cachedExtraBlocks);
    }

    /**
     * @return Non-empty content of {@link ExtraBlockFile#EXTRA_BLOCKS_DATA_FILE},
     * as cases of <code>""</code> as file content return <code>"[]"</code>
     */
    public static String getExtraBlockFile() {
        String fileContent;

        if (EXTRA_BLOCKS_DATA_FILE.exists() && !(fileContent = FileUtil.readFile(EXTRA_BLOCKS_DATA_FILE.getAbsolutePath())).isEmpty()) {
            return fileContent;
        } else {
            return "[]";
        }
    }

    public static synchronized String getPaletteBlockFile() {
        // Read on every palette search keystroke: only go back to the disk when the file changed.
        String stamp = stamp(EXTRA_BLOCKS_PALETTE_FILE);
        if (cachedPaletteFile == null || !stamp.equals(cachedPaletteStamp)) {
            cachedPaletteFile = FileUtil.readFile(EXTRA_BLOCKS_PALETTE_FILE.getAbsolutePath());
            cachedPaletteStamp = stamp;
        }
        return cachedPaletteFile;
    }

    public static String getExtraBlockJson() {
        return "[]";
    }
}
