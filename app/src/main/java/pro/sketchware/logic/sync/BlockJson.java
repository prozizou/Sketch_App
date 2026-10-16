package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Blocks as text, the way a project stores them, and fingerprints of blocks and of Java. */
public final class BlockJson {
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    private BlockJson() {
    }

    public static String toJson(List<BlockBean> blocks) {
        return GSON.toJson(blocks);
    }

    /** @throws IllegalArgumentException when the text is not a list of blocks */
    @NonNull
    public static ArrayList<BlockBean> fromJson(String json) {
        try {
            List<BlockBean> parsed = GSON.fromJson(json, new TypeToken<ArrayList<BlockBean>>() {
            }.getType());
            ArrayList<BlockBean> blocks = new ArrayList<>();
            if (parsed == null) return blocks;
            for (BlockBean bean : parsed) {
                if (bean == null || bean.id == null || bean.opCode == null) throw new IllegalArgumentException("A block has no id or opcode");
                BlockBean rebuilt = new BlockBean();
                if (bean.parameters == null) bean.parameters = new ArrayList<>();
                if (bean.spec == null) bean.spec = "";
                if (bean.type == null) bean.type = " ";
                if (bean.typeName == null) bean.typeName = "";
                rebuilt.copy(bean);
                blocks.add(rebuilt);
            }
            return blocks;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a list of blocks: " + e.getMessage(), e);
        }
    }

    /** A copy of the list whose blocks can be changed without touching the original. */
    public static ArrayList<BlockBean> copyOf(List<BlockBean> blocks) {
        ArrayList<BlockBean> copy = new ArrayList<>();
        for (BlockBean bean : blocks) {
            BlockBean clone = new BlockBean();
            clone.copy(bean);
            copy.add(clone);
        }
        return copy;
    }

    /**
     * A fingerprint of the blocks, equal for blocks that are the same: their ids, kinds, parameters and links. What the
     * editor adds when it saves (colours) and the order of the list don't count.
     */
    public static String hash(List<BlockBean> blocks) {
        List<BlockBean> sorted = new ArrayList<>(blocks);
        sorted.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a.id), Integer.parseInt(b.id));
            } catch (NumberFormatException e) {
                return a.id.compareTo(b.id);
            }
        });
        StringBuilder canonical = new StringBuilder();
        for (BlockBean bean : sorted) {
            canonical.append(bean.id).append('|').append(bean.opCode).append('|').append(bean.spec).append('|').append(bean.type).append('|')
                    .append(bean.typeName == null ? "" : bean.typeName).append('|');
            for (String parameter : bean.parameters) canonical.append(parameter == null ? "" : parameter).append('\u0001');
            canonical.append('|').append(bean.nextBlock).append('|').append(bean.subStack1).append('|').append(bean.subStack2).append('\n');
        }
        return hash(canonical.toString());
    }

    /** The fingerprint of blocks stored as {@link #toJson} text. */
    public static String hashOfJson(String json) {
        return hash(fromJson(json));
    }

    /** A fingerprint of a text; line endings don't count. */
    public static String hash(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
