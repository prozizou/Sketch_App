package pro.sketchware.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the blocks of an event inside the IDE, one statement at a time, keeping the value of every variable and
 * list after each step. It covers variables, numbers, text, conditions, loops, lists and toasts. Blocks that need a
 * phone (views, network, files, Firebase...) are listed as steps but not run, and what they return is unknown.
 * It is a teaching and checking aid, not the app running: the real app can behave differently.
 */
public final class BlockSimulator {
    public static final int DEFAULT_MAX_STEPS = 2000;
    private static final Pattern PARAM_TYPE = Pattern.compile("%\\w+(?:\\.\\w+)?|%\\w");

    /** One executed statement and the state right after it. */
    public record Step(int number, String blockId, String opCode, String description, boolean simulated,
                       Map<String, String> state) {
    }

    /** @param stopReason null when the event ran to its end */
    public record Result(List<Step> steps, List<String> output, String stopReason) {
        public boolean finished() {
            return stopReason == null;
        }
    }

    private static final class Break extends RuntimeException {
        Break() {
            super(null, null, false, false);
        }
    }

    private static final class StepLimit extends RuntimeException {
        StepLimit() {
            super(null, null, false, false);
        }
    }

    private final BlockGraph graph;
    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final Map<String, List<Object>> lists = new LinkedHashMap<>();
    private final List<Step> steps = new ArrayList<>();
    private final List<String> output = new ArrayList<>();
    private final int maxSteps;

    private BlockSimulator(LogicEvent event, int maxSteps) {
        this.graph = new BlockGraph(event);
        this.maxSteps = maxSteps;
    }

    /**
     * @param variables starting values: Double for numbers, String, Boolean; missing ones start as the generated
     *                  code's defaults are unknown here, so they start unset (shown as "?")
     */
    public static Result run(LogicEvent event, Map<String, Object> variables, Map<String, List<Object>> lists, int maxSteps) {
        BlockSimulator simulator = new BlockSimulator(event, maxSteps);
        if (variables != null) simulator.variables.putAll(variables);
        if (lists != null) lists.forEach((k, v) -> simulator.lists.put(k, new ArrayList<>(v)));
        String stop = null;
        LogicBlock root = simulator.graph.root();
        try {
            if (root != null) simulator.runStack(idOf(root));
        } catch (StepLimit e) {
            stop = "Stopped after " + maxSteps + " steps: the event probably never ends.";
        } catch (Break e) {
            stop = "A Break outside a loop: the generated code would not compile.";
        }
        return new Result(List.copyOf(simulator.steps), List.copyOf(simulator.output), stop);
    }

