package pro.sketchware.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.analysis.Category;
import pro.sketchware.analysis.Finding;
import pro.sketchware.analysis.Severity;

/**
 * Which screen opens which: read from the Intent "set screen" blocks of every event, and from {@code X.class}
 * in the project's own Java files. Screens named like fragments are shown in pagers and dialogs, not opened.
 */
public final class NavigationGraph {
    public static final String MAIN_SCREEN = "MainActivity";
    private static final Pattern CLASS_LITERAL = Pattern.compile("\\b([A-Z]\\w*)\\.class\\b");

    /** @param via the event key, or the Java file the link comes from */
    public record Edge(String from, String to, String via, boolean fromJava) {
    }

    private final List<String> screens = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Set<String> javaClasses = new LinkedHashSet<>();

    private NavigationGraph() {
    }

    /** @param javaFiles the project's own Java files: name to content */
    public static NavigationGraph build(List<LogicScreen> logicScreens, Map<String, String> javaFiles) {
        NavigationGraph graph = new NavigationGraph();
        for (LogicScreen screen : logicScreens) {
            graph.screens.add(screen.className());
        }
        for (LogicScreen screen : logicScreens) {
            for (LogicEvent event : screen.events()) {
                Set<String> reachable = new BlockGraph(event).reachable();
                for (LogicBlock block : event.blocks()) {
                    if ("intentSetScreen".equals(block.opCode()) && reachable.contains(block.id())) {
                        String target = block.param(1);
                        if (!target.isEmpty() && !target.startsWith("@")) {
                            graph.addEdge(new Edge(screen.className(), target, event.key(), false));
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, String> file : javaFiles.entrySet()) {
            String name = file.getKey().replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\.java$", "");
            graph.javaClasses.add(name);
            Matcher matcher = CLASS_LITERAL.matcher(file.getValue());
            while (matcher.find()) {
                String target = matcher.group(1);
                if (graph.screens.contains(target) && !target.equals(name)) {
                    graph.addEdge(new Edge(name, target, file.getKey(), true));
                }
            }
        }
        return graph;
    }

    private void addEdge(Edge edge) {
        for (Edge existing : edges) {
            if (existing.from().equals(edge.from()) && existing.to().equals(edge.to()) && existing.via().equals(edge.via())) {
                return;
            }
        }
        edges.add(edge);
    }

    public List<String> screens() {
        return List.copyOf(screens);
    }

    public List<Edge> edges() {
        return List.copyOf(edges);
    }

    public List<Edge> edgesFrom(String screen) {
        return edges.stream().filter(e -> e.from().equals(screen)).toList();
    }

    public static boolean isFragment(String screen) {
        return screen.endsWith("FragmentActivity");
    }

    /** Screens nothing opens, apart from the main screen and fragments. */
    public List<String> unreachable() {
        List<String> result = new ArrayList<>();
        for (String screen : screens) {
            if (screen.equals(MAIN_SCREEN) || isFragment(screen)) continue;
            if (edges.stream().noneMatch(e -> e.to().equals(screen) && !e.from().equals(screen))) {
                result.add(screen);
            }
        }
        return result;
    }

    /** Links to a screen that is not in the project (deleted or renamed). */
    public List<Edge> unknownTargets() {
        return edges.stream().filter(e -> !screens.contains(e.to()) && !javaClasses.contains(e.to())).toList();
    }

    public List<Finding> findings() {
        List<Finding> out = new ArrayList<>();
        List<String> unreachable = unreachable();
        if (!unreachable.isEmpty()) {
            out.add(new Finding("nav.unreachable-screen", Category.QUALITY, Severity.INFO,
                    "Screens no block or Java file opens (" + unreachable.size() + ")",
                    "No Intent block and no Java file of the project opens these screens, so users may never reach them (unless another app or a notification opens them).",
                    "Open them from another screen, or delete them if they are left over.", String.join(", ", unreachable)));
        }
        List<Edge> unknown = unknownTargets();
        if (!unknown.isEmpty()) {
            List<String> items = new ArrayList<>();
            for (Edge edge : unknown) items.add(edge.from() + " > " + edge.via() + " -> " + edge.to());
            out.add(new Finding("nav.unknown-target", Category.QUALITY, Severity.ERROR,
                    "Intents to screens that do not exist (" + unknown.size() + ")",
                    "The generated code refers to a screen class that is not in the project, so the build fails.",
                    "Pick an existing screen in the Intent block, or create the screen again.", String.join(", ", items)));
        }
        return out;
    }

    /** The graph as a Mermaid flowchart, to paste in a README or a Markdown viewer. */
    public String toMermaid() {
        StringBuilder text = new StringBuilder("flowchart LR\n");
        for (String screen : screens) {
            text.append("    ").append(screen).append(isFragment(screen) ? "([" : "[").append(screen)
                    .append(isFragment(screen) ? "])" : "]").append('\n');
        }
        for (Edge edge : edges) {
            String label = edge.via().replace('"', '\'');
            text.append("    ").append(edge.from()).append(edge.fromJava() ? " -.->|" : " -->|").append(label).append("| ")
                    .append(edge.to()).append('\n');
        }
        return text.toString();
    }
}
