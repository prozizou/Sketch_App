package pro.sketchware.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static pro.sketchware.logic.LogicTestData.b;
import static pro.sketchware.logic.LogicTestData.event;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogicSearchTest {

    private static LogicScreen screen() {
        return new LogicScreen("MainActivity.java", List.of(
                event("button1_onClick",
                        b(10, "setVarInt", "set %m.varInt to %d", 11, "count", "@12"),
                        b(11, "doToast", "Toast %s", -1, "Saved!"),
                        b(12, "getVar", "total", -1)),
                event("onCreate_initializeLogic",
                        b(20, "setVarInt", "set %m.varInt to %d", -1, "count", "0")),
                event("calc_moreBlock",
                        b(30, "doToast", "Toast %s", -1, "calc done"))),
                Map.of("count", 1, "total", 1), Set.of(), Set.of("calc"), Set.of("button1"));
    }

    @Test
    public void rendersBlocksLikeTheEditor() {
        assertEquals("set [count] to ( )", LogicSearch.render(b(1, "setVarInt", "set %m.varInt to %d", -1, "count", "@2")));
        assertEquals("Toast [Saved!]", LogicSearch.render(b(1, "doToast", "Toast %s", -1, "Saved!")));
        assertEquals("total", LogicSearch.render(b(1, "getVar", "total", -1)));
        assertEquals("Toast []", LogicSearch.render(b(1, "doToast", "Toast %s", -1)));
    }

    @Test
    public void findsEveryUseOfAVariable() {
        List<LogicSearch.Hit> hits = LogicSearch.search(screen(), "count", 50);
        assertEquals(2, hits.size());
        assertEquals("button1_onClick", hits.get(0).eventKey());
        assertEquals("10", hits.get(0).blockId());
        assertEquals("onCreate_initializeLogic", hits.get(1).eventKey());
    }

    @Test
    public void findsValuesAndIgnoresCase() {
        List<LogicSearch.Hit> hits = LogicSearch.search(screen(), "SAVED", 50);
        assertEquals(1, hits.size());
        assertEquals("11", hits.get(0).blockId());
    }

    @Test
    public void eventsMatchByNameAndComeFirst() {
        List<LogicSearch.Hit> hits = LogicSearch.search(screen(), "calc", 50);
        assertTrue(hits.get(0).isEvent());
        assertEquals("calc_moreBlock", hits.get(0).eventKey());
        assertEquals("calc (more block)", hits.get(0).text());
        assertEquals("30", hits.get(1).blockId());
    }

    @Test
    public void everyWordMustMatchAndLimitApplies() {
        assertEquals(1, LogicSearch.search(screen(), "set 0", 50).size());
        assertTrue(LogicSearch.search(screen(), "count banana", 50).isEmpty());
        assertTrue(LogicSearch.search(screen(), "  ", 50).isEmpty());
        assertEquals(1, LogicSearch.search(screen(), "count", 1).size());
    }

    @Test
    public void eventLabelsAreReadable() {
        assertEquals("button1 · onClick", LogicSearch.eventLabel("button1_onClick"));
        assertEquals("onCreate", LogicSearch.eventLabel("onCreate_initializeLogic"));
        assertEquals("calc (more block)", LogicSearch.eventLabel("calc_moreBlock"));
    }
}
