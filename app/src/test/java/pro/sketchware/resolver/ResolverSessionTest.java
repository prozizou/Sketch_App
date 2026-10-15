package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import pro.sketchware.resolver.ResolverSession.Status;

public class ResolverSessionTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private File res;
    private File images;
    private File journal;
    private File library;

    private static final String LAYOUT = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:layout_width=\"match_parent\"\n"
            + "    android:layout_height=\"match_parent\"\n"
            + "    android:background=\"@color/missing_bg\">\n"
            + "    <ImageView\n        android:layout_width=\"wrap_content\"\n        android:layout_height=\"wrap_content\"\n        android:src=\"@drawable/ic_add\" />\n"
            + "    <ImageView\n        android:layout_width=\"wrap_content\"\n        android:layout_height=\"wrap_content\"\n        android:src=\"@drawable/ic_search\" />\n"
            + "    <ImageView\n        android:layout_width=\"wrap_content\"\n        android:layout_height=\"wrap_content\"\n        android:src=\"@drawable/ic_photo\" />\n"
            + "    <TextView\n        android:layout_width=\"wrap_content\"\n        android:layout_height=\"wrap_content\"\n        android:text=\"@string/hello_missing\"\n        android:textSize=\"@dimen/missing_size\" />\n"
            + "</LinearLayout>\n";

    @Before
    public void setUp() throws Exception {
        res = temp.newFolder("res");
        images = temp.newFolder("images");
        journal = new File(temp.getRoot(), "journal");
        File pack = temp.newFolder("pack");
        TestFiles.write(pack, "svg/add/baseline.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path d=\"M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z\"/></svg>");
        library = new File(pack, "svg");
        TestFiles.write(res, "layout/main.xml", LAYOUT);
        TestFiles.write(res, "drawable/ic_search_24dp.xml", TestFiles.VECTOR);
    }

    private ResolverSession session(String log) {
        return new ResolverSession(MissingResourceParser.parse(log), ResourceInventory.scan(res, images), new PackIconLibrary(library),
                new BuiltInValidator(), res, journal, "#FF000000");
    }

    private static String aaptLog() {
        return "/p/res/layout/main.xml:5: error: resource color/missing_bg (aka com.my.app:color/missing_bg) not found.\n"
                + "/p/res/layout/main.xml:7: error: resource drawable/ic_add (aka com.my.app:drawable/ic_add) not found.\n"
                + "/p/res/layout/main.xml:11: error: resource drawable/ic_search (aka com.my.app:drawable/ic_search) not found.\n"
                + "/p/res/layout/main.xml:15: error: resource drawable/ic_photo (aka com.my.app:drawable/ic_photo) not found.\n"
                + "/p/res/layout/main.xml:19: error: resource string/hello_missing (aka com.my.app:string/hello_missing) not found.\n"
                + "/p/res/layout/main.xml:20: error: resource dimen/missing_size (aka com.my.app:dimen/missing_size) not found.\n"
                + "/p/res/layout/main.xml:21: error: resource android:drawable/nope not found.\n";
    }

    private static MissingResource problem(ResolverSession session, String key) {
        return session.problems().stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    public void statusesSeparateWhatIsSureWhatNeedsInputAndWhatCannotBeFixed() {
        ResolverSession session = session(aaptLog());
        assertEquals(7, session.problems().size());
        assertEquals(6, session.fixable().size());
        assertEquals("a library icon of the same name", Status.SUGGESTED, session.status(problem(session, "drawable/ic_add")));
        assertEquals("a project drawable whose name differs only by a size", Status.SUGGESTED, session.status(problem(session, "drawable/ic_search")));
        assertEquals(Status.NEEDS_INPUT, session.status(problem(session, "drawable/ic_photo")));
        assertEquals(Status.NEEDS_INPUT, session.status(problem(session, "color/missing_bg")));
        assertEquals(Status.NEEDS_INPUT, session.status(problem(session, "string/hello_missing")));
        assertEquals(Status.UNFIXABLE, session.status(problem(session, "drawable/nope")));
    }

    @Test
    public void projectResourcesAreOfferedBeforeLibraryIcons() {
        ResolverSession session = session(aaptLog());
        List<Suggestion> search = session.suggestions(problem(session, "drawable/ic_search"));
        assertTrue(search.get(0).existing());
        assertEquals("ic_search_24dp", search.get(0).source().name());
        List<Suggestion> add = session.suggestions(problem(session, "drawable/ic_add"));
        assertEquals(Suggestion.Kind.MATERIAL_ICON, add.get(0).kind());
    }

    @Test
    public void fixAllTakesTheSureOnesAndLeavesTheRestToThePerson() {
        ResolverSession session = session(aaptLog());
        ResolverSession.FixAllSummary summary = session.fixAll();
        assertEquals(2, summary.selected());
        assertEquals(Set.of("color/missing_bg", "drawable/ic_photo", "string/hello_missing", "dimen/missing_size"),
                new TreeSet<>(summary.needInput().stream().map(MissingResource::key).toList()));
        assertEquals(4, summary.needInput().size());
        assertEquals(1, summary.unfixable().size());
        assertEquals(Status.CHOSEN, session.status(problem(session, "drawable/ic_add")));
        PlanBuilder.Plan plan = session.plan();
        assertEquals(2, plan.changes().size());
        assertTrue(plan.conflicts().toString(), plan.conflicts().isEmpty());
        assertEquals("nothing is made up for what has no sure answer", Set.of("drawable/ic_add", "drawable/ic_search"),
                new TreeSet<>(plan.changes().stream().map(PlannedChange::key).toList()));
    }

    @Test
    public void fixAllNeverReplacesAChoiceAlreadyMade() {
        ResolverSession session = session(aaptLog());
        MissingResource add = problem(session, "drawable/ic_add");
        FileInspector.Inspection png = FileInspector.inspect("drawable", TestFiles.png(30, 30), null);
        session.choose(add, new Resolution.Imported("mine.png", png, null));
        ResolverSession.FixAllSummary summary = session.fixAll();
        assertEquals("only ic_search was left to choose", 1, summary.selected());
        assertTrue(session.choice(add) instanceof Resolution.Imported);
        session.clear(add);
        assertNull(session.choice(add));
        assertEquals(Status.SUGGESTED, session.status(add));
    }

    @Test
    public void aChoiceForAnotherLogIsRefused() {
        ResolverSession session = session(aaptLog());
        MissingResource stranger = MissingResourceParser.parse("error: resource color/other not found.").get(0);
        try {
            session.choose(stranger, new Resolution.Typed("#000000"));
            throw new AssertionError("expected a refusal");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("color/other"));
        }
    }

    @Test
    public void severalResolutionsAreAppliedTogetherAndCanBeTakenBackTogether() throws Exception {
        ResolverSession session = session(aaptLog());
        session.fixAll();
        session.choose(problem(session, "color/missing_bg"), new Resolution.Typed("#FF5722"));
        session.choose(problem(session, "string/hello_missing"), new Resolution.Typed("Hello"));
        session.choose(problem(session, "dimen/missing_size"), new Resolution.Typed("18sp"));
        session.choose(problem(session, "drawable/ic_photo"), new Resolution.Imported("photo.png", FileInspector.inspect("drawable", TestFiles.png(64, 64), null), null));

        PlanBuilder.Plan plan = session.plan();
        assertTrue(plan.conflicts().toString(), plan.conflicts().isEmpty());
        assertEquals(6, plan.changes().size());
        Set<String> before = tree();

        ResourceTransaction.Result result = session.apply(plan.changes());
        assertTrue(result.message(), result.success());
        assertEquals("6 resources added", result.message());
        assertTrue(new File(res, "drawable/ic_add.xml").isFile());
        assertTrue(new File(res, "drawable-xhdpi/ic_photo.png").isFile());
        String drawables = TestFiles.read(new File(res, "values/drawables.xml"));
        assertTrue(drawables, drawables.contains("<drawable name=\"ic_search\">@drawable/ic_search_24dp</drawable>"));
        assertTrue(TestFiles.read(new File(res, "values/colors.xml")).contains("<color name=\"missing_bg\">#FF5722</color>"));
        assertTrue(TestFiles.read(new File(res, "values/dimens.xml")).contains("<dimen name=\"missing_size\">18sp</dimen>"));

        // the session was built before; a new look at the project shows nothing is missing any more
        ResourceInventory after = ResourceInventory.scan(res, images);
        for (MissingResource fixed : session.fixable()) assertFalse(fixed.key(), after.find(fixed.type(), fixed.name()).isEmpty());

        assertEquals(1, session.history().size());
        ResourceJournal.UndoResult undone = session.undo(session.history().get(0));
        assertTrue(undone.kept().toString(), undone.kept().isEmpty());
        assertEquals("the project is exactly as it was", before, tree());
    }

    @Test
    public void aConflictInOneChoiceDoesNotBlockTheOthers() {
        ResolverSession session = session(aaptLog());
        session.fixAll();
        // ic_search_24dp exists; asking for a file called ic_search_24dp is a clash
        MissingResource search = problem(session, "drawable/ic_search");
        session.choose(search, new Resolution.Imported("x.xml", FileInspector.inspect("drawable", TestFiles.VECTOR.getBytes(StandardCharsets.UTF_8), null), null));
        PlanBuilder.Plan plan = session.plan();
        assertEquals("ic_search is free, so its own file is fine", 2, plan.changes().size());

        MissingResource add = problem(session, "drawable/ic_add");
        session.choose(add, new Resolution.Typed("#000000"));
        PlanBuilder.Plan mixed = session.plan();
        assertEquals(1, mixed.changes().size());
        assertEquals(1, mixed.conflicts().size());
        assertEquals("drawable/ic_add", mixed.conflicts().get(0).key());
    }

    @Test
    public void aPickedFileIsCheckedBeforeItIsChosen() {
        ResolverSession session = session(aaptLog());
        MissingResource photo = problem(session, "drawable/ic_photo");
        ResolverSession.ImportOutcome fake = session.importFile(photo, "photo.png", "not an image".getBytes(StandardCharsets.UTF_8));
        assertFalse(fake.ok());
        assertTrue(fake.message(), fake.message().contains("isn't a PNG"));
        assertNull("a refused file is never chosen", session.choice(photo));

        ResolverSession.ImportOutcome good = session.importFile(photo, "photo.png", TestFiles.png(64, 64));
        assertTrue(good.message(), good.ok());
        assertTrue(session.choice(photo) instanceof Resolution.Imported);
        assertEquals(Status.CHOSEN, session.status(photo));

        MissingResource color = problem(session, "color/missing_bg");
        assertFalse("a file can't stand for a colour", session.importFile(color, "c.png", TestFiles.png(2, 2)).ok());
        MissingResource framework = problem(session, "drawable/nope");
        assertFalse(session.importFile(framework, "n.png", TestFiles.png(2, 2)).ok());
    }

    @Test
    public void aPickedFileThatClashesIsNotChosen() throws Exception {
        TestFiles.write(res, "drawable-xhdpi/ic_photo.png", TestFiles.png(3, 3));
        ResolverSession session = session(aaptLog());
        MissingResource photo = problem(session, "drawable/ic_photo");
        ResolverSession.ImportOutcome clash = session.importFile(photo, "photo.png", TestFiles.png(64, 64));
        assertFalse(clash.ok());
        assertTrue(clash.message(), clash.message().contains("already exists"));
        assertNull(session.choice(photo));
    }

    @Test
    public void anSvgIsConvertedWhenItIsPicked() {
        ResolverSession session = session(aaptLog());
        MissingResource photo = problem(session, "drawable/ic_photo");
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"><path fill=\"currentColor\" d=\"M1 1h4v4z\"/></svg>";
        assertTrue(session.importFile(photo, "photo.svg", svg.getBytes(StandardCharsets.UTF_8)).ok());
        PlannedChange change = session.plan().changes().stream().filter(c -> c.key().equals("drawable/ic_photo")).findFirst().orElseThrow();
        assertEquals("drawable/ic_photo.xml", change.path());
        assertTrue(new String(change.content(), StandardCharsets.UTF_8).contains("<vector"));
    }

    @Test
    public void severalFilesAreMatchedToTheResourcesTheyAreNamedAfter() {
        String log = "/p/a.xml:1: error: resource drawable/ic_add (aka x:drawable/ic_add) not found.\n"
                + "/p/a.xml:2: error: resource drawable/ic_back (aka x:drawable/ic_back) not found.\n"
                + "/p/a.xml:3: error: resource mipmap/ic_launcher (aka x:mipmap/ic_launcher) not found.\n"
                + "/p/a.xml:4: error: resource color/brand (aka x:color/brand) not found.\n";
        ResolverSession session = session(log);
        ResolverSession.BatchResult result = session.importFiles(List.of(
                new ResolverSession.NamedFile("ic_add.png", TestFiles.png(48, 48)),
                new ResolverSession.NamedFile("back_black_24dp.png", TestFiles.png(48, 48)),
                new ResolverSession.NamedFile("ic_launcher.png", TestFiles.png(192, 192)),
                new ResolverSession.NamedFile("brand.png", TestFiles.png(8, 8)),
                new ResolverSession.NamedFile("unrelated.png", TestFiles.png(8, 8)),
                new ResolverSession.NamedFile("ic_add.xml", "oops".getBytes(StandardCharsets.UTF_8))));
        assertEquals(List.of("ic_add.png \u2192 drawable/ic_add", "back_black_24dp.png \u2192 drawable/ic_back", "ic_launcher.png \u2192 mipmap/ic_launcher"), result.matched());
        assertEquals("a colour is not a file, and nothing matches unrelated", List.of("brand.png", "unrelated.png"), result.unmatched());
        assertEquals(1, result.refused().size());
        assertTrue(result.refused().get(0), result.refused().get(0).startsWith("ic_add.xml: "));
        PlanBuilder.Plan plan = session.plan();
        assertEquals(java.util.Set.of("drawable-xhdpi/ic_add.png", "drawable-xhdpi/ic_back.png", "mipmap-xxxhdpi/ic_launcher.png"),
                new TreeSet<>(plan.changes().stream().map(PlannedChange::path).toList()));
    }

    @Test
    public void aFileThatFitsTwoResourcesIsNotGuessed() {
        String log = "/p/a.xml:1: error: resource drawable/ic_add_24dp (aka x:drawable/ic_add_24dp) not found.\n"
                + "/p/a.xml:2: error: resource drawable/ic_add_white (aka x:drawable/ic_add_white) not found.\n";
        ResolverSession.BatchResult result = session(log).importFiles(List.of(new ResolverSession.NamedFile("ic_add.png", TestFiles.png(8, 8))));
        assertTrue(result.matched().isEmpty());
        assertEquals(List.of("ic_add.png"), result.unmatched());
    }

    @Test
    public void aTypedValueCanBeCheckedBeforeItIsChosen() {
        ResolverSession session = session(aaptLog());
        MissingResource color = problem(session, "color/missing_bg");
        assertNull(session.check(color, new Resolution.Typed("#FF5722")));
        Conflict bad = session.check(color, new Resolution.Typed("red"));
        assertEquals(Conflict.Reason.INVALID, bad.reason());
        assertNull("checking never chooses", session.choice(color));
    }

    private Set<String> tree() throws Exception {
        Set<String> paths = new TreeSet<>();
        try (var walk = java.nio.file.Files.walk(res.toPath())) {
            walk.filter(java.nio.file.Files::isRegularFile).forEach(p -> paths.add(res.toPath().relativize(p).toString()));
        }
        return paths;
    }

    // ---- the whole loop against the real AAPT2: build, read the log, fix, build again ----

    @Test
    public void theRealBuildLoopEndsWithAProjectThatLinks() throws Exception {
        File aapt2 = Aapt2Locator.find();
        File androidJar = Aapt2Locator.androidJar();
        assumeTrue("needs the Android SDK's aapt2 and android.jar", aapt2 != null && androidJar != null);
        File manifest = Aapt2Build.manifest(temp.newFolder("manifest"),
                "<application android:icon=\"@mipmap/ic_launcher\" android:label=\"@string/app_name\" />");
        // the layout's own references are fixed too, but the manifest's come first in AAPT2's order of checking
        File work = temp.newFolder("work");
        ResourceValidator validator = CompositeValidator.standard(new Aapt2Validator(aapt2, temp.newFolder("scratch")));

        Set<String> original = tree();
        String firstLog = Aapt2Build.run(aapt2, androidJar, res, manifest, work);
        assertFalse("the project starts broken", firstLog.isEmpty());

        List<String> stages = new ArrayList<>();
        List<ResourceJournal.Entry> operations = new ArrayList<>();
        String log = firstLog;
        for (int round = 1; round <= 6 && !log.isEmpty(); round++) {
            List<MissingResource> found = MissingResourceParser.parse(log);
            assertFalse("round " + round + " must be about missing resources, not something else:\n" + log, found.isEmpty());
            stages.add(found.stream().map(MissingResource::key).toList().toString());
            ResolverSession session = new ResolverSession(found, ResourceInventory.scan(res, images), new PackIconLibrary(library), validator, res, journal, "#FF000000");
            session.fixAll();
            for (MissingResource problem : session.fixable()) {
                if (session.choice(problem) != null) continue;
                switch (problem.type()) {
                    case "color" -> session.choose(problem, new Resolution.Typed("#FF5722"));
                    case "string" -> session.choose(problem, new Resolution.Typed("My app"));
                    case "dimen" -> session.choose(problem, new Resolution.Typed("16sp"));
                    case "mipmap" -> session.choose(problem, new Resolution.Imported("launcher.png", FileInspector.inspect("mipmap", TestFiles.png(96, 96), null), null));
                    case "drawable" -> session.choose(problem, new Resolution.Imported("photo.png", FileInspector.inspect("drawable", TestFiles.png(64, 64), null), null));
                    default -> throw new AssertionError("unexpected " + problem);
                }
            }
            PlanBuilder.Plan plan = session.plan();
            assertTrue(plan.conflicts().toString(), plan.conflicts().isEmpty());
            ResourceTransaction.Result result = session.apply(plan.changes());
            assertTrue("round " + round + ": " + result.message() + " " + result.failures(), result.success());
            operations.add(result.journal());
            log = Aapt2Build.run(aapt2, androidJar, res, manifest, work);
        }
        assertEquals("the final build links:\n" + log, "", log);
        assertTrue("AAPT2 reports in stages, so more than one round was needed: " + stages, stages.size() >= 2);

        // and everything the resolver did can be taken back, bringing the first errors back
        List<ResourceJournal.Entry> newestFirst = new ResolverSession(List.of(), ResourceInventory.scan(res, images), null, validator, res, journal, null).history();
        assertEquals(operations.size(), newestFirst.size());
        for (ResourceJournal.Entry entry : newestFirst) {
            ResourceJournal.UndoResult undone = ResourceJournal.undo(res, entry);
            assertTrue(undone.kept().toString(), undone.kept().isEmpty());
        }
        assertEquals(original, tree());
        assertEquals("the same errors as before any fix", firstLog, Aapt2Build.run(aapt2, androidJar, res, manifest, work));
    }

    @Test
    public void aTypedColourThatAaptWouldRefuseNeverGetsWritten() throws Exception {
        File aapt2 = Aapt2Locator.find();
        assumeTrue(aapt2 != null);
        ResourceValidator validator = new CompositeValidator(new BuiltInValidator(), new Aapt2Validator(aapt2, temp.newFolder("scratch")));
        ResolverSession session = new ResolverSession(MissingResourceParser.parse(aaptLog()), ResourceInventory.scan(res, images), null, validator, res, journal, null);
        MissingResource color = problem(session, "color/missing_bg");
        session.choose(color, new Resolution.Typed("#FF5722"));
        // a values file the person already has, which AAPT2 refuses: the resolver must not touch it
        TestFiles.write(res, "values/colors.xml", "<resources><color name=\"oops\">notacolor</color></resources>");
        String before = TestFiles.read(new File(res, "values/colors.xml"));
        PlanBuilder.Plan plan = session.plan();
        ResourceTransaction.Result result = session.apply(plan.changes());
        assertFalse(result.message(), result.success());
        assertNotNull(result.failures());
        assertEquals(before, TestFiles.read(new File(res, "values/colors.xml")));
    }
}
