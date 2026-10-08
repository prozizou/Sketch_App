package pro.sketchware.activities.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import pro.sketchware.activities.search.ProjectSearch.Result;

public class ProjectSearchTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private File root;

    private void write(String path, String content) throws Exception {
        File f = new File(root, path);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), content);
    }

    @Before
    public void setUp() throws Exception {
        root = temp.newFolder("project");
        write("java/Main.java", "class Main {\n    int Counter = 0;\n    void counter() {}\n}\n");
        write("resource/layout/a.xml", "<TextView android:text=\"counter\"/>\n");
    }

    private Result run(String query, boolean cs, boolean rx, boolean ww) {
        return new ProjectSearch(query, cs, rx, ww).search(root, () -> false);
    }

    @Test
    public void findsCaseInsensitiveAcrossFilesWithPositions() {
        Result r = run("counter", false, false, false);
        assertEquals(3, r.matches().size());
        assertEquals(2, r.matches().get(0).line());
        assertEquals(9, r.matches().get(0).column());
        assertEquals(2, r.filesSearched());
        assertFalse(r.truncated());
    }

    @Test
    public void caseSensitiveAndWholeWord() {
        assertEquals(2, run("counter", true, false, false).matches().size());
        assertEquals(1, run("Counter", true, false, true).matches().size());
        assertEquals(0, run("count", false, false, true).matches().size());
    }

    @Test
    public void regexAndLiteralQueries() {
        assertEquals(2, run("int|void", false, true, false).matches().size());
        assertEquals(0, run("int|void", false, false, false).matches().size());
        assertEquals(1, run("{}", false, false, false).matches().size());
    }

    @Test(expected = java.util.regex.PatternSyntaxException.class)
    public void invalidRegexThrows() {
        run("(", false, true, false);
    }

    @Test
    public void skipsBinaryFiles() throws Exception {
        File bin = new File(root, "assets/x.bin");
        bin.getParentFile().mkdirs();
        Files.write(bin.toPath(), new byte[]{'c', 'o', 'u', 'n', 't', 'e', 'r', 0, 1});
        Result r = run("counter", false, false, false);
        assertEquals(3, r.matches().size());
        assertEquals(2, r.filesSearched());
    }

    @Test
    public void cancellationStopsEarly() {
        Result r = new ProjectSearch("counter", false, false, false).search(root, () -> true);
        assertTrue(r.matches().isEmpty());
    }

    @Test
    public void capsResults() throws Exception {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < ProjectSearch.MAX_RESULTS + 50; i++) many.append("hit\n");
        write("big.txt", many.toString());
        Result r = run("hit", false, false, false);
        assertEquals(ProjectSearch.MAX_RESULTS, r.matches().size());
        assertTrue(r.truncated());
    }
}
