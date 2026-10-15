package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

public class ResourceMatcherTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static MissingResource missing(String type, String name) {
        return MissingResourceParser.parse("/p/res/layout/a.xml:5: error: resource " + type + "/" + name + " (aka x:" + type + "/" + name + ") not found.").get(0);
    }

    private ResourceInventory project() throws Exception {
        File res = temp.newFolder();
        TestFiles.write(res, "drawable-night/ic_home.xml", TestFiles.VECTOR);
        TestFiles.write(res, "drawable/ic_search_24dp.xml", TestFiles.VECTOR);
        TestFiles.write(res, "mipmap-xhdpi/ic_launcher.png", TestFiles.png(96, 96));
        TestFiles.write(res, "drawable/ic_settings.xml", TestFiles.VECTOR);
        TestFiles.write(res, "values/colors.xml", "<resources><color name=\"brand_color\">#FF5722</color></resources>");
        TestFiles.write(res, "values-night/colors.xml", "<resources><color name=\"night_accent\">#FFAB91</color></resources>");
        TestFiles.write(res, "values/strings.xml", "<resources><string name=\"app_name\">App</string></resources>");
        return ResourceInventory.scan(res, null);
    }

    private IconLibrary library() throws Exception {
        File root = temp.newFolder();
        for (String name : new String[]{"add", "home", "arrow_back", "settings", "search"}) {
            for (String style : new String[]{"baseline", "outline", "round"}) {
                TestFiles.write(root, "svg/" + name + "/" + style + ".svg", "<svg viewBox=\"0 0 24 24\"><path d=\"M1 1\"/></svg>");
            }
        }
        return new PackIconLibrary(new File(root, "svg"));
    }

    @Test
    public void theSameResourceInAnotherQualifierComesFirstAndIsACopy() throws Exception {
        List<Suggestion> s = ResourceMatcher.suggest(missing("drawable", "ic_home"), project(), library());
        assertEquals(Suggestion.Kind.COPY_FILE, s.get(0).kind());
        assertEquals("-night", s.get(0).source().qualifier());
        assertTrue(s.get(0).existing());
        assertEquals(0.95, s.get(0).score(), 0);
        assertEquals("the library icon is offered after the project's own", Suggestion.Kind.MATERIAL_ICON, s.get(1).kind());
        assertEquals("home", s.get(1).iconName());
        assertFalse(s.get(1).existing());
    }

    @Test
    public void aDrawableCanBeCoveredByTheMipmapOfTheSameName() throws Exception {
        List<Suggestion> s = ResourceMatcher.suggest(missing("drawable", "ic_launcher"), project(), null);
        assertEquals(Suggestion.Kind.COPY_FILE, s.get(0).kind());
        assertEquals("mipmap", s.get(0).source().type());
        assertEquals(0.92, s.get(0).score(), 0);
    }

    @Test
    public void aMissingMipmapFindsTheDrawableOfTheSameName() throws Exception {
        List<Suggestion> s = ResourceMatcher.suggest(missing("mipmap", "ic_settings"), project(), null);
        assertEquals(Suggestion.Kind.COPY_FILE, s.get(0).kind());
        assertEquals("drawable", s.get(0).source().type());
    }

    @Test
    public void similarNamesGiveAnAliasForValuesAndACopyForFiles() throws Exception {
        List<Suggestion> color = ResourceMatcher.suggest(missing("color", "brandcolor"), project(), null);
        assertEquals(Suggestion.Kind.ALIAS, color.get(0).kind());
        assertEquals("brand_color", color.get(0).source().name());
        assertEquals("one edit away: offered, not applied without asking", 0.75, color.get(0).score(), 0);
        assertFalse(color.get(0).confident());

        List<Suggestion> drawable = ResourceMatcher.suggest(missing("drawable", "ic_search"), project(), null);
        assertEquals("ic_search matches ic_search_24dp once size words are ignored", "ic_search_24dp", drawable.get(0).source().name());
        assertEquals(Suggestion.Kind.ALIAS, drawable.get(0).kind());
        assertEquals(0.85, drawable.get(0).score(), 0);
        assertTrue(drawable.get(0).confident());
    }

    @Test
    public void libraryIconsAreMatchedByNameAndStyle() throws Exception {
        ResourceInventory empty = ResourceInventory.scan(null, null);
        List<Suggestion> plain = ResourceMatcher.suggest(missing("drawable", "ic_arrow_back_24dp"), empty, library());
        assertEquals("arrow_back", plain.get(0).iconName());
        assertEquals("baseline", plain.get(0).iconStyle());
        assertTrue(plain.get(0).confident());

        List<Suggestion> styled = ResourceMatcher.suggest(missing("drawable", "icon_add_round"), empty, library());
        assertEquals("add", styled.get(0).iconName());
        assertEquals("round", styled.get(0).iconStyle());

        List<Suggestion> fallback = ResourceMatcher.suggest(missing("drawable", "icon_add_twotone"), empty, library());
        assertEquals("a style the pack lacks falls back to baseline", "baseline", fallback.get(0).iconStyle());
    }

    @Test
    public void aNearIconNameIsOfferedButNotConfident() throws Exception {
        List<Suggestion> s = ResourceMatcher.suggest(missing("drawable", "ic_setting"), ResourceInventory.scan(null, null), library());
        assertEquals("settings", s.get(0).iconName());
        assertEquals(0.7, s.get(0).score(), 0);
        assertNull(ResourceMatcher.confident(s));
    }

    @Test
    public void nothingIsInventedForUnrelatedNamesAndValueTypes() throws Exception {
        assertTrue(ResourceMatcher.suggest(missing("drawable", "banner_photo"), project(), library()).isEmpty());
        assertTrue("a new string has no source to copy from", ResourceMatcher.suggest(missing("string", "hello_world"), project(), library()).isEmpty());
        assertTrue("icons are for files only", ResourceMatcher.suggest(missing("color", "home"), project(), library()).stream()
                .noneMatch(s -> s.kind() == Suggestion.Kind.MATERIAL_ICON));
    }

    @Test
    public void unfixableResourcesGetNoSuggestions() throws Exception {
        MissingResource framework = MissingResourceParser.parse("/p/a.xml:1: error: resource android:drawable/ic_home not found.").get(0);
        assertTrue(ResourceMatcher.suggest(framework, project(), library()).isEmpty());
        assertTrue(ResourceMatcher.suggest(missing("style", "Theme_X"), project(), library()).isEmpty());
    }

    @Test
    public void confidentReturnsTheBestOnlyWhenSure() throws Exception {
        List<Suggestion> sure = ResourceMatcher.suggest(missing("drawable", "ic_home"), project(), library());
        assertNotNull(ResourceMatcher.confident(sure));
        assertEquals(sure.get(0), ResourceMatcher.confident(sure));
        assertNull(ResourceMatcher.confident(List.of()));
    }

    @Test
    public void theRealPackAnswersCommonIconNames() throws Exception {
        assumeTrue(IconPackFixture.available());
        PackIconLibrary real = new PackIconLibrary(IconPackFixture.unpack(temp.newFolder()));
        ResourceInventory empty = ResourceInventory.scan(null, null);
        for (String name : new String[]{"ic_add", "ic_arrow_back", "ic_menu", "ic_close", "ic_search", "ic_delete", "ic_share", "ic_settings", "ic_home"}) {
            List<Suggestion> s = ResourceMatcher.suggest(missing("drawable", name), empty, real);
            assertNotNull(name, ResourceMatcher.confident(s));
            assertEquals(name, "ic_" + s.get(0).iconName(), name);
        }
    }
}
