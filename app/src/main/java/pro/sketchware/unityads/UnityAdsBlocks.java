package pro.sketchware.unityads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The logic-editor blocks for Unity Ads. Every block calls a static method of the generated
 * {@code UnityAdsHelper} class (see {@link UnityAdsHelperSource}).
 */
public final class UnityAdsBlocks {
    public static final String PALETTE_TITLE = "Unity Ads";
    private static final String COLOR = "#546e7a";

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
        BLOCKS.add(new Block("unityAdsInitialize", " ", "UnityAdsHelper.initialize(%1$s.this);", "Unity Ads initialize in %m.activity"));
        BLOCKS.add(new Block("unityAdsIsInitialized", "b", "UnityAdsHelper.isInitialized()", "Unity Ads is initialized"));

        BLOCKS.add(new Block("unityAdsLoadInterstitial", " ", "UnityAdsHelper.loadInterstitial();", "Unity Ads load interstitial"));
        BLOCKS.add(new Block("unityAdsShowInterstitial", " ", "UnityAdsHelper.showInterstitial(%1$s.this);", "Unity Ads show interstitial in %m.activity"));
        BLOCKS.add(new Block("unityAdsIsInterstitialLoaded", "b", "UnityAdsHelper.isInterstitialLoaded()", "Unity Ads interstitial is loaded"));

        BLOCKS.add(new Block("unityAdsLoadRewarded", " ", "UnityAdsHelper.loadRewarded();", "Unity Ads load rewarded"));
        BLOCKS.add(new Block("unityAdsShowRewarded", " ", "UnityAdsHelper.showRewarded(%1$s.this);", "Unity Ads show rewarded in %m.activity"));
        BLOCKS.add(new Block("unityAdsIsRewardedLoaded", "b", "UnityAdsHelper.isRewardedLoaded()", "Unity Ads rewarded is loaded"));

        BLOCKS.add(new Block("unityAdsShowBanner", " ", "UnityAdsHelper.showBanner(%1$s.this, %2$s);", "Unity Ads show banner in %m.activity inside %m.view"));
        BLOCKS.add(new Block("unityAdsHideBanner", " ", "UnityAdsHelper.hideBanner();", "Unity Ads hide banner"));

        BLOCKS.add(listener("unityAdsOnInitComplete", "onInitComplete", "_unused", "Unity Ads when initialized"));
        BLOCKS.add(listener("unityAdsOnInitFailed", "onInitFailed", "_message", "Unity Ads when initialization failed -> _message"));
        BLOCKS.add(listener("unityAdsOnAdLoaded", "onAdLoaded", "_placement", "Unity Ads when ad loaded -> _placement"));
        BLOCKS.add(listener("unityAdsOnAdLoadFailed", "onAdLoadFailed", "_message", "Unity Ads when ad failed to load -> _message"));
        BLOCKS.add(listener("unityAdsOnAdShowFailed", "onAdShowFailed", "_message", "Unity Ads when ad failed to show -> _message"));
        BLOCKS.add(listener("unityAdsOnAdClosed", "onAdClosed", "_placement", "Unity Ads when ad closed -> _placement"));
        BLOCKS.add(listener("unityAdsOnUserRewarded", "onUserRewarded", "_placement", "Unity Ads when user earned reward -> _placement"));
    }

    private UnityAdsBlocks() {
    }

    /** A "c" (container) block that sets one of the helper's static callbacks to run its body. */
    private static Block listener(String name, String field, String parameter, String spec) {
        String code = "UnityAdsHelper." + field + " = new UnityAdsHelper.Callback() {\r\n"
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
