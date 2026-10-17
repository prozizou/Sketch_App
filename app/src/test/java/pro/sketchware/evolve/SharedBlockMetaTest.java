package pro.sketchware.evolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SharedBlockMetaTest {
    private static SharedBlock java(String id, long createdAt, long downloads, long likes, long comments, String authorUid) {
        PostMeta meta = new PostMeta(List.of("ui", "helper"), "1.2", 26, List.of("gson"), List.of(), 0, likes, comments,
                "class A {}", "A.java");
        return new SharedBlock(id, "Helper " + id, "does things", authorUid, "Ann", createdAt, 160, 1, List.of(), false,
                downloads, "", SharedKind.JAVA, "", "", meta);
    }

    @Test
    public void newKindsKnowTheirPayload() {
        assertTrue(SharedKind.JAVA.isCode());
        assertTrue(SharedKind.KOTLIN.isCode());
        assertTrue(SharedKind.COMPONENT.isCode());
        assertFalse(SharedKind.VIEW.isCode());
        assertFalse(SharedKind.JAVA.hasBlocks());
        assertTrue(SharedKind.MOREBLOCK.hasBlocks());
        assertEquals("kt", SharedKind.KOTLIN.extension());
        assertEquals("", SharedKind.BLOCKS.extension());
        assertEquals(SharedKind.KOTLIN, SharedKind.fromKey("kotlin"));
        assertEquals(SharedKind.BLOCKS, SharedKind.fromKey("unknown"));
    }

    @Test
    public void aCodePostSurvivesThePublishMap() {
        SharedBlock post = java("", 0, 0, 0, 0, "u1");
        Map<String, Object> map = new HashMap<>(post.toPublishMap());
        map.put(SharedBlock.CREATED_AT, 5L);
        SharedBlock back = SharedBlock.fromMap("id1", map);
        assertNotNull(back);
        assertEquals(SharedKind.JAVA, back.kind());
        assertEquals("class A {}", back.payloadText());
        assertEquals("A.java", back.meta().fileName());
        assertEquals(List.of("ui", "helper"), back.meta().tags());
        assertEquals("1.2", back.meta().version());
        assertEquals(26, back.meta().minSdk());
        assertEquals(List.of("gson"), back.meta().dependencies());
        assertEquals(0, back.meta().likes());
        assertFalse(map.containsKey(SharedBlock.XML));
        assertFalse(map.containsKey(SharedBlock.BLOCKS_JSON));
    }

    @Test
    public void aCodePostWithoutCodeIsSkipped() {
        Map<String, Object> map = new HashMap<>(java("", 0, 0, 0, 0, "u1").toPublishMap());
        map.remove(PostMeta.CODE);
        assertNull(SharedBlock.fromMap("id1", map));
    }

    @Test
    public void entriesFromBeforePostsReadWithEmptyMeta() {
        Map<String, Object> map = new HashMap<>();
        map.put(SharedBlock.NAME, "Old");
        map.put(SharedBlock.AUTHOR_UID, "u");
        map.put(SharedBlock.BLOCKS_JSON, "[]");
        SharedBlock old = SharedBlock.fromMap("x", map);
        assertNotNull(old);
        assertEquals(PostMeta.EMPTY, old.meta());
        assertTrue(old.meta().tags().isEmpty());
    }

    @Test
    public void publishMapOmitsEmptyOptionalFieldsAndStartsCountersAtZero() {
        SharedBlock plain = new SharedBlock("", "n", "d", "u", "a", 0, 1, 1, List.of("x"), false, 0, "[]");
        Map<String, Object> map = plain.toPublishMap();
        assertFalse(map.containsKey(PostMeta.TAGS));
        assertFalse(map.containsKey(PostMeta.VERSION));
        assertEquals(0L, map.get(PostMeta.LIKES));
        assertEquals(0L, map.get(PostMeta.COMMENT_COUNT));
    }

    @Test
    public void popularityCountsPicksLikesAndComments() {
        assertEquals(1 + 3 * 2 + 2 * 3, EvolveFilter.popularity(java("a", 1, 1, 2, 3, "u")));
    }

    @Test
    public void popularOrdersByPopularityThenNewest() {
        SharedBlock liked = java("liked", 1, 0, 5, 0, "u");   // 15
        SharedBlock picked = java("picked", 2, 10, 0, 0, "u"); // 10
        SharedBlock newer = java("newer", 3, 10, 0, 0, "u");   // 10, newer
        List<SharedBlock> shown = EvolveFilter.apply(List.of(picked, liked, newer), EnumSet.allOf(SharedKind.class), null,
                EvolveFilter.Mode.POPULAR, "", "", 10);
        assertEquals(List.of("liked", "newer", "picked"), shown.stream().map(SharedBlock::id).toList());
    }

    @Test
    public void followingAndSavedScopes() {
        SharedBlock a = java("a", 1, 0, 0, 0, "ann");
        SharedBlock b = java("b", 2, 0, 0, 0, "bob");
        SharedBlock c = java("c", 3, 0, 0, 0, "ann");
        List<SharedBlock> following = EvolveFilter.apply(List.of(a, b, c), EnumSet.allOf(SharedKind.class), null,
                EvolveFilter.Mode.FOLLOWING, "", "me", 10, Set.of("ann"), Set.of());
        assertEquals(List.of("c", "a"), following.stream().map(SharedBlock::id).toList());
        List<SharedBlock> saved = EvolveFilter.apply(List.of(a, b, c), EnumSet.allOf(SharedKind.class), null,
                EvolveFilter.Mode.SAVED, "", "me", 10, Set.of(), Set.of("b"));
        assertEquals(List.of("b"), saved.stream().map(SharedBlock::id).toList());
        assertTrue(EvolveFilter.apply(List.of(a), EnumSet.allOf(SharedKind.class), null, EvolveFilter.Mode.FOLLOWING, "", "me", 10).isEmpty());
    }

    @Test
    public void searchFindsTagsFileNamesAndCode() {
        SharedBlock post = java("a", 1, 0, 0, 0, "u");
        for (String query : new String[]{"helper", "A.java", "class A", "ANN"}) {
            assertEquals(query, 1, EvolveFilter.apply(List.of(post), EnumSet.allOf(SharedKind.class), null, EvolveFilter.Mode.ALL, query, "", 10).size());
        }
        assertEquals(0, EvolveFilter.apply(List.of(post), EnumSet.allOf(SharedKind.class), null, EvolveFilter.Mode.ALL, "nothing here", "", 10).size());
    }

    @Test
    public void kindFilterWorksForNewKinds() {
        SharedBlock post = java("a", 1, 0, 0, 0, "u");
        assertEquals(1, EvolveFilter.apply(List.of(post), EnumSet.allOf(SharedKind.class), SharedKind.JAVA, EvolveFilter.Mode.ALL, "", "", 10).size());
        assertEquals(0, EvolveFilter.apply(List.of(post), EnumSet.allOf(SharedKind.class), SharedKind.KOTLIN, EvolveFilter.Mode.ALL, "", "", 10).size());
        assertEquals(0, EvolveFilter.apply(List.of(post), EnumSet.of(SharedKind.BLOCKS), null, EvolveFilter.Mode.ALL, "", "", 10).size());
    }
}
