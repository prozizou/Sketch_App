package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A way to supply a missing resource that needs no file from the user: reuse something the project already has, or an
 * icon of the offline library.
 *
 * @param kind      what it does
 * @param score     0 to 1, how sure the match is ({@link #CONFIDENT} and above is applied by "Fix all")
 * @param existing  whether it reuses a resource of the project (these are listed before library icons)
 * @param source    the project resource it reuses ({@link Kind#MATERIAL_ICON}: null)
 * @param iconName  the library icon ({@link Kind#MATERIAL_ICON} only)
 * @param iconStyle its style
 */
public record Suggestion(@NonNull Kind kind, double score, boolean existing, @Nullable ResourceInventory.Definition source,
                         @Nullable String iconName, @Nullable String iconStyle) {
    /** From this score up, "Fix all" takes the suggestion without asking for a choice. */
    public static final double CONFIDENT = 0.8;

    public enum Kind {
        /** Copy an existing file (another qualifier, or the same name in drawable/mipmap) to the missing resource's place. */
        COPY_FILE,
        /** Copy an existing value (the same name in another qualifier) into the default values. */
        COPY_VALUE,
        /** Make the missing name an alias of a similarly named resource that exists. */
        ALIAS,
        /** Add an icon of the offline Material pack as a vector drawable. */
        MATERIAL_ICON
    }

    public boolean confident() {
        return score >= CONFIDENT;
    }
}
