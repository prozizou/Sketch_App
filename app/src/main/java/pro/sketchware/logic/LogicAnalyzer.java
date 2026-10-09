package pro.sketchware.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import pro.sketchware.analysis.Category;
import pro.sketchware.analysis.Finding;
import pro.sketchware.analysis.Severity;

/**
 * Finds problems in the blocks of a project: blocks that never run, unused variables and more blocks, loops that
 * never end or freeze the screen, logic errors, and blocks that no longer match the rest of the project
 * ({@code logic.*} and {@code sync.*} rules). Read-only.
 */
public final class LogicAnalyzer {
    /** A repeat with a typed-in count at least this large freezes the screen for a noticeable time. */
    static final double LARGE_REPEAT = 100_000;

    /** Blocks that are slow, show something or leave the screen: they do not belong inside a loop. */
    static final Set<String> HEAVY_IN_LOOP = Set.of("requestnetworkStartRequestNetwork", "startActivity", "doToast",
            "fileutilread", "fileutilwrite", "fileutilcopy", "fileutilmove", "fileutildelete", "fileutillistdir",
            "firebaseAdd", "firebasePush", "firebaseDelete", "firebaseGetChildren", "firebasestorageUploadFile",
            "firebasestorageDownloadFile", "fileSetData", "dialogShow", "mediaplayerCreate", "soundpoolLoad");

    private LogicAnalyzer() {
    }

