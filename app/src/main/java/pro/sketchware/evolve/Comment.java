package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One comment, or reply, under a post: a child of {@code Evolve_comments/<postId>}.
 *
 * @param id         the node's key
 * @param postId     the post it is under
 * @param parentId   the comment it answers, "" for a comment straight under the post
 * @param authorUid  Firebase uid of the writer
 * @param authorName the writer's display name
 * @param text       what was written, plain text of {@link PostRules#MAX_COMMENT_LENGTH} characters at most
 * @param createdAt  server time, milliseconds since the epoch
 * @param mentions   uids of the users named with {@code @name} in the text
 */
public record Comment(String id, String postId, String parentId, String authorUid, String authorName, String text,
                      long createdAt, List<String> mentions) {

    public static final String PARENT_ID = "parentId";
    public static final String AUTHOR_UID = "authorUid";
    public static final String AUTHOR_NAME = "authorName";
    public static final String TEXT = "text";
    public static final String CREATED_AT = "createdAt";
    public static final String MENTIONS = "mentions";

    public Comment {
        mentions = List.copyOf(mentions);
    }

    /** @return the fields to write; {@code createdAt} is the repository's to set (the server's time) */
    public Map<String, Object> toPublishMap() {
        Map<String, Object> map = new HashMap<>();
        if (!parentId.isEmpty()) map.put(PARENT_ID, parentId);
        map.put(AUTHOR_UID, authorUid);
        map.put(AUTHOR_NAME, authorName);
        map.put(TEXT, text);
        if (!mentions.isEmpty()) map.put(MENTIONS, new ArrayList<>(mentions));
        return map;
    }

    /** @return the comment stored under {@code id}, or null when it lacks an author or a text */
    public static Comment fromMap(String postId, String id, Map<String, Object> map) {
        if (id == null || map == null) return null;
        String authorUid = text(map.get(AUTHOR_UID));
        String text = text(map.get(TEXT));
        if (authorUid.isEmpty() || text.isEmpty()) return null;
        List<String> mentions = new ArrayList<>();
        if (map.get(MENTIONS) instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().isEmpty()) mentions.add(item.toString());
            }
        }
        return new Comment(id, postId, text(map.get(PARENT_ID)), authorUid, text(map.get(AUTHOR_NAME)), text,
                map.get(CREATED_AT) instanceof Number n ? n.longValue() : 0L, mentions);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
