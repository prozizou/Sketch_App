package pro.sketchware.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static pro.sketchware.logic.LogicTestData.b;
import static pro.sketchware.logic.LogicTestData.event;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.analysis.Finding;

public class NavigationGraphTest {
    private static LogicScreen screen(String javaName, LogicEvent... events) {
        return new LogicScreen(javaName, List.of(events), Map.of(), Set.of(), Set.of(), Set.of());
    }

    private static final LogicScreen MAIN = screen("MainActivity.java",
            event("button1_onClick",
                    b(10, "intentSetScreen", "%m.intent set screen %m.activity", 11, "intent", "SecondActivity"),
                    b(11, "startActivity", "StartActivity %m.intent", -1, "intent")),
            event("button2_onClick",
                    b(20, "intentSetScreen", "%m.intent set screen %m.activity", -1, "intent", "GoneActivity")));

    @Test
    public void edgesComeFromIntentBlocks() {
        NavigationGraph graph = NavigationGraph.build(List.of(MAIN, screen("SecondActivity.java"), screen("ThirdActivity.java"),
                screen("PageFragmentActivity.java")), Map.of());
        assertEquals(2, graph.edges().size());
        assertEquals("SecondActivity", graph.edgesFrom("MainActivity").get(0).to());
        assertEquals(List.of("ThirdActivity"), graph.unreachable());
        assertEquals("GoneActivity", graph.unknownTargets().get(0).to());
    }

    @Test
    public void javaFilesAddEdgesAndCountAsKnownClasses() {
        NavigationGraph graph = NavigationGraph.build(List.of(MAIN, screen("SecondActivity.java"), screen("ThirdActivity.java")),
                Map.of("Router.java", "class Router { void go(Context c) { c.startActivity(new Intent(c, ThirdActivity.class)); } }",
                        "GoneActivity.java", "public class GoneActivity {}"));
        assertTrue(graph.unreachable().isEmpty());
        assertTrue(graph.unknownTargets().isEmpty());
        assertTrue(graph.edges().stream().anyMatch(e -> e.fromJava() && e.to().equals("ThirdActivity")));
    }

    @Test
    public void looseIntentBlocksAreNotEdges() {
        LogicScreen main = screen("MainActivity.java", event("e",
                b(10, "doToast", "toast %s", -1, "x"),
                b(20, "intentSetScreen", "%m.intent set screen %m.activity", -1, "intent", "SecondActivity")));
        NavigationGraph graph = NavigationGraph.build(List.of(main, screen("SecondActivity.java")), Map.of());
        assertTrue(graph.edges().isEmpty());
    }

    @Test
    public void findingsAndMermaid() {
        NavigationGraph graph = NavigationGraph.build(List.of(MAIN, screen("SecondActivity.java"), screen("ThirdActivity.java")), Map.of());
        List<Finding> findings = graph.findings();
        assertTrue(findings.stream().anyMatch(f -> f.id().equals("nav.unreachable-screen") && f.evidence().contains("ThirdActivity")));
        assertTrue(findings.stream().anyMatch(f -> f.id().equals("nav.unknown-target")));
        String mermaid = graph.toMermaid();
        assertTrue(mermaid.startsWith("flowchart LR"));
        assertTrue(mermaid.contains("MainActivity -->|button1_onClick| SecondActivity"));
    }
}
