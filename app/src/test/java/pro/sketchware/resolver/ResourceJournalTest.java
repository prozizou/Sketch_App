package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class ResourceJournalTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File res;
    private File journal;
    private final ResourceValidator validator = new BuiltInValidator();

    @Before
    public void setUp() throws Exception {
        res = temp.newFolder("res");
        journal = new File(temp.getRoot(), "journal");
    }

    private ResourceJournal.Entry applyMixed() throws Exception {
        TestFiles.write(res, "values/colors.xml", "<resources>\n    <!-- mine -->\n    <color name=\"brand\">#FF5722</color>\n</resources>\n");
        ResourceTransaction.Result result = ResourceTransaction.apply(res, journal, List.of(
                PlannedChange.file("drawable/ic_add", "drawable-xhdpi/ic_add.png", TestFiles.png(8, 8), "t", List.of()),
                PlannedChange.file("drawable/vec", "drawable/vec.xml", TestFiles.VECTOR.getBytes(StandardCharsets.UTF_8), "t", List.of()),
                PlannedChange.value("color/a", "values/colors.xml", new ValuesFile.Entry("color", "a", "#111111"), "t"),
                PlannedChange.value("color/b", "values/colors.xml", new ValuesFile.Entry("color", "b", "#222222"), "t"),
                PlannedChange.value("string/s", "values/strings.xml", new ValuesFile.Entry("string", "s", "Hi"), "t")), validator);
        assertTrue(result.message(), result.success());
        return result.journal();
    }

    @Test
    public void takingBackRemovesExactlyWhatWasAdded() throws Exception {
        String colorsBefore = "<resources>\n    <!-- mine -->\n    <color name=\"brand\">#FF5722</color>\n</resources>\n";
        ResourceJournal.Entry entry = applyMixed();
        ResourceJournal.UndoResult undone = ResourceJournal.undo(res, entry);

        assertEquals(colorsBefore, TestFiles.read(new File(res, "values/colors.xml")));
        assertFalse(new File(res, "drawable-xhdpi").exists());
        assertFalse(new File(res, "drawable").exists());
        assertFalse("a values file the operation created is removed", new File(res, "values/strings.xml").exists());
        assertTrue(undone.kept().isEmpty());
        assertEquals("2 files, 2 colours and 1 text", 5, undone.removed().size());
        assertEquals(ResourceJournal.State.UNDONE, ResourceJournal.list(journal).get(0).state());
    }

    @Test
    public void anOperationCanOnlyBeTakenBackOnce() throws Exception {
        ResourceJournal.Entry entry = applyMixed();
        ResourceJournal.undo(res, entry);
        IOException e = assertThrows(IOException.class, () -> ResourceJournal.undo(res, ResourceJournal.list(journal).get(0)));
        assertEquals("Already taken back", e.getMessage());
    }

    @Test
    public void aFileYouEditedAfterwardsIsKept() throws Exception {
        ResourceJournal.Entry entry = applyMixed();
        File edited = new File(res, "drawable/vec.xml");
        Files.write(edited.toPath(), (TestFiles.VECTOR + "\n<!-- tweaked -->").getBytes(StandardCharsets.UTF_8));
        ResourceJournal.UndoResult undone = ResourceJournal.undo(res, entry);
        assertTrue("the edited file is still there", edited.isFile());
        assertEquals(List.of("drawable/vec.xml (changed since)"), undone.kept());
        assertFalse(new File(res, "drawable-xhdpi/ic_add.png").exists());
    }

    @Test
    public void aValuesFileYouEditedAfterwardsLosesOnlyTheEntriesAdded() throws Exception {
        ResourceJournal.Entry entry = applyMixed();
        File colors = new File(res, "values/colors.xml");
        String current = TestFiles.read(colors);
        // the person adds their own colour and renames one of the added ones
        String edited = current.replace("</resources>", "    <color name=\"mine\">#00FF00</color>\n</resources>")
                .replace("<color name=\"b\">#222222</color>", "<color name=\"b\">#222223</color>");
        Files.write(colors.toPath(), edited.getBytes(StandardCharsets.UTF_8));

        ResourceJournal.UndoResult undone = ResourceJournal.undo(res, entry);
        String after = TestFiles.read(colors);
        assertTrue("their colour stays", after.contains("<color name=\"mine\">#00FF00</color>"));
        assertTrue("the original stays", after.contains("<color name=\"brand\">#FF5722</color>"));
        assertFalse("the untouched added entry is removed", after.contains("name=\"a\""));
        assertTrue("the entry they changed is kept", after.contains("<color name=\"b\">#222223</color>"));
        assertTrue(undone.kept().toString(), undone.kept().stream().anyMatch(k -> k.contains("b") && k.contains("edited since")));
        assertFalse(ValuesFile.parse(after).isEmpty());
    }

    @Test
    public void aFileThatIsAlreadyGoneIsNotAnError() throws Exception {
        ResourceJournal.Entry entry = applyMixed();
        assertTrue(new File(res, "drawable/vec.xml").delete());
        ResourceJournal.UndoResult undone = ResourceJournal.undo(res, entry);
        assertTrue(undone.kept().isEmpty());
    }

    @Test
    public void anInterruptedOperationIsStillListedAndCanBeTakenBack() throws Exception {
        File dir = ResourceJournal.newDirectory(journal, 1000L);
        byte[] content = TestFiles.VECTOR.getBytes(StandardCharsets.UTF_8);
        TestFiles.write(res, "drawable/half.xml", content);
        ResourceJournal.write(dir, 1000L, ResourceJournal.State.PENDING, List.of(new ResourceJournal.Created("drawable/half.xml", ResourceJournal.hash(content))), List.of());
        List<ResourceJournal.Entry> history = ResourceJournal.list(journal);
        assertEquals(ResourceJournal.State.PENDING, history.get(0).state());
        ResourceJournal.undo(res, history.get(0));
        assertFalse(new File(res, "drawable/half.xml").exists());
    }

    @Test
    public void onlyTheNewestTwentyFinishedOperationsAreKept() throws Exception {
        for (int i = 0; i < 24; i++) {
            ResourceTransaction.Result r = ResourceTransaction.apply(res, journal, List.of(
                    PlannedChange.file("drawable/n" + i, "drawable/n" + i + ".xml", TestFiles.VECTOR.getBytes(StandardCharsets.UTF_8), "t", List.of())), validator);
            assertTrue(r.message(), r.success());
        }
        List<ResourceJournal.Entry> history = ResourceJournal.list(journal);
        assertEquals(20, history.size());
        assertTrue("the newest is kept", new File(res, "drawable/n23.xml").isFile());
    }

    @Test
    public void aDamagedRecordIsSkipped() throws Exception {
        File dir = new File(journal, "1");
        assertTrue(dir.mkdirs());
        Files.write(new File(dir, "journal.txt").toPath(), "garbage".getBytes(StandardCharsets.UTF_8));
        assertTrue(ResourceJournal.list(journal).isEmpty());
        assertTrue(ResourceJournal.list(new File(temp.getRoot(), "none")).isEmpty());
    }

    @Test
    public void undoNeverTouchesFilesOutsideRes() throws Exception {
        File dir = ResourceJournal.newDirectory(journal, 5L);
        File outside = temp.newFile("outside.txt");
        ResourceJournal.write(dir, 5L, ResourceJournal.State.DONE, List.of(new ResourceJournal.Created("../outside.txt", ResourceJournal.hash(Files.readAllBytes(outside.toPath())))), List.of());
        assertThrows(IOException.class, () -> ResourceJournal.undo(res, ResourceJournal.list(journal).get(0)));
        assertTrue(outside.exists());
    }
}
