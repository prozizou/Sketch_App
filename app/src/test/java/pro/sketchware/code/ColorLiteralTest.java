package pro.sketchware.code;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ColorLiteralTest {

    @Test
    public void writesJavaAndXmlLiterals() {
        assertEquals("0xFFFF5722", ColorLiteral.format(0xFFFF5722, true));
        assertEquals("#FF5722", ColorLiteral.format(0xFFFF5722, false));
        assertEquals("#80FF5722", ColorLiteral.format(0x80FF5722, false));
        assertEquals("0x00000000", ColorLiteral.format(0, true));
    }

    @Test
    public void readsEveryLiteralForm() {
        assertEquals(Integer.valueOf(0xFFFF5722), ColorLiteral.parse("#FF5722"));
        assertEquals(Integer.valueOf(0x80FF5722), ColorLiteral.parse("#80FF5722"));
        assertEquals(Integer.valueOf(0xFFFFAA00), ColorLiteral.parse("#FA0"));
        assertEquals(Integer.valueOf(0xFF2196F3), ColorLiteral.parse("0xFF2196F3"));
        assertEquals(Integer.valueOf(0xFF2196F3), ColorLiteral.parse("0x2196F3"));
        assertNull(ColorLiteral.parse("#12345"));
        assertNull(ColorLiteral.parse("red"));
    }

    @Test
    public void findsTheLiteralUnderTheCursor() {
        String line = "    android:textColor=\"#FF5722\" android:background=\"#000000\"";
        ColorLiteral.Found found = ColorLiteral.at(line, line.indexOf("FF57") + 2);
        assertNotNull(found);
        assertEquals("#FF5722", line.substring(found.start(), found.end()));
        // Right after the literal still counts, so a color just typed can be changed.
        assertNotNull(ColorLiteral.at(line, line.indexOf("#000000") + 7));
        assertNull(ColorLiteral.at(line, 4));

        String java = "view.setBackgroundColor(0xFF2196F3);";
        ColorLiteral.Found inJava = ColorLiteral.at(java, java.indexOf("0x") + 1);
        assertNotNull(inJava);
        assertEquals(0xFF2196F3, inJava.color());
    }
}
