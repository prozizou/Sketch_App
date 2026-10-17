package com.besome.sketch.editor.logic;

import java.util.ArrayList;
import java.util.List;

/** Which section titles of a palette have nothing under them, so "Hide unrelated blocks" can drop them. */
public final class PalettePruning {
    private PalettePruning() {
    }

    /**
     * @param isSectionTitle one entry per row of the palette, in order: true for a section title, false for a block
     * @return the indices of the titles that are followed by another title or by the end of the palette
     */
    public static List<Integer> emptySections(List<Boolean> isSectionTitle) {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < isSectionTitle.size(); i++) {
            if (!isSectionTitle.get(i)) {
                continue;
            }
            boolean last = i == isSectionTitle.size() - 1;
            if (last || isSectionTitle.get(i + 1)) {
                empty.add(i);
            }
        }
        return empty;
    }
}
