package pro.sketchware.logic;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The logic of one screen.
 *
 * @param javaName    like {@code MainActivity.java}
 * @param variables   variable name to its type number (as the project stores it)
 * @param lists       list names
 * @param moreBlocks  more block names
 * @param widgetIds   ids of the widgets on the screen's layout
 */
public record LogicScreen(String javaName, List<LogicEvent> events, Map<String, Integer> variables, Set<String> lists,
                          Set<String> moreBlocks, Set<String> widgetIds) {
    public LogicScreen {
        events = events == null ? List.of() : List.copyOf(events);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        lists = lists == null ? Set.of() : Set.copyOf(lists);
        moreBlocks = moreBlocks == null ? Set.of() : Set.copyOf(moreBlocks);
        widgetIds = widgetIds == null ? Set.of() : Set.copyOf(widgetIds);
    }

    /** The class name the code generator gives the screen, like {@code MainActivity}. */
    public String className() {
        return javaName.endsWith(".java") ? javaName.substring(0, javaName.length() - 5) : javaName;
    }

    /**
     * The variable name in a custom declaration as the Variable manager stores it, like {@code String name},
     * {@code private int count = 0} or {@code HashMap<String, Object> map}; empty when none can be found.
     */
    public static String declaredName(String declaration) {
        if (declaration == null) return "";
        String text = declaration.trim();
        int equals = text.indexOf('=');
        if (equals >= 0) text = text.substring(0, equals);
        text = text.replace(";", "").trim();
        int space = Math.max(text.lastIndexOf(' '), Math.max(text.lastIndexOf('>'), text.lastIndexOf(']')));
        String name = space < 0 ? text : text.substring(space + 1).trim();
        return name.matches("[A-Za-z_$][\\w$]*") ? name : "";
    }
}
