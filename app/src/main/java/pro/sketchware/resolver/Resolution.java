package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** What a person (or "Fix all") chose to supply one missing resource with. */
public interface Resolution {
    /** A suggestion from {@link ResourceMatcher}: a project resource or a library icon. */
    record Reuse(@NonNull Suggestion suggestion) implements Resolution {
    }

    /**
     * A file the person picked or an icon they downloaded, already inspected.
     *
     * @param label      its name, for the preview ("ic_add.png", "mdi:home")
     * @param inspection what it is and what will be written
     * @param qualifier  the folder qualifier chosen for it ("" for the default); null to use the default for its kind
     */
    record Imported(@NonNull String label, @NonNull FileInspector.Inspection inspection, @Nullable String qualifier) implements Resolution {
    }

    /** A value typed in: a colour, a text, a size, a number, a flag, or a reference to another resource. */
    record Typed(@NonNull String text) implements Resolution {
    }
}
