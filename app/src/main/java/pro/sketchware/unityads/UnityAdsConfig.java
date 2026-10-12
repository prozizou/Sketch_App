package pro.sketchware.unityads;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Per-project Unity Ads settings. Pure Java (no Android types) so it can be unit-tested on a plain JVM.
 */
public class UnityAdsConfig {
    public static final String DEFAULT_BANNER_PLACEMENT = "Banner_Android";
    public static final String DEFAULT_INTERSTITIAL_PLACEMENT = "Interstitial_Android";
    public static final String DEFAULT_REWARDED_PLACEMENT = "Rewarded_Android";

    /** Maven coordinates of the SDK, used by the download button. */
    public static final String SDK_GROUP = "com.unity3d.ads";
    public static final String SDK_ARTIFACT = "unity-ads";
    /** Folder name prefix a downloaded Unity Ads local library has ("unity-ads-4.x.y"). */
    public static final String SDK_LOCAL_LIBRARY_PREFIX = "unity-ads";

    private static final Pattern GAME_ID = Pattern.compile("[0-9A-Za-z]{1,32}");
    private static final Pattern PLACEMENT = Pattern.compile("[0-9A-Za-z_.\\-]{1,64}");

    public boolean enabled;
    public String gameId = "";
    public boolean testMode = true;
    /** Shows a banner at the bottom of every screen without any block. */
    public boolean autoBanner = true;
    public String bannerPlacement = DEFAULT_BANNER_PLACEMENT;
    public String interstitialPlacement = DEFAULT_INTERSTITIAL_PLACEMENT;
    public String rewardedPlacement = DEFAULT_REWARDED_PLACEMENT;

    public static UnityAdsConfig fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new UnityAdsConfig();
        }
        try {
            UnityAdsConfig config = new Gson().fromJson(json, UnityAdsConfig.class);
            return config == null ? new UnityAdsConfig() : config.normalized();
        } catch (RuntimeException e) {
            return new UnityAdsConfig();
        }
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    /** Replaces missing (null) strings, which Gson leaves when a field is absent from an older file. */
    private UnityAdsConfig normalized() {
        gameId = gameId == null ? "" : gameId.trim();
        bannerPlacement = orDefault(bannerPlacement, DEFAULT_BANNER_PLACEMENT);
        interstitialPlacement = orDefault(interstitialPlacement, DEFAULT_INTERSTITIAL_PLACEMENT);
        rewardedPlacement = orDefault(rewardedPlacement, DEFAULT_REWARDED_PLACEMENT);
        return this;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public static boolean isValidGameId(String gameId) {
        return gameId != null && GAME_ID.matcher(gameId).matches();
    }

    public static boolean isValidPlacement(String placement) {
        return placement != null && PLACEMENT.matcher(placement).matches();
    }

    /** @return what is wrong with this config, empty when it can be used to generate code. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (!isValidGameId(gameId)) {
            problems.add("Game ID must be 1-32 letters or digits");
        }
        if (!isValidPlacement(bannerPlacement)) {
            problems.add("Banner placement ID is not valid");
        }
        if (!isValidPlacement(interstitialPlacement)) {
            problems.add("Interstitial placement ID is not valid");
        }
        if (!isValidPlacement(rewardedPlacement)) {
            problems.add("Rewarded placement ID is not valid");
        }
        return problems;
    }

    /** True when code should be generated for this project: switched on and with valid values. */
    public boolean isActive() {
        return enabled && problems().isEmpty();
    }
}
