package pro.sketchware.blocks.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextHistoryTest {
    @Test
    public void undoAndRedo() {
        TextHistory history = new TextHistory("a", 10);
        history.record("ab", 2);
        history.record("abc", 3);
        assertEquals("ab", history.undo().text());
        assertEquals("a", history.undo().text());
        assertNull(history.undo());
        assertEquals("ab", history.redo().text());
        history.record("abX", 3);
        assertFalse(history.canRedo());
        assertTrue(history.canUndo());
    }

    @Test
    public void keepsAtMostTheLimit() {
        TextHistory history = new TextHistory("", 3);
        for (int i = 1; i <= 10; i++) history.record("t" + i, 1);
        int undos = 0;
        while (history.undo() != null) undos++;
        assertEquals(3, undos);
    }
}
