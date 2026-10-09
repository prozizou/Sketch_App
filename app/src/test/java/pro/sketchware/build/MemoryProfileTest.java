package pro.sketchware.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pro.sketchware.build.MemoryProfile.Profile;
import pro.sketchware.build.MemoryProfile.Tier;

public class MemoryProfileTest {
    @Test
    public void anAndroid9PhoneWithTwoGigabytesGetsTheSmallestBuild() {
        // 2 GB phones report about 1.8 GB, 8 cores, and a 256 MB heap even with largeHeap
        Profile profile = MemoryProfile.choose(1800, false, 256, 8, false);
        assertEquals(Tier.LOW, profile.tier());
        assertEquals(1, profile.compilerThreads());
        assertTrue(profile.collectGarbageBetweenSteps());
    }

    @Test
    public void lowRamFlagSmallHeapAndTheUsersChoiceEachForceTheSmallBuild() {
        assertEquals(Tier.LOW, MemoryProfile.choose(8000, true, 512, 8, false).tier());
        assertEquals(Tier.LOW, MemoryProfile.choose(8000, false, 200, 8, false).tier());
        assertEquals(Tier.LOW, MemoryProfile.choose(8000, false, 512, 8, true).tier());
    }

    @Test
    public void threeGigabytesIsStillLowButFourIsNormal() {
        assertEquals(Tier.LOW, MemoryProfile.choose(3072, false, 512, 8, false).tier());
        Profile normal = MemoryProfile.choose(3900, false, 512, 8, false);
        assertEquals(Tier.NORMAL, normal.tier());
        assertEquals(2, normal.compilerThreads());
        assertFalse(normal.collectGarbageBetweenSteps());
    }

    @Test
    public void bigPhonesMayUseMoreThreadsUpToFour() {
        Profile high = MemoryProfile.choose(12000, false, 512, 8, false);
        assertEquals(Tier.HIGH, high.tier());
        assertEquals(4, high.compilerThreads());
        assertEquals(Tier.NORMAL, MemoryProfile.choose(12000, false, 512, 4, false).tier());
    }

    @Test
    public void threadsNeverExceedTheCoresAndUnknownValuesDoNotForceTheSmallBuild() {
        assertEquals(1, MemoryProfile.choose(4000, false, 512, 1, false).compilerThreads());
        assertEquals(1, MemoryProfile.choose(4000, false, 512, 0, false).compilerThreads());
        assertEquals(Tier.NORMAL, MemoryProfile.choose(0, false, 0, 4, false).tier());
    }
}
