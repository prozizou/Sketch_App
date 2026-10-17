package pro.sketchware.evolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import pro.sketchware.evolve.EvolveFeed.Section;
import pro.sketchware.evolve.EvolveFeed.Tab;

public class EvolveFeedTest {
    private static SharedBlock post(String id, long createdAt, String author, long downloads, long likes) {
        PostMeta meta = PostMeta.EMPTY.withCounters(likes, 0);
        return new SharedBlock(id, "Post " + id, "", author, "A", createdAt, 1, 1, List.of(), false, downloads, "[]",
                SharedKind.BLOCKS, "", "", meta);
    }

    private static List<String> ids(List<SharedBlock> posts) {
        return posts.stream().map(SharedBlock::id).toList();
    }

    private static List<SharedBlock> show(EvolveFeed feed, Section section, Tab tab, String query) {
        return feed.visible(section, tab, null, query, "me", Set.of("ann"), List.of("s2", "s1"), 100);
    }

    @Test
    public void recentIsTheNewestFirstAcrossLiveAndOlderPages() {
        EvolveFeed feed = new EvolveFeed();
        feed.setLive(List.of(post("c", 30, "x", 0, 0), post("b", 20, "x", 0, 0)));
        feed.addOlder(List.of(post("a", 10, "x", 0, 0)));
        assertEquals(List.of("c", "b", "a"), ids(show(feed, Section.EXPLORE, Tab.RECENT, "")));
        assertEquals(10, feed.oldestCreatedAt());
    }

    @Test
    public void theLiveCopyOfAPostWinsOverAnOlderCopy() {
        EvolveFeed feed = new EvolveFeed();
        feed.setPopular(List.of(post("a", 10, "x", 5, 0)));
        feed.setLive(List.of(post("a", 10, "x", 5, 9)));
        List<SharedBlock> shown = show(feed, Section.EXPLORE, Tab.POPULAR, "");
        assertEquals(1, shown.size());
        assertEquals(9, shown.get(0).meta().likes());
    }

    @Test
    public void popularUsesEverythingLoaded() {
        EvolveFeed feed = new EvolveFeed();
        feed.setLive(List.of(post("new", 50, "x", 0, 0)));
        feed.setPopular(List.of(post("old", 1, "x", 40, 0), post("mid", 2, "x", 10, 0)));
        assertEquals(List.of("old", "mid", "new"), ids(show(feed, Section.EXPLORE, Tab.POPULAR, "")));
    }

    @Test
    public void followingShowsOnlyFollowedAuthors() {
        EvolveFeed feed = new EvolveFeed();
        feed.setLive(List.of(post("a", 30, "ann", 0, 0), post("b", 20, "bob", 0, 0)));
        feed.setFollowing(List.of(post("c", 10, "ann", 0, 0)));
        assertEquals(List.of("a", "c"), ids(show(feed, Section.EXPLORE, Tab.FOLLOWING, "")));
    }

    @Test
    public void savedFollowsTheOrderTheyWereSaved() {
        EvolveFeed feed = new EvolveFeed();
        feed.setLive(List.of(post("s1", 30, "x", 0, 0), post("other", 25, "x", 0, 0)));
        feed.setSaved(List.of(post("s2", 5, "x", 0, 0)));
        assertEquals(List.of("s2", "s1"), ids(show(feed, Section.SAVED, Tab.RECENT, "")));
    }

    @Test
    public void profileShowsMyPostsOnly() {
        EvolveFeed feed = new EvolveFeed();
        feed.setLive(List.of(post("a", 30, "me", 0, 0), post("b", 20, "bob", 0, 0)));
        feed.setMine(List.of(post("c", 10, "me", 0, 0)));
        assertEquals(List.of("a", "c"), ids(show(feed, Section.PROFILE, Tab.RECENT, "")));
    }

    @Test
    public void searchAppliesInEverySection() {
        EvolveFeed feed = new EvolveFeed();
        feed.setLive(List.of(post("a", 30, "me", 0, 0), post("b", 20, "me", 0, 0)));
        assertEquals(List.of("b"), ids(show(feed, Section.PROFILE, Tab.RECENT, "post b")));
    }

    @Test
    public void aDeletedPostLeavesEveryList() {
        EvolveFeed feed = new EvolveFeed();
        feed.setLive(List.of(post("a", 30, "me", 0, 0)));
        feed.setPopular(List.of(post("a", 30, "me", 0, 0)));
        feed.setMine(List.of(post("a", 30, "me", 0, 0)));
        feed.remove("a");
        assertTrue(feed.isEmpty());
    }

    @Test
    public void refreshForgetsOlderPagesButKeepsLive() {
        EvolveFeed feed = new EvolveFeed();
        feed.setLive(List.of(post("a", 30, "x", 0, 0)));
        feed.addOlder(List.of(post("z", 1, "x", 0, 0)));
        feed.clearOlder();
        assertEquals(List.of("a"), ids(feed.latest()));
        assertEquals(30, feed.oldestCreatedAt());
    }
}