    private static int idOf(LogicBlock block) {
        try {
            return Integer.parseInt(block.id());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void runStack(int first) {
        for (LogicBlock block : graph.stack(first)) {
            execute(block);
        }
    }

    private void execute(LogicBlock block) {
        String op = block.opCode();
        switch (op) {
            case "setVarBoolean", "setVarInt", "setVarString" -> {
                Object value = arg(block, 1);
                variables.put(block.param(0), value);
                record(block, "set " + block.param(0) + " = " + show(value), true);
            }
            case "increaseInt", "decreaseInt" -> {
                String name = block.param(0);
                Object current = variables.get(name);
                Object value = current instanceof Double d ? (Double) (d + ("increaseInt".equals(op) ? 1 : -1)) : null;
                variables.put(name, value);
                record(block, name + ("increaseInt".equals(op) ? "++" : "--") + " -> " + show(value), true);
            }
            case "if" -> {
                Object condition = arg(block, 0);
                record(block, "if " + show(condition) + (condition == null ? " (unknown: not entered)" : ""), true);
                if (Boolean.TRUE.equals(condition) && block.subStack1() >= 0) runStack(block.subStack1());
            }
            case "ifElse" -> {
                Object condition = arg(block, 0);
                record(block, "if " + show(condition) + (condition == null ? " (unknown: else taken)" : ""), true);
                int branch = Boolean.TRUE.equals(condition) ? block.subStack1() : block.subStack2();
                if (branch >= 0) runStack(branch);
            }
            case "repeat" -> {
                Object count = arg(block, 0);
                int times = count instanceof Double d ? (int) (double) d : 0;
                record(block, "repeat " + times + " times" + (count == null ? " (unknown count: skipped)" : ""), true);
                for (int i = 0; i < times && block.subStack1() >= 0; i++) {
                    try {
                        runStack(block.subStack1());
                    } catch (Break e) {
                        break;
                    }
                }
            }
            case "forever" -> {
                record(block, "forever", true);
                while (true) {
                    if (block.subStack1() < 0) {
                        record(block, "forever (empty body)", true);
                        continue;
                    }
                    try {
                        runStack(block.subStack1());
                    } catch (Break e) {
                        break;
                    }
                }
            }
            case "break" -> {
                record(block, "break", true);
                throw new Break();
            }
            case "addListInt", "addListStr" -> {
                List<Object> list = list(block.param(1));
                Object value = arg(block, 0);
                list.add(value);
                record(block, block.param(1) + ".add(" + show(value) + ")", true);
            }
            case "insertListInt", "insertListStr" -> {
                List<Object> list = list(block.param(2));
                Object value = arg(block, 0);
                Object index = arg(block, 1);
                if (index instanceof Double d && d >= 0 && d <= list.size()) {
                    list.add((int) (double) d, value);
                    record(block, block.param(2) + ".add(" + show(index) + ", " + show(value) + ")", true);
                } else {
                    record(block, block.param(2) + ".add(" + show(index) + ", ...): index out of range, the app would crash", true);
                }
            }
            case "deleteList" -> {
                List<Object> list = list(block.param(1));
                Object index = arg(block, 0);
                if (index instanceof Double d && d >= 0 && d < list.size()) {
                    list.remove((int) (double) d);
                    record(block, block.param(1) + ".remove(" + show(index) + ")", true);
                } else {
                    record(block, block.param(1) + ".remove(" + show(index) + "): index out of range, the app would crash", true);
                }
            }
            case "clearList" -> {
                if (lists.containsKey(block.param(0))) {
                    lists.get(block.param(0)).clear();
                    record(block, block.param(0) + ".clear()", true);
                } else {
                    record(block, "clear " + block.param(0), false);
                }
            }
            case "doToast" -> {
                Object text = arg(block, 0);
                output.add(text instanceof String s ? s : show(text));
                record(block, "toast " + show(text), true);
            }
            default -> {
                // Evaluate plugged-in blocks for their effects on the description, but do not run the Android call.
                record(block, op + " (not simulated: needs the phone)", false);
            }
        }
    }

    private List<Object> list(String name) {
        return lists.computeIfAbsent(name, k -> new ArrayList<>());
    }

    private void record(LogicBlock block, String description, boolean simulated) {
        if (steps.size() >= maxSteps) {
            throw new StepLimit();
        }
        Map<String, String> state = new LinkedHashMap<>();
        variables.forEach((k, v) -> state.put(k, show(v)));
        lists.forEach((k, v) -> {
            List<String> shown = new ArrayList<>();
            for (Object o : v) shown.add(show(o));
            state.put(k, shown.toString());
        });
        steps.add(new Step(steps.size() + 1, block.id(), block.opCode(), description, simulated, state));
    }

    // ---- values

    private Object arg(LogicBlock block, int index) {
        String plugged = block.paramBlockId(index);
        if (plugged != null) {
            LogicBlock input = graph.get(plugged);
            return input == null ? null : evaluate(input);
        }
        String literal = block.param(index);
        String type = paramType(block.spec(), index);
        return switch (type) {
            case "%d" -> literal.isEmpty() ? (Object) 0d : parseNumber(literal);
            case "%b" -> literal.isEmpty() ? (Object) Boolean.TRUE : Boolean.valueOf(literal);
            default -> literal;
        };
    }

    static String paramType(String spec, int index) {
        Matcher matcher = PARAM_TYPE.matcher(spec == null ? "" : spec);
        int i = 0;
        while (matcher.find()) {
            if (i++ == index) return matcher.group().toLowerCase(Locale.ROOT);
        }
        return "%s";
    }

    private static Double parseNumber(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Object evaluate(LogicBlock block) {
        String op = block.opCode();
        Object a, b;
        switch (op) {
            case "getVar":
                if (variables.containsKey(block.spec())) return variables.get(block.spec());
                return lists.containsKey(block.spec()) ? lists.get(block.spec()) : null;
            case "true":
                return Boolean.TRUE;
            case "false":
                return Boolean.FALSE;
            case "not":
                a = arg(block, 0);
                return a instanceof Boolean x ? !x : null;
            case "&&", "||":
                a = arg(block, 0);
                b = arg(block, 1);
                if (a instanceof Boolean x && b instanceof Boolean y) return "&&".equals(op) ? x && y : x || y;
                return null;
            case "+", "-", "*", "/", "%", ">", "<", "=":
                a = arg(block, 0);
                b = arg(block, 1);
                if (!(a instanceof Double x) || !(b instanceof Double y)) return null;
                return switch (op) {
                    case "+" -> x + y;
                    case "-" -> x - y;
                    case "*" -> x * y;
                    case "/" -> x / y;
                    case "%" -> x % y;
                    case ">" -> x > y;
                    case "<" -> x < y;
                    default -> x.doubleValue() == y.doubleValue();
                };
            case "random":
                a = arg(block, 0);
                return a instanceof Double ? a : null; // the lowest possible value, so runs are repeatable
            case "stringLength":
                return arg(block, 0) instanceof String s ? (Object) (double) s.length() : null;
            case "stringJoin":
                a = arg(block, 0);
                b = arg(block, 1);
                return a instanceof String x && b instanceof String y ? x + y : null;
            case "stringIndex", "stringLastIndex":
                a = arg(block, 0);
                b = arg(block, 1);
                if (a instanceof String x && b instanceof String y) {
                    return (double) ("stringIndex".equals(op) ? y.indexOf(x) : y.lastIndexOf(x));
                }
                return null;
            case "stringSub": {
                Object s = arg(block, 0), from = arg(block, 1), to = arg(block, 2);
                if (s instanceof String x && from instanceof Double f && to instanceof Double t
                        && f >= 0 && t <= x.length() && f <= t) {
                    return x.substring((int) (double) f, (int) (double) t);
                }
                return null;
            }
            case "stringEquals", "stringContains":
                a = arg(block, 0);
                b = arg(block, 1);
                if (a instanceof String x && b instanceof String y) return "stringEquals".equals(op) ? x.equals(y) : x.contains(y);
                return null;
            case "stringReplace": {
                Object s = arg(block, 0), from = arg(block, 1), to = arg(block, 2);
                return s instanceof String x && from instanceof String f && to instanceof String t ? x.replace(f, t) : null;
            }
            case "toNumber":
                return arg(block, 0) instanceof String s ? parseNumber(s) : null;
            case "trim":
                return arg(block, 0) instanceof String s ? s.trim() : null;
            case "toUpperCase":
                return arg(block, 0) instanceof String s ? s.toUpperCase(Locale.ROOT) : null;
            case "toLowerCase":
                return arg(block, 0) instanceof String s ? s.toLowerCase(Locale.ROOT) : null;
            case "toString":
                return arg(block, 0) instanceof Double d ? String.valueOf((long) (double) d) : null;
            case "toStringWithDecimal":
                return arg(block, 0) instanceof Double d ? String.valueOf(d) : null;
            case "mathPi":
                return Math.PI;
            case "mathE":
                return Math.E;
            case "mathPow", "mathMin", "mathMax":
                a = arg(block, 0);
                b = arg(block, 1);
                if (!(a instanceof Double x) || !(b instanceof Double y)) return null;
                return "mathPow".equals(op) ? Math.pow(x, y) : "mathMin".equals(op) ? Math.min(x, y) : Math.max(x, y);
            case "mathSqrt", "mathAbs", "mathRound", "mathCeil", "mathFloor":
                if (!(arg(block, 0) instanceof Double x)) return null;
                return switch (op) {
                    case "mathSqrt" -> Math.sqrt(x);
                    case "mathAbs" -> Math.abs(x);
                    case "mathRound" -> (double) Math.round(x);
                    case "mathCeil" -> Math.ceil(x);
                    default -> Math.floor(x);
                };
            case "getAtListInt", "getAtListStr": {
                Object index = arg(block, 0);
                List<Object> list = lists.get(block.param(1));
                if (list != null && index instanceof Double d && d >= 0 && d < list.size()) return list.get((int) (double) d);
                return null;
            }
            case "lengthList": {
                List<Object> list = lists.get(block.param(0));
                return list == null ? null : (Object) (double) list.size();
            }
            case "indexListInt", "indexListStr": {
                Object value = arg(block, 0);
                List<Object> list = lists.get(block.param(1));
                return list == null ? null : (Object) (double) list.indexOf(value);
            }
            case "containListInt", "containListStr": {
                List<Object> list = lists.get(block.param(0));
                return list == null ? null : list.contains(arg(block, 1));
            }
            default:
                return null;
        }
    }

    /** How a value is shown: numbers without a needless ".0", text in quotes, unknown as "?". */
    public static String show(Object value) {
        if (value == null) return "?";
        if (value instanceof Double d) {
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) return String.valueOf((long) (double) d);
            return String.valueOf(d);
        }
        if (value instanceof String s) return "\"" + s + "\"";
        return String.valueOf(value);
    }
}
