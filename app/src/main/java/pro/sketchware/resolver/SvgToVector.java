package pro.sketchware.resolver;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an SVG into an Android vector drawable, for the SVG subset an icon uses: paths, the basic shapes, groups that
 * only carry colours and a translation, fill and stroke. Anything else (images, text, filters, masks, gradients,
 * scripts, links, other transforms) is refused with a message instead of being dropped, because a silently different
 * icon is worse than none. The SVG is read with {@link SafeXml}, so nothing in it is ever fetched or run.
 */
public final class SvgToVector {
    /** The SVG uses something an Android vector can't show, or isn't an SVG. */
    public static final class Unsupported extends Exception {
        public Unsupported(String message) {
            super(message);
        }
    }

    public static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_SHAPES = 1000;
    private static final Pattern PATH_DATA = Pattern.compile("[MmLlHhVvCcSsQqTtAaZz0-9eE+\\-.,\\s]*");
    private static final String NUMBER = "[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?";
    private static final Pattern TRANSLATE = Pattern.compile("\\s*translate\\(\\s*(?<x>" + NUMBER + ")(?:[\\s,]+(?<y>" + NUMBER + "))?\\s*\\)\\s*");
    private static final Map<String, Integer> NAMED = new HashMap<>();

    static {
        NAMED.put("black", 0xFF000000);
        NAMED.put("white", 0xFFFFFFFF);
        NAMED.put("red", 0xFFFF0000);
        NAMED.put("green", 0xFF008000);
        NAMED.put("blue", 0xFF0000FF);
        NAMED.put("yellow", 0xFFFFFF00);
        NAMED.put("orange", 0xFFFFA500);
        NAMED.put("gray", 0xFF808080);
        NAMED.put("grey", 0xFF808080);
        NAMED.put("purple", 0xFF800080);
        NAMED.put("pink", 0xFFFFC0CB);
        NAMED.put("transparent", 0);
    }

    private SvgToVector() {
    }

