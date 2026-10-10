package pro.sketchware.control;

import java.util.ArrayList;
import java.util.List;

/**
 * The entries of the "What's new" dialog and where each one leads. Pure Java so it can be unit tested.
 * <p>
 * Each entry of the {@code whats_new_entries} string array is {@code target|title|description}, for example
 * {@code project_health|Open from Project health|Each problem has an Open button…}.
 */
public final class WhatsNewCatalog {

    /** Where tapping an entry goes. Features that live inside a project first ask which project to open. */
    public enum Target {
        APP_SETTINGS("app_settings", false),
        PROJECT_HEALTH("project_health", true),
        AUTO_FIX("auto_fix", true),
        NAVIGATION_GRAPH("navigation_graph", true),
        DATA_DESIGNER("data_designer", true),
        DEVICE_PREVIEW("device_preview", true),
        BLOCK_DEBUGGER("block_debugger", true),
        LOGIC_SEARCH("logic_search", true),
        /** Nothing to open: the entry only informs. */
        NONE("none", false);

        public final String key;
        public final boolean needsProject;

        Target(String key, boolean needsProject) {
            this.key = key;
            this.needsProject = needsProject;
        }

        /**
         * @return the target with that key, or {@code null} when there is none.
         */
        public static Target byKey(String key) {
            for (Target target : values()) {
                if (target.key.equals(key)) {
                    return target;
                }
            }
            return null;
        }
    }

    public record Item(String title, String description, Target target) {
    }

    private WhatsNewCatalog() {
    }

    /**
     * Reads the entries. An entry with an unknown target still shows, but opens nothing; an entry without a
     * title is skipped.
     */
    public static List<Item> parse(String[] entries) {
        List<Item> items = new ArrayList<>();
        for (String entry : entries) {
            String[] parts = entry.split("\\|", 3);
            if (parts.length < 2 || parts[1].trim().isEmpty()) {
                continue;
            }
            Target target = Target.byKey(parts[0].trim());
            items.add(new Item(parts[1].trim(), parts.length == 3 ? parts[2].trim() : "",
                    target == null ? Target.NONE : target));
        }
        return items;
    }
}
