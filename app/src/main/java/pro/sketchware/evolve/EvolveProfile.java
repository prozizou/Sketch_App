package pro.sketchware.evolve;

import java.util.HashMap;
import java.util.Map;

/**
 * A member of the community: a child of {@code Evolve_profiles/<uid>}, written by its owner when they first open the
 * page and kept in step with their Google name.
 *
 * @param uid       Firebase uid
 * @param name      display name (never the e-mail address)
 * @param photoUrl  the Google profile picture's address, "" when there is none
 * @param bio       a line about themselves, at most {@link #MAX_BIO_LENGTH} characters
 * @param createdAt server time of the first visit, 0 when unknown
 */
public record EvolveProfile(String uid, String name, String photoUrl, String bio, long createdAt) {

    public static final int MAX_BIO_LENGTH = 120;
    public static final int MAX_NAME_LENGTH = 100;

    public static final String NAME = "name";
    public static final String PHOTO_URL = "photoUrl";
    public static final String BIO = "bio";
    public static final String CREATED_AT = "createdAt";

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(NAME, name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name);
        if (!photoUrl.isEmpty()) map.put(PHOTO_URL, photoUrl);
        map.put(BIO, bio.length() > MAX_BIO_LENGTH ? bio.substring(0, MAX_BIO_LENGTH) : bio);
        return map;
    }

    public static EvolveProfile fromMap(String uid, Map<String, Object> map) {
        if (uid == null || map == null) return null;
        String name = text(map.get(NAME));
        if (name.isEmpty()) return null;
        return new EvolveProfile(uid, name, text(map.get(PHOTO_URL)), text(map.get(BIO)),
                map.get(CREATED_AT) instanceof Number n ? n.longValue() : 0L);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
