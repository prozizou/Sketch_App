package pro.sketchware.evolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.evolve.SharedBlockRules.Problem;

public class SharedBlockRulesTest {

    @Test
    public void namesMustHaveContentAndStayShort() {
        assertEquals(Problem.NAME_EMPTY, SharedBlockRules.checkName(null));
        assertEquals(Problem.NAME_EMPTY, SharedBlockRules.checkName("   "));
        assertEquals(Problem.NONE, SharedBlockRules.checkName("  My blocks  "));
        assertEquals(Problem.NONE, SharedBlockRules.checkName("a".repeat(SharedBlockRules.MAX_NAME_LENGTH)));
        assertEquals(Problem.NAME_TOO_LONG, SharedBlockRules.checkName("a".repeat(SharedBlockRules.MAX_NAME_LENGTH + 1)));
        assertEquals(Problem.NAME_INVALID, SharedBlockRules.checkName("bad\nname"));
        assertEquals("accents and symbols are fine", Problem.NONE, SharedBlockRules.checkName("Écran d'accueil ✨"));
    }

    @Test
    public void descriptionMayBeEmptyButNotLong() {
        assertEquals(Problem.NONE, SharedBlockRules.checkDescription(null));
        assertEquals(Problem.NONE, SharedBlockRules.checkDescription(""));
        assertEquals(Problem.NONE, SharedBlockRules.checkDescription("d".repeat(SharedBlockRules.MAX_DESCRIPTION_LENGTH)));
        assertEquals(Problem.DESCRIPTION_TOO_LONG, SharedBlockRules.checkDescription("d".repeat(SharedBlockRules.MAX_DESCRIPTION_LENGTH + 1)));
    }

    @Test
    public void stacksAreBoundedInBlocksAndSize() {
        assertEquals(Problem.NO_BLOCKS, SharedBlockRules.checkStack(0, "[]"));
        assertEquals(Problem.NONE, SharedBlockRules.checkStack(1, "[{}]"));
        assertEquals(Problem.NONE, SharedBlockRules.checkStack(SharedBlockRules.MAX_BLOCKS, "[{}]"));
        assertEquals(Problem.TOO_MANY_BLOCKS, SharedBlockRules.checkStack(SharedBlockRules.MAX_BLOCKS + 1, "[{}]"));
        assertEquals(Problem.TOO_LARGE, SharedBlockRules.checkStack(3, "x".repeat(SharedBlockRules.MAX_JSON_LENGTH + 1)));
        assertEquals(Problem.TOO_LARGE, SharedBlockRules.checkStack(3, null));
    }

    @Test
    public void rawCodeBlocksAreRecognised() {
        assertTrue(SharedBlockRules.usesRawCode(List.of("setText", "addSourceDirectly")));
        assertTrue(SharedBlockRules.usesRawCode(List.of("customImport")));
        assertFalse(SharedBlockRules.usesRawCode(List.of("setText", "if", "repeat")));
        assertFalse(SharedBlockRules.usesRawCode(List.of()));
    }

    @Test
    public void opCodesAreDeduplicatedInOrder() {
        List<String> input = new java.util.ArrayList<>(List.of("if", "setText", "if", " ", "repeat", "setText"));
        input.add(null);
        assertEquals(List.of("if", "setText", "repeat"), SharedBlockRules.distinctOpCodes(input));
    }

    @Test
    public void pickingNeverOverwritesAnExistingName() {
        assertEquals("Login", SharedBlockRules.uniqueName("Login", List.of("Other")));
        assertEquals("Login (2)", SharedBlockRules.uniqueName("Login", List.of("Login")));
        assertEquals("Login (3)", SharedBlockRules.uniqueName("Login", List.of("login", "Login (2)")));
        assertEquals("Login", SharedBlockRules.uniqueName("  Login ", Set.of()));
    }

    private static SharedBlock sample() {
        return new SharedBlock("", "Toast helper", "Shows a toast", "uid1", "Aïssatou", 0, 157, 3,
                List.of("if", "showToast"), false, 0, "[{\"id\":\"1\"}]");
    }

    @Test
    public void publishedFieldsMatchTheNodeKeysAndLeaveTheTimeToTheServer() {
        Map<String, Object> map = sample().toPublishMap();
        assertEquals("Toast helper", map.get(SharedBlock.NAME));
        assertEquals("uid1", map.get(SharedBlock.AUTHOR_UID));
        assertEquals(3, map.get(SharedBlock.BLOCK_COUNT));
        assertEquals(0L, map.get(SharedBlock.DOWNLOADS));
        assertEquals(List.of("if", "showToast"), map.get(SharedBlock.OP_CODES));
        assertFalse("the clock is the server's", map.containsKey(SharedBlock.CREATED_AT));
        assertFalse("the key is the node's, not a field", map.containsKey("id"));
    }

