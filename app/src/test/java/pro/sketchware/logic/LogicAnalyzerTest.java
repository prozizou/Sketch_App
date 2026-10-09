package pro.sketchware.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static pro.sketchware.logic.LogicTestData.b;
import static pro.sketchware.logic.LogicTestData.c;
import static pro.sketchware.logic.LogicTestData.event;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.analysis.Finding;
import pro.sketchware.analysis.Severity;

public class LogicAnalyzerTest {
    private static LogicScreen screen(Map<String, Integer> variables, Set<String> lists, Set<String> moreBlocks, LogicEvent... events) {
        return new LogicScreen("MainActivity.java", List.of(events), variables, lists, moreBlocks, Set.of("button1"));
    }

    private static List<Finding> analyze(LogicScreen screen) {
        return LogicAnalyzer.analyze(List.of(screen), List.of());
    }

    private static Finding find(List<Finding> findings, String id) {
        return findings.stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
    }

    @Test
    public void aCleanEventHasNoFinding() {
        LogicScreen s = screen(Map.of("score", 1), Set.of(), Set.of(),
                event("button1_onClick",
                        b(10, "setVarInt", "set %m.varInt to %d", 11, "score", "5"),
                        b(11, "increaseInt", "increase %m.varInt", -1, "score")));
        assertTrue(analyze(s).isEmpty());
    }

