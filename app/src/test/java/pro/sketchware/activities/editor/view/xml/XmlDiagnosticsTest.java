package pro.sketchware.activities.editor.view.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Runs under Robolectric: the parser is Android's own, the one the editor uses on a device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class XmlDiagnosticsTest {
    private static final String HEADER = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n";

    @Test
    public void aWellFormedLayoutIsValidAndCountsItsElements() {
        XmlDiagnostics.Result result = XmlDiagnostics.check(HEADER + "    <TextView/>\n    <Button/>\n</LinearLayout>");
        assertTrue(result.valid());
        assertEquals(3, result.elements());
        assertEquals(-1, result.line());
        assertEquals("", result.message());
    }

    @Test
    public void aMismatchedTagIsReportedOnItsLine() {
        XmlDiagnostics.Result result = XmlDiagnostics.check(HEADER + "    <TextView>\n</LinearLayout>");
        assertFalse(result.valid());
        assertEquals("the error is on the closing tag, the third line", 2, result.line());
        assertFalse(result.message().isEmpty());
        assertFalse("the parser's internal position is not shown", result.message().contains("position:"));
        assertFalse(result.message().contains("StringReader"));
    }

    @Test
    public void anUnclosedAttributeValueIsAnError() {
        XmlDiagnostics.Result result = XmlDiagnostics.check(HEADER + "    <TextView android:text=\"Hello/>\n</LinearLayout>");
        assertFalse(result.valid());
        assertTrue(result.line() >= 1);
    }

    @Test
    public void emptyAndBlankTextAreNotLayouts() {
        assertFalse(XmlDiagnostics.check("").valid());
        assertFalse(XmlDiagnostics.check("   \n ").valid());
        assertFalse(XmlDiagnostics.check(null).valid());
        assertFalse("only a comment: no element", XmlDiagnostics.check("<!-- nothing -->").valid());
    }

    @Test
    public void positionsAreZeroBased() {
        XmlDiagnostics.Result result = XmlDiagnostics.check("<a>\n<b>\n</a>");
        assertFalse(result.valid());
        assertTrue(result.line() >= 0);
        assertTrue(result.column() >= 0);
    }

    @Test
    public void messagesAreCleanedAndCapitalised() {
        assertEquals("Unexpected end tag", XmlDiagnostics.clean("unexpected end tag (position:END_TAG </a> @3:4 in java.io.StringReader@1f)"));
        assertEquals("Not valid XML", XmlDiagnostics.clean(null));
        assertEquals("Not valid XML", XmlDiagnostics.clean("   "));
        assertEquals("Mismatch", XmlDiagnostics.clean("@3:5 mismatch"));
    }
}
