package pro.sketchware.utility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ZipSafetyTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void plainAndNestedEntriesStayInside() throws IOException {
        File dest = temp.newFolder("out");
        assertEquals(new File(dest, "project"), ZipSafety.resolveEntry(dest, "project"));
        assertEquals(new File(dest, "data/a/b.json"), ZipSafety.resolveEntry(dest, "data/a/b.json"));
        assertEquals(new File(dest, "dir/"), ZipSafety.resolveEntry(dest, "dir/"));
    }

    @Test
    public void dotDotThatStaysInsideIsFine() throws IOException {
        File dest = temp.newFolder("out");
        ZipSafety.resolveEntry(dest, "a/../b.txt");
    }

    @Test
    public void parentTraversalIsRejected() throws IOException {
        File dest = temp.newFolder("out");
        assertThrows(IOException.class, () -> ZipSafety.resolveEntry(dest, "../evil.txt"));
        assertThrows(IOException.class, () -> ZipSafety.resolveEntry(dest, "a/../../evil.txt"));
        assertThrows(IOException.class, () -> ZipSafety.resolveEntry(dest, "a/b/../../../evil.txt"));
        assertThrows(IOException.class, () -> ZipSafety.resolveEntry(dest, "/../evil.txt"));
        assertThrows(IOException.class, () -> ZipSafety.resolveEntry(dest, "../.sketch_nws/data/settings.json"));
    }

    @Test
    public void siblingDirectoryWithSamePrefixIsRejected() throws IOException {
        // /tmp/x/out vs /tmp/x/out-evil: a plain startsWith() check would let this through.
        File dest = temp.newFolder("out");
        temp.newFolder("out-evil");
        assertThrows(IOException.class, () -> ZipSafety.resolveEntry(dest, "../out-evil/file.txt"));
    }

    @Test
    public void entryResolvingToTheDirectoryItselfIsAllowed() throws IOException {
        File dest = temp.newFolder("out");
        ZipSafety.resolveEntry(dest, ".");
    }

    @Test
    public void nulByteIsRejected() throws IOException {
        File dest = temp.newFolder("out");
        assertThrows(IOException.class, () -> ZipSafety.resolveEntry(dest, "a\0b"));
        assertThrows(IOException.class, () -> ZipSafety.resolveEntry(dest, null));
    }

    @Test
    public void symlinkPointingOutsideIsRejected() throws IOException {
        File dest = temp.newFolder("out");
        File outside = temp.newFolder("outside");
        try {
            Files.createSymbolicLink(new File(dest, "link").toPath(), outside.toPath());
        } catch (UnsupportedOperationException | IOException e) {
            assumeNoException("Symbolic links aren't available here", e);
        }
        assertThrows(IOException.class, () -> ZipSafety.resolveEntry(dest, "link/evil.txt"));
        assertTrue(outside.list().length == 0);
    }
}
