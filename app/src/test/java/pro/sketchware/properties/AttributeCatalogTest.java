package pro.sketchware.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import a.a.a.Gx;

public class AttributeCatalogTest {
    private static Set<String> names(String viewClass) {
        Set<String> names = new HashSet<>();
        for (AttributeCatalog.Section section : AttributeCatalog.sectionsFor(new Gx(viewClass))) {
            names.addAll(section.names());
        }
        return names;
    }

    private static AttributeCatalog.Attr attr(String viewClass, String name) {
        for (AttributeCatalog.Section section : AttributeCatalog.sectionsFor(new Gx(viewClass))) {
            for (AttributeCatalog.Attr attr : section.attrs()) {
                if (attr.name().equals(name)) {
                    return attr;
                }
            }
        }
        return null;
    }

    @Test
    public void materialWidgetsAreRecognisedByTheirViewType() {
        Set<String> button = new HashSet<>();
        for (AttributeCatalog.Section section : AttributeCatalog.sectionsFor(new Gx("Button"), mod.agus.jcoderz.beans.ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON)) {
            button.addAll(section.names());
        }
        Set<String> card = new HashSet<>();
        for (AttributeCatalog.Section section : AttributeCatalog.sectionsFor(new Gx("LinearLayout"), mod.agus.jcoderz.beans.ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW)) {
            card.addAll(section.names());
        }

        assertTrue(button.contains("app:cornerRadius"));
        assertTrue(button.contains("app:iconGravity"));
        assertTrue(card.contains("app:cardCornerRadius"));
        assertFalse(names("Button").contains("app:cornerRadius"));
    }

    @Test
    public void everyViewGetsTheCommonAttributes() {
        Set<String> names = names("ImageView");

        assertTrue(names.contains("android:contentDescription"));
        assertTrue(names.contains("android:visibility"));
        assertTrue(names.contains("android:rotationX"));
        assertTrue(names.contains("android:elevation"));
    }

    @Test
    public void attributesFollowTheKindOfView() {
        assertTrue(names("TextView").contains("android:letterSpacing"));
        assertFalse(names("ImageView").contains("android:letterSpacing"));
        assertTrue(names("ImageView").contains("android:adjustViewBounds"));
        assertFalse(names("TextView").contains("android:adjustViewBounds"));
        assertTrue(names("GridView").contains("android:stretchMode"));
        assertFalse(names("TextView").contains("android:stretchMode"));
        assertTrue(names("EditText").contains("android:selectAllOnFocus"));
        assertFalse(names("Button").contains("android:selectAllOnFocus"));
        assertTrue(names("Button").contains("android:ellipsize"));
        assertTrue(names("ScrollView").contains("android:fillViewport"));
        assertTrue(names("LinearLayout").contains("android:baselineAligned"));
        assertFalse(names("RelativeLayout").contains("android:baselineAligned"));
    }

    @Test
    public void noAttributeAppearsTwiceForTheSameView() {
        for (String viewClass : List.of("TextView", "EditText", "ImageView", "GridView", "ListView", "LinearLayout", "ScrollView", "CheckBox", "Button")) {
            Set<String> seen = new HashSet<>();
            for (AttributeCatalog.Section section : AttributeCatalog.sectionsFor(new Gx(viewClass))) {
                for (String name : section.names()) {
                    assertTrue(viewClass + " lists " + name + " twice", seen.add(name));
                }
            }
        }
    }

    @Test
    public void attributesTheEditorAlreadyHasRowsForAreNotRepeated() {
        List<String> native_ = List.of("android:text", "android:textSize", "android:textColor", "android:hint", "android:alpha",
                "android:rotation", "android:scaleX", "android:scaleY", "android:translationX", "android:translationY",
                "android:layout_width", "android:layout_height", "android:padding", "android:layout_margin", "android:weightSum",
                "android:layout_weight", "android:layout_gravity", "android:orientation_", "android:src", "android:scaleType",
                "android:enabled", "android:checked", "android:inputType", "android:imeOptions", "android:singleLine",
                "android:lines", "android:background", "android:id", "android:max", "android:progress");
        for (String viewClass : List.of("TextView", "EditText", "ImageView", "GridView", "LinearLayout", "CheckBox")) {
            for (String name : names(viewClass)) {
                assertFalse(viewClass + " repeats a native property: " + name, native_.contains(name));
            }
        }
    }

    @Test
    public void labelsAreReadableFromTheAttributeNames() {
        assertEquals("Min width", attr("TextView", "android:minWidth").label());
        assertEquals("Margin start", attr("TextView", "android:layout_marginStart").label());
        assertEquals("Letter spacing", attr("TextView", "android:letterSpacing").label());
        assertEquals("Transform pivot x", attr("TextView", "android:transformPivotX").label());
    }

    @Test
    public void everySectionHasAttributesForTheViewsItIsShownFor() {
        for (AttributeCatalog.Section section : AttributeCatalog.sectionsFor(new Gx("TextView"))) {
            assertFalse(section.key(), section.attrs().isEmpty());
        }
    }

    @Test
    public void dimensionsAreChecked() {
        AttributeCatalog.Attr attr = attr("TextView", "android:minWidth");

        assertNull(attr.problemWith("48dp"));
        assertNull(attr.problemWith("@dimen/min"));
        assertNull(attr.problemWith("0.5sp"));
        assertNotNull(attr.problemWith("48"));
        assertNotNull(attr.problemWith("48 dp"));
        assertNull(attr.problemWith(""));
    }

    @Test
    public void numbersColorsAndChoicesAreChecked() {
        assertNull(attr("TextView", "android:maxLines").problemWith("3"));
        assertNotNull(attr("TextView", "android:maxLines").problemWith("3.5"));
        assertNull(attr("TextView", "android:letterSpacing").problemWith("0.05"));
        assertNull(attr("TextView", "android:textColorLink").problemWith("#FF5722"));
        assertNull(attr("TextView", "android:textColorLink").problemWith("@color/link"));
        assertNotNull(attr("TextView", "android:textColorLink").problemWith("red"));
        assertNull(attr("TextView", "android:ellipsize").problemWith("end"));
        assertNotNull(attr("TextView", "android:ellipsize").problemWith("finish"));
        assertNull(attr("TextView", "android:visibility") == null ? null : attr("TextView", "android:visibility").problemWith("gone"));
    }

    @Test
    public void flagsMayBeCombined() {
        AttributeCatalog.Attr attr = attr("TextView", "android:foregroundGravity");

        assertNull(attr.problemWith("center|fill"));
        assertNotNull(attr.problemWith("center|middle"));
    }

    @Test
    public void valuesThatGenerationWouldMangleAreRefused() {
        // Spaces are stripped from the custom attributes when the layout is generated; quotes would break the XML.
        AttributeCatalog.Attr attr = attr("TextView", "android:contentDescription");

        assertNotNull(attr.problemWith("hello world"));
        assertNotNull(attr.problemWith("say\"hi"));
        assertNull(attr.problemWith("hello"));
    }
}
