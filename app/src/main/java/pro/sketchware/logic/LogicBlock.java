package pro.sketchware.logic;

import java.util.List;

/**
 * One block of an event, as the project stores it. A parameter that starts with {@code @} is the id of the block
 * plugged into that slot; any other parameter is the literal typed in. {@code next}, {@code subStack1} and
 * {@code subStack2} are block ids, or -1 for none.
 */
public record LogicBlock(String id, String opCode, String spec, String type, List<String> parameters,
                         int next, int subStack1, int subStack2) {
    public LogicBlock {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        spec = spec == null ? "" : spec;
        type = type == null ? "" : type;
    }

    /** Short form for tests: no sub-stacks. */
    public static LogicBlock of(String id, String opCode, String spec, int next, String... parameters) {
        return new LogicBlock(id, opCode, spec, "", List.of(parameters), next, -1, -1);
    }

    public String param(int index) {
        return index < parameters.size() ? parameters.get(index) : "";
    }

    /** The id of the block plugged into parameter {@code index}, or null when it is a literal. */
    public String paramBlockId(int index) {
        String value = param(index);
        return value.startsWith("@") && value.length() > 1 ? value.substring(1) : null;
    }

    /** The first word of the spec: the name of a more block for {@code definedFunc}. */
    public String specName() {
        int space = spec.indexOf(' ');
        return space < 0 ? spec : spec.substring(0, space);
    }
}
