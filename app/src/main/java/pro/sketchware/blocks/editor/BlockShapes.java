package pro.sketchware.blocks.editor;

import java.util.List;

/** The block shapes the Logic editor can draw, as the block type stored in block.json. */
public final class BlockShapes {
    public record Shape(String type, String label, String sampleSpec) {
        /** The type as stored: regular blocks are stored as a single space. */
        public String stored() {
            return type.equals("regular") ? " " : type;
        }

        /** Whether blocks of this shape put other blocks inside them. */
        public boolean hasSlots() {
            return type.equals("c") || type.equals("e");
        }
    }

    public static final List<Shape> ALL = List.of(
            new Shape("regular", "Regular", "do something"),
            new Shape("c", "If", "if %b"),
            new Shape("e", "If-else", "if %b"),
            new Shape("s", "String", "text"),
            new Shape("b", "Boolean", "condition"),
            new Shape("d", "Number", "number"),
            new Shape("v", "Variable", "variable"),
            new Shape("a", "Map", "map"),
            new Shape("f", "Stop", "stop"),
            new Shape("l", "List", "list"),
            new Shape("p", "Component", "component"),
            new Shape("h", "Header", "event"));

    private BlockShapes() {
    }

    /** @return the shape for a stored or typed type ({@code " "} and {@code ""} are regular), or null if unknown */
    public static Shape of(String type) {
        String t = type == null ? "" : type.trim();
        if (t.isEmpty()) t = "regular";
        for (Shape shape : ALL) {
            if (shape.type().equals(t)) return shape;
        }
        return null;
    }

    /** The types whose blocks have a type name (the class of the variable, list, map or component). */
    public static boolean usesTypeName(String type) {
        String t = type == null ? "" : type.trim();
        return t.equals("v") || t.equals("a") || t.equals("l") || t.equals("p");
    }
}
