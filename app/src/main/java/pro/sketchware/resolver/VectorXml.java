package pro.sketchware.resolver;

import androidx.annotation.Nullable;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads an Android vector drawable back into a {@link VectorModel}, only to draw its preview. Colours that are
 * references or theme attributes can't be resolved here and are drawn in a neutral grey; the file is never changed.
 */
public final class VectorXml {
    private static final int NEUTRAL = 0xFF808080;

    private VectorXml() {
    }

    @Nullable
    public static VectorModel read(String xml) {
        float[] size = new float[4];
        List<VectorModel.Shape> shapes = new ArrayList<>();
        try {
            SafeXml.parse(xml, new DefaultHandler() {
                private int depth;
                private float tx;
                private float ty;
                private final java.util.ArrayDeque<float[]> groups = new java.util.ArrayDeque<>();

                @Override
                public void startElement(String uri, String localName, String qName, Attributes a) {
                    depth++;
                    switch (qName) {
                        case "vector" -> {
                            size[0] = dimension(a.getValue("android:width"));
                            size[1] = dimension(a.getValue("android:height"));
                            size[2] = number(a.getValue("android:viewportWidth"));
                            size[3] = number(a.getValue("android:viewportHeight"));
                        }
                        case "group" -> {
                            groups.push(new float[]{tx, ty});
                            tx += number(a.getValue("android:translateX"));
                            ty += number(a.getValue("android:translateY"));
                        }
                        case "path" -> {
                            String data = a.getValue("android:pathData");
                            if (data == null || data.startsWith("@")) return;
                            int fill = color(a.getValue("android:fillColor"));
                            int stroke = color(a.getValue("android:strokeColor"));
                            shapes.add(new VectorModel.Shape(data, fill, stroke, number(a.getValue("android:strokeWidth")),
                                    orDefault(a.getValue("android:strokeLineCap"), "butt"), orDefault(a.getValue("android:strokeLineJoin"), "miter"),
                                    "evenOdd".equals(a.getValue("android:fillType")), tx, ty));
                        }
                        default -> {
                        }
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    if (qName.equals("group") && !groups.isEmpty()) {
                        float[] saved = groups.pop();
                        tx = saved[0];
                        ty = saved[1];
                    }
                    depth--;
                }
            });
        } catch (SafeXml.InvalidXml | RuntimeException e) {
            return null;
        }
        if (size[2] <= 0 || size[3] <= 0 || shapes.isEmpty()) return null;
        return new VectorModel(size[0] > 0 ? size[0] : size[2], size[1] > 0 ? size[1] : size[3], size[2], size[3], shapes);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static float number(String value) {
        if (value == null) return 0;
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static float dimension(String value) {
        return value == null ? 0 : number(value.trim().replaceAll("[a-zA-Z]+$", ""));
    }

    private static int color(String value) {
        if (value == null || value.isBlank()) return 0;
        String v = value.trim();
        try {
            if (v.matches("#[0-9a-fA-F]{6}")) return 0xFF000000 | (int) Long.parseLong(v.substring(1), 16);
            if (v.matches("#[0-9a-fA-F]{8}")) return (int) Long.parseLong(v.substring(1), 16);
            if (v.matches("#[0-9a-fA-F]{3}")) {
                return 0xFF000000 | (int) Long.parseLong("" + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2) + v.charAt(3) + v.charAt(3), 16);
            }
        } catch (NumberFormatException ignored) {
            // falls through to the neutral colour
        }
        return NEUTRAL;
    }
}
