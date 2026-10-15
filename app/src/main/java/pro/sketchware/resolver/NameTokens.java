package pro.sketchware.resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Looks at the words in a resource name so {@code ic_add_black_24dp}, {@code icAdd} and {@code add} are recognised as
 * the same thing. Words that only say how or where an icon is used are left out.
 */
public final class NameTokens {
    /** Icon styles of the offline pack. */
    public static final List<String> STYLES = List.of("baseline", "outline", "round", "sharp", "twotone");
    private static final Set<String> NOISE = Set.of("ic", "icon", "icons", "img", "image", "ico", "drawable", "mipmap", "black", "white",
            "grey", "gray", "dp", "px", "sp", "baseline", "outline", "outlined", "round", "rounded", "sharp", "twotone", "filled", "material", "md");

    private NameTokens() {
    }

    /** The meaningful lowercase words of a name; digits-only and size words ("24dp") are dropped. */
    public static List<String> of(String name) {
        List<String> tokens = new ArrayList<>();
        // camelCase -> camel_Case, then split on anything that is not a letter or digit
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        for (String part : spaced.split("[^a-z0-9]+")) {
            if (part.isEmpty() || NOISE.contains(part)) continue;
            if (part.matches("\\d+(dp|px|sp)?")) continue;
            tokens.add(part);
        }
        return tokens;
    }

    /** The words joined with '_': the comparable form of a name. */
    public static String key(String name) {
        return String.join("_", of(name));
    }

    /** The style a name asks for ({@code icon_add_round} gives {@code round}), or null. */
    public static String styleOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String style : STYLES) {
            if (lower.endsWith("_" + style) || lower.startsWith(style + "_")) return style;
        }
        if (lower.endsWith("_outlined") || lower.startsWith("outlined_")) return "outline";
        if (lower.endsWith("_rounded") || lower.startsWith("rounded_")) return "round";
        return null;
    }

    /** Edit distance between two strings (insert, delete, replace). */
    public static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
