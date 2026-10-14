package pro.sketchware.evolve;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a stack of blocks into the JSON stored in a shared block and back, using the project-file format (only the
 * fields the editor itself saves). What comes back from the database is untrusted: {@link #decode} returns null
 * rather than a stack the editor could choke on.
 */
public final class SharedBlockCodec {

    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    private static final Type BLOCK_LIST = new TypeToken<ArrayList<BlockBean>>() {
    }.getType();

    private SharedBlockCodec() {
    }

    public static String encode(List<BlockBean> blocks) {
        return GSON.toJson(blocks, BLOCK_LIST);
    }

    /**
     * @return the stack, or null if {@code json} isn't a non-empty list of blocks that each have an id and an opcode
     */
    public static ArrayList<BlockBean> decode(String json) {
        if (json == null || json.length() > SharedBlockRules.MAX_JSON_LENGTH) {
            return null;
        }
        try {
            ArrayList<BlockBean> blocks = GSON.fromJson(json, BLOCK_LIST);
            if (blocks == null || blocks.isEmpty() || blocks.size() > SharedBlockRules.MAX_BLOCKS) {
                return null;
            }
            for (BlockBean block : blocks) {
                if (block == null || block.id == null || block.id.isEmpty() || block.opCode == null || block.opCode.isEmpty()) {
                    return null;
                }
                if (block.parameters == null) {
                    block.parameters = new ArrayList<>();
                }
                // A null parameter would crash the block renderer; an empty one is "no value".
                block.parameters.replaceAll(parameter -> parameter == null ? "" : parameter);
                if (block.spec == null) block.spec = "";
                if (block.type == null) block.type = " ";
                if (block.typeName == null) block.typeName = "";
            }
            return blocks;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the opcode of every block of {@code blocks}, in order. */
    public static List<String> opCodes(List<BlockBean> blocks) {
        List<String> opCodes = new ArrayList<>();
        for (BlockBean block : blocks) {
            opCodes.add(block.opCode);
        }
        return opCodes;
    }
}
