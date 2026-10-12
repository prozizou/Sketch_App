package pro.sketchware.unityads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnityAdsHelperSourceTest {
    private static UnityAdsConfig config() {
        UnityAdsConfig config = new UnityAdsConfig();
        config.enabled = true;
        config.gameId = "1234567";
        config.testMode = true;
        return config;
    }

    @Test
    public void usesPackageAndConfigValues() {
        String source = UnityAdsHelperSource.generate("com.my.app", config());
        assertTrue(source.startsWith("package com.my.app;"));
        assertTrue(source.contains("GAME_ID = \"1234567\""));
        assertTrue(source.contains("TEST_MODE = true"));
        assertTrue(source.contains("BANNER_PLACEMENT = \"Banner_Android\""));
        assertTrue(source.contains("INTERSTITIAL_PLACEMENT = \"Interstitial_Android\""));
        assertTrue(source.contains("REWARDED_PLACEMENT = \"Rewarded_Android\""));
        assertFalse("every marker must be replaced", source.contains("%"));
    }

    @Test
    public void autoBannerFollowsTheSetting() {
        UnityAdsConfig config = config();
        assertTrue(UnityAdsHelperSource.generate("a.b", config).contains("AUTO_BANNER = true"));
        config.autoBanner = false;
        assertTrue(UnityAdsHelperSource.generate("a.b", config).contains("AUTO_BANNER = false"));
    }

    @Test
    public void declaresTheProviderTheManifestPointsTo() {
        String source = UnityAdsHelperSource.generate("com.my.app", config());
        assertTrue(source.contains("public static class InitProvider extends ContentProvider"));
        assertEquals("com.my.app.UnityAdsHelper$InitProvider", UnityAdsManifest.initProviderClass("com.my.app"));
    }

    @Test
    public void recordsStartupCrashesAndRecoversInSafeMode() {
        String source = UnityAdsHelperSource.generate("a.b", config());
        assertTrue(source.contains("installCrashRecorder(context)"));
        assertTrue(source.contains("recoverFromCrash(context)"));
        assertTrue("the crash recorder must only run in test mode", source.contains("if (TEST_MODE) {\n                        installCrashRecorder"));
        assertTrue("the previous crash handler must still be called", source.contains("previous.uncaughtException(thread, throwable)"));
    }

    @Test
    public void runsTheDeferredInitializersTheManifestHandsOver() {
        String source = UnityAdsHelperSource.generate("a.b", config());
        assertTrue(source.contains("startDeferredInitializers(context)"));
        assertTrue("must look for the value the manifest merge writes",
                source.contains("\"" + UnityAdsManifest.DEFERRED_VALUE + "\".equals(metaData.getString(name))"));
        assertTrue(source.contains("androidx.startup.AppInitializer"));
    }

    @Test
    public void testModeOffIsWrittenAsFalse() {
        UnityAdsConfig config = config();
        config.testMode = false;
        assertTrue(UnityAdsHelperSource.generate("a.b", config).contains("TEST_MODE = false"));
    }

    @Test
    public void escapesQuotesAndBackslashes() {
        assertEquals("a\\\"b\\\\c", UnityAdsHelperSource.escape("a\"b\\c"));
        assertEquals("ab", UnityAdsHelperSource.escape("a\nb"));
    }

    @Test
    public void staysWithinJava7() {
        String source = UnityAdsHelperSource.generate("a.b", config());
        assertFalse("lambdas are not allowed", source.contains("->"));
        assertFalse(source.contains("::"));
    }

    @Test
    public void everyHelperMemberUsedByTheBlocksExists() {
        String source = UnityAdsHelperSource.generate("a.b", config());
        java.util.regex.Pattern call = java.util.regex.Pattern.compile("UnityAdsHelper\\.(\\w+)");
        for (UnityAdsBlocks.Block block : UnityAdsBlocks.all()) {
            java.util.regex.Matcher matcher = call.matcher(block.code);
            assertTrue(block.name + " should call the helper", matcher.find());
            String member = matcher.group(1);
            assertTrue(block.name + " uses missing member " + member,
                    source.contains(" " + member + "(") || source.contains(" " + member + ";") || source.contains(" " + member + " ="));
        }
    }
}
