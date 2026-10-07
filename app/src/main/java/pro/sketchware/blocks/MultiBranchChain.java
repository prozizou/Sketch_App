package pro.sketchware.blocks;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;

/**
 * A ready-made "if / else if / else if / else" chain, offered in the block collection drawer of the Logic
 * editor. Dropping it places four stacked blocks at once, each with its own code slot, so a conditional
 * with many branches takes one drag instead of four. Add or remove "else if" blocks afterwards as needed.
 * <p>
 * The blocks use the same ids as saved collection entries (99xxxxxx), so the editor assigns them real ids
 * when they are dropped.
 */
public final class MultiBranchChain {
    /** Shown on the drawer entry; reserved, a user collection can't take it over. */
    public static final String NAME = "If / else if / else";

    private static final int FIRST_ID = 99000001;
    private static final int COLOR = 0xffe1a92a;
    private static final int ELSE_IF_COUNT = 2;

    private MultiBranchChain() {
    }

    public static boolean isChain(String collectionName) {
        return NAME.equals(collectionName);
    }

    public static ArrayList<BlockBean> create() {
        ArrayList<BlockBean> blocks = new ArrayList<>();
        blocks.add(branch("if", "if %b then", true));
        for (int i = 0; i < ELSE_IF_COUNT; i++) {
            blocks.add(branch("elseIf", "else if %b", true));
        }
        blocks.add(branch("else", "else", false));

        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).id = String.valueOf(FIRST_ID + i);
            blocks.get(i).nextBlock = i + 1 < blocks.size() ? FIRST_ID + i + 1 : -1;
        }
        return blocks;
    }

    private static BlockBean branch(String opCode, String spec, boolean hasCondition) {
        BlockBean block = new BlockBean("", spec, "c", opCode);
        block.color = COLOR;
        if (hasCondition) {
            block.parameters.add("");
        }
        return block;
    }
}
