package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlanBuilderTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static MissingResource missing(String type, String name) {
        return MissingResourceParser.parse("/p/res/layout/a.xml:5: error: resource " + type + "/" + name + " (aka x:" + type + "/" + name + ") not found.").get(0);
    }

    private ResourceInventory project() throws Exception {
        File res = temp.newFolder();
        File images = temp.newFolder();
        TestFiles.write(res, "drawable-night/ic_home.xml", TestFiles.VECTOR);
        TestFiles.write(res, "drawable/ic_exists.xml", TestFiles.VECTOR);
        TestFiles.write(res, "mipmap-xxhdpi/ic_launcher.png", TestFiles.png(144, 144));
        TestFiles.write(res, "drawable-xhdpi/logo.9.png", TestFiles.png(9, 9));
        TestFiles.write(res, "values/colors.xml", "<resources><color name=\"brand\">#FF5722</color></resources>");
        TestFiles.write(res, "values-night/colors.xml", "<resources><color name=\"night_only\">#111111</color></resources>");
        TestFiles.write(res, "values/broken.xml", "<resources><string name=\"x\">oops</resources>");
        TestFiles.write(images, "photo.png", TestFiles.png(2, 2));
        sources = res;
        return ResourceInventory.scan(res, images);
    }

    private File sources;

    private IconLibrary library() throws Exception {
        File root = temp.newFolder();
        TestFiles.write(root, "svg/add/baseline.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path d=\"M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z\"/></svg>");
        TestFiles.write(root, "svg/add/round.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\"><path d=\"M1 1h2v2z\"/></svg>");
        return new PackIconLibrary(new File(root, "svg"));
    }

    private PlanBuilder.Plan plan(Map<MissingResource, Resolution> choices) throws Exception {
        return new PlanBuilder(project(), library(), "#FF336699").build(choices);
    }

    private static Map<MissingResource, Resolution> one(MissingResource m, Resolution r) {
        Map<MissingResource, Resolution> map = new LinkedHashMap<>();
        map.put(m, r);
        return map;
    }

    private Suggestion suggestion(MissingResource m) throws Exception {
        return ResourceMatcher.suggest(m, project(), library()).get(0);
    }

    @Test
    public void copyingAnExistingFileKeepsItsContentAndExtension() throws Exception {
        MissingResource drawable = missing("drawable", "ic_home");
        Suggestion s = suggestion(drawable);
        PlanBuilder.Plan plan = plan(one(drawable, new Resolution.Reuse(s)));
        assertTrue(plan.conflicts().toString(), plan.conflicts().isEmpty());
        PlannedChange change = plan.changes().get(0);
        assertEquals("a night variant is copied to the default folder", "drawable/ic_home.xml", change.path());
        assertEquals(TestFiles.VECTOR, new String(change.content(), StandardCharsets.UTF_8));
        assertEquals(PlannedChange.Kind.FILE, change.kind());
        assertTrue(change.source().startsWith("Copy of drawable-night/ic_home.xml"));
    }

    @Test
    public void aMipmapCopiedToDrawableKeepsItsDensity() throws Exception {
        MissingResource drawable = missing("drawable", "ic_launcher");
        PlannedChange change = plan(one(drawable, new Resolution.Reuse(suggestion(drawable)))).changes().get(0);
        assertEquals("drawable-xxhdpi/ic_launcher.png", change.path());
    }

    @Test
    public void aLibraryIconBecomesAVectorInTheRightFolder() throws Exception {
        MissingResource drawable = missing("drawable", "ic_add");
        PlannedChange vector = plan(one(drawable, new Resolution.Reuse(suggestion(drawable)))).changes().get(0);
        assertEquals("drawable/ic_add.xml", vector.path());
        String xml = new String(vector.content(), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<vector"));
        assertTrue("the chosen icon colour is used", xml.contains("#FF336699"));
        assertEquals("Material icon add (baseline)", vector.source());

        MissingResource mipmap = missing("mipmap", "ic_add");
        assertEquals("mipmap-anydpi/ic_add.xml", plan(one(mipmap, new Resolution.Reuse(suggestion(mipmap)))).changes().get(0).path());
    }

    @Test
    public void anAliasIsAnEntryOfTheValuesFile() throws Exception {
        MissingResource color = missing("color", "brand_color");
        ResourceInventory inventory = project();
        Suggestion alias = new Suggestion(Suggestion.Kind.ALIAS, 0.85, true, inventory.find("color", "brand").get(0), null, null);
        PlanBuilder.Plan plan = new PlanBuilder(inventory, null, null).build(one(color, new Resolution.Reuse(alias)));
        PlannedChange change = plan.changes().get(0);
        assertEquals(PlannedChange.Kind.VALUE, change.kind());
        assertEquals("values/colors.xml", change.path());
        assertEquals(new ValuesFile.Entry("color", "brand_color", "@color/brand"), change.entry());
        assertEquals("Alias of @color/brand", change.source());
    }

    @Test
    public void aValueCopiedFromAnotherQualifierGoesToTheDefaultValues() throws Exception {
        MissingResource color = missing("color", "night_only");
        PlannedChange change = plan(one(color, new Resolution.Reuse(suggestion(color)))).changes().get(0);
        assertEquals(new ValuesFile.Entry("color", "night_only", "#111111"), change.entry());
        assertEquals("values/colors.xml", change.path());
    }

    @Test
    public void aPickedFileGoesToAFolderThatFitsIt() throws Exception {
        MissingResource drawable = missing("drawable", "photo_new");
        FileInspector.Inspection png = FileInspector.inspect("drawable", TestFiles.png(40, 40), null);
        assertEquals("drawable-xhdpi/photo_new.png", plan(one(drawable, new Resolution.Imported("p.png", png, null))).changes().get(0).path());
        assertEquals("a chosen qualifier wins", "drawable-xxhdpi/photo_new.png",
                plan(one(drawable, new Resolution.Imported("p.png", png, "-xxhdpi"))).changes().get(0).path());

        MissingResource launcher = missing("mipmap", "ic_big");
        FileInspector.Inspection icon = FileInspector.inspect("mipmap", TestFiles.png(192, 192), null);
        assertEquals("a launcher-sized icon goes to its own density", "mipmap-xxxhdpi/ic_big.png", plan(one(launcher, new Resolution.Imported("i.png", icon, null))).changes().get(0).path());

        FileInspector.Inspection vector = FileInspector.inspect("drawable", TestFiles.VECTOR.getBytes(StandardCharsets.UTF_8), null);
        assertEquals("drawable/v_icon.xml", plan(one(missing("drawable", "v_icon"), new Resolution.Imported("v.xml", vector, null))).changes().get(0).path());
        assertEquals("mipmap-anydpi/v_icon.xml", plan(one(missing("mipmap", "v_icon"), new Resolution.Imported("v.xml", vector, null))).changes().get(0).path());
    }

    @Test
    public void aRefusedFileNeverReachesThePlan() throws Exception {
        MissingResource drawable = missing("drawable", "bad");
        FileInspector.Inspection bad = FileInspector.inspect("drawable", "not an image".getBytes(StandardCharsets.UTF_8), null);
        PlanBuilder.Plan plan = plan(one(drawable, new Resolution.Imported("x", bad, null)));
        assertTrue(plan.changes().isEmpty());
        assertEquals(Conflict.Reason.REFUSED, plan.conflicts().get(0).reason());
        assertFalse(plan.conflicts().get(0).message().isEmpty());
    }

    @Test
    public void typedValuesAreCheckedForTheirType() throws Exception {
        assertEquals("#FF5722", valueOf(missing("color", "c1"), "  #FF5722 "));
        assertEquals("#80FF5722", valueOf(missing("color", "c2"), "#80FF5722"));
        assertEquals("16dp", valueOf(missing("dimen", "d1"), "16dp"));
        assertEquals("14sp", valueOf(missing("dimen", "d2"), "14sp"));
        assertEquals("12", valueOf(missing("integer", "i1"), "12"));
        assertEquals("true", valueOf(missing("bool", "b1"), "true"));
        assertEquals("Tom &amp; Jerry\\'s", valueOf(missing("string", "s1"), "Tom & Jerry's"));
        assertEquals("a typed string can never become a reference", "\\@string/other", valueOf(missing("string", "s2"), "@string/other"));
        for (String[] bad : new String[][]{{"color", "red"}, {"color", "#12"}, {"dimen", "16"}, {"dimen", "wide"}, {"integer", "1.5"}, {"bool", "yes"}, {"string", "  "}}) {
            PlanBuilder.Plan plan = plan(one(missing(bad[0], "x_" + bad[0]), new Resolution.Typed(bad[1])));
            assertTrue(bad[0] + " " + bad[1], plan.changes().isEmpty());
            assertEquals(Conflict.Reason.INVALID, plan.conflicts().get(0).reason());
        }
    }

    private String valueOf(MissingResource m, String text) throws Exception {
        PlanBuilder.Plan plan = plan(one(m, new Resolution.Typed(text)));
        assertTrue(plan.conflicts().toString(), plan.conflicts().isEmpty());
        return plan.changes().get(0).entry().value();
    }

    @Test
    public void aTypedReferenceMustPointAtSomethingThatExists() throws Exception {
        PlanBuilder.Plan ok = plan(one(missing("color", "accent"), new Resolution.Typed("@color/brand")));
        assertEquals("@color/brand", ok.changes().get(0).entry().value());
        PlanBuilder.Plan unknown = plan(one(missing("color", "accent"), new Resolution.Typed("@color/ghost")));
        assertEquals(Conflict.Reason.SOURCE_MISSING, unknown.conflicts().get(0).reason());
        PlanBuilder.Plan wrongType = plan(one(missing("color", "accent"), new Resolution.Typed("@string/x")));
        assertEquals(Conflict.Reason.INVALID, wrongType.conflicts().get(0).reason());
    }

    @Test
    public void aReferenceCanPointAtAValueTheSamePlanAdds() throws Exception {
        Map<MissingResource, Resolution> choices = new LinkedHashMap<>();
        choices.put(missing("color", "base_color"), new Resolution.Typed("#123456"));
        choices.put(missing("color", "accent"), new Resolution.Typed("@color/base_color"));
        PlanBuilder.Plan plan = plan(choices);
        assertTrue(plan.conflicts().toString(), plan.conflicts().isEmpty());
        assertEquals(2, plan.changes().size());
    }

    @Test
    public void anExistingNameIsAConflictNotAnOverwrite() throws Exception {
        MissingResource exists = missing("drawable", "ic_exists");
        FileInspector.Inspection png = FileInspector.inspect("drawable", TestFiles.png(8, 8), null);
        PlanBuilder.Plan sameFolder = plan(one(exists, new Resolution.Imported("p.png", png, "")));
        assertEquals(Conflict.Reason.EXISTS, sameFolder.conflicts().get(0).reason());
        assertTrue(sameFolder.conflicts().get(0).message(), sameFolder.conflicts().get(0).message().contains("already exists"));
        assertTrue(sameFolder.changes().isEmpty());

        PlanBuilder.Plan otherFolder = plan(one(exists, new Resolution.Imported("p.png", png, "-xhdpi")));
        assertTrue("another density is a different resource configuration", otherFolder.conflicts().isEmpty());

        MissingResource caseOnly = missing("drawable", "IC_EXISTS");
        assertEquals("letter case doesn't make a new name", Conflict.Reason.EXISTS, plan(one(caseOnly, new Resolution.Imported("p.png", png, ""))).conflicts().get(0).reason());

        MissingResource value = missing("color", "brand");
        assertEquals(Conflict.Reason.EXISTS, plan(one(value, new Resolution.Typed("#000000"))).conflicts().get(0).reason());
    }

    @Test
    public void anImageOfTheImageManagerClashesInEveryFolder() throws Exception {
        MissingResource photo = missing("drawable", "photo");
        FileInspector.Inspection png = FileInspector.inspect("drawable", TestFiles.png(8, 8), null);
        assertEquals(Conflict.Reason.EXISTS, plan(one(photo, new Resolution.Imported("p.png", png, "-xxhdpi"))).conflicts().get(0).reason());
    }

    @Test
    public void twoChoicesForTheSameResourceAreNotBothApplied() throws Exception {
        Map<MissingResource, Resolution> choices = new LinkedHashMap<>();
        FileInspector.Inspection png = FileInspector.inspect("drawable", TestFiles.png(8, 8), null);
        choices.put(missing("drawable", "twin"), new Resolution.Imported("a.png", png, "-xhdpi"));
        choices.put(missing("drawable", "TWIN"), new Resolution.Imported("b.png", png, "-xhdpi"));
        PlanBuilder.Plan plan = plan(choices);
        assertEquals(1, plan.changes().size());
        assertEquals(Conflict.Reason.DUPLICATE_IN_PLAN, plan.conflicts().get(0).reason());
    }

    @Test
    public void anAliasCannotPointAtItself() throws Exception {
        ResourceInventory inventory = project();
        MissingResource color = missing("color", "brand2");
        ResourceInventory.Definition self = new ResourceInventory.Definition("color", "brand2", "-night", ResourceInventory.Kind.VALUE, "/x", "#000", ResourceInventory.Origin.PROJECT_RES);
        PlanBuilder.Plan same = new PlanBuilder(inventory, null, null).build(one(color, new Resolution.Reuse(new Suggestion(Suggestion.Kind.ALIAS, 0.9, true, self, null, null))));
        assertEquals(Conflict.Reason.ALIAS_CYCLE, same.conflicts().get(0).reason());

        ResourceInventory.Definition pointsBack = new ResourceInventory.Definition("color", "other", "", ResourceInventory.Kind.VALUE, "/x", "@color/brand2", ResourceInventory.Origin.PROJECT_RES);
        PlanBuilder.Plan back = new PlanBuilder(inventory, null, null).build(one(color, new Resolution.Reuse(new Suggestion(Suggestion.Kind.ALIAS, 0.9, true, pointsBack, null, null))));
        assertEquals(Conflict.Reason.ALIAS_CYCLE, back.conflicts().get(0).reason());
    }

    @Test
    public void aBrokenValuesFileIsNotEdited() throws Exception {
        // a value for a type whose file is the broken one: put the broken file where strings go
        File res = temp.newFolder();
        TestFiles.write(res, "values/strings.xml", "<resources><string name=\"x\">oops</resources>");
        ResourceInventory inventory = ResourceInventory.scan(res, null);
        PlanBuilder.Plan plan = new PlanBuilder(inventory, null, null).build(one(missing("string", "hello"), new Resolution.Typed("Hello")));
        assertTrue(plan.changes().isEmpty());
        assertEquals(Conflict.Reason.UNREADABLE_VALUES, plan.conflicts().get(0).reason());
    }

    @Test
    public void unfixableResourcesAreReportedNotPlanned() throws Exception {
        MissingResource framework = MissingResourceParser.parse("/p/a.xml:1: error: resource android:drawable/x not found.").get(0);
        PlanBuilder.Plan plan = plan(one(framework, new Resolution.Typed("#000000")));
        assertTrue(plan.changes().isEmpty());
        assertEquals(1, plan.conflicts().size());
    }

    @Test
    public void aMissingSourceIsReportedWhenTheFileIsGone() throws Exception {
        MissingResource drawable = missing("drawable", "ic_home");
        Suggestion s = suggestion(drawable);
        assertTrue(new File(s.source().path()).delete());
        PlanBuilder.Plan plan = new PlanBuilder(project(), library(), null).build(one(drawable, new Resolution.Reuse(s)));
        assertEquals(Conflict.Reason.SOURCE_MISSING, plan.conflicts().get(0).reason());
        assertTrue(plan.conflicts().get(0).message(), plan.conflicts().get(0).message().contains("isn't there any more"));
    }
}
