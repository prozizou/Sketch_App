package pro.sketchware.code;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes the color literals the code editors insert: {@code #RRGGBB} in XML, {@code 0xAARRGGBB} in Java. */
public final class ColorLiteral {

    private static final Pattern LITERAL = Pattern.compile("#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})\\b|0[xX][0-9A-Fa-f]{8}\\b|0[xX][0-9A-Fa-f]{6}\\b");

    /** A color literal found in a line: its columns and the ARGB color it stands for. */
    public record Found(int start, int end, int color) {
    }

    private ColorLiteral() {
    }

    /** @return the color literal the column is on or right after, or null. */
    @Nullable
    public static Found at(String line, int column) {
        Matcher matcher = LITERAL.matcher(line);
        while (matcher.find()) {
            if (column >= matcher.start() && column <= matcher.end()) {
                Integer color = parse(matcher.group());
                if (color != null) {
                    return new Found(matcher.start(), matcher.end(), color);
                }
            }
        }
        return null;
    }

    /** @return the ARGB color of {@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB}, {@code 0xRRGGBB} or {@code 0xAARRGGBB}. */
    @Nullable
    public static Integer parse(String literal) {
        String hex;
        if (literal.startsWith("#")) {
            hex = literal.substring(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            }
        } else if (literal.startsWith("0x") || literal.startsWith("0X")) {
            hex = literal.substring(2);
        } else {
            return null;
        }
        if (hex.length() == 6) {
            hex = "FF" + hex;
        }
        if (hex.length() != 8) {
            return null;
        }
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** @return the color as Java ({@code 0xFFRRGGBB}) or XML / other ({@code #RRGGBB}, {@code #AARRGGBB} when not opaque). */
    public static String format(int color, boolean java) {
        if (java) {
            return String.format(Locale.US, "0x%08X", color);
        }
        if ((color >>> 24) == 0xFF) {
            return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
        }
        return String.format(Locale.US, "#%08X", color);
    }
}
