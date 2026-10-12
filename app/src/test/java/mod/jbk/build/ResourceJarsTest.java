package mod.jbk.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourceJarsTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File jar(String folder, String name, String... entries) throws IOException {
        File dir = temp.newFolder(folder);
        File file = new File(dir, name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                out.write(entry.getBytes());
                out.closeEntry();
            }
        }
        return file;
    }

    private static Set<String> names(File jar) throws IOException {
        Set<String> names = new TreeSet<>();
        try (ZipFile zip = new ZipFile(jar)) {
            zip.stream().forEach(entry -> names.add(entry.getName()));
        }
        return names;
    }

    @Test
    public void theSecondLibraryLosesTheResourceTheFirstAlreadyProvided() throws IOException {
        File builtIn = jar("kotlin-stdlib-2.2.0", "classes.jar", "kotlin/annotation/annotation.kotlin_builtins", "Only.properties");
        File downloaded = jar("kotlin-stdlib-v2.1.21", "classes.jar", "kotlin/annotation/annotation.kotlin_builtins", "kotlin/Extra.txt");
        Set<String> added = new HashSet<>();
        File out = temp.newFolder("out");

        File first = ResourceJars.withoutEntries(builtIn, added, out);
        File second = ResourceJars.withoutEntries(downloaded, added, out);

        assertEquals(new TreeSet<>(java.util.Arrays.asList("kotlin/annotation/annotation.kotlin_builtins", "Only.properties")), names(first));
        assertEquals("only what is new is kept", Collections.singleton("kotlin/Extra.txt"), names(second));
    }

    @Test
    public void classFilesAndDirectoriesAreLeftOut() throws IOException {
        File lib = jar("lib", "classes.jar", "a/B.class", "a/", "res/data.json");
        File copy = ResourceJars.withoutEntries(lib, new HashSet<String>(), temp.newFolder("out"));
        assertEquals(Collections.singleton("res/data.json"), names(copy));
    }

    @Test
    public void copiesOfSameNamedJarsDoNotOverwriteEachOther() throws IOException {
        File a = jar("a-lib", "classes.jar", "one.txt");
        File b = jar("b-lib", "classes.jar", "two.txt");
        Set<String> added = new HashSet<>();
        File out = temp.newFolder("out");
        File copyA = ResourceJars.withoutEntries(a, added, out);
        File copyB = ResourceJars.withoutEntries(b, added, out);
        assertFalse(copyA.equals(copyB));
        assertTrue(names(copyA).contains("one.txt"));
        assertTrue(names(copyB).contains("two.txt"));
    }

    @Test
    public void anUnreadableJarIsReturnedUnchanged() throws IOException {
        File notAJar = temp.newFile("broken.jar");
        File dir = temp.newFolder("broken-lib");
        File inDir = new File(dir, "classes.jar");
        assertTrue(inDir.createNewFile());
        assertSame(inDir, ResourceJars.withoutEntries(inDir, new HashSet<String>(), temp.newFolder("out")));
        assertNotNull(notAJar);
    }
}
