package pro.sketchware.resolver;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ResourceTransactionTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File res;
    private File journal;
    private final ResourceValidator builtIn = new BuiltInValidator();

    @Before
    public void setUp() throws Exception {
        res = temp.newFolder("res");
        journal = new File(temp.getRoot(), "journal");
    }

    private static PlannedChange file(String key, String path, byte[] content) {
        return PlannedChange.file(key, path, content, "test", List.of());
    }

    private static PlannedChange value(String key, String path, String type, String name, String value) {
        return PlannedChange.value(key, path, new ValuesFile.Entry(type, name, value), "test");
    }

    private static byte[] vector() {
        return TestFiles.VECTOR.getBytes(StandardCharsets.UTF_8);
    }

    private Set<String> tree() throws Exception {
        Set<String> paths = new TreeSet<>();
        try (var walk = Files.walk(res.toPath())) {
            walk.filter(Files::isRegularFile).forEach(p -> paths.add(res.toPath().relativize(p).toString()));
        }
        return paths;
    }

    @Test
    public void writesNewFilesAndAddsValuesInOneOperation() throws Exception {
        TestFiles.write(res, "values/colors.xml", "<resources>\n    <color name=\"brand\">#FF5722</color>\n</resources>\n");
        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(
                file("drawable/ic_add", "drawable/ic_add.xml", vector()),
                file("drawable/logo", "drawable-xhdpi/logo.png", TestFiles.png(8, 8)),
                value("color/accent", "values/colors.xml", "color", "accent", "@color/brand"),
                value("string/hello", "values/strings.xml", "string", "hello", "Hello")), builtIn);

        assertTrue(result.message(), result.success());
        assertEquals("4 resources added", result.message());
        assertEquals(List.of("drawable/ic_add.xml", "drawable-xhdpi/logo.png", "values/strings.xml"), result.created());
        assertEquals(List.of("values/colors.xml"), result.modified());
        assertEquals(Set.of("drawable/ic_add.xml", "drawable-xhdpi/logo.png", "values/colors.xml", "values/strings.xml"), tree());
        assertEquals(TestFiles.VECTOR, TestFiles.read(new File(res, "drawable/ic_add.xml")));
        String colors = TestFiles.read(new File(res, "values/colors.xml"));
        assertTrue(colors.startsWith("<resources>\n    <color name=\"brand\">#FF5722</color>\n"));
        assertTrue(colors.contains("<color name=\"accent\">@color/brand</color>"));
        assertTrue(TestFiles.read(new File(res, "values/strings.xml")).contains("<string name=\"hello\">Hello</string>"));
        assertNotNull(result.journal());
        assertEquals(ResourceJournal.State.DONE, result.journal().state());
        assertEquals(4, result.journal().changeCount());
        assertFalse("no temporary file is left behind", tree().stream().anyMatch(p -> p.endsWith(".tmp")));
    }

    @Test
    public void severalEntriesGoIntoOneValuesFileTogether() throws Exception {
        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(
                value("color/a", "values/colors.xml", "color", "a", "#111111"),
                value("color/b", "values/colors.xml", "color", "b", "#222222")), builtIn);
        assertTrue(result.message(), result.success());
        assertEquals(2, ValuesFile.parse(TestFiles.read(new File(res, "values/colors.xml"))).size());
    }

    @Test
    public void anExistingFileIsNeverOverwritten() throws Exception {
        TestFiles.write(res, "drawable/ic_add.png", TestFiles.png(2, 2));
        byte[] before = Files.readAllBytes(new File(res, "drawable/ic_add.png").toPath());
        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(file("drawable/ic_add", "drawable/ic_add.xml", vector())), builtIn);
        assertFalse("the same name with another extension is the same resource", result.success());
        assertTrue(result.message(), result.message().contains("already exists"));
        assertArrayEquals(before, Files.readAllBytes(new File(res, "drawable/ic_add.png").toPath()));
        assertEquals(Set.of("drawable/ic_add.png"), tree());
        assertFalse("no journal for an operation that wrote nothing", journal.exists() && journal.list().length > 0);

        TestFiles.write(res, "drawable/Logo.png", TestFiles.png(2, 2));
        assertFalse("letter case doesn't make a new file", ResourceTransaction.apply(res, journal, List.of(file("drawable/logo", "drawable/logo.xml", vector())), builtIn).success());
    }

    @Test
    public void aValueThatExistsIsNeverRedefined() throws Exception {
        TestFiles.write(res, "values/colors.xml", "<resources><color name=\"brand\">#FF5722</color></resources>");
        String before = TestFiles.read(new File(res, "values/colors.xml"));
        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(value("color/brand", "values/colors.xml", "color", "brand", "#000000")), builtIn);
        assertFalse(result.success());
        assertTrue(result.message(), result.message().contains("already defined"));
        assertEquals(before, TestFiles.read(new File(res, "values/colors.xml")));
    }

    @Test
    public void oneRefusedFileStopsEverythingAndNothingIsWritten() throws Exception {
        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(
                file("drawable/good", "drawable/good.xml", vector()),
                file("drawable/bad", "drawable/bad.xml", "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"><path></vector>".getBytes(StandardCharsets.UTF_8)),
                value("color/c", "values/colors.xml", "color", "c", "#123456")), builtIn);
        assertFalse(result.success());
        assertEquals(1, result.failures().size());
        assertEquals("drawable/bad", result.failures().get(0).key());
        assertEquals("drawable/bad.xml", result.failures().get(0).path());
        assertTrue(result.failures().get(0).message(), result.failures().get(0).message().contains("valid"));
        assertTrue("the good file wasn't written either", tree().isEmpty());
        assertNull(result.journal());
    }

    @Test
    public void aValuesFileThatIsNotValidIsNotRewritten() throws Exception {
        TestFiles.write(res, "values/strings.xml", "<resources><string name=\"x\">oops</resources>");
        String before = TestFiles.read(new File(res, "values/strings.xml"));
        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(value("string/hello", "values/strings.xml", "string", "hello", "Hello")), builtIn);
        assertFalse(result.success());
        assertTrue(result.message(), result.message().contains("can't be edited"));
        assertEquals(before, TestFiles.read(new File(res, "values/strings.xml")));
    }

    @Test
    public void thePlanItselfIsCheckedForTheSameFileTwice() throws Exception {
        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(
                file("drawable/a", "drawable/a.xml", vector()), file("drawable/A", "drawable/A.xml", vector())), builtIn);
        assertFalse(result.success());
        assertTrue(result.message(), result.message().contains("Two changes write"));
        assertTrue(tree().isEmpty());
    }

    @Test
    public void nothingCanBeWrittenOutsideRes() throws Exception {
        ResourceTransaction.Result escape = ResourceTransaction.apply(res, journal, List.of(file("drawable/x", "../escape.xml", vector())), new AcceptAll());
        assertFalse(escape.success());
        assertFalse(new File(temp.getRoot(), "escape.xml").exists());
        ResourceTransaction.Result valuesEscape = ResourceTransaction.apply(res, journal, List.of(value("color/x", "../values.xml", "color", "x", "#000000")), builtIn);
        assertFalse(valuesEscape.success());
        assertFalse(new File(temp.getRoot(), "values.xml").exists());
    }

    @Test
    public void emptyPlansAreRefused() {
        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(), builtIn);
        assertFalse(result.success());
        assertEquals("There is nothing to apply", result.message());
    }

    @Test
    public void aFailureWhileWritingPutsTheProjectBack() throws Exception {
        TestFiles.write(res, "values/colors.xml", "<resources>\n    <color name=\"brand\">#FF5722</color>\n</resources>\n");
        String colorsBefore = TestFiles.read(new File(res, "values/colors.xml"));
        // "drawable-xhdpi" is a regular file here, so the folder for the second file can't be made
        TestFiles.write(res, "drawable-xhdpi", "I am not a folder");
        Set<String> before = tree();

        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(
                file("drawable/first", "drawable/first.xml", vector()),
                file("drawable/second", "drawable-xhdpi/second.png", TestFiles.png(4, 4)),
                value("color/accent", "values/colors.xml", "color", "accent", "#FFFFFF")), builtIn);

        assertFalse(result.success());
        assertTrue(result.message(), result.message().contains("left as it was"));
        assertEquals("what was created before the failure is removed again", before, tree());
        assertFalse(new File(res, "drawable").exists());
        assertEquals("the values file is as it was", colorsBefore, TestFiles.read(new File(res, "values/colors.xml")));
        assertTrue("the record of the failed operation is removed", journal.list() == null || journal.list().length == 0);
    }

    @Test
    public void everyOperationIsRecordedSoItCanBeTakenBack() throws Exception {
        ResourceTransaction.Result first = ResourceTransaction.apply(res, journal, List.of(file("drawable/a", "drawable/a.xml", vector())), builtIn);
        Thread.sleep(5);
        ResourceTransaction.Result second = ResourceTransaction.apply(res, journal, List.of(file("drawable/b", "drawable/b.xml", vector())), builtIn);
        List<ResourceJournal.Entry> history = ResourceJournal.list(journal);
        assertEquals(2, history.size());
        assertEquals("newest first", second.journal().id(), history.get(0).id());
        assertEquals(first.journal().id(), history.get(1).id());
    }

    /** A validator that accepts anything, to check that the transaction keeps its own guards. */
    private static final class AcceptAll implements ResourceValidator {
        @Override
        public Result validateFile(String relativePath, byte[] content) {
            return Result.OK;
        }

        @Override
        public Result validateValues(String relativePath, String xml) {
            return Result.OK;
        }
    }
}
