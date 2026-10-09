package pro.sketchware.logic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.analysis.AnalysisReport;
import pro.sketchware.analysis.ProjectAnalyzer;
import pro.sketchware.analysis.ProjectFacts;
import pro.sketchware.analysis.SourceFile;

public class LogicScreenTest {
    @Test
    public void namesOfCustomDeclarations() {
        assertEquals("name", LogicScreen.declaredName("String name"));
        assertEquals("count", LogicScreen.declaredName("private int count = 0;"));
        assertEquals("map", LogicScreen.declaredName("HashMap<String, Object> map"));
        assertEquals("items", LogicScreen.declaredName("String[] items = {}"));
        assertEquals("", LogicScreen.declaredName("  "));
        assertEquals("", LogicScreen.declaredName(null));
    }

    @Test
    public void classNameDropsTheExtension() {
        assertEquals("MainActivity", new LogicScreen("MainActivity.java", null, null, null, null, null).className());
    }

    @Test
    public void projectAnalyzerIncludesLogicAndNavigation() {
        LogicScreen main = new LogicScreen("MainActivity.java", List.of(LogicTestData.event("e",
                LogicTestData.b(10, "definedFunc", "gone", -1))), Map.of(), Set.of(), Set.of(), Set.of());
        ProjectFacts facts = new ProjectFacts("com.example", 21, 34, Set.of(), List.of(new SourceFile("MainActivity.java", "class MainActivity {}")),
                Set.of(), true, Set.of(), List.of(), true);
        AnalysisReport report = ProjectAnalyzer.run(facts, List.of(), List.of(), Map.of(), List.of(main, new LogicScreen("OtherActivity.java", null, null, null, null, null)));
        assertEquals(1, report.findings().stream().filter(f -> f.id().equals("logic.missing-moreblock")).count());
        assertEquals(1, report.findings().stream().filter(f -> f.id().equals("sync.custom-java-duplicates-screen")).count());
        assertEquals(1, report.findings().stream().filter(f -> f.id().equals("nav.unreachable-screen")).count());
    }
}
