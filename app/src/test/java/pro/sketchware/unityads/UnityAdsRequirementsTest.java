package pro.sketchware.unityads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnityAdsRequirementsTest {
    @Test
    public void reportsTheDataStoreClassThatBrokeStartup() {
        Set<String> available = new HashSet<>(UnityAdsRequirements.classFiles());
        available.remove("androidx/datastore/core/DataStore.class");
        List<UnityAdsRequirements.Requirement> missing = UnityAdsRequirements.missing(available);
        assertEquals(1, missing.size());
        assertEquals("androidx.datastore:datastore-core:1.0.0", missing.get(0).coordinate);
        assertEquals("datastore-core", missing.get(0).artifactId());
    }

    @Test
    public void nothingMissingWhenEveryClassIsThere() {
        assertTrue(UnityAdsRequirements.missing(UnityAdsRequirements.classFiles()).isEmpty());
        assertEquals(UnityAdsRequirements.all().size(), UnityAdsRequirements.missing(Collections.<String>emptyList()).size());
    }

    @Test
    public void coordinatesAreWellFormed() {
        Set<String> classFiles = new HashSet<>();
        for (UnityAdsRequirements.Requirement requirement : UnityAdsRequirements.all()) {
            assertEquals(requirement.coordinate, 3, requirement.coordinate.split(":").length);
            assertTrue(requirement.classFile, requirement.classFile.endsWith(".class"));
            assertTrue("duplicate " + requirement.classFile, classFiles.add(requirement.classFile));
        }
    }

    @Test
    public void readsTheSdkVersionFromTheLibraryName() {
        List<HashMap<String, Object>> libs = new ArrayList<>();
        assertNull(UnityAdsRequirements.sdkVersion(libs));
        for (String name : Arrays.asList("datastore-core-v1.0.0", "unity-ads-mediation-v1.0", "unity-ads-v4.21.0")) {
            HashMap<String, Object> lib = new HashMap<>();
            lib.put("name", name);
            libs.add(lib);
        }
        assertEquals("4.21.0", UnityAdsRequirements.sdkVersion(libs));
        assertNull(UnityAdsRequirements.sdkVersion(null));
    }
}
