package pro.sketchware.logic.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Blocks that are still in the event keep their id when the Java is applied. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class IdAssignerTest {
    private static ArrayList<BlockBean> first(String java) {
        JavaToBlocks.Result result = SyncFixtures.converter().convert(java, List.of());
        assertTrue(result.ok());
        return result.blocks();
    }

    private static ArrayList<BlockBean> then(List<BlockBean> previous, String java) {
        JavaToBlocks.Result result = SyncFixtures.converter().convert(java, previous);
        assertTrue(result.problems().toString(), result.ok());
        assertUnique(result.blocks());
        return result.blocks();
    }

    private static void assertUnique(List<BlockBean> blocks) {
        Set<String> ids = new HashSet<>();
        for (BlockBean bean : blocks) assertTrue("duplicate id " + bean.id + " in\n" + SyncFixtures.outline(blocks), ids.add(bean.id));
    }

    private static String idOf(List<BlockBean> blocks, String opCode, int nth) {
        int seen = 0;
        for (BlockBean bean : blocks) if (bean.opCode.equals(opCode) && seen++ == nth) return bean.id;
        throw new AssertionError("no " + opCode + " #" + nth + " in\n" + SyncFixtures.outline(blocks));
    }

    private static int maxId(List<BlockBean> blocks) {
        int max = 0;
        for (BlockBean bean : blocks) max = Math.max(max, Integer.parseInt(bean.id));
        return max;
    }

    @Test
    public void aChangedParameterKeepsTheBlock() {
        ArrayList<BlockBean> before = first("name = \"a\";\ncounter++;");
        ArrayList<BlockBean> after = then(before, "name = \"b\";\ncounter++;");
        assertEquals(idOf(before, "setVarString", 0), idOf(after, "setVarString", 0));
        assertEquals(idOf(before, "increaseInt", 0), idOf(after, "increaseInt", 0));
        assertEquals("b", after.get(0).parameters.get(1));
    }

    @Test
    public void anInsertedStatementIsNewAndTheOthersStay() {
        ArrayList<BlockBean> before = first("counter++;\ncounter--;");
        ArrayList<BlockBean> after = then(before, "counter++;\nname = \"x\";\ncounter--;");
        assertEquals(idOf(before, "increaseInt", 0), idOf(after, "increaseInt", 0));
        assertEquals(idOf(before, "decreaseInt", 0), idOf(after, "decreaseInt", 0));
        assertTrue(Integer.parseInt(idOf(after, "setVarString", 0)) > maxId(before));
    }

    @Test
    public void aDeletedStatementIsGoneAndItsIdIsNotReused() {
        ArrayList<BlockBean> before = first("counter++;\nname = \"x\";\ncounter--;");
        String removed = idOf(before, "setVarString", 0);
        ArrayList<BlockBean> after = then(before, "counter++;\ncounter--;");
        for (BlockBean bean : after) assertNotEquals(removed, bean.id);
        assertEquals(2, after.size());
        ArrayList<BlockBean> later = then(after, "counter++;\nflag = true;\ncounter--;");
        assertNotEquals("a new block never takes the id of a block that was deleted", removed, idOf(later, "setVarBoolean", 0));
    }

    @Test
    public void reorderedStatementsKeepTheirIds() {
        ArrayList<BlockBean> before = first("counter++;\nname = \"x\";\ncounter--;");
        ArrayList<BlockBean> after = then(before, "counter--;\ncounter++;\nname = \"x\";");
        assertEquals(idOf(before, "increaseInt", 0), idOf(after, "increaseInt", 0));
        assertEquals(idOf(before, "decreaseInt", 0), idOf(after, "decreaseInt", 0));
        assertEquals(idOf(before, "setVarString", 0), idOf(after, "setVarString", 0));
        assertEquals("decreaseInt", after.get(0).opCode);
    }

    @Test
    public void identicalStatementsKeepDistinctIds() {
        ArrayList<BlockBean> before = first("counter++;\ncounter++;");
        assertNotEquals(idOf(before, "increaseInt", 0), idOf(before, "increaseInt", 1));
        ArrayList<BlockBean> after = then(before, "counter++;\ncounter++;\ncounter++;");
        assertEquals(idOf(before, "increaseInt", 0), idOf(after, "increaseInt", 0));
        assertEquals(idOf(before, "increaseInt", 1), idOf(after, "increaseInt", 1));
        assertTrue(Integer.parseInt(idOf(after, "increaseInt", 2)) > maxId(before));
    }

    @Test
    public void aStatementMovedIntoABlockKeepsItsId() {
        ArrayList<BlockBean> before = first("counter++;\nif (flag) {\n  name = \"x\";\n}");
        ArrayList<BlockBean> after = then(before, "if (flag) {\n  name = \"x\";\n  counter++;\n}");
        assertEquals(idOf(before, "increaseInt", 0), idOf(after, "increaseInt", 0));
        assertEquals(idOf(before, "if", 0), idOf(after, "if", 0));
        assertEquals(idOf(before, "setVarString", 0), idOf(after, "setVarString", 0));
    }

    @Test
    public void aChangeInsideAStackKeepsTheOuterBlockAndTheInnerOne() {
        ArrayList<BlockBean> before = first("while (true) {\n  counter = counter + 1;\n  break;\n}");
        ArrayList<BlockBean> after = then(before, "while (true) {\n  counter = counter + 2;\n  break;\n}");
        assertEquals(idOf(before, "forever", 0), idOf(after, "forever", 0));
        assertEquals(idOf(before, "setVarInt", 0), idOf(after, "setVarInt", 0));
        assertEquals(idOf(before, "+", 0), idOf(after, "+", 0));
        assertEquals(idOf(before, "break", 0), idOf(after, "break", 0));
    }

    @Test
    public void aChangedConditionKeepsTheIfBlock() {
        ArrayList<BlockBean> before = first("if (counter > 5) {\n  counter++;\n}");
        ArrayList<BlockBean> after = then(before, "if (counter > 9) {\n  counter++;\n}");
        assertEquals(idOf(before, "if", 0), idOf(after, "if", 0));
        assertEquals(idOf(before, ">", 0), idOf(after, ">", 0));
        assertEquals(idOf(before, "increaseInt", 0), idOf(after, "increaseInt", 0));
    }

    @Test
    public void aStatementReplacedByAnotherKindGetsANewId() {
        ArrayList<BlockBean> before = first("counter++;");
        ArrayList<BlockBean> after = then(before, "counter--;");
        assertNotEquals(idOf(before, "increaseInt", 0), idOf(after, "decreaseInt", 0));
    }

    @Test
    public void unchangedJavaGivesTheSameBlocks() {
        String java = "counter = counter + 1;\nif (flag) {\n  name = \"x\";\n} else {\n  doIt();\n}";
        ArrayList<BlockBean> before = first(java);
        ArrayList<BlockBean> after = then(before, java);
        assertEquals(BlockJson.hash(before), BlockJson.hash(after));
        assertTrue(ChangeSummary.compare(before, after).isEmpty());
    }

    @Test
    public void blocksLyingLooseInTheEventAreKept() {
        ArrayList<BlockBean> before = first("counter++;");
        BlockBean loose = new BlockBean("50", "Toast %s", " ", "doToast");
        loose.parameters.add("kept");
        before.add(loose);
        BlockBean looseNext = new BlockBean("51", "set %m.varInt to %d", " ", "setVarInt");
        looseNext.parameters.add("counter");
        looseNext.parameters.add("1");
        loose.nextBlock = 51;
        before.add(looseNext);

        ArrayList<BlockBean> after = then(before, "counter--;\nname = \"y\";");
        assertEquals("kept", after.stream().filter(b -> b.id.equals("50")).findFirst().get().parameters.get(0));
        assertTrue(after.stream().anyMatch(b -> b.id.equals("51")));
        for (BlockBean bean : after) {
            if (!bean.id.equals("50") && !bean.id.equals("51")) assertTrue("new ids are above the loose ones: " + bean.id, Integer.parseInt(bean.id) > 51);
        }
        assertEquals("the first block is still the event's first statement", "decreaseInt", after.get(0).opCode);
    }

    @Test
    public void manyEditsNeverDuplicateAnId() {
        String[] steps = {
                "counter++;",
                "counter++;\ncounter++;",
                "if (flag) {\n  counter++;\n}\ncounter++;",
                "counter++;\nif (flag) {\n  counter++;\n} else {\n  counter--;\n}",
                "while (true) {\n  if (flag) {\n    counter++;\n  }\n  break;\n}\ncounter++;",
                "counter++;",
                "",
                "name = \"a\";\nname = \"a\";\nname = \"b\";",
                "name = \"b\";\nname = \"a\";\nname = \"a\";",
                "name = name.concat(\"x\");\nname = name.concat(\"x\");",
        };
        ArrayList<BlockBean> blocks = new ArrayList<>();
        for (String step : steps) {
            blocks = then(blocks, step);
            assertFalse(blocks.isEmpty() && !step.isEmpty());
        }
    }
}
