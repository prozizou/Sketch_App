package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Looks inside a file a person (or the network) provides and says what it really is, by its content and not its name:
 * a PNG or WebP image (with its size), a vector drawable, an adaptive icon, another drawable XML, or an SVG that is
 * converted to a vector drawable. Anything else is refused with the reason. Nothing is written here.
 */
public final class FileInspector {
    public enum Format {PNG, WEBP, VECTOR_XML, ADAPTIVE_ICON_XML, DRAWABLE_XML, SVG, INVALID}

    public static final int MAX_RASTER_BYTES = 5 * 1024 * 1024;
    public static final int MAX_XML_BYTES = 512 * 1024;
    public static final int MAX_PIXELS_SIDE = 4096;

    private static final Set<String> DRAWABLE_ROOTS = Set.of("shape", "selector", "layer-list", "bitmap", "nine-patch", "inset", "clip",
            "level-list", "ripple", "rotate", "scale", "transition", "animated-vector", "animated-selector", "animation-list");
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /**
     * What a file turned out to be.
     *
     * @param format    its kind; {@link Format#INVALID} when {@code problem} is set
     * @param width     pixels for an image, 0 otherwise
     * @param height    pixels for an image, 0 otherwise
     * @param problem   why it can't be used, or null
     * @param content   what to write: the file's own bytes, or the converted vector for an SVG
     * @param extension the extension to write it with (".png", ".webp", ".xml")
     * @param preview   the drawing to preview, for a vector or SVG when it could be read
     * @param notes     things worth knowing that don't stop the import (a very large image, ...)
     */
    public record Inspection(@NonNull Format format, int width, int height, @Nullable String problem, @NonNull byte[] content,
                             @NonNull String extension, @Nullable VectorModel preview, @NonNull List<String> notes) {
        public boolean ok() {
            return problem == null;
        }

        static Inspection invalid(String problem) {
            return new Inspection(Format.INVALID, 0, 0, problem, new byte[0], "", null, List.of());
        }
    }

    private FileInspector() {
    }

