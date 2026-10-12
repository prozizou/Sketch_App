package pro.sketchware.blocks.editor;

import java.util.List;
import java.util.Locale;

/** The Java a use of a block adds to the app, built the way the Logic editor builds it. */
public final class JavaPreview {
    private JavaPreview() {
    }

    /**
     * Inputs are shown as {@code input1}, {@code input2}…; the blocks inside an if block as comments. Block imports
     * are listed first, as written (they are stored with the block).
     */
    public static String build(String spec, String code, String imports) {
        int params = BlockSpec.paramCount(spec);
        Object[] values = new Object[params + 2];
        for (int i = 0; i < params; i++) values[i] = "input" + (i + 1);
        values[params] = "/* blocks */";
        values[params + 1] = "/* else blocks */";
        StringBuilder preview = new StringBuilder();
        List<ImportList.Entry> entries = ImportList.parse(imports);
        for (ImportList.Entry entry : entries) {
            if (entry.valid()) preview.append("import ").append(entry.type()).append(";\n");
        }
        if (preview.length() > 0) preview.append('\n');
        try {
            preview.append(String.format(Locale.ROOT, code == null ? "" : code, values));
        } catch (RuntimeException e) {
            preview.append("/* Failed to resolve Custom Block's code: ").append(e).append(" */");
        }
        return preview.toString();
    }
}
