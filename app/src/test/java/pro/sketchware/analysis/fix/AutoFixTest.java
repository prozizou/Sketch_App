package pro.sketchware.analysis.fix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.analysis.CompatibilityAnalyzer;
import pro.sketchware.analysis.DesignSystemChecker;
import pro.sketchware.analysis.Finding;
import pro.sketchware.analysis.ProjectFacts;
import pro.sketchware.analysis.ViewFacts;
import pro.sketchware.analysis.ViewFacts.Kind;
import pro.sketchware.logic.LogicAnalyzer;
import pro.sketchware.logic.LogicBlock;
import pro.sketchware.logic.LogicEvent;
import pro.sketchware.logic.LogicScreen;

public class AutoFixTest {
    private static ViewFacts view(String id, Kind kind, boolean clickable, int w, int h, Integer text, Integer bg, int sp, int margin, int padding) {
        return new ViewFacts("main", id, kind, w, h, clickable, kind != Kind.IMAGE, text, bg, sp, margin, margin, padding, padding, false);
    }

    private static final List<ViewFacts> VIEWS = List.of(
            view("imageview1", Kind.IMAGE, false, 48, 48, null, null, 0, 0, 0),
            view("button1", Kind.BUTTON, true, 30, 20, 0xff000000, 0xffffffff, 14, 8, 8),
            view("textview1", Kind.TEXT, false, -2, -2, 0xffbbbbbb, 0xffffffff, 9, 15, 6),
            view("textview2", Kind.TEXT, false, -2, -2, null, null, 13, 0, 0),
            view("textview3", Kind.TEXT, false, -2, -2, null, null, 11, 0, 0),
            new ViewFacts("main", "textview4", Kind.TEXT, -2, -2, false, true, null, null, 14, 0, 0, 0, 0, true));

    private static ProjectFacts facts(List<ViewFacts> views) {
        return new ProjectFacts("com.my.app", 24, 35, Set.of(), List.of(), Set.of(), true, Set.of(), views, true);
    }

    private static List<Finding> fixableFindings(List<ViewFacts> views) {
        List<Finding> all = new ArrayList<>(CompatibilityAnalyzer.analyze(facts(views)));
        all.addAll(DesignSystemChecker.analyze(views));
        return all.stream().filter(f -> AutoFix.Rule.forFinding(f.id()) != null).toList();
    }

    @Test
    public void theFindingsAreGoneOnceThePlanIsApplied() {
        assertFalse(fixableFindings(VIEWS).isEmpty());
        AutoFix.Plan plan = AutoFix.plan(VIEWS, List.of());
        List<ViewFacts> fixed = VIEWS.stream().map(v -> AutoFix.applyTo(v, plan.widgetChanges())).toList();
        assertEquals(List.of(), fixableFindings(fixed).stream().map(Finding::id).toList());
    }

    @Test
    public void eachRuleMakesTheExpectedChange() {
        AutoFix.Plan plan = AutoFix.plan(VIEWS, List.of());
        assertEquals(1, plan.count(AutoFix.Rule.CONTENT_DESCRIPTION));
        assertEquals(2, plan.count(AutoFix.Rule.TOUCH_TARGET));               // width and height of button1
        assertEquals(2, plan.count(AutoFix.Rule.SMALL_TEXT));                 // 9 sp and 11 sp
        assertEquals(1, plan.count(AutoFix.Rule.TEXT_SCALE));                 // 13 sp -> 12 sp
        assertEquals(1, plan.count(AutoFix.Rule.CONTRAST));
        assertEquals(4, plan.count(AutoFix.Rule.SPACING));                    // 15 and 6, left and right
        assertEquals(1, plan.count(AutoFix.Rule.HARDCODED_TEXT));
        assertTrue(has(plan, "textview4", AutoFix.Field.TEXT_TO_RESOURCE, 0));
        assertTrue(has(plan, "textview2", AutoFix.Field.TEXT_SIZE, 12));
        assertTrue(has(plan, "textview1", AutoFix.Field.TEXT_SIZE, 12));
        assertTrue(has(plan, "textview1", AutoFix.Field.TEXT_COLOR, 0xff000000));
        assertTrue(has(plan, "textview1", AutoFix.Field.MARGIN_LEFT, 16));
        assertTrue(has(plan, "textview1", AutoFix.Field.PADDING_RIGHT, 4));
        assertTrue(has(plan, "button1", AutoFix.Field.WIDTH, 48));
    }

