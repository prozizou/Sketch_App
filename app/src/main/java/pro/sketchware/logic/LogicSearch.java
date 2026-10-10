package pro.sketchware.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds blocks and events of one screen from what the user types: a variable, a list, a more block, a widget, a
 * typed-in value or any word of a block. Pure Java so it can be unit tested.
 */
public final class LogicSearch {

    /** A slot of a block spec: %s, %d, %b, %s.inputOnly, %m.varInt... */
    private static final Pattern SLOT = Pattern.compile("%(?:[sdb](?:\\.[A-Za-z0-9_]+)?|m\\.[A-Za-z0-9_]+)");

    /**
     * @param eventKey the event, like {@code button1_onClick} or {@code name_moreBlock}
     * @param blockId  the block found, empty when the event itself matched by name
     * @param text     what to show: the block as it reads in the editor, or the event name
     */
    public record Hit(String eventKey, String blockId, String text) {
        public boolean isEvent() {
            return blockId.isEmpty();
        }
    }

    private LogicSearch() {
    }

    /**
     * @return the events whose name matches first, then the matching blocks, event by event, at most {@code limit}.
     * Every word of the query must appear; case is ignored. An empty query finds nothing.
     */
    public static List<Hit> search(LogicScreen screen, String query, int limit) {
        List<Hit> hits = new ArrayList<>();
        String[] words = words(query);
        if (words.length == 0 || screen == null) {
            return hits;
        }
        List<LogicEvent> events = new ArrayList<>(screen.events());
        events.sort(Comparator.comparing(LogicEvent::key));
        for (LogicEvent event : events) {
            if (hits.size() >= limit) return hits;
            String name = eventLabel(event.key());
            if (matches(name + " " + event.key(), words)) {
                hits.add(new Hit(event.key(), "", name));
            }
        }
        for (LogicEvent event : events) {
            for (LogicBlock block : event.blocks()) {
                if (hits.size() >= limit) return hits;
                String text = render(block);
                if (matches(text, words)) {
                    hits.add(new Hit(event.key(), block.id(), text));
                }
            }
        }
        return hits;
    }

    /**
     * The block as it reads in the editor: typed-in values in brackets, a plugged-in block as "( )".
     * {@code set %m.varInt to %d} with "count" and "5" reads {@code set [count] to [5]}.
     */
    public static String render(LogicBlock block) {
        String spec = block.spec();
        Matcher slot = SLOT.matcher(spec);
        StringBuilder out = new StringBuilder();
        int last = 0;
        int index = 0;
        while (slot.find()) {
            out.append(spec, last, slot.start());
            String value = block.param(index++);
            if (block.paramBlockId(index - 1) != null) {
                out.append("( )");
            } else {
                out.append('[').append(value).append(']');
            }
            last = slot.end();
        }
        out.append(spec.substring(last));
        return out.toString().trim();
    }

    /**
     * A readable event name: {@code button1_onClick} becomes "button1 · onClick", {@code calc_moreBlock}
     * becomes "calc (more block)", {@code onCreate_initializeLogic} becomes "onCreate".
     */
    public static String eventLabel(String key) {
        int split = key.lastIndexOf('_');
        if (split < 0) return key;
        String target = key.substring(0, split);
        String event = key.substring(split + 1);
        if (event.equals("moreBlock")) return target + " (more block)";
        if (event.equals("initializeLogic")) return target;
        return target + " · " + event;
    }

    private static String[] words(String query) {
        String trimmed = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    private static boolean matches(String text, String[] words) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (!lower.contains(word)) return false;
        }
        return true;
    }
}
