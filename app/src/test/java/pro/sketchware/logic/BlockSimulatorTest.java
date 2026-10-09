package pro.sketchware.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static pro.sketchware.logic.LogicTestData.b;
import static pro.sketchware.logic.LogicTestData.c;
import static pro.sketchware.logic.LogicTestData.event;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class BlockSimulatorTest {
    @Test
    public void setsAndIncreasesAVariable() {
        BlockSimulator.Result result = BlockSimulator.run(event("e",
                b(10, "setVarInt", "set %m.varInt to %d", 11, "score", "5"),
                b(11, "increaseInt", "increase %m.varInt", -1, "score")), Map.of(), Map.of(), 100);
        assertTrue(result.finished());
        assertEquals(2, result.steps().size());
        assertEquals("5", result.steps().get(0).state().get("score"));
        assertEquals("6", result.steps().get(1).state().get("score"));
    }

    @Test
    public void evaluatesNestedExpressions() {
        // x = (2 + 3) * 4
        BlockSimulator.Result result = BlockSimulator.run(event("e",
                b(10, "setVarInt", "set %m.varInt to %d", -1, "x", "@11"),
                b(11, "*", "%d * %d", -1, "@12", "4"),
                b(12, "+", "%d + %d", -1, "2", "3")), Map.of(), Map.of(), 100);
        assertEquals("20", result.steps().get(0).state().get("x"));
        assertEquals("set x = 20", result.steps().get(0).description());
    }

    @Test
    public void ifElseTakesTheRightBranch() {
        BlockSimulator.Result result = BlockSimulator.run(event("e",
                c(10, "ifElse", "if %b then else", -1, 11, 12, "@13"),
                b(11, "doToast", "toast %s", -1, "big"),
                b(12, "doToast", "toast %s", -1, "small"),
                b(13, ">", "%d > %d", -1, "@14", "10"),
                b(14, "getVar", "n", -1)), Map.of("n", 3d), Map.of(), 100);
        assertEquals(List.of("small"), result.output());
    }

    @Test
    public void repeatRunsItsBodyAndBreakEndsIt() {
        BlockSimulator.Result result = BlockSimulator.run(event("e",
                c(10, "repeat", "repeat %d", -1, 11, -1, "10"),
                b(11, "increaseInt", "increase %m.varInt", 12, "i"),
                c(12, "if", "if %b then", -1, 13, -1, "@14"),
                b(13, "break", "break", -1),
                b(14, "=", "%d = %d", -1, "@15", "3"),
                b(15, "getVar", "i", -1)), Map.of("i", 0d), Map.of(), 1000);
        assertTrue(result.finished());
        assertEquals("3", result.steps().get(result.steps().size() - 1).state().get("i"));
    }

    @Test
    public void foreverWithoutBreakStopsAtTheStepLimit() {
        BlockSimulator.Result result = BlockSimulator.run(event("e",
                c(10, "forever", "forever", -1, 11, -1),
                b(11, "increaseInt", "increase %m.varInt", -1, "i")), Map.of("i", 0d), Map.of(), 50);
        assertFalse(result.finished());
        assertEquals(50, result.steps().size());
        assertTrue(result.stopReason().contains("never ends"));
    }

    @Test
    public void listsAndText() {
        BlockSimulator.Result result = BlockSimulator.run(event("e",
                b(10, "addListStr", "add %s to %m.listStr", 11, "a", "names"),
                b(11, "addListStr", "add %s to %m.listStr", 12, "@20", "names"),
                b(12, "setVarString", "set %m.varStr to %s", 13, "s", "@21"),
                b(13, "deleteList", "delete %d of %m.list", -1, "5", "names"),
                b(20, "toUpperCase", "%s to upper case", -1, "b"),
                b(21, "getAtListStr", "get at %d of %m.listStr", -1, "1", "names")), Map.of(), Map.of(), 100);
        Map<String, String> last = result.steps().get(result.steps().size() - 1).state();
        assertEquals("[\"a\", \"B\"]", last.get("names"));
        assertEquals("\"B\"", last.get("s"));
        assertTrue(result.steps().get(3).description().contains("out of range"));
    }

    @Test
    public void androidBlocksAreListedButNotRun() {
        BlockSimulator.Result result = BlockSimulator.run(event("e",
                b(10, "setText", "%m.textview setText %s", 11, "textview1", "hi"),
                b(11, "setVarString", "set %m.varStr to %s", -1, "s", "@12"),
                b(12, "getText", "%m.textview getText", -1, "textview1")), Map.of(), Map.of(), 100);
        assertFalse(result.steps().get(0).simulated());
        assertEquals("?", result.steps().get(1).state().get("s"));
    }

    @Test
    public void emptyParametersFollowTheCodeGenerator() {
        BlockSimulator.Result result = BlockSimulator.run(event("e",
                c(10, "if", "if %b then", 11, 12, -1, ""),
                b(11, "setVarInt", "set %m.varInt to %d", -1, "x", ""),
                b(12, "doToast", "toast %s", -1, "in")), Map.of(), Map.of(), 100);
        assertEquals(List.of("in"), result.output());
        assertEquals("0", result.steps().get(result.steps().size() - 1).state().get("x"));
    }

    @Test
    public void showFormatsValues() {
        assertEquals("3", BlockSimulator.show(3d));
        assertEquals("2.5", BlockSimulator.show(2.5));
        assertEquals("\"x\"", BlockSimulator.show("x"));
        assertEquals("?", BlockSimulator.show(null));
        assertEquals("%m.liststr", BlockSimulator.paramType("add %s to %m.listStr", 1));
    }

    @Test
    public void startsFromTheGeneratedDefaults() {
        Map<String, Object> values = BlockSimulator.initialValues(Map.of("flag", 0, "n", 1, "s", 2, "m", 3));
        assertEquals(Boolean.FALSE, values.get("flag"));
        assertEquals(0d, values.get("n"));
        assertEquals("", values.get("s"));
        assertFalse(values.containsKey("m"));
    }
}
