package pro.sketchware.admobads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdMobHelperSourceTest {
    @Test
    public void everyPlaceholderIsReplaced() {
        String source = AdMobHelperSource.generate("com.my.app", "", "", "");
        assertFalse(source, source.matches("(?s).*%[A-Z_]+%.*"));
        assertTrue(source.startsWith("package com.my.app;"));
        assertTrue(source.contains("public final class AdMobHelper"));
    }

    @Test
    public void emptyLibraryAdUnitsBecomeGoogleTestAdUnits() {
        String source = AdMobHelperSource.generate("com.my.app", "", " ", null);
        assertTrue(source.contains("LIB_BANNER = \"" + AdMobHelperSource.TEST_BANNER + "\""));
        assertTrue(source.contains("LIB_INTERSTITIAL = \"" + AdMobHelperSource.TEST_INTERSTITIAL + "\""));
        assertTrue(source.contains("LIB_REWARDED = \"" + AdMobHelperSource.TEST_REWARDED + "\""));
    }

    @Test
    public void savedLibraryAdUnitsAreUsed() {
        String source = AdMobHelperSource.generate("com.my.app", "ca-app-pub-1/11", "ca-app-pub-1/22", "ca-app-pub-1/33");
        assertTrue(source.contains("LIB_BANNER = \"ca-app-pub-1/11\""));
        assertTrue(source.contains("LIB_INTERSTITIAL = \"ca-app-pub-1/22\""));
        assertTrue(source.contains("LIB_REWARDED = \"ca-app-pub-1/33\""));
    }

    @Test
    public void anAdUnitCannotBreakOutOfItsStringLiteral() {
        String source = AdMobHelperSource.generate("com.my.app", "a\"; evil(); \"", "", "");
        assertTrue(source.contains("LIB_BANNER = \"a\\\"; evil(); \\\"\""));
    }

    @Test
    public void theSourceStaysCompatibleWithTheBuildersJavaLevel() {
        String source = AdMobHelperSource.generate("com.my.app", "", "", "");
        assertFalse("lambda", source.contains("->"));
        assertFalse("method reference", source.contains("::"));
        assertFalse("var", source.contains(" var "));
        assertFalse("text block", source.contains("\"\"\""));
    }

    @Test
    public void testAdUnitsAreGooglesPublicOnes() {
        for (String id : new String[]{AdMobHelperSource.TEST_BANNER, AdMobHelperSource.TEST_ADAPTIVE_BANNER,
                AdMobHelperSource.TEST_INTERSTITIAL, AdMobHelperSource.TEST_REWARDED,
                AdMobHelperSource.TEST_REWARDED_INTERSTITIAL, AdMobHelperSource.TEST_APP_OPEN,
                AdMobHelperSource.TEST_NATIVE, AdMobHelperSource.TEST_NATIVE_VIDEO}) {
            assertTrue(id, id.startsWith("ca-app-pub-3940256099942544/"));
        }
    }

    @Test
    public void escapeDropsLineBreaksAndQuotesBackslashes() {
        assertEquals("a\\\\b\\\"c", AdMobHelperSource.escape("a\\b\"c\n\r"));
        assertEquals("", AdMobHelperSource.escape(null));
    }
}
