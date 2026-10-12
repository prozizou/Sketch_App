package pro.sketchware.blocks.editor;

/** What the editor holds for a block: used for drafts, to tell whether anything changed and to restore a state. */
public record BlockFields(String name, String type, String typeName, String spec, String spec2, String color,
                          String imports, String code) {
    public static final BlockFields EMPTY = new BlockFields("", "regular", "", "", "", "", "", "");
}
