package pro.sketchware.admobads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdMobBlocksTest {
    @Test
    public void namesAreUniqueAndPrefixed() {
        Set<String> names = new HashSet<>();
        for (AdMobBlocks.Block block : AdMobBlocks.all()) {
            assertTrue(block.name, block.name.startsWith("adMob"));
            assertTrue("duplicate " + block.name, names.add(block.name));
        }
    }

    @Test
    public void everyAdFormatHasItsBlocks() {
        Set<String> names = new HashSet<>();
        for (AdMobBlocks.Block block : AdMobBlocks.all()) names.add(block.name);
        for (String expected : new String[]{
                "adMobInitialize", "adMobLoadInterstitial", "adMobShowInterstitial", "adMobLoadRewarded", "adMobShowRewarded",
                "adMobLoadRewardedInterstitial", "adMobShowRewardedInterstitial", "adMobLoadAppOpen", "adMobShowAppOpen",
                "adMobShowAdaptiveBanner", "adMobShowMediumBanner", "adMobShowNative", "adMobShowExitDialog",
                "adMobShowLoading", "adMobHideLoading", "adMobOnAdLoaded", "adMobOnAdFailed", "adMobOnUserRewarded"}) {
            assertTrue(expected, names.contains(expected));
        }
    }

    @Test
    public void containerBlocksHaveABodyPlaceholder() {
        for (AdMobBlocks.Block block : AdMobBlocks.all()) {
            if (block.type.equals("c")) {
                assertTrue(block.name, block.code.contains("%1$s"));
            }
        }
    }

    @Test
    public void placeholdersMatchTheSpecParameters() {
        for (AdMobBlocks.Block block : AdMobBlocks.all()) {
            int specParameters = count(block.spec, "%m.") + count(block.spec, "%s") + count(block.spec, "%d") + count(block.spec, "%b");
            int highest = 0;
            for (int i = 1; i <= 5; i++) {
                if (block.code.contains("%" + i + "$s")) {
                    highest = i;
                }
            }
            int expected = block.type.equals("c") ? specParameters + 1 : specParameters;
            assertEquals(block.name, expected, highest);
        }
    }

    @Test
    public void blocksOnlyUseMenusTheEditorKnows() {
        for (AdMobBlocks.Block block : AdMobBlocks.all()) {
            assertFalse(block.name, block.spec.contains("%m.adMob"));
            for (String menu : menusOf(block.spec)) {
                assertTrue(block.name + " " + menu, menu.equals("activity") || menu.equals("view"));
            }
        }
    }

    @Test
    public void everyCalledHelperMethodExistsInTheGeneratedClass() {
        String source = AdMobHelperSource.generate("com.example", "", "", "");
        for (AdMobBlocks.Block block : AdMobBlocks.all()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("AdMobHelper\\.(\\w+)").matcher(block.code);
            while (matcher.find()) {
                String member = matcher.group(1);
                if (member.equals("Callback")) continue;
                assertTrue(block.name + " -> " + member, source.contains(" " + member + "(") || source.contains(" " + member + ";")
                        || source.contains(" " + member + " ="));
            }
        }
    }

    @Test
    public void addToProducesEditorMaps() {
        List<HashMap<String, Object>> target = new ArrayList<>();
        AdMobBlocks.addTo(target);
        assertEquals(AdMobBlocks.all().size(), target.size());
        HashMap<String, Object> first = target.get(0);
        for (String key : new String[]{"name", "type", "typeName", "code", "color", "palette", "spec"}) {
            assertTrue(key, first.containsKey(key));
        }
    }

    private static List<String> menusOf(String spec) {
        List<String> menus = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%m\\.(\\w+)").matcher(spec);
        while (matcher.find()) menus.add(matcher.group(1));
        return menus;
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) count++;
        return count;
    }
}