    @Test
    public void aNodeReadBackIsTheSameBlock() {
        Map<String, Object> map = new HashMap<>(sample().toPublishMap());
        map.put(SharedBlock.CREATED_AT, 1760400000000L);
        map.put(SharedBlock.DOWNLOADS, 4.0); // the database may hand numbers back as Double
        SharedBlock read = SharedBlock.fromMap("-Nabc", map);
        assertNotNull(read);
        assertEquals("-Nabc", read.id());
        assertEquals("Toast helper", read.name());
        assertEquals("Aïssatou", read.authorName());
        assertEquals(1760400000000L, read.createdAt());
        assertEquals(4L, read.downloads());
        assertEquals(157, read.appVersion());
        assertEquals(List.of("if", "showToast"), read.opCodes());
    }

    @Test
    public void damagedNodesAreSkippedNotShownHalfEmpty() {
        assertNull(SharedBlock.fromMap(null, new HashMap<>()));
        assertNull(SharedBlock.fromMap("x", null));
        assertNull(SharedBlock.fromMap("x", new HashMap<>()));
        Map<String, Object> noBlocks = new HashMap<>(sample().toPublishMap());
        noBlocks.remove(SharedBlock.BLOCKS_JSON);
        assertNull(SharedBlock.fromMap("x", noBlocks));
        Map<String, Object> noAuthor = new HashMap<>(sample().toPublishMap());
        noAuthor.remove(SharedBlock.AUTHOR_UID);
        assertNull(SharedBlock.fromMap("x", noAuthor));
    }

    @Test
    public void oddValuesNeverThrow() {
        Map<String, Object> map = new HashMap<>(sample().toPublishMap());
        map.put(SharedBlock.BLOCK_COUNT, "three");
        map.put(SharedBlock.OP_CODES, "not a list");
        map.put(SharedBlock.USES_RAW_CODE, "yes");
        SharedBlock read = SharedBlock.fromMap("x", map);
        assertNotNull(read);
        assertEquals(0, read.blockCount());
        assertTrue(read.opCodes().isEmpty());
        assertFalse(read.usesRawCode());
    }

    @Test
    public void kindsRoundTripAndOldNodesAreBlocks() {
        assertEquals(SharedKind.BLOCKS, SharedKind.fromKey(null));
        assertEquals(SharedKind.BLOCKS, SharedKind.fromKey("something new"));
        assertEquals(SharedKind.MOREBLOCK, SharedKind.fromKey("moreblock"));
        assertEquals(SharedKind.VIEW, SharedKind.fromKey("view"));
        Map<String, Object> old = new java.util.HashMap<>(sample().toPublishMap());
        old.remove(SharedBlock.KIND);
        assertEquals(SharedKind.BLOCKS, SharedBlock.fromMap("k", old).kind());
    }

    @Test
    public void aMoreBlockNeedsItsSpecAndAViewNeedsItsXml() {
        SharedBlock moreBlock = new SharedBlock("", "calc", "", "uid1", "A", 0, 157, 2, List.of("definedFunc"), false, 0,
                "[{\"id\":\"1\"}]", SharedKind.MOREBLOCK, "calc %d.a", "");
        Map<String, Object> map = new java.util.HashMap<>(moreBlock.toPublishMap());
        assertEquals("moreblock", map.get(SharedBlock.KIND));
        assertEquals("calc %d.a", map.get(SharedBlock.SPEC));
        assertEquals(SharedKind.MOREBLOCK, SharedBlock.fromMap("k", map).kind());
        map.remove(SharedBlock.SPEC);
        assertNull(SharedBlock.fromMap("k", map));

        SharedBlock view = new SharedBlock("", "Login screen", "", "uid1", "A", 0, 157, 4, List.of(), false, 0, "",
                SharedKind.VIEW, "", "<LinearLayout/>");
        Map<String, Object> viewMap = new java.util.HashMap<>(view.toPublishMap());
        assertEquals("view", viewMap.get(SharedBlock.KIND));
        assertEquals("<LinearLayout/>", viewMap.get(SharedBlock.XML));
        assertFalse("a view carries no block JSON", viewMap.containsKey(SharedBlock.BLOCKS_JSON));
        SharedBlock back = SharedBlock.fromMap("k", viewMap);
        assertEquals(SharedKind.VIEW, back.kind());
        assertEquals("<LinearLayout/>", back.xml());
        viewMap.remove(SharedBlock.XML);
        assertNull(SharedBlock.fromMap("k", viewMap));
    }

