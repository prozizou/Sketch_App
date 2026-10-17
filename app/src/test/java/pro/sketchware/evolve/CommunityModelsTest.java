package pro.sketchware.evolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommunityModelsTest {
    private static Comment comment(String id, String parent, long at) {
        return new Comment(id, "p", parent, "u" + id, "N" + id, "text " + id, at, List.of());
    }

    private static List<String> order(List<CommentThreads.Row> rows) {
        return rows.stream().map(r -> r.comment().id() + "@" + r.depth()).toList();
    }

    @Test
    public void threadsNestRepliesUnderTheirParentOldestFirst() {
        List<Comment> comments = List.of(
                comment("c", "a", 30), comment("a", "", 10), comment("b", "", 20), comment("d", "c", 40), comment("e", "a", 25));
        assertEquals(List.of("a@0", "e@1", "c@1", "d@2", "b@0"), order(CommentThreads.rows(comments)));
    }

    @Test
    public void depthStopsAtTheMaximum() {
        List<Comment> chain = List.of(comment("a", "", 1), comment("b", "a", 2), comment("c", "b", 3), comment("d", "c", 4),
                comment("e", "d", 5), comment("f", "e", 6));
        assertEquals(List.of("a@0", "b@1", "c@2", "d@3", "e@3", "f@3"), order(CommentThreads.rows(chain)));
    }

    @Test
    public void anAnswerToAMissingCommentStandsOnItsOwn() {
        assertEquals(List.of("x@0"), order(CommentThreads.rows(List.of(comment("x", "gone", 1)))));
    }

    @Test
    public void aCycleOfAnswersDoesNotLoopForever() {
        List<Comment> cycle = List.of(comment("a", "b", 1), comment("b", "a", 2), comment("self", "self", 3));
        List<CommentThreads.Row> rows = CommentThreads.rows(cycle);
        assertTrue(rows.size() <= 3);
        assertEquals(1, rows.stream().filter(r -> r.comment().id().equals("self")).count());
    }

    @Test
    public void noCommentsNoRows() {
        assertTrue(CommentThreads.rows(List.of()).isEmpty());
    }

    private static Map<String, String> people() {
        Map<String, String> people = new LinkedHashMap<>();
        people.put("Ann", "u1");
        people.put("Ann Lee", "u2");
        people.put("Bob", "u3");
        return people;
    }

    @Test
    public void theLongestNameWins() {
        assertEquals(List.of("u2"), Mentions.extract("hi @Ann Lee, thanks", people()));
        assertEquals(List.of("u1"), Mentions.extract("hi @Ann, thanks", people()));
    }

    @Test
    public void mentionsAreCaseInsensitiveOrderedAndUnique() {
        assertEquals(List.of("u3", "u1"), Mentions.extract("@bob and @ANN and @Bob again", people()));
    }

    @Test
    public void anAddressOrAPartialWordIsNotAMention() {
        assertTrue(Mentions.extract("write to ann@Ann.com", people()).isEmpty());
        assertTrue(Mentions.extract("@Annabel", people()).isEmpty());
        assertTrue(Mentions.extract("no mention here", people()).isEmpty());
        assertTrue(Mentions.extract("@Ann", Map.of()).isEmpty());
        assertTrue(Mentions.extract(null, people()).isEmpty());
    }

    @Test
    public void typingDetectsAMentionBeingWritten() {
        assertEquals("Ba", Mentions.typing("hello @Ba"));
        assertEquals("", Mentions.typing("hello @"));
        assertNull(Mentions.typing("hello"));
        assertNull(Mentions.typing("mail me@ho"));
        assertNull(Mentions.typing("@Bob said\nhi"));
    }

    @Test
    public void aCommentSurvivesTheMap() {
        Comment original = new Comment("", "p", "parent1", "u1", "Ann", "hello @Bob", 0, List.of("u3"));
        Map<String, Object> map = new HashMap<>(original.toPublishMap());
        map.put(Comment.CREATED_AT, 7L);
        Comment back = Comment.fromMap("p", "c1", map);
        assertNotNull(back);
        assertEquals("parent1", back.parentId());
        assertEquals("hello @Bob", back.text());
        assertEquals(List.of("u3"), back.mentions());
        assertEquals(7L, back.createdAt());
    }

    @Test
    public void aCommentWithoutAuthorOrTextIsSkipped() {
        Map<String, Object> map = new HashMap<>();
        map.put(Comment.TEXT, "x");
        assertNull(Comment.fromMap("p", "c", map));
        map.put(Comment.AUTHOR_UID, "u");
        map.put(Comment.TEXT, "");
        assertNull(Comment.fromMap("p", "c", map));
        assertFalse(new Comment("c", "p", "", "u", "n", "t", 1, List.of()).toPublishMap().containsKey(Comment.PARENT_ID));
    }

    @Test
    public void notificationsRoundTripAndUnknownTypesAreSkipped() {
        EvolveNotification n = new EvolveNotification("", EvolveNotification.Type.REPLY, "u1", "Ann", "p1", "Post", 0, false);
        Map<String, Object> map = new HashMap<>(n.toPublishMap());
        map.put(EvolveNotification.CREATED_AT, 9L);
        EvolveNotification back = EvolveNotification.fromMap("n1", map);
        assertNotNull(back);
        assertEquals(EvolveNotification.Type.REPLY, back.type());
        assertEquals("Post", back.postName());
        assertFalse(back.read());
        map.put(EvolveNotification.TYPE, "teleport");
        assertNull(EvolveNotification.fromMap("n1", map));
        EvolveNotification follow = new EvolveNotification("", EvolveNotification.Type.FOLLOW, "u1", "Ann", "", "", 0, false);
        assertFalse(follow.toPublishMap().containsKey(EvolveNotification.POST_ID));
    }

    @Test
    public void profilesKeepTheirLimits() {
        EvolveProfile p = new EvolveProfile("u", "N".repeat(150), "", "b".repeat(300), 0);
        assertEquals(EvolveProfile.MAX_NAME_LENGTH, String.valueOf(p.toMap().get(EvolveProfile.NAME)).length());
        assertEquals(EvolveProfile.MAX_BIO_LENGTH, String.valueOf(p.toMap().get(EvolveProfile.BIO)).length());
        assertFalse(p.toMap().containsKey(EvolveProfile.PHOTO_URL));
        assertNull(EvolveProfile.fromMap("u", new HashMap<>()));
        Map<String, Object> map = new HashMap<>();
        map.put(EvolveProfile.NAME, "Ann");
        assertEquals("Ann", EvolveProfile.fromMap("u", map).name());
    }
}
