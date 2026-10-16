package pro.sketchware.logic.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextDiffTest {
    @Test
    public void equalTextsHaveNoDifference() {
        TextDiff diff = TextDiff.of("a\nb\nc", "a\nb\nc");
        assertTrue(diff.isEmpty());
        assertEquals(3, diff.lines().size());
    }

    @Test
    public void anAddedLineIsAdded() {
        TextDiff diff = TextDiff.of("a\nc", "a\nb\nc");
        assertEquals(1, diff.added());
        assertEquals(0, diff.removed());
        assertEquals("b", diff.lines().stream().filter(l -> l.kind() == TextDiff.Kind.ADDED).findFirst().get().text());
    }

    @Test
    public void aChangedLineIsRemovedAndAdded() {
        TextDiff diff = TextDiff.of("a\nold\nc", "a\nnew\nc");
        assertEquals(1, diff.added());
        assertEquals(1, diff.removed());
    }

    @Test
    public void movedLinesShowAsRemovedAndAdded() {
        TextDiff diff = TextDiff.of("a\nb\nc", "b\nc\na");
        assertEquals(1, diff.added());
        assertEquals(1, diff.removed());
    }

    @Test
    public void lineEndingsDoNotMatter() {
        assertTrue(TextDiff.of("a\r\nb", "a\nb").isEmpty());
    }

    @Test
    public void emptyAgainstTextIsAllAdded() {
        TextDiff diff = TextDiff.of("", "a\nb");
        assertEquals(2, diff.added());
        assertEquals(0, diff.removed());
        TextDiff reverse = TextDiff.of("a\nb", "");
        assertEquals(2, reverse.removed());
    }

    @Test
    public void theUnifiedTextShowsChangesWithContext() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            before.append("line").append(i).append('\n');
            after.append(i == 10 ? "changed" : "line" + i).append('\n');
        }
        String unified = TextDiff.of(before.toString(), after.toString()).toUnified(2);
        assertTrue(unified, unified.contains("- line10"));
        assertTrue(unified, unified.contains("+ changed"));
        assertTrue(unified, unified.contains("  line9"));
        assertTrue("far lines are left out", !unified.contains("line2\n"));
    }

    @Test
    public void distantChangesAreSeparatedByAnEllipsis() {
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            before.append("line").append(i).append('\n');
            after.append(i == 3 || i == 25 ? "changed" + i : "line" + i).append('\n');
        }
        String unified = TextDiff.of(before.toString(), after.toString()).toUnified(1);
        assertTrue(unified, unified.contains("…"));
        assertTrue(unified.contains("+ changed3") && unified.contains("+ changed25"));
    }

    @Test
    public void veryLongTextsAreStillCompared() {
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            a.append("x").append(i).append('\n');
            b.append(i == 2500 ? "y" : "x" + i).append('\n');
        }
        TextDiff diff = TextDiff.of(a.toString(), b.toString());
        assertEquals(1, diff.added());
        assertEquals(1, diff.removed());
    }
}
