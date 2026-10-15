package pro.sketchware.activities.editor.view.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class XmlSymbolsTest {
    private static XmlSymbols.Symbol key(String label) {
        return XmlSymbols.ALL.stream().filter(s -> s.label().equals(label)).findFirst().orElseThrow();
    }

    @Test
    public void aPairLandsTheCaretBetweenItsHalves() {
        XmlSymbols.Edit edit = XmlSymbols.edit(key("\"\""), "");
        assertEquals("\"\"", edit.text());
        assertEquals(1, edit.caret());
    }

    @Test
    public void aPairWrapsTheSelection() {
        XmlSymbols.Edit edit = XmlSymbols.edit(key("\"\""), "match_parent");
        assertEquals("\"match_parent\"", edit.text());
        assertEquals("the caret goes after the closing quote", edit.text().length(), edit.caret());
    }

    @Test
    public void aPlainKeyReplacesTheSelection() {
        XmlSymbols.Edit edit = XmlSymbols.edit(key("@+id/"), "old");
        assertEquals("@+id/", edit.text());
        assertEquals(5, edit.caret());
    }

    @Test
    public void everyKeyHasALabelAnAccessibleNameAndTextToType() {
        assertFalse(XmlSymbols.ALL.isEmpty());
        for (XmlSymbols.Symbol symbol : XmlSymbols.ALL) {
            assertFalse(symbol.label().isEmpty());
            assertFalse(symbol.text().isEmpty());
            assertTrue(symbol.describe() != 0);
            assertTrue(symbol.caretBack() >= 0 && symbol.caretBack() < symbol.text().length());
            assertFalse("keys are typed on one line", symbol.text().contains("\n"));
        }
    }
}
