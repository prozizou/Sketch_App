package pro.sketchware.evolve;

import java.util.HashMap;
import java.util.Map;

/**
 * Something that happened to the user's posts or comments: a child of {@code Evolve_notifications/<uid>}.
 *
 * @param id       the node's key
 * @param type     what happened
 * @param fromUid  who did it
 * @param fromName their display name
 * @param postId   the post concerned ("" for a follow)
 * @param postName the post's title, kept so the notification reads without loading the post
 * @param createdAt server time, milliseconds since the epoch
 * @param read     whether the user has seen it
 */
public record EvolveNotification(String id, Type type, String fromUid, String fromName, String postId, String postName,
                                 long createdAt, boolean read) {

    public enum Type {
        LIKE("like"), COMMENT("comment"), REPLY("reply"), MENTION("mention"), FOLLOW("follow");

        public final String key;

        Type(String key) {
            this.key = key;
        }

        /** @return the type stored under {@code key}, or null for one this version doesn't know */
        public static Type fromKey(String key) {
            for (Type type : values()) {
                if (type.key.equals(key)) return type;
            }
            return null;
        }
    }

    public static final String TYPE = "type";
    public static final String FROM_UID = "fromUid";
    public static final String FROM_NAME = "fromName";
    public static final String POST_ID = "postId";
    public static final String POST_NAME = "postName";
    public static final String CREATED_AT = "createdAt";
    public static final String READ = "read";

    /** @return the fields to write; {@code createdAt} is the repository's to set (the server's time) */
    public Map<String, Object> toPublishMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(TYPE, type.key);
        map.put(FROM_UID, fromUid);
        map.put(FROM_NAME, fromName);
        if (!postId.isEmpty()) {
            map.put(POST_ID, postId);
            map.put(POST_NAME, postName);
        }
        map.put(READ, false);
        return map;
    }

    /** @return the notification stored under {@code id}, or null for one without a known type or sender */
    public static EvolveNotification fromMap(String id, Map<String, Object> map) {
        if (id == null || map == null) return null;
        Type type = Type.fromKey(text(map.get(TYPE)));
        String fromUid = text(map.get(FROM_UID));
        if (type == null || fromUid.isEmpty()) return null;
        return new EvolveNotification(id, type, fromUid, text(map.get(FROM_NAME)), text(map.get(POST_ID)),
                text(map.get(POST_NAME)), map.get(CREATED_AT) instanceof Number n ? n.longValue() : 0L,
                Boolean.TRUE.equals(map.get(READ)));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
