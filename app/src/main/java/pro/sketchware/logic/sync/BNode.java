package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A block before it has an id: its opcode, its parameters (a text, or another block), and the stacks inside it.
 * The converter builds a forest of these; {@link BlockAssembler} gives them ids and turns them into the editor's beans.
 */
final class BNode {
    /** One parameter: a text, or a reporter block that takes the parameter's place. */
    record Param(@Nullable String text, @Nullable BNode block) {
        static Param text(String text) {
            return new Param(text, null);
        }

        static Param of(BNode block) {
            return new Param(null, block);
        }
    }

    final String opCode;
    final String spec;
    final String type;
    final String typeName;
    final List<Param> params = new ArrayList<>();
    final List<BNode> stack1 = new ArrayList<>();
    final List<BNode> stack2 = new ArrayList<>();
    /** The id this block had before, when it is the same block as one already in the event. */
    @Nullable
    String reuseId;
    /** The line (1-based, in the person's text) the block comes from, 0 when unknown. */
    int line;
    /** Whether the block carries code that is not a known block, as text. */
    boolean source;
    /** Why the code is kept as text; set with {@link #source}. */
    String reason = "";

    BNode(@NonNull String opCode, @NonNull String spec, @NonNull String type, @NonNull String typeName) {
        this.opCode = opCode;
        this.spec = spec;
        this.type = type;
        this.typeName = typeName;
    }

    /** A statement block holding code as it was written. */
    static BNode sourceStatement(String code, int line, String reason) {
        BNode node = new BNode("addSourceDirectly", "add source directly %s.inputOnly", " ", "");
        node.params.add(Param.text(code));
        node.source = true;
        node.reason = reason;
        node.line = line;
        return node;
    }

    /** A reporter block holding an expression as it was written; {@code type} is {@code b}, {@code d} or {@code s}. */
    static BNode sourceReporter(String code, String type, int line, String reason) {
        String opCode = switch (type) {
            case "b" -> "asdBoolean";
            case "d" -> "asdNumber";
            default -> "asdString";
        };
        String spec = switch (type) {
            case "b" -> "boolean %s.inputOnly";
            case "d" -> "number %s.inputOnly";
            default -> "string %s.inputOnly";
        };
        BNode node = new BNode(opCode, spec, type.equals("b") || type.equals("d") ? type : "s", "");
        node.params.add(Param.text(code));
        node.source = true;
        node.reason = reason;
        node.line = line;
        return node;
    }
}
