package pro.sketchware.admobads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The logic-editor blocks for AdMob. Every block calls a static method of the generated {@code AdMobHelper} class
 * (see {@link AdMobHelperSource}); nothing else has to be added to the project.
 */
public final class AdMobBlocks {
    public static final String PALETTE_TITLE = "AdMob";
    private static final String COLOR = "#e65100";

    /** Block definition: the fields the logic editor reads from a block map. */
    public static final class Block {
        public final String name;
        public final String type;
        public final String code;
        public final String spec;

        Block(String name, String type, String code, String spec) {
            this.name = name;
            this.type = type;
            this.code = code;
            this.spec = spec;
        }
    }

    private static final List<Block> BLOCKS = new ArrayList<>();

    static {
        BLOCKS.add(new Block("adMobInitialize", " ", "AdMobHelper.initialize(%1$s.this);", "AdMob initialize in %m.activity"));

        // Ad units: Google's test ones always fill; the library ones are those saved in the AdMob settings
        BLOCKS.add(new Block("adMobTestBannerId", "s", "AdMobHelper.TEST_BANNER", "AdMob test banner ad unit"));
        BLOCKS.add(new Block("adMobTestAdaptiveBannerId", "s", "AdMobHelper.TEST_ADAPTIVE_BANNER", "AdMob test adaptive banner ad unit"));
        BLOCKS.add(new Block("adMobTestInterstitialId", "s", "AdMobHelper.TEST_INTERSTITIAL", "AdMob test interstitial ad unit"));
        BLOCKS.add(new Block("adMobTestRewardedId", "s", "AdMobHelper.TEST_REWARDED", "AdMob test rewarded ad unit"));
        BLOCKS.add(new Block("adMobTestRewardedInterstitialId", "s", "AdMobHelper.TEST_REWARDED_INTERSTITIAL", "AdMob test rewarded interstitial ad unit"));
        BLOCKS.add(new Block("adMobTestAppOpenId", "s", "AdMobHelper.TEST_APP_OPEN", "AdMob test app open ad unit"));
        BLOCKS.add(new Block("adMobTestNativeId", "s", "AdMobHelper.TEST_NATIVE", "AdMob test native ad unit"));
        BLOCKS.add(new Block("adMobTestNativeVideoId", "s", "AdMobHelper.TEST_NATIVE_VIDEO", "AdMob test native video ad unit"));
        BLOCKS.add(new Block("adMobLibraryBannerId", "s", "AdMobHelper.LIB_BANNER", "AdMob banner ad unit from settings"));
        BLOCKS.add(new Block("adMobLibraryInterstitialId", "s", "AdMobHelper.LIB_INTERSTITIAL", "AdMob interstitial ad unit from settings"));
        BLOCKS.add(new Block("adMobLibraryRewardedId", "s", "AdMobHelper.LIB_REWARDED", "AdMob rewarded ad unit from settings"));

        BLOCKS.add(new Block("adMobLoadInterstitial", " ", "AdMobHelper.loadInterstitial(%1$s.this, %2$s);", "AdMob load interstitial in %m.activity ad unit %s"));
        BLOCKS.add(new Block("adMobShowInterstitial", " ", "AdMobHelper.showInterstitial(%1$s.this);", "AdMob show interstitial in %m.activity"));
        BLOCKS.add(new Block("adMobIsInterstitialLoaded", "b", "AdMobHelper.isInterstitialLoaded()", "AdMob interstitial is loaded"));

        BLOCKS.add(new Block("adMobLoadRewarded", " ", "AdMobHelper.loadRewarded(%1$s.this, %2$s);", "AdMob load rewarded in %m.activity ad unit %s"));
        BLOCKS.add(new Block("adMobShowRewarded", " ", "AdMobHelper.showRewarded(%1$s.this);", "AdMob show rewarded in %m.activity"));
        BLOCKS.add(new Block("adMobIsRewardedLoaded", "b", "AdMobHelper.isRewardedLoaded()", "AdMob rewarded is loaded"));

        BLOCKS.add(new Block("adMobLoadRewardedInterstitial", " ", "AdMobHelper.loadRewardedInterstitial(%1$s.this, %2$s);", "AdMob load rewarded interstitial in %m.activity ad unit %s"));
        BLOCKS.add(new Block("adMobShowRewardedInterstitial", " ", "AdMobHelper.showRewardedInterstitial(%1$s.this, %2$s);", "AdMob show rewarded interstitial in %m.activity can be closed early %b"));
        BLOCKS.add(new Block("adMobIsRewardedInterstitialLoaded", "b", "AdMobHelper.isRewardedInterstitialLoaded()", "AdMob rewarded interstitial is loaded"));

        BLOCKS.add(new Block("adMobLoadAppOpen", " ", "AdMobHelper.loadAppOpen(%1$s.this, %2$s);", "AdMob load app open ad in %m.activity ad unit %s"));
        BLOCKS.add(new Block("adMobShowAppOpen", " ", "AdMobHelper.showAppOpen(%1$s.this);", "AdMob show app open ad in %m.activity"));
        BLOCKS.add(new Block("adMobIsAppOpenLoaded", "b", "AdMobHelper.isAppOpenLoaded()", "AdMob app open ad is loaded"));

        BLOCKS.add(new Block("adMobShowAdaptiveBanner", " ", "AdMobHelper.showAdaptiveBanner(%1$s.this, %2$s, %3$s);", "AdMob adaptive banner in %m.activity ad unit %s inside %m.view"));
        BLOCKS.add(new Block("adMobShowMediumBanner", " ", "AdMobHelper.showMediumBanner(%1$s.this, %2$s, %3$s);", "AdMob medium banner in %m.activity ad unit %s inside %m.view"));
        BLOCKS.add(new Block("adMobShowNative", " ", "AdMobHelper.showNative(%1$s.this, %2$s, %3$s, %4$s);", "AdMob native ad in %m.activity ad unit %s inside %m.view with video %b"));

        BLOCKS.add(new Block("adMobShowExitDialog", " ", "AdMobHelper.showExitDialog(%1$s.this, %2$s);", "AdMob exit dialog with ad in %m.activity ad unit %s"));
        BLOCKS.add(new Block("adMobShowLoading", " ", "AdMobHelper.showLoading(%1$s.this, %2$s);", "AdMob loading dialog with ad in %m.activity ad unit %s"));
        BLOCKS.add(new Block("adMobHideLoading", " ", "AdMobHelper.hideLoading();", "AdMob close loading dialog"));

        BLOCKS.add(new Block("adMobRewardAmount", "d", "AdMobHelper.rewardAmount()", "AdMob last reward amount"));
        BLOCKS.add(new Block("adMobRewardType", "s", "AdMobHelper.rewardType()", "AdMob last reward type"));

        BLOCKS.add(listener("adMobOnAdLoaded", "onAdLoaded", "_adType", "AdMob when ad loaded -> _adType"));
        BLOCKS.add(listener("adMobOnAdFailed", "onAdFailed", "_message", "AdMob when ad failed -> _message"));
        BLOCKS.add(listener("adMobOnAdShown", "onAdShown", "_adType", "AdMob when ad shown -> _adType"));
        BLOCKS.add(listener("adMobOnAdClosed", "onAdClosed", "_adType", "AdMob when ad closed -> _adType"));
        BLOCKS.add(listener("adMobOnUserRewarded", "onUserRewarded", "_adType", "AdMob when user earned reward -> _adType"));
    }

    private AdMobBlocks() {
    }

    /** A "c" (container) block that sets one of the helper's static callbacks to run its body. */
    private static Block listener(String name, String field, String parameter, String spec) {
        String code = "AdMobHelper." + field + " = new AdMobHelper.Callback() {\r\n"
                + "    @Override\r\n"
                + "    public void onEvent(String " + parameter + ") {\r\n"
                + "        %1$s\r\n"
                + "    }\r\n"
                + "};";
        return new Block(name, "c", code, spec);
    }

    public static List<Block> all() {
        return new ArrayList<>(BLOCKS);
    }

    /** Adds the blocks to {@code target} in the shape the logic editor expects. */
    public static void addTo(List<HashMap<String, Object>> target) {
        for (Block block : BLOCKS) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("name", block.name);
            map.put("type", block.type);
            map.put("typeName", "");
            map.put("code", block.code);
            map.put("color", COLOR);
            map.put("palette", "-1");
            map.put("spec", block.spec);
            target.add(map);
        }
    }
}
