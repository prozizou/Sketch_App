package pro.sketchware.blocks.editor;

import java.util.List;

/** The parameter types the block editor offers, in the order it has always listed them. */
public final class BlockParamTypes {
    public record Type(String label, String spec) {
        public BlockSpec.Param param() {
            return (BlockSpec.Param) BlockSpec.parseToken(spec);
        }
    }

    public static final List<Type> ALL = List.of(
            new Type("inputOnly", "%s.inputOnly"),
            new Type("string", "%s"),
            new Type("boolean", "%b"),
            new Type("number", "%d"),
            new Type("map", "%m.varMap"),
            new Type("view", "%m.view"),
            new Type("textView", "%m.textview"),
            new Type("editText", "%m.edittext"),
            new Type("ImageView", "%m.imageview"),
            new Type("listView", "%m.listview"),
            new Type("list", "%m.list"),
            new Type("listMap", "%m.listMap"),
            new Type("listString", "%m.listStr"),
            new Type("listNumber", "%m.listInt"),
            new Type("intent", "%m.intent"),
            new Type("color", "%m.color"),
            new Type("activity", "%m.activity"),
            new Type("resource", "%m.resource"),
            new Type("custom views", "%m.customViews"),
            new Type("layout", "%m.layout"),
            new Type("anim", "%m.anim"),
            new Type("drawable", "%m.drawable"),
            new Type("ResStrings", "%m.ResString"));

    private BlockParamTypes() {
    }

    /** @return the name of a parameter's type: "string" for {@code %s}, "view" for {@code %m.view}… */
    public static String labelOf(BlockSpec.Param param) {
        for (Type type : ALL) {
            if (type.spec().equals(param.text())) return type.label();
        }
        return param.menu() != null ? param.menu() : switch (param.kind()) {
            case 's' -> "string";
            case 'b' -> "boolean";
            case 'd' -> "number";
            default -> "menu";
        };
    }

    /** @return the index in {@link #ALL} of a parameter's type, or -1 for a menu the editor doesn't list */
    public static int indexOf(BlockSpec.Param param) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).spec().equals(param.text())) return i;
        }
        return -1;
    }
}
