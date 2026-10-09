package pro.sketchware.flags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class FeatureFlagTest {
    @Test
    public void keysAreUniqueAndFollowTheNamingConvention() {
        Set<String> keys = new HashSet<>();
        for (FeatureFlag flag : FeatureFlag.values()) {
            assertTrue(flag.key(), flag.key().startsWith("ff-"));
            assertTrue("duplicate key " + flag.key(), keys.add(flag.key()));
        }
        assertEquals(FeatureFlag.values().length, keys.size());
    }
}
