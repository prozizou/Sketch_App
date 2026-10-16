package pro.sketchware.logic.sync;

import androidx.annotation.Nullable;

import a.a.a.lq;

/**
 * The text a block shows ({@code set %m.varInt to %d}), which the editor keeps in the saved block. Built-in blocks have
 * no spec of their own: it is put together from the app's block texts (a head, the words between the parameters, and a
 * tail), exactly as the palette does. Extra blocks carry their spec with them.
 */
public final class BlockSpecs {
    /** Looks a string up by its resource name; null when there is none. */
    public interface Strings {
        @Nullable
        String get(String name);
    }

    private BlockSpecs() {
    }

    /** The spec of {@code def} as the editor shows it, or just its own spec text when the block has no texts. */
    public static String specOf(BlockDef def, Strings strings) {
        if (!def.builtIn()) return def.spec();
        String name = lq.d(def.opCode());
        if (name == null || name.isEmpty()) return def.spec();
        StringBuilder spec = new StringBuilder();
        int words = 0;
        String head = strings.get("block_" + name + "_head");
        if (head != null && !head.isEmpty()) words++;
        append(spec, head);
        for (int i = 0; i < def.params().size(); i++) {
            append(spec, def.params().get(i));
            String body = strings.get("block_" + name + "_body_" + (i + 1));
            if (body != null && !body.isEmpty()) words++;
            append(spec, body);
        }
        // texts after the last parameter that the loop above did not reach
        for (int i = def.params().size() + 1; i < def.params().size() + 4; i++) {
            String body = strings.get("block_" + name + "_body_" + i);
            if (body != null && !body.isEmpty()) words++;
            append(spec, body);
        }
        String tail = strings.get("block_" + name + "_tail");
        if (tail != null && !tail.isEmpty()) words++;
        append(spec, tail);
        // with none of the block's texts there is nothing but parameters: show the block's name with them instead
        return words == 0 ? def.spec() : spec.toString().trim();
    }

    private static void append(StringBuilder spec, @Nullable String part) {
        if (part == null || part.isEmpty()) return;
        if (spec.length() > 0) spec.append(' ');
        spec.append(part);
    }
}
