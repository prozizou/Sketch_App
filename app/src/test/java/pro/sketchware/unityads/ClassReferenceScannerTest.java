package pro.sketchware.unityads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ClassReferenceScannerTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static byte[] classBytes(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            assertNotNull(in);
            return in.readAllBytes();
        }
    }

    private File jar(String name, Map<String, byte[]> entries) throws IOException {
        File file = temp.newFile(name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return file;
    }

    @Test
    public void readsTheClassesAClassFileRefersTo() throws IOException {
        try (InputStream in = ClassReferenceScanner.class.getResourceAsStream("ClassReferenceScanner.class")) {
            List<String> references = ClassReferenceScanner.referencedClasses(in);
            assertTrue(references.contains("java/io/DataInputStream"));
            assertTrue(references.contains("java/util/zip/ZipFile"));
            assertTrue(references.contains("pro/sketchware/unityads/ClassReferenceScanner"));
        }
    }

    @Test
    public void reportsAClassNoLibraryProvides() throws IOException {
        File program = jar("program.jar", Collections.singletonMap("pro/sketchware/unityads/UnityAdsConfig.class", classBytes(UnityAdsConfig.class)));
        File provider = jar("provider.jar", Collections.singletonMap("something/Else.class", new byte[0]));

        Map<String, String> missing = ClassReferenceScanner.findMissing(Collections.singletonList(program), Collections.singletonList(provider));

        assertTrue("Gson is used by UnityAdsConfig", missing.containsKey("com/google/gson/Gson"));
        assertEquals("pro/sketchware/unityads/UnityAdsConfig", missing.get("com/google/gson/Gson"));
        assertFalse("java.* is the platform's", missing.containsKey("java/lang/String"));
    }

    @Test
    public void aProvidedClassIsNotReported() throws IOException {
        File program = jar("program.jar", Collections.singletonMap("pro/sketchware/unityads/UnityAdsConfig.class", classBytes(UnityAdsConfig.class)));
        File gson = jar("gson.jar", Collections.singletonMap("com/google/gson/Gson.class", new byte[0]));
        File itself = jar("self.jar", Collections.singletonMap("pro/sketchware/unityads/UnityAdsConfig.class", new byte[0]));
        File other = jar("other.jar", Collections.singletonMap("com/google/gson/reflect/TypeToken.class", new byte[0]));

        Map<String, String> missing = ClassReferenceScanner.findMissing(
                Collections.singletonList(program), Arrays.asList(gson, itself, other));

        assertFalse(missing.containsKey("com/google/gson/Gson"));
        assertFalse(missing.containsKey("pro/sketchware/unityads/UnityAdsConfig"));
    }

    @Test
    public void unreadableInputsAreSkipped() throws IOException {
        File notAJar = temp.newFile("broken.jar");
        File garbage = jar("garbage.jar", Collections.singletonMap("a/B.class", new byte[]{1, 2, 3}));
        assertTrue(ClassReferenceScanner.findMissing(Arrays.asList(notAJar, garbage), Collections.singletonList(notAJar)).isEmpty());
    }
}
