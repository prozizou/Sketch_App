package pro.sketchware.resolver;

import androidx.annotation.NonNull;

/**
 * Why a chosen resolution can't go into the plan. It is reported; nothing is overwritten, renamed or skipped quietly.
 *
 * @param key     the missing resource, {@code type/name}
 * @param reason  the kind of problem
 * @param message what is wrong, in one line
 */
public record Conflict(@NonNull String key, @NonNull Reason reason, @NonNull String message) {
    public enum Reason {
        /** The resource (or one with the same R name) is already defined in this qualifier. */
        EXISTS,
        /** Two chosen resolutions would write the same resource. */
        DUPLICATE_IN_PLAN,
        /** The name or value isn't one AAPT2 accepts. */
        INVALID,
        /** The file or icon to copy can't be read any more. */
        SOURCE_MISSING,
        /** A values file of the project is unreadable XML, so it is not edited. */
        UNREADABLE_VALUES,
        /** An alias would point back at itself. */
        ALIAS_CYCLE,
        /** The file or icon is not accepted (wrong format, damaged, refused by AAPT2). */
        REFUSED
    }
}
