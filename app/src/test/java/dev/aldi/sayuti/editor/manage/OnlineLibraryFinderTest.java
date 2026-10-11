package dev.aldi.sayuti.editor.manage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void guessesAndroidXGroupsFromFolderNames() {
        assertTrue(OnlineLibraryFinder.guessGoogleGroups("activity").contains("androidx.activity"));
        assertTrue(OnlineLibraryFinder.guessGoogleGroups("appcompat-resources").contains("androidx.appcompat"));
        assertTrue(OnlineLibraryFinder.guessGoogleGroups("core-common").contains("androidx.arch.core"));
        assertTrue(OnlineLibraryFinder.guessGoogleGroups("cursoradapter").contains("androidx.cursoradapter"));
        assertTrue(OnlineLibraryFinder.guessGoogleGroups("material").contains("com.google.android.material"));
    }

    @Test
    public void readsGoogleMavenIndexes() {
        String master = "<?xml version='1.0' encoding='UTF-8'?>\n<metadata>\n  <androidx.activity/>\n  <androidx.arch.core/>\n"
                + "  <com.google.android.material/>\n</metadata>";
        List<String> groups = OnlineLibraryFinder.parseIndexGroups(master);
        assertEquals(List.of("androidx.activity", "androidx.arch.core", "com.google.android.material"), groups);
        assertEquals(List.of("androidx.arch.core"), OnlineLibraryFinder.googleGroupsMatching("core-runtime", groups));

        String group = "<?xml version='1.0' encoding='UTF-8'?>\n<androidx.arch.core>\n"
                + "  <core-common versions=\"2.0.0,2.1.0,2.2.0\"/>\n  <core-runtime versions=\"2.0.0,2.2.0\"/>\n</androidx.arch.core>";
        assertEquals(List.of("2.0.0", "2.1.0", "2.2.0"), OnlineLibraryFinder.parseGroupIndexVersions(group, "core-common"));
        assertTrue(OnlineLibraryFinder.parseGroupIndexVersions(group, "core").isEmpty());
    }

    @Test
    public void readsGitHubRepositorySearches() {
        String json = "{\"total_count\":2,\"items\":[{\"id\":1,\"full_name\":\"jsibbold/zoomage\",\"name\":\"zoomage\"},"
                + "{\"id\":2,\"full_name\":\"someone/zoomage-demo\"}]}";
        List<String[]> repos = OnlineLibraryFinder.parseGitHubRepos(json);
        assertEquals(2, repos.size());
        assertEquals("jsibbold", repos.get(0)[0]);
        assertEquals("zoomage", repos.get(0)[1]);
    }
}
