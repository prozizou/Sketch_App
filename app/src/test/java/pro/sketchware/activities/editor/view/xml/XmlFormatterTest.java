package pro.sketchware.activities.editor.view.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Runs under Robolectric: the parser is Android's own, the one the editor uses on a device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class XmlFormatterTest {
    @Test
    public void everyElementGetsItsOwnLineAndEveryAttributeToo() {
        String messy = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\"\n"
                + "   android:orientation=\"vertical\"><TextView android:id=\"@+id/t\"   android:text=\"Hello\"/>"
                + "<Button/></LinearLayout>";
        String expected = """
                <LinearLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:orientation="vertical">
                    <TextView
                        android:id="@+id/t"
                        android:text="Hello" />
                    <Button />
                </LinearLayout>
                """;
        assertEquals(expected, XmlFormatter.format(messy));
    }

    @Test
    public void anElementWithOneAttributeStaysOnOneLine() {
        assertEquals("<include layout=\"@layout/row\" />\n", XmlFormatter.format("<include   layout=\"@layout/row\"/>"));
    }

    @Test
    public void formattingIsIdempotent() {
        String once = XmlFormatter.format("<a x=\"1\" y=\"2\"><b/><c z=\"3\"><d/></c></a>");
        assertNotNull(once);
        assertEquals(once, XmlFormatter.format(once));
    }

    @Test
    public void valuesWithBracketsQuotesAndSlashesSurvive() {
        String xml = "<a b=\"x > y\" c='it\"s' d=\"a/b\"/>";
        String formatted = XmlFormatter.format(xml);
        assertNotNull(formatted);
        assertTrue(formatted.contains("b=\"x > y\""));
        assertTrue(formatted.contains("c='it\"s'"));
        assertTrue(formatted.contains("d=\"a/b\""));
        assertTrue("still well-formed", XmlDiagnostics.check(formatted).valid());
    }

    @Test
    public void commentsDeclarationsAndTextAreKept() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><!-- header --><root><item>text</item><!-- c --></root>";
        String expected = """
                <?xml version="1.0" encoding="utf-8"?>
                <!-- header -->
                <root>
                    <item>text</item>
                    <!-- c -->
                </root>
                """;
        assertEquals(expected, XmlFormatter.format(xml));
    }

    @Test
    public void theMeaningDoesNotChange() {
        String xml = "<a p=\"1\" q=\"2\"><b r=\"3\"/><c>hello</c></a>";
        String formatted = XmlFormatter.format(xml);
        assertEquals(XmlDiagnostics.check(xml).elements(), XmlDiagnostics.check(formatted).elements());
        assertEquals(xml.replace(" ", "").length() > 0, formatted.replaceAll("\\s", "").length() > 0);
        assertEquals("same characters apart from whitespace and the space before />",
                xml.replaceAll("\\s", "").replace("/>", "/>"),
                formatted.replaceAll("\\s", ""));
    }

    @Test
    public void anythingNotWellFormedIsLeftAlone() {
        assertNull(XmlFormatter.format("<a><b></a>"));
        assertNull(XmlFormatter.format(""));
        assertNull(XmlFormatter.format(null));
        assertNull(XmlFormatter.format("<a attr=\"unclosed></a>"));
    }
}
