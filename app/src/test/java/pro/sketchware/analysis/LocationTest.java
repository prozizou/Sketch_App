package pro.sketchware.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.logic.LogicAnalyzer;
import pro.sketchware.logic.LogicBlock;
import pro.sketchware.logic.LogicEvent;
import pro.sketchware.logic.LogicScreen;
import pro.sketchware.logic.NavigationGraph;

/** Findings point at the widget, line, event or screen they are about, so the health screen can open it. */
public class LocationTest {
    private static Finding find(List<Finding> findings, String id) {
        return findings.stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    public void imageWithoutDescriptionPointsAtTheWidget() {
        ViewFacts image = new ViewFacts("main", "imageview1", ViewFacts.Kind.IMAGE, 48, 48, false, false,
                null, null, 0, 0, 0, 0, 0, false);
        ProjectFacts facts = new ProjectFacts("com.my.app", 24, 35, Set.of(), List.of(), Set.of(), true, Set.of(), List.of(image), true);
        Finding finding = find(CompatibilityAnalyzer.analyze(facts), "a11y.content-description");
        assertEquals(List.of(Location.widget("main", "imageview1")), finding.locations());
        Location location = finding.locations().get(0);
        assertEquals(Location.Kind.WIDGET, location.kind());
        assertEquals("main", location.file());
        assertEquals("imageview1", location.target());
        assertEquals("main / imageview1", location.label());
    }

    @Test
    public void codeRulesPointAtTheFileAndLine() {
        SourceFile source = new SourceFile("com/my/app/Util.java", "class Util {\n\n  AsyncTask task;\n}", "/sdcard/x/Util.java");
        ProjectFacts facts = new ProjectFacts("com.my.app", 24, 35, Set.of(), List.of(source), Set.of(), true, Set.of(), List.of(), true);
        Location location = find(CompatibilityAnalyzer.analyze(facts), "compat.async-task").locations().get(0);
        assertEquals(Location.Kind.SOURCE, location.kind());
        assertEquals("/sdcard/x/Util.java", location.file());
        assertEquals(3, location.line());
        assertEquals("com/my/app/Util.java:3", location.label());
    }

    @Test
    public void secretsPointAtTheirLine() {
        SourceFile file = new SourceFile("config.json", "{\n \"key\": \"AKIAABCDEFGHIJKLMNOP\"\n}", "/data/config.json");
        ProjectFacts facts = new ProjectFacts("com.my.app", 24, 35, Set.of(), List.of(), Set.of(), true, Set.of(), List.of(), true);
        Location location = find(SecurityScanner.analyze(facts, List.of(file), List.of()), "security.secret-aws").locations().get(0);
        assertEquals("/data/config.json", location.file());
        assertEquals(2, location.line());
    }

    @Test
    public void blockFindingsPointAtTheEventAndBlock() {
        LogicEvent event = new LogicEvent("button1_onClick", List.of(
                new LogicBlock("10", "forever", "forever", "", List.of(), -1, 11, -1),
                new LogicBlock("11", "doToast", "toast %s", "", List.of("hi"), -1, -1, -1)));
        LogicScreen screen = new LogicScreen("MainActivity.java", List.of(event), Map.of("unused", 1), Set.of(), Set.of("helper"), Set.of());
        List<Finding> findings = LogicAnalyzer.analyze(List.of(screen), List.of());
        Location loop = find(findings, "logic.infinite-loop").locations().get(0);
        assertEquals(Location.Kind.EVENT, loop.kind());
        assertEquals("MainActivity.java", loop.file());
        assertEquals("button1_onClick", loop.target());
        assertEquals("10", loop.detail());
        assertEquals("button1", loop.eventTarget());
        assertEquals("onClick", loop.eventName());
        assertEquals("helper_moreBlock", find(findings, "logic.unused-moreblock").locations().get(0).target());
        assertTrue(find(findings, "logic.unused-variable").locations().isEmpty());
    }

    @Test
    public void customJavaNamedLikeAScreenPointsAtTheFile() {
        LogicScreen screen = new LogicScreen("MainActivity.java", List.of(), Map.of(), Set.of(), Set.of(), Set.of());
        Location location = find(LogicAnalyzer.analyze(List.of(screen), Map.of("MainActivity.java", "/p/files/java/MainActivity.java")),
                "sync.custom-java-duplicates-screen").locations().get(0);
        assertEquals("/p/files/java/MainActivity.java", location.file());
    }

    @Test
    public void navigationFindingsPointAtTheScreenOrTheEvent() {
        LogicScreen main = new LogicScreen("MainActivity.java", List.of(new LogicEvent("button1_onClick", List.of(
                new LogicBlock("10", "intentSetScreen", "%m.intent set screen %m.activity", "", List.of("i", "GoneActivity"), -1, -1, -1)))),
                Map.of(), Set.of(), Set.of(), Set.of());
        LogicScreen other = new LogicScreen("OtherActivity.java", List.of(), Map.of(), Set.of(), Set.of(), Set.of());
        List<Finding> findings = NavigationGraph.build(List.of(main, other), Map.of()).findings();
        assertEquals(Location.screen("OtherActivity.java"), find(findings, "nav.unreachable-screen").locations().get(0));
        assertEquals(Location.event("MainActivity.java", "button1_onClick", ""), find(findings, "nav.unknown-target").locations().get(0));
    }

    @Test
    public void designFindingsPointAtTheWidgets() {
        ViewFacts text = new ViewFacts("main", "textview1", ViewFacts.Kind.TEXT, -2, -2, false, true, null, null, 15, 15, 15, 8, 8, false);
        List<Finding> findings = DesignSystemChecker.analyze(List.of(text));
        assertEquals(Location.widget("main", "textview1"), find(findings, "design.spacing-off-scale").locations().get(0));
        assertEquals(Location.widget("main", "textview1"), find(findings, "design.text-size-off-scale").locations().get(0));
    }

    @Test
    public void findingsKeepAtMostOneHundredLocations() {
        List<Location> many = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) many.add(Location.widget("main", "w" + i));
        Finding finding = new Finding("x", Category.QUALITY, Severity.INFO, "t", "c", "s", null, many);
        assertEquals(Finding.MAX_LOCATIONS, finding.locations().size());
        assertTrue(new Finding("x", Category.QUALITY, Severity.INFO, "t", "c", "s", null).locations().isEmpty());
        assertEquals(1, Location.lineOf("abc", 2));
        assertEquals(3, Location.lineOf("a\nb\nc", 4));
    }
}
