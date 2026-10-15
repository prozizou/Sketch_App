package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * An icon as paths, enough to draw a preview and to write an Android vector drawable. It is built from an SVG (by
 * {@link SvgToVector}) or read back from a vector drawable, so what the preview shows is what the file will contain.
 *
 * @param width          the drawable's size in dp
 * @param height         the drawable's size in dp
 * @param viewportWidth  the coordinate space of the paths
 * @param viewportHeight the coordinate space of the paths
 */
public record VectorModel(float width, float height, float viewportWidth, float viewportHeight, @NonNull List<Shape> shapes) {

    /**
     * @param pathData    SVG/Android path data
     * @param fillColor   ARGB, 0 for no fill
     * @param strokeColor ARGB, 0 for no stroke
     * @param strokeWidth in viewport units
     * @param strokeCap   {@code butt}, {@code round} or {@code square}
     * @param strokeJoin  {@code miter}, {@code round} or {@code bevel}
     * @param evenOdd     fill rule
     * @param translateX  offset of the group the path was in
     * @param translateY  offset of the group the path was in
     */
    public record Shape(@NonNull String pathData, int fillColor, int strokeColor, float strokeWidth, @NonNull String strokeCap,
                        @NonNull String strokeJoin, boolean evenOdd, float translateX, float translateY) {
    }

    /** The Android vector drawable XML for this model. */
    public String toXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        xml.append("    android:width=\"").append(number(width)).append("dp\"\n");
        xml.append("    android:height=\"").append(number(height)).append("dp\"\n");
        xml.append("    android:viewportWidth=\"").append(number(viewportWidth)).append("\"\n");
        xml.append("    android:viewportHeight=\"").append(number(viewportHeight)).append("\">\n");
        for (Shape shape : shapes) {
            boolean grouped = shape.translateX() != 0 || shape.translateY() != 0;
            String indent = grouped ? "        " : "    ";
            if (grouped) {
                xml.append("    <group\n        android:translateX=\"").append(number(shape.translateX()))
                        .append("\"\n        android:translateY=\"").append(number(shape.translateY())).append("\">\n");
            }
            xml.append(indent).append("<path\n");
            if (shape.fillColor() != 0) xml.append(indent).append("    android:fillColor=\"").append(color(shape.fillColor())).append("\"\n");
            if (shape.evenOdd()) xml.append(indent).append("    android:fillType=\"evenOdd\"\n");
            if (shape.strokeColor() != 0) {
                xml.append(indent).append("    android:strokeColor=\"").append(color(shape.strokeColor())).append("\"\n");
                xml.append(indent).append("    android:strokeWidth=\"").append(number(shape.strokeWidth())).append("\"\n");
                if (!shape.strokeCap().equals("butt")) xml.append(indent).append("    android:strokeLineCap=\"").append(shape.strokeCap()).append("\"\n");
                if (!shape.strokeJoin().equals("miter")) xml.append(indent).append("    android:strokeLineJoin=\"").append(shape.strokeJoin()).append("\"\n");
            }
            xml.append(indent).append("    android:pathData=\"").append(escapeAttribute(shape.pathData())).append("\"/>\n");
            if (grouped) xml.append("    </group>\n");
        }
        xml.append("</vector>\n");
        return xml.toString();
    }

    /** {@code 24}, {@code 0.5}: no trailing zeros. */
    static String number(float value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        String text = String.format(Locale.ROOT, "%.4f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** {@code #AARRGGBB} */
    static String color(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    private static String escapeAttribute(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
