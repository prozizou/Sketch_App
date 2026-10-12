package pro.sketchware.unityads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnityAdsBlocksTest {
    @Test
    public void namesAreUniqueAndPrefixed() {
        Set<String> names = new HashSet<>();
        for (UnityAdsBlocks.Block block : UnityAdsBlocks.all()) {
            assertTrue(block.name, block.name.startsWith("unityAds"));
            assertTrue("duplicate " + block.name, names.add(block.name));
        }
    }

    @Test
    public void containerBlocksHaveABodyPlaceholder() {
        for (UnityAdsBlocks.Block block : UnityAdsBlocks.all()) {
            if (block.type.equals("c")) {
                assertTrue(block.name, block.code.contains("%1$s"));
            }
        }
    }

    @Test
    public void placeholdersMatchTheSpecParameters() {
        for (UnityAdsBlocks.Block block : UnityAdsBlocks.all()) {
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
    public void addToProducesEditorMaps() {
        List<HashMap<String, Object>> target = new ArrayList<>();
        UnityAdsBlocks.addTo(target);
        assertEquals(UnityAdsBlocks.all().size(), target.size());
        HashMap<String, Object> first = target.get(0);
        for (String key : new String[]{"name", "type", "typeName", "code", "color", "palette", "spec"}) {
            assertTrue(key, first.containsKey(key));
        }
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            count++;
        }
        return count;
    }
}
