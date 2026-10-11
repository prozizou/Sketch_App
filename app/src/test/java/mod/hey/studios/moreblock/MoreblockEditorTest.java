package mod.hey.studios.moreblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.util.Pair;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.eC;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class MoreblockEditorTest {

    private static final String JAVA = "MainActivity.java";

    @Test
    public void partsSplitLabelsAndTypedParameters() {
        ArrayList<Pair<String, String>> parts = MoreblockEditor.parts("uniqueID[String] for %s.prefix with %m.view.target");
        assertEquals(4, parts.size());
        assertEquals(new Pair<>("t", "for"), parts.get(0));
        assertEquals(new Pair<>("s", "prefix"), parts.get(1));
        assertEquals(new Pair<>("t", "with"), parts.get(2));
        assertEquals(new Pair<>("m.view", "target"), parts.get(3));
        assertTrue(MoreblockEditor.parts("reset").isEmpty());
        assertTrue(MoreblockEditor.parts("items[l|List Map]").isEmpty());
    }

    @Test
    public void callSpecDropsReturnType() {
        assertEquals("uniqueID %s.prefix", MoreblockEditor.callSpec("uniqueID[String] %s.prefix"));
        assertEquals("setValue %s.key", MoreblockEditor.callSpec("setValue %s.key"));
    }

    @Test
    public void applyRenamesDefinitionBodyAndCallsKeepingMatchingValues() {
        eC logic = logic("setValue", "setValue %s.key %d.value");
        BlockBean call = call("1", "setValue %s.key %d.value", "name", "@2");
        BlockBean number = new BlockBean("2", "%d + %d", "d", "plus");
        events(logic).put("onCreate_initializeLogic", new ArrayList<>(List.of(call, number)));

        int updated = MoreblockEditor.apply(logic, JAVA, "setValue", "store", "store %d.value to %s.target");

        assertEquals(1, updated);
        assertEquals(new Pair<>("store", "store %d.value to %s.target"), logic.g.get(JAVA).get(0));
        assertNull(events(logic).get("setValue_moreBlock"));
        assertNotNull(events(logic).get("store_moreBlock"));
        assertEquals("store %d.value to %s.target", call.spec);
        // "value" keeps its block, "key" is gone, "target" is new and empty.
        assertEquals(List.of("@2", ""), call.parameters);
        assertEquals(2, events(logic).get("onCreate_initializeLogic").size());
    }

    @Test
    public void removedParameterDropsPluggedBlocks() {
        eC logic = logic("uniqueID[String]", "uniqueID[String] %s.prefix %d.length");
        BlockBean call = call("1", "uniqueID %s.prefix %d.length", "@2", "8");
        call.type = "s";
        BlockBean join = new BlockBean("2", "%s + %s", "s", "stringJoin");
        join.parameters = new ArrayList<>(List.of("@3", "b"));
        BlockBean inner = new BlockBean("3", "%s", "s", "toString");
        BlockBean unrelated = new BlockBean("4", "%s", "s", "toString");
        events(logic).put("button1_onClick", new ArrayList<>(List.of(call, join, inner, unrelated)));

        MoreblockEditor.apply(logic, JAVA, "uniqueID[String]", "uniqueID[String]", "uniqueID[String] %d.length");

        assertEquals(List.of("8"), call.parameters);
        ArrayList<BlockBean> left = events(logic).get("button1_onClick");
        assertEquals(2, left.size());
        assertEquals("1", left.get(0).id);
        assertEquals("4", left.get(1).id);
    }

    @Test
    public void typeChangeOfParameterDropsItsValue() {
        eC logic = logic("f", "f %s.x");
        BlockBean call = call("1", "f %s.x", "hello");
        events(logic).put("e", new ArrayList<>(List.of(call)));

        MoreblockEditor.apply(logic, JAVA, "f", "f", "f %d.x");

        assertEquals(List.of(""), call.parameters);
    }

    @Test
    public void callsInsideOtherMoreBlocksAreUpdated() {
        eC logic = logic("a", "a %s.x");
        BlockBean recursive = call("1", "a %s.x", "v");
        events(logic).put("a_moreBlock", new ArrayList<>(List.of(recursive)));

        assertTrue(MoreblockEditor.isUsed(logic, JAVA, "a %s.x"));
        MoreblockEditor.apply(logic, JAVA, "a", "b", "b %s.x");

        assertEquals("b %s.x", recursive.spec);
        assertEquals(List.of("v"), recursive.parameters);
        assertFalse(MoreblockEditor.isUsed(logic, JAVA, "a %s.x"));
        assertTrue(MoreblockEditor.isUsed(logic, JAVA, "b %s.x"));
    }

    private static eC logic(String name, String spec) {
        eC logic = new eC("test");
        logic.g = new HashMap<>();
        logic.g.put(JAVA, new ArrayList<>(List.of(new Pair<>(name, spec))));
        logic.d = new HashMap<>();
        HashMap<String, ArrayList<BlockBean>> events = new HashMap<>();
        events.put(name + "_moreBlock", new ArrayList<>());
        logic.d.put(JAVA, events);
        return logic;
    }

    private static HashMap<String, ArrayList<BlockBean>> events(eC logic) {
        return logic.d.get(JAVA);
    }

    private static BlockBean call(String id, String spec, String... values) {
        BlockBean call = new BlockBean(id, spec, " ", "definedFunc");
        call.parameters = new ArrayList<>(List.of(values));
        return call;
    }
}
