package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the community page has loaded, and what each section and tab shows of it. The pieces arrive separately
 * (the live newest posts, older pages, the most popular, the followed authors' posts, the saved posts, the user's
 * own), and a post can be in several of them: the freshest copy wins, so a counter that changed shows everywhere.
 * Pure Java, so the merging and the choice of what is shown are tested directly.
 */
public final class EvolveFeed {

    public enum Section {EXPLORE, SAVED, PROFILE}

    public enum Tab {POPULAR, RECENT, FOLLOWING}

    private List<SharedBlock> live = new ArrayList<>();
    private final List<SharedBlock> older = new ArrayList<>();
    private List<SharedBlock> popular = new ArrayList<>();
    private List<SharedBlock> following = new ArrayList<>();
    private List<SharedBlock> saved = new ArrayList<>();
    private List<SharedBlock> mine = new ArrayList<>();

    /** The newest posts as the database holds them now; replaces the previous ones. */
    public void setLive(List<SharedBlock> posts) {
        live = new ArrayList<>(posts);
    }

    /** An older page, added below what is loaded. */
    public void addOlder(List<SharedBlock> posts) {
        older.addAll(posts);
    }

    public void setPopular(List<SharedBlock> posts) {
        popular = new ArrayList<>(posts);
    }

    public void setFollowing(List<SharedBlock> posts) {
        following = new ArrayList<>(posts);
    }

    public void setSaved(List<SharedBlock> posts) {
        saved = new ArrayList<>(posts);
    }

    public void setMine(List<SharedBlock> posts) {
        mine = new ArrayList<>(posts);
    }

    /** Forgets the older pages, for a refresh. */
    public void clearOlder() {
        older.clear();
    }

    /** Takes a deleted post out of every list at once. */
    public void remove(String id) {
        live.removeIf(p -> p.id().equals(id));
        older.removeIf(p -> p.id().equals(id));
        popular.removeIf(p -> p.id().equals(id));
        following.removeIf(p -> p.id().equals(id));
        saved.removeIf(p -> p.id().equals(id));
        mine.removeIf(p -> p.id().equals(id));
    }

    /** @return the creation time of the oldest post loaded by paging, or 0 when none is (the cursor of the next page) */
    public long oldestCreatedAt() {
        long oldest = 0;
        for (SharedBlock post : concat(live, older)) {
            if (oldest == 0 || post.createdAt() < oldest) oldest = post.createdAt();
        }
        return oldest;
    }

    public boolean isEmpty() {
        return live.isEmpty() && older.isEmpty() && popular.isEmpty() && following.isEmpty() && saved.isEmpty() && mine.isEmpty();
    }

    /** @return the posts of the paged feed (live and older), newest first: what a restart shows from the disk copy */
    public List<SharedBlock> latest() {
        return merge(concat(live, older));
    }

    /**
     * @param kind         one kind chosen with the chips, null for all
     * @param query        the search text
     * @param savedOrder   ids of the saved posts, newest saved first
     */
    public List<SharedBlock> visible(Section section, Tab tab, SharedKind kind, String query, String myUid, Set<String> followingUids,
                                     List<String> savedOrder, long now) {
        EnumSet<SharedKind> all = EnumSet.allOf(SharedKind.class);
        switch (section) {
            case SAVED -> {
                List<SharedBlock> shown = EvolveFilter.apply(merge(concat(live, older, popular, following, saved, mine)), all, kind,
                        EvolveFilter.Mode.SAVED, query, myUid, now, followingUids, new HashSet<>(savedOrder));
                List<String> order = new ArrayList<>(savedOrder);
                shown.sort(Comparator.comparingInt((SharedBlock p) -> order.indexOf(p.id())));
                return shown;
            }
            case PROFILE -> {
                return EvolveFilter.apply(merge(concat(live, older, mine)), all, kind, EvolveFilter.Mode.MINE, query, myUid, now, followingUids, Set.of());
            }
            default -> {
                return switch (tab) {
                    case POPULAR -> EvolveFilter.apply(merge(concat(live, older, popular)), all, kind, EvolveFilter.Mode.POPULAR, query, myUid, now, followingUids, Set.of());
                    case FOLLOWING -> EvolveFilter.apply(merge(concat(live, older, following)), all, kind, EvolveFilter.Mode.FOLLOWING, query, myUid, now, followingUids, Set.of());
                    default -> EvolveFilter.apply(merge(concat(live, older)), all, kind, EvolveFilter.Mode.ALL, query, myUid, now, followingUids, Set.of());
                };
            }
        }
    }

    @SafeVarargs
    private static List<SharedBlock> concat(List<SharedBlock>... lists) {
        List<SharedBlock> all = new ArrayList<>();
        for (List<SharedBlock> list : lists) all.addAll(list);
        return all;
    }

    /** The union by id; the first copy of a post (the live one comes first) wins. */
    private static List<SharedBlock> merge(Collection<SharedBlock> posts) {
        Map<String, SharedBlock> byId = new LinkedHashMap<>();
        for (SharedBlock post : posts) {
            byId.putIfAbsent(post.id(), post);
        }
        return new ArrayList<>(byId.values());
    }
}
