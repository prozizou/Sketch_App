package pro.sketchware.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashSet;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class MultiBranchChainTest {
    @Test
    public void chainIsIfThenElseIfsThenElse() {
        ArrayList<String> ops = new ArrayList<>();
        for (BlockBean block : MultiBranchChain.create()) {
            ops.add(block.opCode);
        }
        assertEquals(java.util.List.of("if", "elseIf", "elseIf", "else"), ops);
    }

    @Test
    public void blocksAreLinkedInOrderAndEmpty() {
        ArrayList<BlockBean> blocks = MultiBranchChain.create();
        for (int i = 0; i < blocks.size(); i++) {
            BlockBean block = blocks.get(i);
            int expectedNext = i + 1 < blocks.size() ? Integer.parseInt(blocks.get(i + 1).id) : -1;
            assertEquals(expectedNext, block.nextBlock);
            assertEquals(-1, block.subStack1);
            assertEquals(-1, block.subStack2);
            assertEquals("c", block.type);
        }
    }

    @Test
    public void idsAreUniqueAndInTheCollectionRange() {
        HashSet<String> ids = new HashSet<>();
        for (BlockBean block : MultiBranchChain.create()) {
            assertTrue(Integer.parseInt(block.id) >= 99000000);
            assertTrue("duplicate id " + block.id, ids.add(block.id));
        }
    }

    @Test
    public void conditionSlotsMatchTheSpecs() {
        for (BlockBean block : MultiBranchChain.create()) {
            int slots = block.spec.split("%b", -1).length - 1;
            assertEquals(block.opCode, slots, block.parameters.size());
        }
    }

    @Test
    public void everyCallGivesFreshBlocksAndTheNameIsReserved() {
        assertFalse(MultiBranchChain.create().get(0) == MultiBranchChain.create().get(0));
        assertTrue(MultiBranchChain.isChain(MultiBranchChain.NAME));
        assertFalse(MultiBranchChain.isChain("My favorite"));
    }
}