    /**
     * @param screens          the screens of the project
     * @param customJavaFiles  file names of the project's own Java files (Java manager), like {@code Util.java}
     */
    public static List<Finding> analyze(List<LogicScreen> screens, Collection<String> customJavaFiles) {
        Map<String, List<String>> evidence = new LinkedHashMap<>();
        for (LogicScreen screen : screens) {
            analyzeScreen(screen, evidence);
        }
        for (String file : customJavaFiles) {
            String simple = simpleName(file);
            for (LogicScreen screen : screens) {
                if (simple.equals(screen.className())) {
                    add(evidence, "sync.custom-java-duplicates-screen", file);
                }
            }
        }
        List<Finding> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : evidence.entrySet()) {
            out.add(finding(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private static void analyzeScreen(LogicScreen screen, Map<String, List<String>> evidence) {
        String where = screen.javaName();
        StringBuilder usage = new StringBuilder();
        Set<String> calledMoreBlocks = new java.util.HashSet<>();
        for (LogicEvent event : screen.events()) {
            BlockGraph graph = new BlockGraph(event);
            String at = where + " > " + event.key();
            Set<String> reachable = graph.reachable();
            int unconnected = event.blocks().size() - reachable.size();
            if (unconnected > 0) {
                add(evidence, "logic.unconnected-blocks", at + " (" + unconnected + ")");
            }
            for (LogicBlock block : event.blocks()) {
                usage.append(' ').append(block.spec());
                for (String p : block.parameters()) usage.append(' ').append(p);
                for (String ref : BlockGraph.references(block)) {
                    if (graph.get(ref) == null) {
                        add(evidence, "logic.broken-reference", at + " #" + block.id());
                        break;
                    }
                }
                if (!reachable.contains(block.id())) {
                    continue;
                }
                checkBlock(screen, graph, block, at, evidence, calledMoreBlocks);
            }
            if (graph.root() != null) {
                checkBreaks(graph, graph.root().id().matches("-?\\d+") ? Integer.parseInt(graph.root().id()) : -1, false, at, evidence);
            }
        }
        String text = usage.toString();
        for (String variable : screen.variables().keySet()) {
            if (!containsWord(text, variable)) add(evidence, "logic.unused-variable", where + " > " + variable);
        }
        for (String list : screen.lists()) {
            if (!containsWord(text, list)) add(evidence, "logic.unused-list", where + " > " + list);
        }
        for (String moreBlock : screen.moreBlocks()) {
            if (!calledMoreBlocks.contains(moreBlock) && !text.contains("_" + moreBlock + "(")) {
                add(evidence, "logic.unused-moreblock", where + " > " + moreBlock);
            }
        }
    }

    private static void checkBlock(LogicScreen screen, BlockGraph graph, LogicBlock block, String at,
                                   Map<String, List<String>> evidence, Set<String> calledMoreBlocks) {
        String op = block.opCode();
        String here = at + " #" + block.id();
        switch (op) {
            case "definedFunc" -> {
                calledMoreBlocks.add(block.specName());
                if (!screen.moreBlocks().contains(block.specName())) {
                    add(evidence, "logic.missing-moreblock", here + " " + block.specName());
                }
            }
            case "getVar" -> {
                String name = block.spec();
                if (!screen.variables().containsKey(name) && !screen.lists().contains(name)) {
                    add(evidence, "logic.undeclared-variable", here + " " + name);
                }
            }
            case "forever" -> {
                if (block.subStack1() < 0) {
                    add(evidence, "logic.infinite-loop", here + " (empty)");
                } else if (graph.bodyBlocks(block.subStack1(), true).stream().noneMatch(b -> "break".equals(b.opCode()))) {
                    add(evidence, "logic.infinite-loop", here);
                }
                checkHeavyInLoop(graph, block, here, evidence);
            }
            case "repeat" -> {
                Double count = number(block.param(0));
                if (count != null && count >= LARGE_REPEAT) {
                    add(evidence, "logic.large-loop", here + " (" + block.param(0) + " times)");
                }
                if (count != null && count <= 0) {
                    add(evidence, "logic.dead-loop", here + " (" + block.param(0) + " times)");
                }
                if (block.subStack1() < 0) add(evidence, "logic.empty-block", here + " repeat");
                checkHeavyInLoop(graph, block, here, evidence);
            }
            case "if", "ifElse" -> {
                LogicBlock condition = graph.get(block.paramBlockId(0));
                String literal = block.param(0);
                if ((condition != null && ("true".equals(condition.opCode()) || "false".equals(condition.opCode())))
                        || "true".equals(literal) || "false".equals(literal)) {
                    add(evidence, "logic.constant-condition", here);
                } else if (block.paramBlockId(0) == null && literal.isEmpty()) {
                    // The code generator turns an empty condition into true.
                    add(evidence, "logic.constant-condition", here + " (empty, always true)");
                }
                if (block.subStack1() < 0 && ("if".equals(op) || block.subStack2() < 0)) {
                    add(evidence, "logic.empty-block", here + " " + op);
                }
            }
            case "/", "%" -> {
                // The code generator turns an empty number into 0.
                Double divisor = block.param(1).isEmpty() ? Double.valueOf(0) : number(block.param(1));
                if (block.paramBlockId(1) == null && divisor != null && divisor == 0) {
                    add(evidence, "logic.division-by-zero", here);
                }
            }
            case "setVarBoolean", "setVarInt", "setVarString" -> {
                LogicBlock value = graph.get(block.paramBlockId(1));
                if (value != null && "getVar".equals(value.opCode()) && value.spec().equals(block.param(0))) {
                    add(evidence, "logic.self-assignment", here + " " + block.param(0));
                }
            }
            default -> {
            }
        }
    }

    private static void checkHeavyInLoop(BlockGraph graph, LogicBlock loop, String here, Map<String, List<String>> evidence) {
        if (loop.subStack1() < 0) return;
        for (LogicBlock inner : graph.bodyBlocks(loop.subStack1(), false)) {
            if (HEAVY_IN_LOOP.contains(inner.opCode())) {
                add(evidence, "logic.heavy-call-in-loop", here + " " + inner.opCode());
                return;
            }
        }
    }

    /** A break must be inside a loop, or the generated Java does not compile. */
    private static void checkBreaks(BlockGraph graph, int first, boolean insideLoop, String at, Map<String, List<String>> evidence) {
        for (LogicBlock block : graph.stack(first)) {
            if ("break".equals(block.opCode()) && !insideLoop) {
                add(evidence, "logic.break-outside-loop", at + " #" + block.id());
            }
            boolean inside = insideLoop || BlockGraph.isLoop(block.opCode());
            if (block.subStack1() >= 0) checkBreaks(graph, block.subStack1(), inside, at, evidence);
            if (block.subStack2() >= 0) checkBreaks(graph, block.subStack2(), inside, at, evidence);
        }
    }

    static Double number(String literal) {
        if (literal == null || literal.isEmpty() || literal.startsWith("@")) return null;
        try {
            return Double.parseDouble(literal.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean containsWord(String text, String word) {
        return Pattern.compile("(?<![\\w$])" + Pattern.quote(word) + "(?![\\w$])").matcher(text).find();
    }

    private static String simpleName(String file) {
        String name = file.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.endsWith(".java") ? name.substring(0, name.length() - 5) : name;
    }

    private static void add(Map<String, List<String>> evidence, String id, String item) {
        evidence.computeIfAbsent(id, k -> new ArrayList<>()).add(item);
    }

    private static Finding finding(String id, List<String> items) {
        String sample = String.join(", ", items.subList(0, Math.min(5, items.size()))) + (items.size() > 5 ? ", ..." : "");
        int n = items.size();
        return switch (id) {
            case "logic.unconnected-blocks" -> new Finding(id, Category.QUALITY, Severity.INFO,
                    "Blocks not connected to their event (" + n + " events)",
                    "Blocks left loose in an event are kept in the project but never run.",
                    "Attach them under the event's first block, or delete them.", sample);
            case "logic.broken-reference" -> new Finding(id, Category.QUALITY, Severity.WARNING,
                    "Blocks pointing at blocks that do not exist (" + n + ")",
                    "The project data links a block to a missing one, usually after an interrupted save or a manual edit. The generated code can miss statements.",
                    "Open the event, check the blocks around the one named, and re-attach them; restore a snapshot if pieces are missing.", sample);
            case "logic.unused-variable" -> new Finding(id, Category.QUALITY, Severity.INFO,
                    "Variables never used (" + n + ")",
                    "A variable that no block reads or sets only adds a field to the generated class.",
                    "Remove it from the Variable list, or use it.", sample);
            case "logic.unused-list" -> new Finding(id, Category.QUALITY, Severity.INFO,
                    "Lists never used (" + n + ")",
                    "A list that no block uses only adds a field to the generated class.",
                    "Remove it, or use it.", sample);
            case "logic.unused-moreblock" -> new Finding(id, Category.QUALITY, Severity.INFO,
                    "More blocks never called (" + n + ")",
                    "A more block that no block calls is generated as a method that never runs.",
                    "Call it where it is needed, or delete it.", sample);
            case "logic.missing-moreblock" -> new Finding(id, Category.QUALITY, Severity.ERROR,
                    "Calls to more blocks that no longer exist (" + n + ")",
                    "The generated Java calls a method that is not generated any more, so the build fails.",
                    "Delete the call blocks, or create the more block again with the same name.", sample);
            case "logic.undeclared-variable" -> new Finding(id, Category.QUALITY, Severity.WARNING,
                    "Variables read but not declared in the screen (" + n + ")",
                    "A variable block that refers to a variable deleted from this screen makes the generated Java fail to compile.",
                    "Create the variable again, or replace the block.", sample);
            case "logic.infinite-loop" -> new Finding(id, Category.QUALITY, Severity.ERROR,
                    "Forever loops with no break (" + n + ")",
                    "Events run on the screen's main thread: a loop that never ends freezes the app, and Android then offers to close it (Application Not Responding).",
                    "Put a Break inside an If in the loop, use Repeat with a count, or a Timer for something that must happen again and again.", sample);
            case "logic.large-loop" -> new Finding(id, Category.QUALITY, Severity.WARNING,
                    "Very long Repeat loops (" + n + ")",
                    "A loop of a hundred thousand turns or more on the main thread freezes the screen while it runs.",
                    "Reduce the count, or do the work in smaller parts with a Timer.", sample);
            case "logic.dead-loop" -> new Finding(id, Category.QUALITY, Severity.INFO,
                    "Repeat loops that never run (" + n + ")",
                    "A count of zero or less means the blocks inside never run.",
                    "Fix the count or remove the loop.", sample);
            case "logic.heavy-call-in-loop" -> new Finding(id, Category.QUALITY, Severity.WARNING,
                    "Slow or visible actions inside loops (" + n + ")",
                    "Network requests, file and Firebase operations, toasts, dialogs or starting a screen inside a loop repeat that action on every turn, which is slow and rarely intended.",
                    "Move the action out of the loop, and collect the data in the loop instead.", sample);
            case "logic.constant-condition" -> new Finding(id, Category.QUALITY, Severity.WARNING,
                    "Conditions that are always true or always false (" + n + ")",
                    "An If with a fixed true or false, or with no condition (the generated code uses true), always takes the same branch, so the other one is dead code.",
                    "Use a real condition, or remove the If and keep the branch that runs.", sample);
            case "logic.empty-block" -> new Finding(id, Category.QUALITY, Severity.INFO,
                    "Ifs and loops with nothing inside (" + n + ")",
                    "They do nothing, or only waste time.",
                    "Add the intended blocks, or remove them.", sample);
            case "logic.division-by-zero" -> new Finding(id, Category.QUALITY, Severity.ERROR,
                    "Division by a typed-in zero (" + n + ")",
                    "Dividing by 0 gives Infinity or NaN, and the remainder of 0 gives NaN.",
                    "Divide by a non-zero value, or check the value before dividing.", sample);
            case "logic.self-assignment" -> new Finding(id, Category.QUALITY, Severity.INFO,
                    "Variables set to themselves (" + n + ")",
                    "Setting a variable to its own value does nothing, so another value was probably meant.",
                    "Check which value should be stored.", sample);
            case "logic.break-outside-loop" -> new Finding(id, Category.QUALITY, Severity.ERROR,
                    "Break blocks outside a loop (" + n + ")",
                    "Java only allows break inside a loop, so the build fails.",
                    "Move the Break into a Repeat or Forever, or remove it.", sample);
            case "sync.custom-java-duplicates-screen" -> new Finding(id, Category.BUILD, Severity.ERROR,
                    "Java files with the same name as a screen (" + n + ")",
                    "The build compiles both the Java generated from the blocks and the project's own Java files. A file with the name of a screen defines the class twice, so the build fails, and edits to one are never seen in the other.",
                    "Rename or delete the Java file (Java manager), or move its code into the screen's blocks or an Add source directly block.", sample);
            default -> new Finding(id, Category.QUALITY, Severity.INFO, id, id, id, sample);
        };
    }
}
