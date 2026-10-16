package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.Set;

/**
 * What the screen of the open event declares: the names Java code may use that the blocks know about. A statement is
 * only turned into a block when the names in it are ones the screen has, so a typo never becomes a block that points at
 * nothing.
 *
 * @param activityName the generated class ({@code MainActivity})
 * @param fragment     whether the screen is a fragment
 * @param viewBinding  whether widgets are reached through {@code binding}
 * @param variables    name to type: {@code b} boolean, {@code d} number, {@code s} string, {@code a} map, {@code v:Type} custom
 * @param lists        name to kind: {@code listInt}, {@code listStr} or {@code listMap}
 * @param views        ids of the screen's widgets
 * @param components   ids of the screen's components (intent, timer, dialog, ...)
 * @param moreBlocks   the screen's more blocks by name
 * @param args         the event's parameters by name (without the underscore) to type letter
 */
public record ProjectContext(@NonNull String activityName, boolean fragment, boolean viewBinding, @NonNull Map<String, String> variables,
                             @NonNull Map<String, String> lists, @NonNull Set<String> views, @NonNull Set<String> components,
                             @NonNull Map<String, MoreBlock> moreBlocks, @NonNull Map<String, String> args) {
    /**
     * A more block the screen defines.
     *
     * @param spec     its spec as the editor stores it ({@code name %s %d})
     * @param type     the shape of its call: {@code " "} for a statement, {@code b}, {@code d}, {@code s} or another letter
     * @param typeName extra type information of the return value, "" when none
     */
    public record MoreBlock(@NonNull String name, @NonNull String spec, @NonNull String type, @NonNull String typeName) {
    }

    /** A context with nothing declared, for code that only uses literals and the blocks' own names. */
    public static ProjectContext empty(String activityName) {
        return new ProjectContext(activityName, false, false, Map.of(), Map.of(), Set.of(), Set.of(), Map.of(), Map.of());
    }
}
