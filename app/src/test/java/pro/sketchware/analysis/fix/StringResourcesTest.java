package pro.sketchware.analysis.fix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StringResourcesTest {
    private static final String EXISTING = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">My App</string>
                <string name="welcome">Welcome</string>
            </resources>
            """;

    @Test
    public void readsExistingStrings() throws Exception {
        Map<String, String> strings = StringResources.parse(EXISTING);
        assertEquals(Map.of("app_name", "My App", "welcome", "Welcome"), strings);
        assertTrue(StringResources.parse("").isEmpty());
        assertTrue(StringResources.parse(null).isEmpty());
    }

    @Test
    public void refusesAFileItCannotRead() {
        assertThrows(StringResources.InvalidStringsFile.class, () -> StringResources.parse("<resources><string name=\"a\">x</resources>"));
        assertThrows(StringResources.InvalidStringsFile.class, () -> StringResources.parse("<layout/>"));
    }

    @Test
    public void keysComeFromTheTextAndStayUnique() throws Exception {
        Map<String, String> existing = StringResources.parse(EXISTING);
        assertEquals("welcome", StringResources.keyFor("Welcome", existing, Set.of()));          // same text: reused
        assertEquals("sign_in", StringResources.keyFor("Sign in!", existing, Set.of()));
        assertEquals("welcome_2", StringResources.keyFor("WELCOME", existing, Set.of()));        // key taken by another text
        assertEquals("sign_in_2", StringResources.keyFor("Sign-in", existing, Set.of("sign_in")));
        assertEquals("text_42", StringResources.keyFor("42", existing, Set.of()));
        assertEquals("text", StringResources.keyFor("!!!", existing, Set.of()));
        assertEquals("text", StringResources.keyFor("Привет", existing, Set.of()));
        assertTrue(StringResources.keyFor("a very long sentence that goes on and on and on", existing, Set.of()).length() <= StringResources.MAX_KEY_LENGTH);
    }

    @Test
    public void trickyTextsComeBackUnchanged() throws Exception {
        List<String> texts = List.of("Don't \"quote\" me", "@username", "?maybe", "  padded  ", "two\nlines", "A & B < C > D",
                "50% off", "back\\slash", "tab\there", "Ça marche ✓");
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < texts.size(); i++) entries.put("t" + i, texts.get(i));
        Map<String, String> read = StringResources.parse(StringResources.append(EXISTING, entries));
        for (int i = 0; i < texts.size(); i++) assertEquals(texts.get(i), read.get("t" + i));
        assertEquals("My App", read.get("app_name"));
    }

    @Test
    public void percentSignsAreNotFormatArguments() {
        String xml = StringResources.append("", Map.of("sale", "50% off 10%"));
        assertTrue(xml.contains("<string name=\"sale\" formatted=\"false\">50% off 10%</string>"));
    }

    @Test
    public void createsTheFileOrOpensASelfClosingRoot() throws Exception {
        String created = StringResources.append("", Map.of("hello", "Hello"));
        assertEquals(Map.of("hello", "Hello"), StringResources.parse(created));
        String opened = StringResources.append("<resources/>", Map.of("hello", "Hello"));
        assertEquals(Map.of("hello", "Hello"), StringResources.parse(opened));
    }
}
