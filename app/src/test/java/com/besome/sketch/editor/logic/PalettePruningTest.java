package com.besome.sketch.editor.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PalettePruningTest {
    private static final boolean TITLE = true;
    private static final boolean BLOCK = false;

    @Test
    public void aTitleFollowedByBlocksStays() {
        assertTrue(PalettePruning.emptySections(Arrays.asList(TITLE, BLOCK, BLOCK, TITLE, BLOCK)).isEmpty());
    }

    @Test
    public void aTitleFollowedByAnotherTitleIsEmpty() {
        assertEquals(List.of(0), PalettePruning.emptySections(Arrays.asList(TITLE, TITLE, BLOCK)));
    }

    @Test
    public void aTitleAtTheEndIsEmpty() {
        assertEquals(List.of(2), PalettePruning.emptySections(Arrays.asList(TITLE, BLOCK, TITLE)));
    }

    @Test
    public void severalEmptyTitlesInARowAreAllFound() {
        assertEquals(List.of(0, 1, 2), PalettePruning.emptySections(Arrays.asList(TITLE, TITLE, TITLE)));
    }

    @Test
    public void blocksWithoutTitlesAndAnEmptyPaletteAreLeftAlone() {
        assertTrue(PalettePruning.emptySections(Arrays.asList(BLOCK, BLOCK)).isEmpty());
        assertTrue(PalettePruning.emptySections(Collections.emptyList()).isEmpty());
    }
}
