package pro.sketchware.logic.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.BlockBean;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class SyncSessionTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File folder;
    private long now;

    @Before
    public void setUp() throws Exception {
        folder = temp.newFolder("sync");
        now = 1_000;
    }

    private SyncSession open() {
        return new SyncSession(new SyncStore(folder, "MainActivity_onCreate"), SyncFixtures.converter(), SyncFixtures::generate, () -> now++);
    }

    private static ArrayList<BlockBean> blocksOf(String java) {
        JavaToBlocks.Result result = SyncFixtures.converter().convert(java, List.of());
        assertTrue(result.ok());
        return result.blocks();
    }

    private static String javaOf(List<BlockBean> blocks) {
        return blocks.isEmpty() ? "" : SyncFixtures.generate(new ArrayList<>(blocks)).replace("\r\n", "\n");
    }

    // ---- preview

    @Test
    public void thePreviewSaysWhatWouldChangeAndChangesNothing() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        String before = BlockJson.hash(current);
        SyncSession.Preview preview = open().prepare("counter++;\nname = \"x\";", current);
        assertTrue(preview.ok);
        assertFalse(preview.isInSync());
        assertEquals(1, preview.changes.added() - 0 >= 1 ? 1 : 0);
        assertTrue(preview.changes.added() >= 1);
        assertEquals(1, preview.diff.added());
        assertEquals(0, preview.diff.removed());
        assertEquals("counter++;", preview.currentJava);
        assertTrue(preview.newJava.contains("name = \"x\";"));
        assertEquals("the event's blocks were not touched", before, BlockJson.hash(current));
    }

    @Test
    public void javaThatMatchesTheBlocksIsInSync() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        SyncSession.Preview preview = open().prepare("  counter++;  // same\n", current);
        assertTrue(preview.ok);
        assertEquals("only a comment differs, which becomes a block", 1, preview.changes.added());
        SyncSession.Preview same = open().prepare("counter  ++ ;", current);
        assertTrue(same.isInSync());
        assertTrue(same.diff.isEmpty());
    }

    @Test
    public void aPreviewOfInvalidJavaCannotBeApplied() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        SyncSession session = open();
        SyncSession.Preview preview = session.prepare("counter++", current);
        assertFalse(preview.ok);
        assertFalse(preview.problems.isEmpty());
        SyncSession.Outcome outcome = session.apply(preview, current);
        assertEquals(SyncSession.Outcome.Status.REFUSED, outcome.status);
        assertNull(outcome.blocks);
        assertFalse(session.history().canUndo());
    }

    @Test
    public void whatIsKeptAsTextIsListedInThePreview() {
        SyncSession.Preview preview = open().prepare("int local = 1;\ncounter++;\nhelper();", new ArrayList<>());
        assertEquals(2, preview.sources.size());
        assertEquals("int local = 1;", preview.sources.get(0).code());
    }

    // ---- applying

    @Test
    public void applyingGivesTheNewBlocksAndKeepsTheStepToTakeBack() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        SyncSession session = open();
        SyncSession.Preview preview = session.prepare("counter++;\ncounter--;", current);
        SyncSession.Outcome outcome = session.apply(preview, current);
        assertEquals(SyncSession.Outcome.Status.DONE, outcome.status);
        assertEquals("counter++;\ncounter--;", outcome.java);
        assertEquals(2, outcome.blocks.size());
        assertTrue(session.history().canUndo());
    }

    @Test
    public void applyingRefusesWhenTheBlocksChangedSinceTheReview() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        SyncSession session = open();
        SyncSession.Preview preview = session.prepare("counter--;", current);
        ArrayList<BlockBean> changedMeanwhile = blocksOf("counter++;\nname = \"x\";");
        SyncSession.Outcome outcome = session.apply(preview, changedMeanwhile);
        assertEquals(SyncSession.Outcome.Status.BLOCKS_CHANGED, outcome.status);
        assertNull(outcome.blocks);
        assertFalse("nothing was recorded", session.history().canUndo());
    }

    @Test
    public void undoRestoresTheBlocksExactlyAndRedoDoesItAgain() {
        ArrayList<BlockBean> original = blocksOf("counter++;\nif (flag) {\n  name = \"a\";\n}");
        SyncSession session = open();
        SyncSession.Outcome applied = session.apply(session.prepare("counter--;\nif (flag) {\n  name = \"b\";\n}\nname = \"c\";", original), original);

        SyncSession.Outcome undone = session.undo(applied.blocks, false);
        assertEquals(SyncSession.Outcome.Status.DONE, undone.status);
        assertEquals(BlockJson.hash(original), BlockJson.hash(undone.blocks));
        assertEquals(javaOf(original), undone.java);
        assertFalse(session.history().canUndo());

        SyncSession.Outcome redone = session.redo(undone.blocks, false);
        assertEquals(SyncSession.Outcome.Status.DONE, redone.status);
        assertEquals(BlockJson.hash(applied.blocks), BlockJson.hash(redone.blocks));
        assertEquals(applied.java, redone.java);
        assertEquals(SyncSession.Outcome.Status.NOTHING, session.redo(redone.blocks, false).status);
    }

    @Test
    public void undoDoesNotSilentlyDiscardBlocksEditedByHand() {
        ArrayList<BlockBean> original = blocksOf("counter++;");
        SyncSession session = open();
        SyncSession.Outcome applied = session.apply(session.prepare("counter--;", original), original);
        ArrayList<BlockBean> edited = blocksOf("counter--;\nname = \"by hand\";");

        SyncSession.Outcome refused = session.undo(edited, false);
        assertEquals(SyncSession.Outcome.Status.BLOCKS_CHANGED, refused.status);
        assertTrue("still undoable", session.history().canUndo());

        SyncSession.Outcome forced = session.undo(edited, true);
        assertEquals(SyncSession.Outcome.Status.DONE, forced.status);
        assertEquals(BlockJson.hash(original), BlockJson.hash(forced.blocks));
        assertNotNull(applied);
    }

    @Test
    public void aColourAddedByTheEditorDoesNotCountAsAChange() {
        ArrayList<BlockBean> original = blocksOf("counter++;");
        SyncSession session = open();
        SyncSession.Outcome applied = session.apply(session.prepare("counter--;", original), original);
        ArrayList<BlockBean> saved = BlockJson.copyOf(applied.blocks);
        for (BlockBean bean : saved) bean.color = 0xff112233; // the editor fills this in when it saves
        assertEquals(SyncSession.Outcome.Status.DONE, session.undo(saved, false).status);
    }

    @Test
    public void aNewStepDropsWhatCouldHaveBeenRedone() {
        ArrayList<BlockBean> a = blocksOf("counter++;");
        SyncSession session = open();
        SyncSession.Outcome b = session.apply(session.prepare("counter--;", a), a);
        SyncSession.Outcome undone = session.undo(b.blocks, false);
        assertTrue(session.history().canRedo());
        session.apply(session.prepare("name = \"z\";", undone.blocks), undone.blocks);
        assertFalse(session.history().canRedo());
    }

    @Test
    public void onlyTheLatestStepsAreKept() {
        ArrayList<BlockBean> blocks = new ArrayList<>();
        SyncSession session = open();
        for (int i = 0; i < SyncHistory.MAX_ENTRIES + 5; i++) {
            SyncSession.Outcome outcome = session.apply(session.prepare("counter = " + (i + 1) + ";", blocks), blocks);
            blocks = outcome.blocks;
        }
        assertEquals(SyncHistory.MAX_ENTRIES, session.history().size());
    }

    // ---- persistence

    @Test
    public void historySurvivesClosingTheEditor() {
        ArrayList<BlockBean> original = blocksOf("counter++;");
        SyncSession first = open();
        SyncSession.Outcome applied = first.apply(first.prepare("counter--;\nname = \"x\";", original), original);
        first.dropDraft();

        SyncSession reopened = open();
        assertTrue(reopened.history().canUndo());
        SyncSession.Outcome undone = reopened.undo(applied.blocks, false);
        assertEquals(SyncSession.Outcome.Status.DONE, undone.status);
        assertEquals(BlockJson.hash(original), BlockJson.hash(undone.blocks));
        assertTrue(open().history().canRedo());
    }

    @Test
    public void theJavaNotAppliedYetSurvivesClosingTheEditor() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        SyncSession first = open();
        first.keepDraft("counter++;\nname = \"half done\"", current);

        SyncSession reopened = open();
        SyncStore.Draft draft = reopened.draft();
        assertNotNull(draft);
        assertEquals("counter++;\nname = \"half done\"", draft.text());
        assertTrue(reopened.draftMatches(draft, current));
        assertFalse("the blocks changed since: the draft must be offered, not forced", reopened.draftMatches(draft, blocksOf("counter--;")));
    }

    @Test
    public void aDraftEqualToWhatTheBlocksMakeIsNotKept() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        SyncSession session = open();
        session.keepDraft("counter++;\nname = \"x\"", current);
        assertNotNull(session.draft());
        session.keepDraft(javaOf(current), current);
        assertNull(session.draft());
    }

    @Test
    public void theDraftIsOnlyDroppedOnceTheCallerHasStoredTheNewBlocks() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        SyncSession session = open();
        session.keepDraft("counter--;", current);
        assertNotNull(session.draft());
        session.apply(session.prepare("counter--;", current), current);
        assertNotNull("a crash now must not lose what was typed", open().draft());
        assertTrue(open().history().canUndo());
        session.dropDraft();
        assertNull(open().draft());
        assertTrue("dropping the draft keeps the history", open().history().canUndo());
    }

    @Test
    public void aConverterForTheScreenAsItIsNowCanBeUsedForThePreview() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        ProjectContext now = SyncFixtures.context();
        java.util.Map<String, String> variables = new java.util.LinkedHashMap<>(now.variables());
        variables.put("brandNew", "d");
        ProjectContext changed = new ProjectContext(now.activityName(), now.fragment(), now.viewBinding(), variables, now.lists(), now.views(),
                now.components(), now.moreBlocks(), now.args());
        SyncSession session = open();
        SyncSession.Preview old = session.prepare("brandNew = 1;", current);
        assertEquals("addSourceDirectly", old.blocks.get(0).opCode);
        SyncSession.Preview fresh = session.prepare("brandNew = 1;", current, SyncFixtures.converter().withContext(changed));
        assertEquals("setVarInt", fresh.blocks.get(0).opCode);
    }

    @Test
    public void eventsKeepTheirOwnFiles() throws Exception {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        new SyncSession(new SyncStore(folder, "MainActivity_onCreate"), SyncFixtures.converter(), SyncFixtures::generate).keepDraft("a();", current);
        SyncSession other = new SyncSession(new SyncStore(folder, "MainActivity_button1_onClick"), SyncFixtures.converter(), SyncFixtures::generate);
        assertNull(other.draft());
        assertEquals(1, folder.list().length);
    }

    @Test
    public void aDamagedFileIsIgnoredNotTrusted() throws Exception {
        File file = new File(folder, "MainActivity_onCreate.json");
        Files.writeString(file.toPath(), "{ this is not json");
        SyncSession session = open();
        assertNull(session.draft());
        assertFalse(session.history().canUndo());
        Files.writeString(file.toPath(), "{\"history\":\"{\\\"entries\\\":[{\\\"time\\\":1}],\\\"cursor\\\":1}\"}");
        assertFalse("an entry without its blocks is not a step", open().history().canUndo());
    }

    @Test
    public void noHalfWrittenFileIsLeftBehind() {
        ArrayList<BlockBean> current = blocksOf("counter++;");
        open().keepDraft("x();", current);
        for (String name : folder.list()) assertFalse(name, name.endsWith(".tmp"));
    }

    @Test
    public void unusableNamesAreMadeSafeForAFile() {
        SyncStore store = new SyncStore(folder, "../../etc/pass wd:*");
        ArrayList<BlockBean> current = blocksOf("counter++;");
        new SyncSession(store, SyncFixtures.converter(), SyncFixtures::generate).keepDraft("x();", current);
        File[] files = folder.listFiles();
        assertEquals(1, files.length);
        assertTrue(files[0].getName(), files[0].getName().matches("[A-Za-z0-9._-]+\\.json"));
        assertEquals(folder, files[0].getParentFile());
    }
}
