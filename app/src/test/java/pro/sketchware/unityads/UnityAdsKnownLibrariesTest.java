package pro.sketchware.unityads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnityAdsKnownLibrariesTest {
    @Test
    public void everyEntryIsWellFormedAndUnique() {
        Set<String> coordinates = new HashSet<>();
        Set<String> classFiles = new HashSet<>();
        for (UnityAdsKnownLibraries.Entry entry : UnityAdsKnownLibraries.all()) {
            assertEquals(entry.coordinate, 3, entry.coordinate.split(":").length);
            assertTrue(entry.classFile, entry.classFile.endsWith(".class"));
            assertFalse("a generated resource class says nothing about the library", entry.classFile.matches(".*/R(\\$.*)?\\.class"));
            assertTrue("duplicate " + entry.coordinate, coordinates.add(entry.coordinate));
            assertTrue("duplicate class " + entry.classFile, classFiles.add(entry.classFile));
        }
    }

    @Test
    public void containsTheSdkAndEveryLibraryThatBrokeThisProject() {
        Set<String> coordinates = new HashSet<>();
        for (UnityAdsKnownLibraries.Entry entry : UnityAdsKnownLibraries.all()) {
            coordinates.add(entry.coordinate);
        }
        assertTrue(coordinates.contains("com.unity3d.ads:unity-ads:" + UnityAdsKnownLibraries.SDK_VERSION));
        for (UnityAdsRequirements.Requirement requirement : UnityAdsRequirements.all()) {
            assertTrue(requirement.coordinate + " must be in the table too", coordinates.contains(requirement.coordinate));
        }
    }

    @Test
    public void missingIsWhatTheAppDoesNotContain() {
        Set<String> available = new HashSet<>(UnityAdsKnownLibraries.classFiles());
        assertTrue(UnityAdsKnownLibraries.missing(available).isEmpty());

        available.remove("androidx/datastore/core/Data.class");
        List<UnityAdsKnownLibraries.Entry> missing = UnityAdsKnownLibraries.missing(available);
        assertEquals(1, missing.size());
        assertEquals("datastore-core", missing.get(0).artifactId());
        assertEquals("1.0.0", missing.get(0).version());
    }
}
