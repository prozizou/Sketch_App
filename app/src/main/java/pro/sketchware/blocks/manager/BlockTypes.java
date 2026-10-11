package pro.sketchware.blocks.manager;

import java.util.Set;

/** The block types of custom blocks, as written by the block editor. */
public final class BlockTypes {
    private static final Set<String> KNOWN = Set.of(" ", "regular", "c", "e", "s", "b", "d", "v", "a", "f", "l", "p", "h");

    private BlockTypes() {
    }

    public static boolean isKnown(String type) {
        return type.isEmpty() || KNOWN.contains(type);
    }
}
