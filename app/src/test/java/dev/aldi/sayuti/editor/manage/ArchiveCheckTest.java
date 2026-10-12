package dev.aldi.sayuti.editor.manage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ArchiveCheckTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File zip() throws IOException {
        File file = temp.newFile("a.aar");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new ZipEntry("classes.jar"));
            out.write(new byte[2000]);
            out.closeEntry();
        }
        return file;
    }

    @Test
    public void acceptsACompleteArchive() throws IOException {
        assertTrue(ArchiveCheck.isValidZip(zip()));
    }

    @Test
    public void rejectsACutOffArchive() throws IOException {
        File file = zip();
        byte[] bytes = Files.readAllBytes(file.toPath());
        File cut = temp.newFile("cut.aar");
        Files.write(cut.toPath(), java.util.Arrays.copyOf(bytes, bytes.length / 2));
        assertFalse("the failure from the bug report: END header not found", ArchiveCheck.isValidZip(cut));
    }

    @Test
    public void rejectsAnErrorPageMissingAndEmptyFiles() throws IOException {
        File page = temp.newFile("page.aar");
        Files.write(page.toPath(), "Your ip has exceeded rate limits. Find out more here https://central.sonatype.org/faq/429-error/"
                .getBytes(StandardCharsets.UTF_8));
        assertFalse(ArchiveCheck.isValidZip(page));
        assertFalse(ArchiveCheck.isValidZip(temp.newFile("empty.aar")));
        assertFalse(ArchiveCheck.isValidZip(new File(temp.getRoot(), "missing.aar")));
        assertFalse(ArchiveCheck.isValidZip(null));
    }
}
