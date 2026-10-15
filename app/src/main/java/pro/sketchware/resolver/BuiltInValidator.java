package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The checks that need no tool: where the file goes, what its name and content are. It stands in front of
 * {@link Aapt2Validator} (which asks the real AAPT2) and is the only check when AAPT2 isn't unpacked yet.
 */
public final class BuiltInValidator implements ResourceValidator {
    private static final Pattern FILE_PATH = Pattern.compile("(?<type>[a-z]+)(?<qualifier>(?:-[A-Za-z0-9]+)*)/(?<base>[^/.]+)(?<extension>(?:\\.[A-Za-z0-9]+)+)");
    private static final Set<String> EXTENSIONS = Set.of(".png", ".webp", ".xml");

    @NonNull
    @Override
    public Result validateFile(@NonNull String relativePath, @NonNull byte[] content) {
        Matcher path = FILE_PATH.matcher(relativePath);
        if (!path.matches()) return Result.fail("\"" + relativePath + "\" isn't a valid resource path (type[-qualifier]/name.ext)");
        String type = path.group("type");
        if (!ResourceTypes.isFile(type)) return Result.fail("Files of type \"" + type + "\" can't be added by the resolver");
        String name = path.group("base");
        if (!ResourceNames.isValidFor(type, name)) {
            return Result.fail("\"" + name + "\" isn't a valid " + type + " name (letters, digits and _ only, starting with a letter)");
        }
        String qualifier = path.group("qualifier");
        if (!allowedQualifier(type, qualifier)) return Result.fail("The qualifier \"" + qualifier + "\" isn't one the resolver writes to");
        String extension = path.group("extension");
        if (!EXTENSIONS.contains(extension)) return Result.fail("\"" + extension + "\" files aren't accepted (use .png, .webp or .xml)");

        FileInspector.Inspection inspection = FileInspector.inspect(type, content, null);
        if (!inspection.ok()) return Result.fail(inspection.problem());
        if (inspection.format() == FileInspector.Format.SVG) return Result.fail("An SVG must be converted to a vector drawable first");
        boolean xml = inspection.format() == FileInspector.Format.VECTOR_XML || inspection.format() == FileInspector.Format.ADAPTIVE_ICON_XML
                || inspection.format() == FileInspector.Format.DRAWABLE_XML;
        if (xml != extension.equals(".xml")) return Result.fail("The file's content doesn't match its extension " + extension);
        if (inspection.format() == FileInspector.Format.PNG && !extension.equals(".png")) return Result.fail("A PNG must be saved as .png");
        if (inspection.format() == FileInspector.Format.WEBP && !extension.equals(".webp")) return Result.fail("A WebP must be saved as .webp");
        return Result.OK;
    }

    @NonNull
    @Override
    public Result validateValues(@NonNull String relativePath, @NonNull String xml) {
        if (!relativePath.matches("values(-[A-Za-z0-9]+)*/[A-Za-z0-9_]+\\.xml")) {
            return Result.fail("\"" + relativePath + "\" isn't a values file path");
        }
        List<ValuesFile.Entry> entries;
        try {
            entries = ValuesFile.parse(xml);
        } catch (SafeXml.InvalidXml e) {
            return Result.fail("The values file isn't valid XML" + (e.line() > 0 ? " (line " + e.line() + ")" : "") + ": " + e.getMessage());
        }
        Set<String> seen = new HashSet<>();
        for (ValuesFile.Entry entry : entries) {
            if (!seen.add(entry.type() + "/" + ResourceNames.rName(entry.name()))) {
                return Result.fail("\"" + entry.name() + "\" is defined twice as " + entry.type());
            }
        }
        return Result.OK;
    }

    /** Folders the resolver may write to: the default, one density, nodpi, and the any-density folders for XML. */
    static boolean allowedQualifier(String type, String qualifier) {
        if (DestinationRules.RASTER_QUALIFIERS.contains(qualifier)) return true;
        if (qualifier.equals(DestinationRules.ADAPTIVE_QUALIFIER)) return type.equals("mipmap");
        return qualifier.equals(DestinationRules.VECTOR_MIPMAP_QUALIFIER) && type.equals("mipmap");
    }

    /** Bytes of a text, for callers that hold a string. */
    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
