package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A stack of blocks shared on Evolve: one child of the {@code Blocks_share} node of the Firebase Realtime
 * Database.
 *
 * <p>Pure data, no Android or Firebase dependency. The blocks themselves travel as one JSON string
 * ({@link #blocksJson}) in the project-file format, so the node doesn't depend on how the editor lays blocks out
 * in memory; everything else is plain fields that can be listed, searched and checked without decoding the blocks.
 *
 * @param id          the node's key, assigned by the database ("" before publishing)
 * @param name        shown name, also the name the block gets in the picker's collection
 * @param description what it does, free text
 * @param authorUid   Firebase uid of the publisher: only that user may delete it
 * @param authorName  the publisher's display name (never the e-mail address)
 * @param createdAt   server time of publishing, milliseconds since the epoch (0 before publishing)
 * @param appVersion  versionCode of the app that published it
 * @param blockCount  how many blocks the stack holds
 * @param opCodes     distinct opcodes used, in first-use order
 * @param usesRawCode whether it contains blocks that inject Java source ({@link SharedBlockRules#RAW_CODE_OPCODES})
 * @param downloads   how many times it was picked
 * @param blocksJson  the stack, as a JSON array of blocks ("" for a {@link SharedKind#VIEW})
 * @param kind        what the entry holds
 * @param spec        for a {@link SharedKind#MOREBLOCK}, the more block's spec ("" otherwise)
 * @param xml         for a {@link SharedKind#VIEW}, the layout's XML ("" otherwise)
 */
public record SharedBlock(
        String id,
        String name,
        String description,
        String authorUid,
        String authorName,
        long createdAt,
        int appVersion,
        int blockCount,
        List<String> opCodes,
        boolean usesRawCode,
        long downloads,
        String blocksJson,
        SharedKind kind,
        String spec,
        String xml) {

    /** A plain block stack, which is what Evolve held before kinds existed. */
    public SharedBlock(String id, String name, String description, String authorUid, String authorName, long createdAt,
                       int appVersion, int blockCount, List<String> opCodes, boolean usesRawCode, long downloads,
                       String blocksJson) {
        this(id, name, description, authorUid, authorName, createdAt, appVersion, blockCount, opCodes, usesRawCode,
                downloads, blocksJson, SharedKind.BLOCKS, "", "");
    }

    /** Keys of a node, kept in one place so the code, the docs and the database rules agree. */
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String AUTHOR_UID = "authorUid";
    public static final String AUTHOR_NAME = "authorName";
    public static final String CREATED_AT = "createdAt";
    public static final String APP_VERSION = "appVersion";
    public static final String BLOCK_COUNT = "blockCount";
    public static final String OP_CODES = "opCodes";
    public static final String USES_RAW_CODE = "usesRawCode";
    public static final String DOWNLOADS = "downloads";
    public static final String BLOCKS_JSON = "blocksJson";
    public static final String KIND = "kind";
    public static final String SPEC = "spec";
    public static final String XML = "xml";

    /**
     * @return the fields to write when publishing. {@code createdAt} is left out: the repository sets it to the
     * server's time so a phone's clock can't backdate or reorder entries.
     */
    public Map<String, Object> toPublishMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(NAME, name);
        map.put(DESCRIPTION, description);
        map.put(AUTHOR_UID, authorUid);
        map.put(AUTHOR_NAME, authorName);
        map.put(APP_VERSION, appVersion);
        map.put(BLOCK_COUNT, blockCount);
        map.put(OP_CODES, new ArrayList<>(opCodes));
        map.put(USES_RAW_CODE, usesRawCode);
        map.put(DOWNLOADS, 0L);
        map.put(KIND, kind.key());
        if (kind.hasBlocks()) {
            map.put(BLOCKS_JSON, blocksJson);
        } else {
            map.put(XML, xml);
        }
        if (kind == SharedKind.MOREBLOCK) {
            map.put(SPEC, spec);
        }
        return map;
    }

    /**
     * @return the entry stored under {@code id}, or null when the node lacks what a shared entry needs (a name,
     * an author and its content: blocks, blocks and a spec, or XML): such nodes are skipped rather than shown half-empty. Numbers come back from the
     * database as Long or Double, text may be missing: nothing here throws.
     */
    public static SharedBlock fromMap(String id, Map<String, Object> map) {
        if (id == null || map == null) {
            return null;
        }
        String name = text(map.get(NAME));
        String authorUid = text(map.get(AUTHOR_UID));
        String blocksJson = text(map.get(BLOCKS_JSON));
        String xml = text(map.get(XML));
        SharedKind kind = SharedKind.fromKey(text(map.get(KIND)));
        String spec = text(map.get(SPEC));
        if (name.isEmpty() || authorUid.isEmpty()
                || (kind.hasBlocks() ? blocksJson.isEmpty() : xml.isEmpty())
                || (kind == SharedKind.MOREBLOCK && spec.isEmpty())) {
            return null;
        }
        List<String> opCodes = new ArrayList<>();
        if (map.get(OP_CODES) instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    opCodes.add(item.toString());
                }
            }
        }
        return new SharedBlock(id, name, text(map.get(DESCRIPTION)), authorUid, text(map.get(AUTHOR_NAME)),
                number(map.get(CREATED_AT)), (int) number(map.get(APP_VERSION)), (int) number(map.get(BLOCK_COUNT)),
                opCodes, Boolean.TRUE.equals(map.get(USES_RAW_CODE)), number(map.get(DOWNLOADS)), blocksJson, kind, spec, xml);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
