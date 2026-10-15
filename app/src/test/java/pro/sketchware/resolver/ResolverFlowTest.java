package pro.sketchware.resolver;

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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;

import pro.sketchware.resolver.ResolverFlow.Page;
import pro.sketchware.resolver.ResolverSession.Status;

public class ResolverFlowTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** Work waits until the test lets it run, so the steps in between can be looked at. */
    private static final class Manual implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        void runAll() {
            while (!queue.isEmpty()) queue.poll().run();
        }
    }

    private File res;
    private File journal;
    private Manual background;
    private Manual main;
    private int changes;

    private static final String LOG = "/p/a.xml:1: error: resource drawable/ic_add (aka x:drawable/ic_add) not found.\n"
            + "/p/a.xml:2: error: resource drawable/ic_search (aka x:drawable/ic_search) not found.\n"
            + "/p/a.xml:3: error: resource color/brand (aka x:color/brand) not found.\n"
            + "/p/a.xml:4: error: resource android:drawable/nope not found.\n";

    @Before
    public void setUp() throws Exception {
        res = temp.newFolder("res");
        journal = new File(temp.getRoot(), "journal");
        TestFiles.write(res, "drawable/ic_search_24dp.xml", TestFiles.VECTOR);
        background = new Manual();
        main = new Manual();
    }

    private ResolverFlow flow(ResourceValidator validator) throws Exception {
        File pack = temp.newFolder();
        TestFiles.write(pack, "svg/add/baseline.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path d=\"M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z\"/></svg>");
        ResolverSession session = new ResolverSession(MissingResourceParser.parse(LOG), ResourceInventory.scan(res, null), new PackIconLibrary(new File(pack, "svg")),
                validator, res, journal, "#FF000000");
        ResolverFlow flow = new ResolverFlow(session, background, main);
        flow.setListener(() -> changes++);
        return flow;
    }

    private static MissingResource problem(ResolverFlow flow, String key) {
        return flow.session().problems().stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
    }

    private static final class Refuse implements ResourceValidator {
        @Override
        public Result validateFile(String relativePath, byte[] content) {
            return Result.fail("refused " + relativePath);
        }

        @Override
        public Result validateValues(String relativePath, String xml) {
            return Result.fail("refused " + relativePath);
        }
    }

    @Test
    public void startsOnTheListAndAReviewWithNothingChosenStaysThere() throws Exception {
        ResolverFlow flow = flow(new BuiltInValidator());
        assertEquals(Page.LIST, flow.page());
        flow.openReview();
        assertEquals(Page.LIST, flow.page());
        assertTrue(flow.message(), flow.message().startsWith("Choose a file"));
        assertNull(flow.review());
    }

    @Test
    public void fixAllChoosesWhatIsSureAndShowsTheReviewBeforeAnythingIsWritten() throws Exception {
        ResolverFlow flow = flow(new BuiltInValidator());
        flow.fixAll();
        assertEquals(Page.REVIEW, flow.page());
        ResolverFlow.Review review = flow.review();
        assertNotNull(review);
        assertEquals(2, review.changes.size());
        assertEquals("the colour has no sure answer", List.of("color/brand"), review.unresolved.stream().map(MissingResource::key).toList());
        assertEquals(2, flow.selectedCount());
        assertFalse("nothing is on disk yet", new File(res, "drawable/ic_add.xml").exists());
        assertTrue(flow.message().isEmpty());
    }

    @Test
    public void fixAllWithNothingSureSaysSo() throws Exception {
        String log = "/p/a.xml:1: error: resource color/brand (aka x:color/brand) not found.\n";
        ResolverSession session = new ResolverSession(MissingResourceParser.parse(log), ResourceInventory.scan(res, null), null, new BuiltInValidator(), res, journal, null);
        ResolverFlow flow = new ResolverFlow(session, background, main);
        flow.fixAll();
        assertEquals(Page.LIST, flow.page());
        assertTrue(flow.message(), flow.message().contains("without your help"));
    }

    @Test
    public void nothingIsWrittenWhenNoChangeIsTicked() throws Exception {
        ResolverFlow flow = flow(new BuiltInValidator());
        flow.fixAll();
        flow.setChecked(0, false);
        flow.setChecked(1, false);
        assertEquals(0, flow.selectedCount());
        flow.apply();
        assertEquals(Page.REVIEW, flow.page());
        assertEquals("Tick at least one change to apply.", flow.message());
        assertTrue(background.queue.isEmpty());
    }

    @Test
    public void applyWorksInTheBackgroundAndEndsDone() throws Exception {
        ResolverFlow flow = flow(new BuiltInValidator());
        flow.fixAll();
        flow.apply();
        assertEquals("the screen shows progress while the files are written", Page.WORKING, flow.page());
        assertFalse(new File(res, "drawable/ic_add.xml").exists());
        background.runAll();
        assertTrue("the files are written", new File(res, "drawable/ic_add.xml").isFile());
        assertEquals("but the screen only hears of it on its own thread", Page.WORKING, flow.page());
        main.runAll();
        assertEquals(Page.DONE, flow.page());
        assertTrue(flow.result().success());
        assertEquals("2 resources added", flow.result().message());
        assertEquals(Status.APPLIED, flow.session().status(problem(flow, "drawable/ic_add")));
        assertEquals(Status.APPLIED, flow.session().status(problem(flow, "drawable/ic_search")));
        assertEquals(1, flow.session().notApplied().stream().filter(MissingResource::fixable).count());
    }

    @Test
    public void onlyTheTickedChangesAreWritten() throws Exception {
        ResolverFlow flow = flow(new BuiltInValidator());
        flow.fixAll();
        int search = flow.review().changes.get(0).key().equals("drawable/ic_search") ? 0 : 1;
        flow.setChecked(search, false);
        flow.apply();
        background.runAll();
        main.runAll();
        assertEquals(Page.DONE, flow.page());
        assertTrue(new File(res, "drawable/ic_add.xml").isFile());
        assertFalse(new File(res, "values/drawables.xml").exists());
        assertEquals(Status.APPLIED, flow.session().status(problem(flow, "drawable/ic_add")));
        assertEquals(Status.CHOSEN, flow.session().status(problem(flow, "drawable/ic_search")));
    }

    @Test
    public void aFailureChangesNothingAndLeavesAWayBack() throws Exception {
        ResolverFlow flow = flow(new Refuse());
        flow.fixAll();
        flow.apply();
        background.runAll();
        main.runAll();
        assertEquals(Page.FAILED, flow.page());
        assertFalse(flow.result().success());
        assertTrue(flow.result().message(), flow.result().message().contains("refused"));
        assertFalse(new File(res, "drawable/ic_add.xml").exists());
        assertFalse(new File(res, "values/drawables.xml").exists());
        assertEquals("nothing is marked as applied", Status.CHOSEN, flow.session().status(problem(flow, "drawable/ic_add")));
        flow.back();
        assertEquals(Page.REVIEW, flow.page());
        flow.back();
        assertEquals(Page.LIST, flow.page());
    }

    @Test
    public void theLastOperationCanBeTakenBack() throws Exception {
        ResolverFlow flow = flow(new BuiltInValidator());
        flow.fixAll();
        flow.apply();
        background.runAll();
        main.runAll();
        assertEquals(Page.DONE, flow.page());

        flow.undoLast();
        background.runAll();
        main.runAll();
        assertEquals(Page.LIST, flow.page());
        assertEquals("The changes were taken back.", flow.message());
        assertFalse(new File(res, "drawable/ic_add.xml").exists());
        assertFalse(new File(res, "values/drawables.xml").exists());
        assertEquals("the resources can be chosen again", Status.SUGGESTED, flow.session().status(problem(flow, "drawable/ic_add")));
        assertNotNull(flow.undone());
    }

    @Test
    public void takingBackKeepsWhatYouChangedAndSaysSo() throws Exception {
        ResolverFlow flow = flow(new BuiltInValidator());
        flow.fixAll();
        flow.apply();
        background.runAll();
        main.runAll();
        Files.write(new File(res, "drawable/ic_add.xml").toPath(), (TestFiles.VECTOR + "<!-- mine -->").getBytes(StandardCharsets.UTF_8));
        flow.undoLast();
        background.runAll();
        main.runAll();
        assertTrue(new File(res, "drawable/ic_add.xml").isFile());
        assertTrue(flow.message(), flow.message().contains("except 1 that you changed"));
    }

    @Test
    public void anEarlierOperationCanBeTakenBackFromTheHistory() throws Exception {
        ResolverFlow flow = flow(new BuiltInValidator());
        flow.fixAll();
        flow.apply();
        background.runAll();
        main.runAll();
        ResourceJournal.Entry entry = flow.session().history().get(0);
        boolean[] done = {false};
        flow.undo(entry, () -> done[0] = true);
        background.runAll();
        main.runAll();
        assertTrue(done[0]);
        assertFalse(new File(res, "drawable/ic_add.xml").exists());
        assertEquals(Status.SUGGESTED, flow.session().status(problem(flow, "drawable/ic_add")));
        // a second time is refused with a reason, not a crash
        flow.undo(flow.session().history().get(0), () -> {
        });
        background.runAll();
        main.runAll();
        assertTrue(flow.message(), flow.message().startsWith("Couldn't take the changes back"));
    }

    @Test
    public void everyStepTellsTheScreen() throws Exception {
        ResolverFlow flow = flow(new BuiltInValidator());
        int before = changes;
        flow.fixAll();
        flow.apply();
        assertTrue(changes >= before + 2);
    }

    @Test
    public void keysAreDerivedFromPathsAndValueLines() {
        assertEquals("drawable/ic_add", ResolverFlow.keyOf("drawable-xhdpi/ic_add.png"));
        assertEquals("mipmap/ic_launcher", ResolverFlow.keyOf("mipmap-anydpi-v26/ic_launcher.xml"));
        assertEquals("drawable/logo", ResolverFlow.keyOf("drawable/logo.9.png"));
        assertEquals("color/a", ResolverFlow.keyOfValueLine("    <color name=\"a\">#111</color>\n"));
        assertEquals("id/main", ResolverFlow.keyOfValueLine("    <item name=\"main\" type=\"id\"></item>\n"));
        assertEquals("", ResolverFlow.keyOfValueLine("garbage"));
    }
}
