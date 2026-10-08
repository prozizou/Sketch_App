package mod.jbk.code.completion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ClassIndexTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void readsClassNamesFromAJarWithoutNestedOnes() throws Exception {
        File jar = temp.newFile("lib.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar.toPath()))) {
            for (String entry : new String[]{"android/app/Activity.class", "android/app/Activity$1.class", "META-INF/versions/9/X.class",
                    "android/app/package-info.class", "android/app/readme.txt", "Top.class"}) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.closeEntry();
            }
        }
        assertEquals(List.of("Top", "android.app.Activity"), ClassIndex.fromJar(jar).all());
    }

    @Test
    public void readsClassNamesFromAJarInsideAnArchive() throws Exception {
        java.io.ByteArrayOutputStream jar = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(jar)) {
            for (String entry : new String[]{"android/app/Activity.class", "android/widget/Button.class"}) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(new byte[5000]);
                zip.closeEntry();
            }
        }
        java.io.ByteArrayOutputStream archive = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(archive)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write(1);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("android.jar"));
            zip.write(jar.toByteArray());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("other.jar"));
            zip.write(jar.toByteArray());
            zip.closeEntry();
        }
        ClassIndex index = ClassIndex.fromArchiveOfJars(new java.io.ByteArrayInputStream(archive.toByteArray()));
        assertEquals(List.of("android.app.Activity", "android.widget.Button"), index.all());
    }

    @Test
    public void readsClassNamesFromSourceFolders() throws Exception {
        File root = temp.newFolder("java");
        new File(root, "com/my/app").mkdirs();
        Files.writeString(new File(root, "com/my/app/Helper.java").toPath(), "class Helper {}");
        Files.writeString(new File(root, "com/my/app/notes.txt").toPath(), "");
        assertEquals(List.of("com.my.app.Helper"), ClassIndex.fromSourceDir(root).all());
    }

    @Test
    public void looksUpByPrefixAndSimpleName() {
        ClassIndex index = ClassIndex.of(List.of("a.Button", "b.Button", "a.ButtonBar", "a.Other"));
        assertEquals(List.of("a.Button", "a.ButtonBar", "b.Button"), index.withSimpleNamePrefix("butt"));
        assertEquals(List.of("a.Button", "b.Button"), index.withSimpleName("Button"));
        assertEquals(List.of("a.Button", "a.ButtonBar", "a.Other"), index.withQualifiedPrefix("a."));
        assertEquals("a", ClassIndex.packageOf("a.Button"));
        assertNull(ClassIndex.classNameOf("x/readme.md"));
    }

    @Test
    public void findsTheProjectOfAJavaFile() {
        assertEquals("605", ClassIndex.projectIdOfJavaFile("/storage/emulated/0/.sketch_nws/data/605/files/java/com/my/Foo.java"));
        assertNull(ClassIndex.projectIdOfJavaFile("/storage/emulated/0/Download/Foo.java"));
        assertNull(ClassIndex.projectIdOfJavaFile("/storage/emulated/0/.sketch_nws/data/605/files/assets/Foo.java"));
        assertNull(ClassIndex.projectIdOfJavaFile(null));
    }

    @Test
    public void mergesIndexes() {
        ClassIndex merged = ClassIndex.merge(ClassIndex.of(List.of("a.A")), ClassIndex.of(List.of("b.B", "a.A")), ClassIndex.empty());
        assertEquals(List.of("a.A", "b.B"), merged.all());
    }
}
