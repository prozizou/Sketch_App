package com.besome.sketch.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import com.besome.sketch.editor.CopyPlacement.Box;
import com.besome.sketch.editor.CopyPlacement.Spot;

/** Copies made with Duplicate go to a free part of the visible canvas instead of on top of the code. */
public class CopyPlacementTest {
    private static final Box SCREEN = new Box(0, 0, 1000, 2000);

    private static boolean overlapsAny(Spot spot, float width, float height, List<Box> blocks) {
        for (Box b : blocks) {
            if (spot.x() < b.right() && b.left() < spot.x() + width && spot.y() < b.bottom() && b.top() < spot.y() + height) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void emptyCanvasUsesTheTopLeftCorner() {
        Spot spot = CopyPlacement.find(List.of(), SCREEN, 300, 100, 10, 20);
        assertEquals(10, spot.x(), 0);
        assertEquals(10, spot.y(), 0);
        assertTrue(spot.visible());
    }

    @Test
    public void copyNeverCoversExistingBlocks() {
        // The event of the video: a tall column of blocks on the left and a few to the right at the top.
        List<Box> blocks = List.of(new Box(0, 0, 600, 900), new Box(620, 0, 900, 200));
        Spot spot = CopyPlacement.find(blocks, SCREEN, 300, 120, 10, 20);
        assertTrue(spot.visible());
        assertFalse(overlapsAny(spot, 300, 120, blocks));
    }

    @Test
    public void successiveCopiesDoNotPileUp() {
        List<Box> blocks = new ArrayList<>(List.of(new Box(0, 0, 500, 400)));
        for (int i = 0; i < 6; i++) {
            Spot spot = CopyPlacement.find(blocks, SCREEN, 300, 120, 10, 20);
            assertFalse("copy " + i + " overlaps", overlapsAny(spot, 300, 120, blocks));
            blocks.add(new Box(spot.x(), spot.y(), spot.x() + 300, spot.y() + 120));
        }
    }

    @Test
    public void staysInsideTheVisibleArea() {
        Box visible = new Box(200, 3000, 1200, 4500); // scrolled down, palette cut off the bottom
        Spot spot = CopyPlacement.find(List.of(new Box(200, 3000, 1200, 3300)), visible, 300, 120, 10, 20);
        assertTrue(spot.visible());
        assertTrue(spot.x() >= 200 && spot.x() + 300 <= 1200);
        assertTrue(spot.y() >= 3300 && spot.y() + 120 <= 4500);
    }

    @Test
    public void fullScreenSendsTheCopyBelowTheCode() {
        List<Box> blocks = List.of(new Box(0, 0, 1000, 2600));
        Spot spot = CopyPlacement.find(blocks, SCREEN, 300, 120, 10, 20);
        assertFalse(spot.visible());
        assertEquals(2610, spot.y(), 0);
        assertFalse(overlapsAny(spot, 300, 120, blocks));
    }

    @Test
    public void copyWiderThanTheScreenStillGetsASpot() {
        Spot spot = CopyPlacement.find(List.of(new Box(0, 0, 1000, 300)), SCREEN, 1500, 100, 10, 20);
        assertTrue(spot.visible());
        assertEquals(10, spot.x(), 0);
        assertTrue(spot.y() >= 310);
    }
}
