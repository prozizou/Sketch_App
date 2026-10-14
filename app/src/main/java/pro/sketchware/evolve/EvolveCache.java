package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.List;

/**
 * The last pages loaded, kept in memory so reopening Evolve shows the list at once (it is refreshed behind it).
 * Dropped when something is shared or deleted, and after {@link #MAX_AGE_MS}.
 */
public final class EvolveCache {

    public static final long MAX_AGE_MS = 5 * 60 * 1000;

    private static List<SharedBlock> items = new ArrayList<>();
    private static boolean hasMore;
    private static long storedAt;

    private EvolveCache() {
    }

    public static synchronized void store(List<SharedBlock> loaded, boolean more, long now) {
        items = new ArrayList<>(loaded);
        hasMore = more;
        storedAt = now;
    }

    /** @return the cached list, or an empty list when there is none or it is too old */
    public static synchronized List<SharedBlock> items(long now) {
        return now - storedAt <= MAX_AGE_MS ? new ArrayList<>(items) : new ArrayList<>();
    }

    public static synchronized boolean hasMore() {
        return hasMore;
    }

    public static synchronized void clear() {
        items = new ArrayList<>();
        storedAt = 0;
    }
}