    @Test
    public void nothingToDoOnACleanScreen() {
        assertTrue(AutoFix.plan(List.of(view("t", Kind.TEXT, false, -2, -2, 0xff000000, 0xffffffff, 16, 16, 8)), List.of()).isEmpty());
    }

    @Test
    public void whiteTextOnDarkBackgrounds() {
        assertEquals(0xffffffff, AutoFix.readableOn(0xff202020));
        assertEquals(0xff000000, AutoFix.readableOn(0xfff0f0f0));
    }

    @Test
    public void typeScaleRounding() {
        assertEquals(12, AutoFix.nearestReadable(13));
        assertEquals(16, AutoFix.nearestReadable(17));
        assertEquals(24, AutoFix.nearestReadable(25));
    }

    @Test
    public void descriptionsFromPictureOrId() {
        assertEquals("User avatar", AutoFix.describe("ic_user_avatar", "imageview1"));
        assertEquals("Imageview 1", AutoFix.describe(null, "imageview1"));
        assertEquals("Imageview 1", AutoFix.describe("default_image", "imageview1"));
        assertEquals("Profile picture", AutoFix.describe("profilePicture", "x"));
    }

    @Test
    public void looseBlocksArePlannedForRemovalAndTheFindingGoes() {
        LogicEvent event = new LogicEvent("button1_onClick", List.of(
                new LogicBlock("10", "doToast", "toast %s", "", List.of("hi"), -1, -1, -1),
                new LogicBlock("20", "doToast", "toast %s", "", List.of("@21"), -1, -1, -1),
                new LogicBlock("21", "getVar", "x", "", List.of(), -1, -1, -1)));
        LogicScreen screen = new LogicScreen("MainActivity.java", List.of(event), Map.of(), Set.of(), Set.of(), Set.of());
        AutoFix.Plan plan = AutoFix.plan(List.of(), List.of(screen));
        assertEquals(Set.of("20", "21"), plan.blockRemovals().get(0).blockIds());
        assertEquals(2, plan.count(AutoFix.Rule.UNCONNECTED_BLOCKS));

        Set<String> removed = plan.blockRemovals().get(0).blockIds();
        LogicEvent cleaned = new LogicEvent(event.key(), event.blocks().stream().filter(b -> !removed.contains(b.id())).toList());
        LogicScreen after = new LogicScreen("MainActivity.java", List.of(cleaned), Map.of(), Set.of(), Set.of(), Set.of());
        assertTrue(LogicAnalyzer.analyze(List.of(after), List.of()).stream().noneMatch(f -> f.id().equals("logic.unconnected-blocks")));
    }

    @Test
    public void blockRemovalIsOffByDefaultAndCanBeLeftOut() {
        assertFalse(AutoFix.Rule.UNCONNECTED_BLOCKS.onByDefault);
        LogicEvent event = new LogicEvent("e", List.of(
                new LogicBlock("10", "doToast", "toast %s", "", List.of("a"), -1, -1, -1),
                new LogicBlock("20", "doToast", "toast %s", "", List.of("b"), -1, -1, -1)));
        AutoFix.Plan plan = AutoFix.plan(VIEWS, List.of(new LogicScreen("MainActivity.java", List.of(event), Map.of(), Set.of(), Set.of(), Set.of())));
        AutoFix.Plan withoutBlocks = plan.only(EnumSet.complementOf(EnumSet.of(AutoFix.Rule.UNCONNECTED_BLOCKS)));
        assertTrue(withoutBlocks.blockRemovals().isEmpty());
        assertEquals(plan.widgetChanges().size(), withoutBlocks.widgetChanges().size());
        assertTrue(plan.only(EnumSet.of(AutoFix.Rule.CONTRAST)).widgetChanges().stream().allMatch(c -> c.rule() == AutoFix.Rule.CONTRAST));
    }

    @Test
    public void findingsThatNeedADecisionAreNotFixable() {
        Finding target = new Finding("compat.target-sdk-old", pro.sketchware.analysis.Category.COMPATIBILITY,
                pro.sketchware.analysis.Severity.ERROR, "t", "c", "s", null);
        Finding image = new Finding("a11y.content-description", pro.sketchware.analysis.Category.COMPATIBILITY,
                pro.sketchware.analysis.Severity.WARNING, "t", "c", "s", null);
        assertEquals(List.of(target), AutoFix.notFixable(List.of(target, image)));
    }

    private static boolean has(AutoFix.Plan plan, String id, AutoFix.Field field, int value) {
        return plan.widgetChanges().stream().anyMatch(c -> c.id().equals(id) && c.field() == field && c.value() == value);
    }
}
