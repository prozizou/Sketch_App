package pro.sketchware.unityads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnityAdsConfigTest {
    @Test
    public void emptyOrBrokenJsonGivesDisabledDefaults() {
        for (String json : new String[]{null, "", "  ", "not json", "[]"}) {
            UnityAdsConfig config = UnityAdsConfig.fromJson(json);
            assertFalse(config.enabled);
            assertFalse(config.isActive());
            assertEquals(UnityAdsConfig.DEFAULT_REWARDED_PLACEMENT, config.rewardedPlacement);
        }
    }

    @Test
    public void roundTripKeepsEveryField() {
        UnityAdsConfig config = new UnityAdsConfig();
        config.enabled = true;
        config.gameId = "1234567";
        config.testMode = false;
        config.bannerPlacement = "MyBanner";
        config.interstitialPlacement = "MyInter";
        config.rewardedPlacement = "MyReward";

        UnityAdsConfig read = UnityAdsConfig.fromJson(config.toJson());

        assertTrue(read.enabled);
        assertEquals("1234567", read.gameId);
        assertFalse(read.testMode);
        assertEquals("MyBanner", read.bannerPlacement);
        assertEquals("MyInter", read.interstitialPlacement);
        assertEquals("MyReward", read.rewardedPlacement);
    }

    @Test
    public void fileMissingFieldsFallBackToDefaults() {
        UnityAdsConfig config = UnityAdsConfig.fromJson("{\"enabled\":true,\"gameId\":\"42\"}");
        assertEquals(UnityAdsConfig.DEFAULT_BANNER_PLACEMENT, config.bannerPlacement);
        assertEquals(UnityAdsConfig.DEFAULT_INTERSTITIAL_PLACEMENT, config.interstitialPlacement);
        assertTrue(config.isActive());
    }

    @Test
    public void enabledWithoutGameIdIsNotActive() {
        UnityAdsConfig config = new UnityAdsConfig();
        config.enabled = true;
        assertFalse(config.isActive());
        assertFalse(config.problems().isEmpty());
    }

    @Test
    public void invalidValuesAreReported() {
        UnityAdsConfig config = new UnityAdsConfig();
        config.enabled = true;
        config.gameId = "12 34\";";
        config.bannerPlacement = "a b";
        assertEquals(2, config.problems().size());
        assertFalse(config.isActive());
    }

    @Test
    public void disabledConfigWithValidValuesIsNotActive() {
        UnityAdsConfig config = new UnityAdsConfig();
        config.gameId = "1234567";
        assertTrue(config.problems().isEmpty());
        assertFalse(config.isActive());
    }
}
