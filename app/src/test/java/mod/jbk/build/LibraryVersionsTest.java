package mod.jbk.build;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class LibraryVersionsTest {
    @Test
    public void splitsFolderNamesIntoArtifactAndVersion() {
        assertArrayEquals(new String[]{"kotlinx-coroutines-core-jvm", "1.8.1"}, LibraryVersions.split("kotlinx-coroutines-core-jvm-1.8.1"));
        assertArrayEquals(new String[]{"kotlinx-coroutines-core-jvm", "1.10.2"}, LibraryVersions.split("kotlinx-coroutines-core-jvm-v1.10.2"));
        assertArrayEquals(new String[]{"kotlin-stdlib-jdk7", "2.1.21"}, LibraryVersions.split("kotlin-stdlib-jdk7-2.1.21"));
        assertArrayEquals(new String[]{"error_prone_annotations", "2.38.0"}, LibraryVersions.split("error_prone_annotations-2.38.0"));
        assertNull(LibraryVersions.split("nameWithoutVersion"));
        assertNull(LibraryVersions.split(null));
    }

    @Test
    public void comparesVersionsNumerically() {
        assertTrue(LibraryVersions.compare("1.10.2", "1.8.1") > 0);
        assertTrue(LibraryVersions.compare("2.1.21", "2.2.0") < 0);
        assertEquals(0, LibraryVersions.compare("1.0", "1.0.0"));
    }

    @Test
    public void onlyBuiltInLibrariesWithANewerDownloadedCopyAreOutdated() {
        // Real case: Unity Ads 4.21.0 needs coroutines 1.10.2; NWS has 1.8.1 and a newer Kotlin than the SDK ships.
        assertEquals(Arrays.asList("kotlinx-coroutines-core-jvm-1.8.1", "kotlinx-coroutines-android-1.8.1"),
                LibraryVersions.outdatedBuiltIns(
                        Arrays.asList("kotlin-stdlib-2.2.0", "kotlinx-coroutines-core-jvm-1.8.1", "kotlinx-coroutines-android-1.8.1", "annotations-23.0.0"),
                        Arrays.asList("kotlin-stdlib-v2.1.21", "kotlinx-coroutines-core-jvm-v1.10.2", "kotlinx-coroutines-android-v1.10.2", "unity-ads-v4.21.0")));
    }

    @Test
    public void nothingIsOutdatedWithoutDownloadedLibraries() {
        assertTrue(LibraryVersions.outdatedBuiltIns(Arrays.asList("kotlin-stdlib-2.2.0"), Collections.<String>emptyList()).isEmpty());
    }
}