    /**
     * @param svg       the SVG text
     * @param fillColor the colour of what has no colour of its own and of {@code currentColor}, as {@code #RRGGBB} or {@code #AARRGGBB}
     * @throws Unsupported what can't be converted, in words
     */
    public static VectorModel convert(String svg, String fillColor) throws Unsupported {
        if (svg == null || svg.isBlank()) throw new Unsupported("The SVG is empty");
        if (svg.length() > MAX_BYTES) throw new Unsupported("The SVG is too large (256 KB at most)");
        int base = androidColor(fillColor == null ? "#FF000000" : fillColor);
        Converter converter = new Converter(base);
        try {
            SafeXml.parse(svg, converter);
        } catch (SafeXml.InvalidXml e) {
            throw new Unsupported("Not a valid SVG: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new Unsupported("Not a valid SVG: " + e.getMessage());
        }
        if (converter.failure != null) throw new Unsupported(converter.failure);
        if (!converter.sawSvg) throw new Unsupported("The file is not an SVG");
        if (converter.shapes.isEmpty()) throw new Unsupported("The SVG has no shapes to draw");
        return new VectorModel(converter.width, converter.height, converter.viewportWidth, converter.viewportHeight, List.copyOf(converter.shapes));
    }

    /** The colour the user picks, the way Android writes it: {@code #RRGGBB} or {@code #AARRGGBB}. */
    public static int androidColor(String value) throws Unsupported {
        String hex = value == null ? "" : value.trim();
        if (hex.matches("#[0-9a-fA-F]{6}")) return 0xFF000000 | (int) Long.parseLong(hex.substring(1), 16);
        if (hex.matches("#[0-9a-fA-F]{8}")) return (int) Long.parseLong(hex.substring(1), 16);
        throw new Unsupported("\"" + value + "\" is not a colour (use #RRGGBB or #AARRGGBB)");
    }

    /** A colour as SVG writes it, or the default for an empty value. @throws IllegalArgumentException for one it doesn't know */
    static int parseColor(String value, int currentColor) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals("currentcolor")) return currentColor;
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            if (hex.matches("[0-9a-f]{3}")) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
            if (hex.matches("[0-9a-f]{6}")) return 0xFF000000 | (int) Long.parseLong(hex, 16);
            if (hex.matches("[0-9a-f]{8}")) {
                // SVG 2 writes #RRGGBBAA; an Android colour is #AARRGGBB.
                long parsed = Long.parseLong(hex, 16);
                return (int) (((parsed & 0xFF) << 24) | (parsed >>> 8));
            }
            throw new IllegalArgumentException("colour " + value);
        }
        Matcher rgb = Pattern.compile("rgb\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)").matcher(v);
        if (rgb.matches()) {
            int r = Integer.parseInt(rgb.group(1));
            int g = Integer.parseInt(rgb.group(2));
            int b = Integer.parseInt(rgb.group(3));
            if (r > 255 || g > 255 || b > 255) throw new IllegalArgumentException("colour " + value);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        Integer named = NAMED.get(v);
        if (named != null) return named;
        throw new IllegalArgumentException("colour " + value);
    }

    private static final class Style {
        int fill;
        int stroke;
        float strokeWidth = 1;
        String cap = "butt";
        String join = "miter";
        boolean evenOdd;
        float opacity = 1;
        float translateX;
        float translateY;

        Style copy() {
            Style style = new Style();
            style.fill = fill;
            style.stroke = stroke;
            style.strokeWidth = strokeWidth;
            style.cap = cap;
            style.join = join;
            style.evenOdd = evenOdd;
            style.opacity = opacity;
            style.translateX = translateX;
            style.translateY = translateY;
            return style;
        }
    }

    private static final class Converter extends DefaultHandler {
        final int base;
        final List<VectorModel.Shape> shapes = new ArrayList<>();
        final Deque<Style> styles = new ArrayDeque<>();
        final Deque<String> tags = new ArrayDeque<>();
        String failure;
        boolean sawSvg;
        float width = 24, height = 24, viewportWidth = 24, viewportHeight = 24;

        Converter(int base) {
            this.base = base;
        }

        private void fail(String message) throws SAXException {
            failure = message;
            throw new SAXException(message);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes a) throws SAXException {
            String tag = qName;
            int colon = tag.indexOf(':');
            if (colon >= 0) tag = tag.substring(colon + 1);
            tags.push(tag);
            if (!sawSvg) {
                if (!tag.equals("svg")) fail("The root element is <" + tag + ">, not <svg>");
                sawSvg = true;
                readSvgRoot(a);
                Style style = new Style();
                style.fill = base;
                styles.push(inherit(style, a));
                return;
            }
            Style style = styles.peek().copy();
            switch (tag) {
                case "title", "desc", "metadata" -> {
                    styles.push(style);
                }
                case "defs" -> {
                    styles.push(style);
                }
                case "g" -> styles.push(inherit(withTransform(style, a), a));
                case "path", "rect", "circle", "ellipse", "line", "polyline", "polygon" -> {
                    Style own = inherit(withTransform(style, a), a);
                    styles.push(own);
                    String data = shapeData(tag, a);
                    if (data != null) addShape(data, own, tag);
                }
                default -> fail("<" + tag + "> is not supported in an Android vector drawable");
            }
            if (shapes.size() > MAX_SHAPES) fail("The SVG has too many shapes");
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (!styles.isEmpty()) styles.pop();
            if (!tags.isEmpty()) tags.pop();
        }

        private void readSvgRoot(Attributes a) throws SAXException {
            String viewBox = a.getValue("viewBox");
            float vw = 0, vh = 0;
            if (viewBox != null) {
                String[] parts = viewBox.trim().split("[\\s,]+");
                if (parts.length != 4) fail("The viewBox is not four numbers");
                try {
                    float minX = Float.parseFloat(parts[0]);
                    float minY = Float.parseFloat(parts[1]);
                    vw = Float.parseFloat(parts[2]);
                    vh = Float.parseFloat(parts[3]);
                    if (minX != 0 || minY != 0) fail("A viewBox that doesn't start at 0 0 isn't supported");
                } catch (NumberFormatException e) {
                    fail("The viewBox is not four numbers");
                }
            }
            float w = length(a.getValue("width"));
            float h = length(a.getValue("height"));
            if (vw <= 0 || vh <= 0) {
                if (w <= 0 || h <= 0) fail("The SVG has no viewBox and no width and height");
                vw = w;
                vh = h;
            }
            if (w <= 0 || h <= 0) {
                w = vw;
                h = vh;
            }
            width = w;
            height = h;
            viewportWidth = vw;
            viewportHeight = vh;
        }

        /** A width or height as a plain number of dp; "24", "24px", "24dp" are fine, percentages are not. */
        private float length(String value) throws SAXException {
            if (value == null || value.isBlank()) return 0;
            String v = value.trim().toLowerCase(Locale.ROOT).replaceAll("(px|dp|pt)$", "");
            if (v.endsWith("%")) fail("A percentage size isn't supported");
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                fail("The size \"" + value + "\" isn't supported");
                return 0;
            }
        }

        private Style withTransform(Style style, Attributes a) throws SAXException {
            String transform = a.getValue("transform");
            if (transform == null || transform.isBlank()) return style;
            Matcher m = TRANSLATE.matcher(transform);
            if (!m.matches()) fail("Only translate() is supported as a transform");
            style.translateX += Float.parseFloat(m.group("x"));
            style.translateY += m.group("y") == null ? 0 : Float.parseFloat(m.group("y"));
            return style;
        }

        /** Applies the presentation attributes of an element (and of its {@code style} attribute) on top of what it inherits. */
        private Style inherit(Style style, Attributes a) throws SAXException {
            Map<String, String> props = new HashMap<>();
            for (int i = 0; i < a.getLength(); i++) props.put(a.getQName(i), a.getValue(i));
            String css = a.getValue("style");
            if (css != null) {
                for (String declaration : css.split(";")) {
                    int colon = declaration.indexOf(':');
                    if (colon > 0) props.put(declaration.substring(0, colon).trim(), declaration.substring(colon + 1).trim());
                }
            }
            try {
                String fill = props.get("fill");
                if (fill != null) {
                    if (fill.contains("url(")) fail("A gradient or pattern fill isn't supported");
                    style.fill = fill.trim().equalsIgnoreCase("none") ? 0 : parseColor(fill, base);
                }
                String stroke = props.get("stroke");
                if (stroke != null) {
                    if (stroke.contains("url(")) fail("A gradient or pattern stroke isn't supported");
                    style.stroke = stroke.trim().equalsIgnoreCase("none") ? 0 : parseColor(stroke, base);
                }
                if (props.containsKey("stroke-width")) style.strokeWidth = Float.parseFloat(props.get("stroke-width").trim());
                String cap = props.get("stroke-linecap");
                if (cap != null) {
                    if (!List.of("butt", "round", "square").contains(cap.trim())) fail("stroke-linecap \"" + cap + "\" isn't supported");
                    style.cap = cap.trim();
                }
                String join = props.get("stroke-linejoin");
                if (join != null) {
                    if (!List.of("miter", "round", "bevel").contains(join.trim())) fail("stroke-linejoin \"" + join + "\" isn't supported");
                    style.join = join.trim();
                }
                String rule = props.get("fill-rule");
                if (rule != null) style.evenOdd = rule.trim().equals("evenodd");
                String opacity = props.get("opacity");
                if (opacity != null) style.opacity *= clamp(Float.parseFloat(opacity.trim()));
                String fillOpacity = props.get("fill-opacity");
                if (fillOpacity != null) style.opacity *= clamp(Float.parseFloat(fillOpacity.trim()));
            } catch (IllegalArgumentException e) {
                fail("Unsupported value: " + e.getMessage());
            }
            return style;
        }

        private static float clamp(float v) {
            return Math.max(0, Math.min(1, v));
        }

        private String shapeData(String tag, Attributes a) throws SAXException {
            switch (tag) {
                case "path" -> {
                    String d = a.getValue("d");
                    if (d == null || d.isBlank()) return null;
                    if (!PATH_DATA.matcher(d).matches()) fail("The path data has characters a path can't have");
                    return d.trim().replaceAll("\\s+", " ");
                }
                case "rect" -> {
                    float x = num(a, "x"), y = num(a, "y"), w = num(a, "width"), h = num(a, "height");
                    float rx = num(a, "rx"), ry = a.getValue("ry") == null ? rx : num(a, "ry");
                    if (a.getValue("rx") == null && a.getValue("ry") != null) rx = ry;
                    if (w <= 0 || h <= 0) return null;
                    rx = Math.min(rx, w / 2);
                    ry = Math.min(ry, h / 2);
                    if (rx <= 0 || ry <= 0) {
                        return "M" + n(x) + "," + n(y) + "h" + n(w) + "v" + n(h) + "h" + n(-w) + "z";
                    }
                    return "M" + n(x + rx) + "," + n(y) + "h" + n(w - 2 * rx) + "a" + n(rx) + "," + n(ry) + " 0 0 1 " + n(rx) + "," + n(ry)
                            + "v" + n(h - 2 * ry) + "a" + n(rx) + "," + n(ry) + " 0 0 1 " + n(-rx) + "," + n(ry)
                            + "h" + n(-(w - 2 * rx)) + "a" + n(rx) + "," + n(ry) + " 0 0 1 " + n(-rx) + "," + n(-ry)
                            + "v" + n(-(h - 2 * ry)) + "a" + n(rx) + "," + n(ry) + " 0 0 1 " + n(rx) + "," + n(-ry) + "z";
                }
                case "circle" -> {
                    float cx = num(a, "cx"), cy = num(a, "cy"), r = num(a, "r");
                    return r <= 0 ? null : ellipse(cx, cy, r, r);
                }
                case "ellipse" -> {
                    float cx = num(a, "cx"), cy = num(a, "cy"), rx = num(a, "rx"), ry = num(a, "ry");
                    return rx <= 0 || ry <= 0 ? null : ellipse(cx, cy, rx, ry);
                }
                case "line" -> {
                    return "M" + n(num(a, "x1")) + "," + n(num(a, "y1")) + "L" + n(num(a, "x2")) + "," + n(num(a, "y2"));
                }
                case "polyline", "polygon" -> {
                    String points = a.getValue("points");
                    if (points == null || points.isBlank()) return null;
                    String[] parts = points.trim().split("[\\s,]+");
                    if (parts.length < 4 || parts.length % 2 != 0) fail("The points of a <" + tag + "> aren't pairs of numbers");
                    StringBuilder d = new StringBuilder();
                    for (int i = 0; i < parts.length; i += 2) {
                        try {
                            d.append(i == 0 ? "M" : "L").append(n(Float.parseFloat(parts[i]))).append(",").append(n(Float.parseFloat(parts[i + 1])));
                        } catch (NumberFormatException e) {
                            fail("The points of a <" + tag + "> aren't numbers");
                        }
                    }
                    return tag.equals("polygon") ? d + "z" : d.toString();
                }
                default -> {
                    return null;
                }
            }
        }

        private static String ellipse(float cx, float cy, float rx, float ry) {
            return "M" + n(cx - rx) + "," + n(cy) + "a" + n(rx) + "," + n(ry) + " 0 1,0 " + n(2 * rx) + ",0"
                    + "a" + n(rx) + "," + n(ry) + " 0 1,0 " + n(-2 * rx) + ",0z";
        }

        private float num(Attributes a, String name) throws SAXException {
            String value = a.getValue(name);
            if (value == null || value.isBlank()) return 0;
            String v = value.trim();
            if (v.endsWith("%")) fail("A percentage in \"" + name + "\" isn't supported");
            v = v.replaceAll("(px)$", "");
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                fail("The value of \"" + name + "\" (" + value + ") isn't a number");
                return 0;
            }
        }

        private static String n(float value) {
            return VectorModel.number(value);
        }

        private void addShape(String data, Style style, String tag) {
            boolean open = tag.equals("line") || tag.equals("polyline");
            int fill = open ? 0 : style.fill;
            int stroke = style.stroke;
            if (fill == 0 && stroke == 0) return; // draws nothing
            shapes.add(new VectorModel.Shape(data, withOpacity(fill, style.opacity), withOpacity(stroke, style.opacity),
                    style.strokeWidth, style.cap, style.join, style.evenOdd, style.translateX, style.translateY));
        }

        private static int withOpacity(int argb, float opacity) {
            if (argb == 0 || opacity >= 1) return argb;
            int alpha = Math.round(((argb >>> 24) & 0xFF) * opacity);
            return (alpha << 24) | (argb & 0x00FFFFFF);
        }
    }
}
