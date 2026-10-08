package com.besome.sketch.editor.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LogicViewModeTest {
    @Test
    public void toolbarSwitchesBetweenBlocksAndSplit() {
        assertEquals(LogicViewMode.SPLIT, LogicViewMode.BLOCKS.toggled());
        assertEquals(LogicViewMode.BLOCKS, LogicViewMode.SPLIT.toggled());
        assertEquals(LogicViewMode.BLOCKS, LogicViewMode.CODE.toggled());
    }

    @Test
    public void panelButtonEnlargesAndShrinksTheCode() {
        assertEquals(LogicViewMode.CODE, LogicViewMode.SPLIT.maximizeToggled());
        assertEquals(LogicViewMode.SPLIT, LogicViewMode.CODE.maximizeToggled());
        assertEquals(LogicViewMode.BLOCKS, LogicViewMode.BLOCKS.maximizeToggled());
    }

    @Test
    public void splitShowsBothAndCodeOnlyTheCode() {
        assertTrue(LogicViewMode.SPLIT.showsBlocks() && LogicViewMode.SPLIT.showsCode());
        assertFalse(LogicViewMode.CODE.showsBlocks());
        assertFalse(LogicViewMode.BLOCKS.showsCode());
        assertEquals(2f, LogicViewMode.SPLIT.blocksWeight(), 0f);
    }

    @Test
    public void rememberedModeNeverReopensOnTheCodeAlone() {
        assertEquals(LogicViewMode.SPLIT, LogicViewMode.fromStored(LogicViewMode.CODE.storedKey()));
        assertEquals(LogicViewMode.SPLIT, LogicViewMode.fromStored(LogicViewMode.SPLIT.storedKey()));
        assertEquals(LogicViewMode.BLOCKS, LogicViewMode.fromStored(null));
        assertEquals(LogicViewMode.BLOCKS, LogicViewMode.fromStored("garbage"));
    }
}
