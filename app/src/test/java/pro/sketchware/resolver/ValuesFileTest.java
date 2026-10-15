package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ValuesFileTest {
    private static final String EXISTING = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "    <!-- brand -->\n"
            + "    <color name=\"brand\">#FF5722</color>\n"
            + "    <string name=\"app_name\">My app</string>\n"
            + "    <style name=\"AppTheme\" parent=\"android:Theme\">\n        <item name=\"android:colorPrimary\">@color/brand</item>\n    </style>\n"
            + "    <item name=\"main_id\" type=\"id\" />\n"
            + "    <string-array name=\"days\"><item>Mon</item></string-array>\n"
            + "</resources>\n";

    @Test
    public void readsTheNamesOfEveryKindOfEntry() throws Exception {
        List<ValuesFile.Entry> entries = ValuesFile.parse(EXISTING);
        assertEquals(new ValuesFile.Entry("color", "brand", "#FF5722"), entries.get(0));
        assertEquals(new ValuesFile.Entry("string", "app_name", "My app"), entries.get(1));
        assertEquals("style", entries.get(2).type());
        assertEquals("AppTheme", entries.get(2).name());
        assertEquals(new ValuesFile.Entry("id", "main_id", ""), entries.get(3));
        assertEquals("array", entries.get(4).type());
        assertEquals("a style's inner items are not entries of the file", 5, entries.size());
    }

    @Test
    public void addingKeepsEverythingThatWasThere() throws Exception {
        String result = ValuesFile.append(EXISTING, List.of(
                new ValuesFile.Entry("color", "accent", "@color/brand"),
                new ValuesFile.Entry("string", "hello", "Hello")));
        assertTrue(result.startsWith(EXISTING.substring(0, EXISTING.lastIndexOf("</resources>"))));
        assertTrue(result.contains("    <!-- brand -->\n"));
        assertTrue(result.endsWith("    <color name=\"accent\">@color/brand</color>\n    <string name=\"hello\">Hello</string>\n</resources>\n"));
        assertEquals(7, ValuesFile.parse(result).size());
    }

    @Test
    public void aNewFileGetsAHeaderAndTheEntries() throws Exception {
        String created = ValuesFile.append(null, List.of(new ValuesFile.Entry("dimen", "gap", "8dp")));
        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <dimen name=\"gap\">8dp</dimen>\n</resources>\n", created);
        assertEquals(created, ValuesFile.append("  \n", List.of(new ValuesFile.Entry("dimen", "gap", "8dp"))));
    }

    @Test
    public void aSelfClosingRootIsOpenedUp() throws Exception {
        String result = ValuesFile.append("<?xml version=\"1.0\"?>\n<resources/>\n", List.of(new ValuesFile.Entry("bool", "on", "true")));
        assertEquals(1, ValuesFile.parse(result).size());
        assertTrue(result.contains("<bool name=\"on\">true</bool>"));
    }

    @Test
    public void aFileThatIsNotValidIsNeverRewritten() {
        assertThrows(SafeXml.InvalidXml.class, () -> ValuesFile.append("<resources><string name=\"a\">x</resources>", List.of()));
        assertThrows(SafeXml.InvalidXml.class, () -> ValuesFile.append("<root/>", List.of(new ValuesFile.Entry("bool", "a", "true"))));
        assertThrows(SafeXml.InvalidXml.class, () -> ValuesFile.parse("<resources><color name=\"a\">"));
    }

    @Test
    public void typedTextIsEscapedTheWayAndroidReadsIt() {
        assertEquals("Tom &amp; Jerry", ValuesFile.textValue("Tom & Jerry"));
        assertEquals("It\\'s \\\"fine\\\"", ValuesFile.textValue("It's \"fine\""));
        assertEquals("\\@home", ValuesFile.textValue("@home"));
        assertEquals("a@b", ValuesFile.textValue("a@b"));
        assertEquals("line1\\nline2", ValuesFile.textValue("line1\nline2"));
        assertEquals("&lt;b&gt;", ValuesFile.textValue("<b>"));
    }

    @Test
    public void writtenTextReadsBackAsEntered() throws Exception {
        String xml = ValuesFile.append(null, List.of(new ValuesFile.Entry("string", "t", ValuesFile.textValue("A & B <c>"))));
        assertFalse("the file stays well-formed whatever the text", ValuesFile.parse(xml).isEmpty());
    }

    @Test
    public void aDoctypeIsRefusedBeforeAnythingIsParsed() {
        SafeXml.InvalidXml e = assertThrows(SafeXml.InvalidXml.class, () -> ValuesFile.parse(
                "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><resources><string name=\"a\">&x;</string></resources>"));
        assertTrue(e.getMessage().contains("DOCTYPE"));
    }

    @Test
    public void aReferenceIsWrittenAsAtTypeSlashName() {
        assertEquals("@color/brand", ValuesFile.reference("color", "brand"));
    }
}