    @Test
    public void looseBlocksAreUnconnected() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(),
                event("button1_onClick", b(10, "doToast", "toast %s", -1, "hi"), b(20, "doToast", "toast %s", -1, "never")));
        Finding f = find(analyze(s), "logic.unconnected-blocks");
        assertTrue(f.evidence().contains("button1_onClick (1)"));
    }

    @Test
    public void pluggedInBlocksAreNotLoose() {
        LogicScreen s = screen(Map.of("x", 1), Set.of(), Set.of(),
                event("e", b(10, "setVarInt", "set %m.varInt to %d", -1, "x", "@11"),
                        b(11, "+", "%d + %d", -1, "1", "2")));
        assertTrue(find(analyze(s), "logic.unconnected-blocks") == null);
    }

    @Test
    public void unusedVariablesListsAndMoreBlocks() {
        LogicScreen s = screen(Map.of("used", 1, "unused", 2), Set.of("items"), Set.of("helper", "called"),
                event("e", b(10, "setVarInt", "set %m.varInt to %d", 11, "used", "1"),
                        b(11, "definedFunc", "called", -1)));
        List<Finding> findings = analyze(s);
        assertTrue(find(findings, "logic.unused-variable").evidence().contains("unused"));
        assertFalse(find(findings, "logic.unused-variable").evidence().contains("> used"));
        assertTrue(find(findings, "logic.unused-list").evidence().contains("items"));
        assertTrue(find(findings, "logic.unused-moreblock").evidence().contains("helper"));
        assertFalse(find(findings, "logic.unused-moreblock").evidence().contains("called"));
    }

    @Test
    public void variableUsedOnlyInAddSourceDirectlyCountsAsUsed() {
        LogicScreen s = screen(Map.of("counter", 1), Set.of(), Set.of(),
                event("e", b(10, "addSourceDirectly", "add source directly %s", -1, "counter = counter * 2;")));
        assertTrue(find(analyze(s), "logic.unused-variable") == null);
    }

    @Test
    public void callToADeletedMoreBlockIsAnError() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(), event("e", b(10, "definedFunc", "gone %s", -1, "x")));
        Finding f = find(analyze(s), "logic.missing-moreblock");
        assertEquals(Severity.ERROR, f.severity());
        assertTrue(f.evidence().contains("gone"));
    }

    @Test
    public void readingAnUndeclaredVariable() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(),
                event("e", b(10, "doToast", "toast %s", -1, "@11"), b(11, "getVar", "ghost", -1)));
        assertTrue(find(analyze(s), "logic.undeclared-variable").evidence().contains("ghost"));
    }

    @Test
    public void foreverWithoutBreakIsAnInfiniteLoop() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(),
                event("e", c(10, "forever", "forever", -1, 11, -1), b(11, "doToast", "toast %s", -1, "hi")));
        List<Finding> findings = analyze(s);
        assertEquals(Severity.ERROR, find(findings, "logic.infinite-loop").severity());
        assertTrue(find(findings, "logic.heavy-call-in-loop").evidence().contains("doToast"));
    }

    @Test
    public void foreverWithABreakInsideAnIfIsFine() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(),
                event("e", c(10, "forever", "forever", -1, 11, -1),
                        c(11, "if", "if %b then", -1, 12, -1, "@13"),
                        b(12, "break", "break", -1),
                        b(13, "true", "true", -1)));
        assertTrue(find(analyze(s), "logic.infinite-loop") == null);
    }

    @Test
    public void aBreakThatOnlyEndsAnInnerLoopDoesNotCount() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(),
                event("e", c(10, "forever", "forever", -1, 11, -1),
                        c(11, "repeat", "repeat %d", -1, 12, -1, "3"),
                        b(12, "break", "break", -1)));
        assertTrue(find(analyze(s), "logic.infinite-loop") != null);
    }

    @Test
    public void largeAndDeadRepeats() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(),
                event("e", c(10, "repeat", "repeat %d", 11, 12, -1, "1000000"),
                        c(11, "repeat", "repeat %d", -1, 12, -1, "0"),
                        b(12, "increaseInt", "increase %m.varInt", -1, "x")));
        List<Finding> findings = analyze(s);
        assertTrue(find(findings, "logic.large-loop") != null);
        assertTrue(find(findings, "logic.dead-loop") != null);
    }

    @Test
    public void constantAndEmptyConditions() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(),
                event("e", c(10, "if", "if %b then", 11, 12, -1, "@13"),
                        c(11, "if", "if %b then", -1, 12, -1, ""),
                        b(12, "doToast", "toast %s", -1, "x"),
                        b(13, "false", "false", -1)));
        Finding f = find(analyze(s), "logic.constant-condition");
        assertTrue(f.evidence().contains("#10"));
        assertTrue(f.evidence().contains("#11 (empty, always true)"));
    }

    @Test
    public void divisionByTypedZeroOrEmpty() {
        LogicScreen s = screen(Map.of("x", 1), Set.of(), Set.of(),
                event("e", b(10, "setVarInt", "set %m.varInt to %d", 11, "x", "@12"),
                        b(11, "setVarInt", "set %m.varInt to %d", -1, "x", "@13"),
                        b(12, "/", "%d / %d", -1, "4", "0"),
                        b(13, "%", "%d %% %d", -1, "4", "")));
        Finding f = find(analyze(s), "logic.division-by-zero");
        assertTrue(f.evidence().contains("#12") && f.evidence().contains("#13"));
    }

    @Test
    public void selfAssignment() {
        LogicScreen s = screen(Map.of("x", 1), Set.of(), Set.of(),
                event("e", b(10, "setVarInt", "set %m.varInt to %d", -1, "x", "@11"), b(11, "getVar", "x", -1)));
        assertTrue(find(analyze(s), "logic.self-assignment") != null);
    }

    @Test
    public void breakOutsideALoopIsAnError() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(),
                event("e", c(10, "if", "if %b then", -1, 11, -1, "@12"), b(11, "break", "break", -1), b(12, "true", "true", -1)));
        assertEquals(Severity.ERROR, find(analyze(s), "logic.break-outside-loop").severity());
    }

    @Test
    public void brokenReference() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of(), event("e", b(10, "doToast", "toast %s", 99, "@55")));
        assertTrue(find(analyze(s), "logic.broken-reference") != null);
    }

    @Test
    public void customJavaFileNamedLikeAScreen() {
        LogicScreen s = screen(Map.of(), Set.of(), Set.of());
        List<Finding> findings = LogicAnalyzer.analyze(List.of(s), List.of("com/my/app/MainActivity.java", "Util.java"));
        Finding f = find(findings, "sync.custom-java-duplicates-screen");
        assertEquals(Severity.ERROR, f.severity());
        assertTrue(f.evidence().contains("MainActivity.java"));
        assertFalse(f.evidence().contains("Util"));
    }

    @Test
    public void wholeWordMatch() {
        assertTrue(LogicAnalyzer.containsWord(" set score to", "score"));
        assertFalse(LogicAnalyzer.containsWord(" highscore", "score"));
        assertFalse(LogicAnalyzer.containsWord(" score_2", "score"));
    }
}
