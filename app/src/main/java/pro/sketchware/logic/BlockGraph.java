package pro.sketchware.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The links between the blocks of an event: what follows what, what sits inside what, and what is plugged in where. */
public final class BlockGraph {
    private final Map<String, LogicBlock> byId = new HashMap<>();
    private final LogicBlock root;

    public BlockGraph(LogicEvent event) {
        for (LogicBlock block : event.blocks()) {
            byId.putIfAbsent(block.id(), block);
        }
        root = event.blocks().isEmpty() ? null : event.blocks().get(0);
    }

    public LogicBlock root() {
        return root;
    }

    public LogicBlock get(String id) {
        return id == null ? null : byId.get(id);
    }

    public LogicBlock get(int id) {
        return id < 0 ? null : byId.get(String.valueOf(id));
    }

    /** Ids a block refers to (next, sub-stacks, plugged-in blocks), in that order, whether they exist or not. */
    public static List<String> references(LogicBlock block) {
        List<String> refs = new ArrayList<>();
        if (block.next() >= 0) refs.add(String.valueOf(block.next()));
        if (block.subStack1() >= 0) refs.add(String.valueOf(block.subStack1()));
        if (block.subStack2() >= 0) refs.add(String.valueOf(block.subStack2()));
        for (int i = 0; i < block.parameters().size(); i++) {
            String id = block.paramBlockId(i);
            if (id != null) refs.add(id);
        }
        return refs;
    }

    /** Every block that runs, or is evaluated, when the event fires: the root and all it leads to. */
    public Set<String> reachable() {
        Set<String> seen = new LinkedHashSet<>();
        if (root == null) {
            return seen;
        }
        Deque<LogicBlock> todo = new ArrayDeque<>();
        todo.push(root);
        while (!todo.isEmpty()) {
            LogicBlock block = todo.pop();
            if (!seen.add(block.id())) {
                continue;
            }
            for (String ref : references(block)) {
                LogicBlock target = byId.get(ref);
                if (target != null) todo.push(target);
            }
        }
        return seen;
    }

    /** Blocks of a stack starting at {@code first}, following {@code next} only (the statements of one body). */
    public List<LogicBlock> stack(int first) {
        List<LogicBlock> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        LogicBlock block = get(first);
        while (block != null && seen.add(block.id())) {
            result.add(block);
            block = get(block.next());
        }
        return result;
    }

    /**
     * Every block inside the body starting at {@code first}, with the blocks plugged into them, but without going
     * into the bodies of nested loops when {@code skipNestedLoops} is set (a break there ends the inner loop).
     */
    public List<LogicBlock> bodyBlocks(int first, boolean skipNestedLoops) {
        List<LogicBlock> result = new ArrayList<>();
        collectBody(first, skipNestedLoops, result, new LinkedHashSet<>());
        return result;
    }

    private void collectBody(int first, boolean skipNestedLoops, List<LogicBlock> into, Set<String> seen) {
        for (LogicBlock block : stack(first)) {
            addWithInputs(block, into, seen);
            if (skipNestedLoops && isLoop(block.opCode())) {
                continue;
            }
            if (block.subStack1() >= 0) collectBody(block.subStack1(), skipNestedLoops, into, seen);
            if (block.subStack2() >= 0) collectBody(block.subStack2(), skipNestedLoops, into, seen);
        }
    }

    private void addWithInputs(LogicBlock block, List<LogicBlock> into, Set<String> seen) {
        if (!seen.add(block.id())) {
            return;
        }
        into.add(block);
        for (int i = 0; i < block.parameters().size(); i++) {
            LogicBlock input = get(block.paramBlockId(i));
            if (input != null) addWithInputs(input, into, seen);
        }
    }

    public static boolean isLoop(String opCode) {
        return "forever".equals(opCode) || "repeat".equals(opCode);
    }
}
