package com.besome.sketch.editor.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SidebarWidthTest {
    @Test
    public void widthStaysBetweenTheLimits() {
        assertEquals(SidebarWidth.MIN_DP, SidebarWidth.clampDp(10f), 0f);
        assertEquals(SidebarWidth.MAX_DP, SidebarWidth.clampDp(500f), 0f);
        assertEquals(100f, SidebarWidth.clampDp(100f), 0f);
    }

    @Test
    public void defaultIsNarrowerThanTheOldFixedWidth() {
        assertTrue(SidebarWidth.DEFAULT_DP < 88f);
        assertEquals(SidebarWidth.DEFAULT_DP, SidebarWidth.clampDp(SidebarWidth.DEFAULT_DP), 0f);
    }

    @Test
    public void narrowSidebarDropsTheLabelSoNothingIsCutOff() {
        assertFalse(SidebarWidth.showsLabel(SidebarWidth.DEFAULT_DP));
        assertTrue(SidebarWidth.showsLabel(100f));
    }
}
