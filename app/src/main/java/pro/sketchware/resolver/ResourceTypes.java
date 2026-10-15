package pro.sketchware.resolver;

import java.util.List;
import java.util.Set;

/** What the resolver can do for each Android resource type. */
public final class ResourceTypes {
    /** Types that are one file in {@code res/<type>[-qualifiers]/}: an image, a vector, ... */
    public static final Set<String> FILE_TYPES = Set.of("drawable", "mipmap");
    /** Types that are one entry in a {@code values/*.xml} file. */
    public static final Set<String> VALUE_TYPES = Set.of("color", "string", "dimen", "integer", "bool");
    /** Types whose entry can point at another resource of the same type: {@code <color name="a">@color/b</color>}. */
    public static final Set<String> ALIAS_TYPES = Set.of("color", "string", "dimen", "integer", "bool", "drawable");

    /** The {@code values} file each value type goes to, as the project's resource editors use them. */
    public static String valuesFile(String type) {
        return switch (type) {
            case "string" -> "strings.xml";
            case "color" -> "colors.xml";
            case "dimen" -> "dimens.xml";
            case "integer" -> "integers.xml";
            case "bool" -> "bools.xml";
            case "drawable" -> "drawables.xml";
            default -> throw new IllegalArgumentException("No values file for " + type);
        };
    }

    /** Resource types AAPT2 knows, for recognising a type in a log line. */
    public static final List<String> KNOWN = List.of("anim", "animator", "array", "attr", "bool", "color", "dimen", "drawable",
            "font", "fraction", "id", "integer", "interpolator", "layout", "menu", "mipmap", "navigation", "plurals", "raw",
            "string", "style", "styleable", "transition", "xml");

    private ResourceTypes() {
    }

    public static boolean isFile(String type) {
        return FILE_TYPES.contains(type);
    }

    public static boolean isValue(String type) {
        return VALUE_TYPES.contains(type);
    }

    /** Whether the resolver offers a fix for a missing resource of this type. */
    public static boolean isSupported(String type) {
        return isFile(type) || isValue(type);
    }
}
