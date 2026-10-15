package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * One change to the project's {@code res} folder, decided and checked, not yet written.
 *
 * @param kind    a new file, or entries added to a values file
 * @param key     the missing resource it fixes, {@code type/name}
 * @param path    under {@code res}: the new file ({@code drawable-xhdpi/ic_add.png}) or the values file ({@code values/colors.xml})
 * @param content the new file's bytes (null for a value)
 * @param entry   the entry added (null for a file)
 * @param source  what it is made from, for the preview ("Material icon add (baseline)", "Alias of @color/brand")
 * @param notes   things to know before confirming (a large image, ...)
 */
public record PlannedChange(@NonNull Kind kind, @NonNull String key, @NonNull String path, @Nullable byte[] content,
                            @Nullable ValuesFile.Entry entry, @NonNull String source, @NonNull List<String> notes) {
    public enum Kind {FILE, VALUE}

    public static PlannedChange file(String key, String path, byte[] content, String source, List<String> notes) {
        return new PlannedChange(Kind.FILE, key, path, content, null, source, List.copyOf(notes));
    }

    public static PlannedChange value(String key, String valuesPath, ValuesFile.Entry entry, String source) {
        return new PlannedChange(Kind.VALUE, key, valuesPath, null, entry, source, List.of());
    }
}
