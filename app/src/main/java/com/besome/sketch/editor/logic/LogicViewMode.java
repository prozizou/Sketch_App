package com.besome.sketch.editor.logic;

import androidx.annotation.Nullable;

/**
 * What the Logic editor shows: only the blocks, the blocks over the generated Java (two thirds and one
 * third of the screen), or the Java alone.
 */
public enum LogicViewMode {
    BLOCKS,
    SPLIT,
    CODE;

    public boolean showsBlocks() {
        return this != CODE;
    }

    public boolean showsCode() {
        return this != BLOCKS;
    }

    /** The toolbar action: from the blocks to the split view, and from any view with code back to the blocks. */
    public LogicViewMode toggled() {
        return this == BLOCKS ? SPLIT : BLOCKS;
    }

    /** The button in the code panel: grows the code to the whole area, or gives the blocks their share back. */
    public LogicViewMode maximizeToggled() {
        return switch (this) {
            case SPLIT -> CODE;
            case CODE -> SPLIT;
            case BLOCKS -> BLOCKS;
        };
    }

    /** Layout weight of the blocks area; the code area always has weight 1, so the split is 2 : 1. */
    public float blocksWeight() {
        return this == SPLIT ? 2f : 0f;
    }

    /** What is remembered for next time: the code-only view comes back as the split view. */
    public String storedKey() {
        return (this == CODE ? SPLIT : this).name();
    }

    public static LogicViewMode fromStored(@Nullable String key) {
        if (SPLIT.name().equals(key)) {
            return SPLIT;
        }
        return BLOCKS;
    }
}
