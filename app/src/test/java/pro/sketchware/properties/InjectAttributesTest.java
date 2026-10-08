package pro.sketchware.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

public class InjectAttributesTest {
    @Test
    public void readsValuesFromTheCustomAttributes() {
        InjectAttributes attributes = new InjectAttributes("android:numColumns=\"3\"\nandroid:stretchMode=\"columnWidth\"");

        assertEquals("3", attributes.get("android:numColumns"));
        assertEquals("columnWidth", attributes.get("android:stretchMode"));
        assertNull(attributes.get("android:gravity"));
    }

    @Test
    public void settingReplacesInPlaceAndKeepsTheOrder() {
        InjectAttributes attributes = new InjectAttributes("android:numColumns=\"3\"\nandroid:stretchMode=\"columnWidth\"");

        attributes.set("android:numColumns", "4");

        assertEquals("android:numColumns=\"4\"\nandroid:stretchMode=\"columnWidth\"", attributes.toString());
    }

    @Test
    public void newAttributesAreAppended() {
        InjectAttributes attributes = new InjectAttributes("android:numColumns=\"3\"");

        attributes.set("android:minHeight", "48dp");

        assertEquals("android:numColumns=\"3\"\nandroid:minHeight=\"48dp\"", attributes.toString());
    }

    @Test
    public void emptyOrNullValueRemovesTheAttribute() {
        InjectAttributes attributes = new InjectAttributes("a:x=\"1\"\nb:y=\"2\"");

        attributes.set("a:x", null);
        attributes.set("b:y", "");

        assertEquals("", attributes.toString());
    }

    @Test
    public void linesThatAreNotPlainAttributesSurviveEdits() {
        String text = "style=\"@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox\"\napp:lottie_loop=\"true\" \nsomething odd";
        InjectAttributes attributes = new InjectAttributes(text);

        attributes.set("android:minWidth", "10dp");

        String result = attributes.toString();
        assertEquals("true", attributes.get("app:lottie_loop"));
        assertEquals(text + "\nandroid:minWidth=\"10dp\"", result);
    }

    @Test
    public void blankInputGivesNoAttributes() {
        assertEquals("", new InjectAttributes(null).toString());
        assertEquals("", new InjectAttributes("  \n \n").toString());
    }

    @Test
    public void countsHowManyOfTheGivenAttributesAreSet() {
        InjectAttributes attributes = new InjectAttributes("a:x=\"1\"\nb:y=\"2\"");

        assertEquals(1, attributes.countSet(List.of("a:x", "c:z")));
    }

    @Test
    public void roundTripOfASetValueIsStable() {
        InjectAttributes attributes = new InjectAttributes("");
        attributes.set("android:foregroundGravity", "center|fill");

        assertEquals("center|fill", new InjectAttributes(attributes.toString()).get("android:foregroundGravity"));
    }
}
