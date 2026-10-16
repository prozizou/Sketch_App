package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * What the editor knows about one kind of block, enough to build it and to ask the generator what Java it makes.
 *
 * @param opCode   the block's opcode ({@code setVarInt}, {@code doToast}, ...)
 * @param type     the shape: {@code " "} statement, {@code b} boolean, {@code d} number, {@code s} string, {@code c} one
 *                 inner stack, {@code e} two inner stacks, {@code f} a block nothing can follow
 * @param typeName extra type information of a reporter ({@code TextView} of a view variable), "" when none
 * @param params   the parameter types in order: {@code %s}, {@code %d}, {@code %b}, {@code %m.varInt}, ...
 * @param builtIn  whether the generator knows the block itself (not an extra block from a code template)
 */
public record BlockDef(@NonNull String opCode, @NonNull String type, @NonNull String typeName, @NonNull List<String> params, boolean builtIn) {
    public boolean isStatement() {
        return type.equals(" ") || type.equals("c") || type.equals("e") || type.equals("f");
    }

    public boolean isReporter() {
        return type.equals("b") || type.equals("d") || type.equals("s");
    }

    /** How many inner stacks the block holds. */
    public int stacks() {
        return switch (type) {
            case "c" -> 1;
            case "e" -> 2;
            default -> 0;
        };
    }

    /** A spec in the editor's own syntax, with the parameters where the block takes them. */
    public String spec() {
        StringBuilder spec = new StringBuilder(opCode);
        for (String param : params) spec.append(' ').append(param);
        return spec.toString();
    }
}
