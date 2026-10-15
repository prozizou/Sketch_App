package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PackIconLibraryTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void theShippedPackHasTheWholeMaterialSet() throws Exception {
        assumeTrue(IconPackFixture.available());
        PackIconLibrary library = new PackIconLibrary(IconPackFixture.unpack(temp.newFolder()));
        assertTrue("more than 2000 icons: " + library.size(), library.size() > 2000);
        IconLibrary.IconInfo add = library.find("add");
        assertNotNull(add);
        assertEquals(List.of("baseline", "outline", "round", "sharp", "twotone"), add.styles());
        assertTrue(library.svg("add", "baseline").contains("<path"));
    }

    @Test
    public void searchPutsTheExactNameFirstThenPrefixThenContains() throws Exception {
        assumeTrue(IconPackFixture.available());
        PackIconLibrary library = new PackIconLibrary(IconPackFixture.unpack(temp.newFolder(), "add", "add_circle", "playlist_add", "home", "arrow_back"));
        List<IconLibrary.IconInfo> found = library.search("add", 10);
        assertEquals(List.of("add", "add_circle", "playlist_add"), found.stream().map(IconLibrary.IconInfo::name).toList());
        assertEquals("a space matches an underscore", "arrow_back", library.search("arrow back", 5).get(0).name());
        assertEquals(2, library.search("add", 2).size());
        assertTrue(library.search("zzzz", 5).isEmpty());
        assertEquals("an empty query lists them all", 5, library.search("", 10).size());
    }

    @Test
    public void anUnknownIconOrStyleIsAnErrorNotAGuess() throws Exception {
        File root = temp.newFolder();
        TestFiles.write(root, "svg/dot/baseline.svg", "<svg viewBox=\"0 0 24 24\"><path d=\"M1 1\"/></svg>");
        PackIconLibrary library = new PackIconLibrary(new File(root, "svg"));
        assertNull(library.find("nothing"));
        assertNull(library.find(null));
        assertThrows(IOException.class, () -> library.svg("dot", "sharp"));
        assertThrows(IOException.class, () -> library.svg("nothing", "baseline"));
        assertEquals(0, new PackIconLibrary(new File(root, "absent")).size());
    }
}
