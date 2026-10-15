package pro.sketchware.resolver;

import java.util.List;

/**
 * Where a file goes in {@code res}: the folder for its type and the qualifier that fits what it is. Sketchware keeps
 * its own images and the launcher icon in the {@code xhdpi} folders, so a bitmap goes there unless its size says it is
 * a launcher icon of another density.
 */
public final class DestinationRules {
    /** Every qualifier the resolver writes to; it never invents any other. */
    public static final List<String> RASTER_QUALIFIERS = List.of("-xhdpi", "-mdpi", "-hdpi", "-xxhdpi", "-xxxhdpi", "-nodpi", "");
    public static final String VECTOR_MIPMAP_QUALIFIER = "-anydpi";
    public static final String ADAPTIVE_QUALIFIER = "-anydpi-v26";

    private DestinationRules() {
    }

    /** The qualifier a file gets by default. */
    public static String defaultQualifier(String type, FileInspector.Format format, int width, int height) {
        switch (format) {
            case PNG, WEBP -> {
                if (type.equals("mipmap")) {
                    int side = Math.max(width, height);
                    if (width == height) {
                        switch (side) {
                            case 48 -> {
                                return "-mdpi";
                            }
                            case 72 -> {
                                return "-hdpi";
                            }
                            case 96 -> {
                                return "-xhdpi";
                            }
                            case 144 -> {
                                return "-xxhdpi";
                            }
                            case 192 -> {
                                return "-xxxhdpi";
                            }
                            default -> {
                            }
                        }
                    }
                }
                return "-xhdpi";
            }
            case ADAPTIVE_ICON_XML -> {
                return ADAPTIVE_QUALIFIER;
            }
            case VECTOR_XML, SVG, DRAWABLE_XML -> {
                return type.equals("mipmap") ? VECTOR_MIPMAP_QUALIFIER : "";
            }
            default -> {
                return "";
            }
        }
    }

    /** The qualifiers a person may choose for a file; empty when there is nothing to choose (XML goes where it must). */
    public static List<String> choices(FileInspector.Format format) {
        return format == FileInspector.Format.PNG || format == FileInspector.Format.WEBP ? RASTER_QUALIFIERS : List.of();
    }

    /** {@code drawable-xhdpi/ic_add.png}: the path under {@code res}. */
    public static String path(String type, String qualifier, String name, String extension) {
        return type + qualifier + "/" + name + extension;
    }
}