    private static SharedBlock entry(String id, String name, String author, String uid, long createdAt, long downloads, SharedKind kind) {
        return new SharedBlock(id, name, "desc of " + name, uid, author, createdAt, 157, 2, List.of("if"), false, downloads,
                kind == SharedKind.VIEW ? "" : "[{}]", kind, kind == SharedKind.MOREBLOCK ? "spec" : "",
                kind == SharedKind.VIEW ? "<LinearLayout/>" : "");
    }

    @Test
    public void theListIsFilteredAndOrderedByTheChips() {
        long now = 100L * 24 * 60 * 60 * 1000;
        long day = 24L * 60 * 60 * 1000;
        SharedBlock fresh = entry("a", "Login", "Aminata", "u1", now - day, 3, SharedKind.BLOCKS);
        SharedBlock old = entry("b", "Calculator", "Moussa", "u2", now - 30 * day, 50, SharedKind.MOREBLOCK);
        SharedBlock layout = entry("c", "Profile", "Aminata", "u1", now - 2 * day, 9, SharedKind.VIEW);
        List<SharedBlock> all = List.of(old, layout, fresh);
        java.util.EnumSet<SharedKind> every = java.util.EnumSet.allOf(SharedKind.class);

        assertEquals(List.of(fresh, layout, old), EvolveFilter.apply(all, every, null, EvolveFilter.Mode.ALL, "", "u1", now));
        assertEquals("most picked first", List.of(old, layout, fresh), EvolveFilter.apply(all, every, null, EvolveFilter.Mode.POPULAR, "", "u1", now));
        assertEquals("last 7 days only", List.of(fresh, layout), EvolveFilter.apply(all, every, null, EvolveFilter.Mode.RECENT, "", "u1", now));
        assertEquals("mine", List.of(fresh, layout), EvolveFilter.apply(all, every, null, EvolveFilter.Mode.MINE, "", "u1", now));
        assertEquals("nobody signed in: nothing is mine", List.of(), EvolveFilter.apply(all, every, null, EvolveFilter.Mode.MINE, "", "", now));
        assertEquals("one kind", List.of(old), EvolveFilter.apply(all, every, SharedKind.MOREBLOCK, EvolveFilter.Mode.ALL, "", "u1", now));
        assertEquals("the screen only offers blocks", List.of(fresh),
                EvolveFilter.apply(all, java.util.EnumSet.of(SharedKind.BLOCKS), null, EvolveFilter.Mode.ALL, "", "u1", now));
    }

    @Test
    public void searchMatchesTitleAuthorDescriptionIdentifierAndXml() {
        long now = 1_000_000L;
        SharedBlock block = entry("-Nabc123", "Login", "Aminata", "u1", now, 0, SharedKind.BLOCKS);
        SharedBlock layout = entry("-Nxyz", "Profile", "Moussa", "u2", now, 0, SharedKind.VIEW);
        List<SharedBlock> all = List.of(block, layout);
        java.util.EnumSet<SharedKind> every = java.util.EnumSet.allOf(SharedKind.class);
        for (String query : new String[]{"logi", "AMINATA", "desc of login", "nabc123", "if"}) {
            assertEquals(query, List.of(block), EvolveFilter.apply(all, every, null, EvolveFilter.Mode.ALL, query, "", now).stream()
                    .filter(b -> b.id().equals(block.id())).toList());
        }
        assertEquals(List.of(layout), EvolveFilter.apply(all, every, null, EvolveFilter.Mode.ALL, "linearlayout", "", now));
        assertEquals(List.of(), EvolveFilter.apply(all, every, null, EvolveFilter.Mode.ALL, "zzz", "", now));
    }

    @Test
    public void theCacheKeepsPagesForAWhileOnly() {
        EvolveCache.clear();
        SharedBlock block = entry("a", "Login", "A", "u1", 5, 0, SharedKind.BLOCKS);
        EvolveCache.store(List.of(block), true, 1_000);
        assertEquals(List.of(block), EvolveCache.items(1_000 + 60_000));
        assertTrue(EvolveCache.hasMore());
        assertEquals("too old", List.of(), EvolveCache.items(1_000 + EvolveCache.MAX_AGE_MS + 1));
        EvolveCache.clear();
        assertEquals(List.of(), EvolveCache.items(1_001));
    }
}
