package mod.hey.studios.project.proguard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OptimizationModeTest {
    @Test
    public void onlyTheOffLevelSkipsTheShrinker() {
        assertFalse(OptimizationMode.OFF.isShrinking());
        assertTrue(OptimizationMode.SAFE.isShrinking());
        assertTrue(OptimizationMode.MAX.isShrinking());
    }

    @Test
    public void theSafeLevelKeepsEveryNameAndTheFullLevelAddsNothing() {
        assertTrue(OptimizationMode.SAFE.extraRules().contains("-dontobfuscate"));
        assertEquals("", OptimizationMode.MAX.extraRules());
        assertEquals("", OptimizationMode.OFF.extraRules());
    }

    @Test
    public void aStoredLevelIsReadBack() {
        for (OptimizationMode mode : OptimizationMode.values()) {
            assertEquals(mode, OptimizationMode.fromConfig(mode.key(), mode.isShrinking()));
        }
    }

    @Test
    public void configsFromBeforeTheLevelsKeepTheirMeaning() {
        // The old "enabled" switch was the full shrink-and-scramble.
        assertEquals(OptimizationMode.MAX, OptimizationMode.fromConfig(null, true));
        assertEquals(OptimizationMode.OFF, OptimizationMode.fromConfig(null, false));
    }

    @Test
    public void anUnknownStoredLevelFallsBackToTheOldSwitch() {
        assertEquals(OptimizationMode.MAX, OptimizationMode.fromConfig("turbo", true));
        assertEquals(OptimizationMode.OFF, OptimizationMode.fromConfig("turbo", false));
    }
}
