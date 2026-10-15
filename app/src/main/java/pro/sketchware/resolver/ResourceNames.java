package pro.sketchware.resolver;

import java.util.Locale;
import java.util.regex.Pattern;

/** Rules for resource names, as AAPT2 and the Android build enforce them. */
public final class ResourceNames {
    /**
     * A resource file's base name: AAPT2 rejects a '.' other than the extension, and R fields need a Java identifier.
     * Uppercase is accepted by AAPT2 (checked against the real tool), so the name the log asks for is kept as it is.
     */
    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    /** A value's name: letters, digits, '_' and '.', starting with a letter or '_' ({@code Theme.App} becomes {@code Theme_App}). */
    private static final Pattern VALUE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    private ResourceNames() {
    }

    /** @return whether a resource of this type may have this name */
    public static boolean isValidFor(String type, String name) {
        if (name == null || name.isEmpty()) return false;
        return ResourceTypes.isFile(type) ? FILE_NAME.matcher(name).matches() : VALUE_NAME.matcher(name).matches();
    }

    /** The name R gives a resource: dots become underscores. Two names with the same R name collide. */
    public static String rName(String name) {
        return name.replace('.', '_');
    }

    /** A name compared case-insensitively, as the shared storage compares file names. */
    public static String foldedKey(String name) {
        return rName(name).toLowerCase(Locale.ROOT);
    }

    /**
     * A valid file-resource name made from free text: lowercase, with other characters turned into '_'.
     *
     * @return the name, or null when nothing usable is left
     */
    public static String fileNameFrom(String text) {
        if (text == null) return null;
        String name = text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (name.isEmpty()) return null;
        if (!Character.isLetter(name.charAt(0))) name = "img_" + name;
        return name;
    }
}
