package pro.sketchware.properties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and edits the "custom attributes" text of a view: one {@code name="value"} per line, exactly what
 * ends up in the generated layout XML. Lines that aren't a plain attribute are kept untouched, and edits
 * keep the order the user already has.
 */
public final class InjectAttributes {
    private static final Pattern ATTRIBUTE = Pattern.compile("^\\s*([A-Za-z_][\\w.]*(?::[\\w.]+)?)\\s*=\\s*\"(.*)\"\\s*$");

    private final List<String> lines = new ArrayList<>();

    public InjectAttributes(@Nullable String text) {
        if (text != null && !text.isBlank()) {
            for (String line : text.split("\n", -1)) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }
    }

    /** The value of {@code name}, or {@code null} if the view doesn't set it. */
    @Nullable
    public String get(String name) {
        int index = indexOf(name);
        if (index < 0) {
            return null;
        }
        Matcher matcher = ATTRIBUTE.matcher(lines.get(index));
        return matcher.matches() ? matcher.group(2) : null;
    }

    /** Sets {@code name}; an empty or {@code null} value removes it. */
    public InjectAttributes set(String name, @Nullable String value) {
        int index = indexOf(name);
        if (value == null || value.isEmpty()) {
            if (index >= 0) {
                lines.remove(index);
            }
            return this;
        }
        String line = name + "=\"" + value + "\"";
        if (index >= 0) {
            lines.set(index, line);
        } else {
            lines.add(line);
        }
        return this;
    }

    /** Every plain attribute of the view, in the order they are written. */
    public Map<String, String> asMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher matcher = ATTRIBUTE.matcher(line);
            if (matcher.matches()) {
                map.put(matcher.group(1), matcher.group(2));
            }
        }
        return map;
    }

    /** How many attributes whose name is in {@code names} the view sets. */
    public int countSet(Iterable<String> names) {
        int count = 0;
        for (String name : names) {
            if (get(name) != null) {
                count++;
            }
        }
        return count;
    }

    private int indexOf(String name) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = ATTRIBUTE.matcher(lines.get(i));
            if (matcher.matches() && matcher.group(1).equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public String toString() {
        return String.join("\n", lines);
    }
}