    /**
     * @param type      the resource type the file is for ({@code drawable} or {@code mipmap})
     * @param bytes     the file
     * @param svgColour the colour for an SVG's {@code currentColor} and unset fills ({@code #RRGGBB}), null for black
     */
    @NonNull
    public static Inspection inspect(String type, byte[] bytes, @Nullable String svgColour) {
        if (bytes == null || bytes.length == 0) return Inspection.invalid("The file is empty");
        if (isPng(bytes)) return inspectPng(bytes);
        if (isWebp(bytes)) return inspectWebp(bytes);
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return Inspection.invalid("JPEG images aren't supported here: use PNG or WebP");
        }
        if (looksLikeText(bytes)) return inspectXml(type, bytes, svgColour);
        return Inspection.invalid("This isn't a PNG, WebP, vector drawable XML or SVG file");
    }

    // ---- images ----

    private static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 16 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static Inspection inspectPng(byte[] b) {
        if (b.length > MAX_RASTER_BYTES) return Inspection.invalid("The image is larger than 5 MB");
        // signature, then IHDR: length 13, "IHDR", width, height
        if (b.length < 33 || be32(b, 8) != 13 || b[12] != 'I' || b[13] != 'H' || b[14] != 'D' || b[15] != 'R') {
            return Inspection.invalid("The PNG header is damaged");
        }
        int width = be32(b, 16);
        int height = be32(b, 20);
        // a PNG ends with an empty IEND chunk: a download that was cut short doesn't
        boolean ended = b.length >= 12 && b[b.length - 8] == 'I' && b[b.length - 7] == 'E' && b[b.length - 6] == 'N' && b[b.length - 5] == 'D';
        if (!ended) return Inspection.invalid("The PNG is incomplete (it doesn't end properly)");
        return raster(Format.PNG, width, height, b, ".png");
    }

    private static Inspection inspectWebp(byte[] b) {
        if (b.length > MAX_RASTER_BYTES) return Inspection.invalid("The image is larger than 5 MB");
        int width;
        int height;
        if (b[12] == 'V' && b[13] == 'P' && b[14] == '8' && b[15] == ' ' && b.length >= 30 && (b[23] & 0xFF) == 0x9D && b[24] == 0x01 && b[25] == 0x2A) {
            width = le16(b, 26) & 0x3FFF;
            height = le16(b, 28) & 0x3FFF;
        } else if (b[12] == 'V' && b[13] == 'P' && b[14] == '8' && b[15] == 'L' && b.length >= 25 && (b[20] & 0xFF) == 0x2F) {
            width = 1 + ((b[21] & 0xFF) | ((b[22] & 0x3F) << 8));
            height = 1 + (((b[22] & 0xC0) >> 6) | ((b[23] & 0xFF) << 2) | ((b[24] & 0x0F) << 10));
        } else if (b[12] == 'V' && b[13] == 'P' && b[14] == '8' && b[15] == 'X' && b.length >= 30) {
            width = 1 + le24(b, 24);
            height = 1 + le24(b, 27);
        } else {
            return Inspection.invalid("The WebP file is damaged or uses an unknown format");
        }
        return raster(Format.WEBP, width, height, b, ".webp");
    }

    private static Inspection raster(Format format, int width, int height, byte[] bytes, String extension) {
        if (width < 1 || height < 1) return Inspection.invalid("The image has no size");
        if (width > MAX_PIXELS_SIDE || height > MAX_PIXELS_SIDE) {
            return Inspection.invalid("The image is " + width + "×" + height + " px; the largest accepted side is " + MAX_PIXELS_SIDE + " px");
        }
        List<String> notes = new ArrayList<>();
        if (Math.max(width, height) > 1024) notes.add("Large image (" + width + "×" + height + " px): it makes the app bigger and uses more memory.");
        return new Inspection(format, width, height, null, bytes, extension, null, notes);
    }

    private static int be32(byte[] b, int i) {
        return ((b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
    }

    private static int le16(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8);
    }

    private static int le24(byte[] b, int i) {
        return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8) | ((b[i + 2] & 0xFF) << 16);
    }

    // ---- XML ----

    private static boolean looksLikeText(byte[] b) {
        int i = 0;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) i = 3;
        while (i < b.length && (b[i] == ' ' || b[i] == '\n' || b[i] == '\r' || b[i] == '\t')) i++;
        return i < b.length && b[i] == '<';
    }

    private static Inspection inspectXml(String type, byte[] bytes, String svgColour) {
        if (bytes.length > MAX_XML_BYTES) return Inspection.invalid("The XML file is larger than 512 KB");
        String xml = new String(bytes, StandardCharsets.UTF_8);
        if (xml.startsWith("\uFEFF")) xml = xml.substring(1);
        Root root = new Root();
        try {
            SafeXml.parse(xml, root);
        } catch (SafeXml.InvalidXml e) {
            return Inspection.invalid("The XML isn't valid" + (e.line() > 0 ? " (line " + e.line() + ")" : "") + ": " + e.getMessage());
        }
        if (root.name == null) return Inspection.invalid("The XML has no element");
        switch (root.name) {
            case "svg" -> {
                try {
                    VectorModel model = SvgToVector.convert(xml, svgColour);
                    return new Inspection(Format.SVG, 0, 0, null, model.toXml().getBytes(StandardCharsets.UTF_8), ".xml", model, List.of());
                } catch (SvgToVector.Unsupported e) {
                    return Inspection.invalid(e.getMessage());
                }
            }
            case "vector" -> {
                String problem = vectorProblem(root);
                if (problem != null) return Inspection.invalid(problem);
                return new Inspection(Format.VECTOR_XML, 0, 0, null, bytes, ".xml", VectorXml.read(xml), List.of());
            }
            case "adaptive-icon" -> {
                if (!type.equals("mipmap")) return Inspection.invalid("An adaptive icon belongs in mipmap, not in " + type);
                if (!root.children.contains("foreground")) return Inspection.invalid("The adaptive icon has no <foreground>");
                if (!ANDROID_NS.equals(root.attributes.get("xmlns:android"))) return Inspection.invalid("The android namespace is missing on <adaptive-icon>");
                return new Inspection(Format.ADAPTIVE_ICON_XML, 0, 0, null, bytes, ".xml", null, List.of());
            }
            default -> {
                if (DRAWABLE_ROOTS.contains(root.name)) {
                    if (!ANDROID_NS.equals(root.attributes.get("xmlns:android"))) {
                        return Inspection.invalid("The android namespace (xmlns:android) is missing on <" + root.name + ">");
                    }
                    return new Inspection(Format.DRAWABLE_XML, 0, 0, null, bytes, ".xml", null, List.of());
                }
                return Inspection.invalid("<" + root.name + "> is not a drawable");
            }
        }
    }

    private static String vectorProblem(Root root) {
        if (!ANDROID_NS.equals(root.attributes.get("xmlns:android"))) return "The android namespace (xmlns:android) is missing on <vector>";
        for (String attribute : new String[]{"android:width", "android:height", "android:viewportWidth", "android:viewportHeight"}) {
            String value = root.attributes.get(attribute);
            if (value == null || value.isBlank()) return "The vector has no " + attribute;
            if (attribute.startsWith("android:viewport")) {
                try {
                    if (Float.parseFloat(value.trim()) <= 0) return attribute + " must be more than 0";
                } catch (NumberFormatException e) {
                    return attribute + " must be a number";
                }
            }
        }
        if (!root.descendants.contains("path")) return "The vector has no <path>";
        return null;
    }

    private static final class Root extends DefaultHandler {
        String name;
        final java.util.Map<String, String> attributes = new java.util.HashMap<>();
        final Set<String> children = new java.util.HashSet<>();
        final Set<String> descendants = new java.util.HashSet<>();
        private int depth;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes a) {
            depth++;
            if (depth == 1) {
                name = qName;
                for (int i = 0; i < a.getLength(); i++) attributes.put(a.getQName(i), a.getValue(i));
            } else {
                descendants.add(qName);
                if (depth == 2) children.add(qName);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            depth--;
        }
    }
}
