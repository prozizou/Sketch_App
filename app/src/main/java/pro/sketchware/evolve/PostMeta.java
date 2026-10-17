package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a post of the community page adds to a shared entry: how it is found (tags), what it needs (dependencies,
 * Android version), what it looks like (screenshots), its counters, and the source text of Java, Kotlin and
 * component posts. Pure data, stored in the same node as the entry (see {@link SharedBlock}).
 *
 * @param tags         up to {@link PostRules#MAX_TAGS} lowercase words
 * @param version      the author's own version of the post, like "1.2"; "" when not set
 * @param minSdk       the lowest Android API level it works on, 0 when the author did not say
 * @param dependencies libraries it needs, one per entry (Maven coordinates or a library name)
 * @param screenshots  https addresses of the pictures, in Firebase Storage
 * @param updatedAt    server time of the last update by the author, 0 when never updated
 * @param likes        how many users liked it
 * @param commentCount how many comments and replies it has
 * @param code         for {@link SharedKind#isCode() code kinds}, the source text ("" otherwise)
 * @param fileName     for Java and Kotlin, the file's name with its extension ("" otherwise)
 */
public record PostMeta(
        List<String> tags,
        String version,
        int minSdk,
        List<String> dependencies,
        List<String> screenshots,
        long updatedAt,
        long likes,
        long commentCount,
        String code,
        String fileName) {

    public static final PostMeta EMPTY = new PostMeta(List.of(), "", 0, List.of(), List.of(), 0, 0, 0, "", "");

    public static final String TAGS = "tags";
    public static final String VERSION = "version";
    public static final String MIN_SDK = "minSdk";
    public static final String DEPENDENCIES = "dependencies";
    public static final String SCREENSHOTS = "screenshots";
    public static final String UPDATED_AT = "updatedAt";
    public static final String LIKES = "likes";
    public static final String COMMENT_COUNT = "commentCount";
    public static final String CODE = "code";
    public static final String FILE_NAME = "fileName";

    public PostMeta {
        tags = List.copyOf(tags);
        dependencies = List.copyOf(dependencies);
        screenshots = List.copyOf(screenshots);
    }

    /** Same data with the counters and the update time changed, as the community page shows a live post. */
    public PostMeta withCounters(long likes, long commentCount) {
        return new PostMeta(tags, version, minSdk, dependencies, screenshots, updatedAt, likes, commentCount, code, fileName);
    }

    /** @return the fields to write when publishing; the counters start at 0 and the times are the server's */
    public Map<String, Object> toPublishMap(Map<String, Object> into) {
        if (!tags.isEmpty()) into.put(TAGS, new ArrayList<>(tags));
        if (!version.isEmpty()) into.put(VERSION, version);
        if (minSdk > 0) into.put(MIN_SDK, minSdk);
        if (!dependencies.isEmpty()) into.put(DEPENDENCIES, new ArrayList<>(dependencies));
        if (!screenshots.isEmpty()) into.put(SCREENSHOTS, new ArrayList<>(screenshots));
        if (!code.isEmpty()) into.put(CODE, code);
        if (!fileName.isEmpty()) into.put(FILE_NAME, fileName);
        into.put(LIKES, 0L);
        into.put(COMMENT_COUNT, 0L);
        return into;
    }

    /** @return the meta stored in {@code map}; every field is optional, so entries from before posts read as empty */
    public static PostMeta fromMap(Map<String, Object> map) {
        return new PostMeta(strings(map.get(TAGS)), text(map.get(VERSION)), (int) number(map.get(MIN_SDK)),
                strings(map.get(DEPENDENCIES)), strings(map.get(SCREENSHOTS)), number(map.get(UPDATED_AT)),
                number(map.get(LIKES)), number(map.get(COMMENT_COUNT)), text(map.get(CODE)), text(map.get(FILE_NAME)));
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString());
                }
            }
        }
        return out;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
