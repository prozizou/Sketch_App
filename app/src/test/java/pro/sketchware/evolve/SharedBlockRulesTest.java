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
}
