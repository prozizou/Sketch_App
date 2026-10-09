package pro.sketchware.editor.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpacingScaleTest {
    @Test
    public void knowsTheSteps() {
        assertTrue(SpacingScale.isOnScale(16));
        assertFalse(SpacingScale.isOnScale(15));
    }

    @Test
    public void nearestPrefersTheSmallerStepOnATie() {
        assertEquals(16, SpacingScale.nearest(15));
        assertEquals(8, SpacingScale.nearest(10));   // 8 and 12 are equally near
        assertEquals(0, SpacingScale.nearest(-3));
        assertEquals(72, SpacingScale.nearest(70));
    }

    @Test
    public void snapOnlyWithinTheThreshold() {
        assertEquals(16, SpacingScale.snap(15, 1));
        assertEquals(14, SpacingScale.snap(14, 1));
    }
}
