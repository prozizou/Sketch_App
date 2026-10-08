package dev.aldi.sayuti.editor.manage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

public class OnlineLibraryFinderTest {
    @Test
    public void readsArtifactAndVersionFromFolderNames() {
        assertEquals("zoomage", LibraryNameParser.parse("zoomage_V_1.3.1").artifactId);
        assertEquals("1.3.1", LibraryNameParser.parse("zoomage_V_1.3.1").version);
        assertEquals("appcompat", LibraryNameParser.parse("appcompat-v1.6.1").artifactId);
        assertEquals("1.6.1", LibraryNameParser.parse("appcompat-v1.6.1").version);
        assertEquals("material", LibraryNameParser.parse("material-1.9.0").artifactId);
        assertEquals("1.9.0-beta01", LibraryNameParser.parse("material-1.9.0-beta01").version);
        assertEquals("gson", LibraryNameParser.parse("gson").artifactId);
        assertNull(LibraryNameParser.parse("gson").version);
    }

    @Test
    public void picksTheWantedVersionWhenItExists() {
        assertEquals("1.3.1", MavenVersions.pickBest("1.3.1", List.of("1.0.0", "1.3.1", "2.0.0")));
    }

    @Test
    public void otherwiseTheNewestStableOfTheSameMajor() {
        assertEquals("1.9.2", MavenVersions.pickBest("1.3.1", List.of("1.9.2", "1.10.0-beta01", "2.0.0", "0.9")));
        assertEquals("1.10.0", MavenVersions.pickBest("1.3.1", List.of("1.9.2", "1.10.0", "2.0.0")));
    }

    @Test
    public void otherwiseTheNewestStableThenTheNewestOfAll() {
        assertEquals("3.1.0", MavenVersions.pickBest("2.0", List.of("3.0.0", "3.1.0", "4.0.0-rc1")));
        assertEquals("4.0.0-rc1", MavenVersions.pickBest(null, List.of("4.0.0-alpha1", "4.0.0-rc1")));
        assertNull(MavenVersions.pickBest("1.0", List.of()));
    }

    @Test
    public void readsMavenCentralSearchAnswers() {
        String json = "{\"response\":{\"numFound\":2,\"docs\":["
                + "{\"id\":\"com.jsibbold:zoomage\",\"g\":\"com.jsibbold\",\"a\":\"zoomage\",\"latestVersion\":\"1.3.1\",\"versionCount\":6},"
                + "{\"id\":\"x.y:zoomage-extra\",\"g\":\"x.y\",\"a\":\"zoomage-extra\",\"versionCount\":30}]}}";
        List<OnlineLibraryFinder.Candidate> candidates = OnlineLibraryFinder.parseSearch(json);
        assertEquals(2, candidates.size());
        OnlineLibraryFinder.Candidate best = OnlineLibraryFinder.bestCandidate("zoomage", candidates);
        assertNotNull(best);
        assertEquals("com.jsibbold", best.group);
        assertEquals(0, OnlineLibraryFinder.parseSearch("not json").size());
    }

    @Test
    public void readsTheVersionsOfAMavenMetadata() {
        String xml = "<metadata><groupId>g</groupId><artifactId>a</artifactId><versioning><latest>2.0</latest>"
                + "<versions><version>1.0</version><version>2.0</version></versions></versioning></metadata>";
        assertEquals(List.of("1.0", "2.0"), OnlineLibraryFinder.parseVersions(xml));
    }
}
