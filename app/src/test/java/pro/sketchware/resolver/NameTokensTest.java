package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class NameTokensTest {
    @Test
    public void wordsAreSplitOnSeparatorsAndCamelCase() {
        assertEquals(List.of("arrow", "back"), NameTokens.of("ic_arrow_back_black_24dp"));
        assertEquals(List.of("arrow", "back"), NameTokens.of("icArrowBack"));
        assertEquals(List.of("add"), NameTokens.of("icon_add_round"));
        assertEquals("", NameTokens.key("ic_24dp"));
    }

    @Test
    public void theStyleAWordAsksForIsRecognised() {
        assertEquals("round", NameTokens.styleOf("icon_add_round"));
        assertEquals("outline", NameTokens.styleOf("outline_home"));
        assertEquals("outline", NameTokens.styleOf("ic_home_outlined"));
        assertEquals("twotone", NameTokens.styleOf("icon_x_twotone"));
        assertNull(NameTokens.styleOf("ic_add"));
    }

    @Test
    public void distanceCountsEdits() {
        assertEquals(0, NameTokens.distance("add", "add"));
        assertEquals(1, NameTokens.distance("settings", "setting"));
        assertEquals(1, NameTokens.distance("cat", "cut"));
        assertEquals(3, NameTokens.distance("kitten", "sitting"));
        assertEquals(3, NameTokens.distance("", "abc"));
    }

    @Test
    public void similarityRatesLikenessAndRefusesStrangers() {
        assertEquals(0.85, ResourceMatcher.similarity("add", "add"), 0);
        assertEquals(0.75, ResourceMatcher.similarity("setting", "settings"), 0);
        assertEquals("one edit in a short name is too loose", 0.0, ResourceMatcher.similarity("add", "odd"), 0);
        assertEquals(0.6, ResourceMatcher.similarity("add", "add_circle"), 0);
        assertTrue(ResourceMatcher.similarity("home", "cart") == 0);
        assertFalse(ResourceMatcher.similarity("", "x") > 0);
    }
}
