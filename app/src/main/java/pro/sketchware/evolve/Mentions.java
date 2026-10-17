package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code @name} mentions in a comment. A mention is matched against the people already in the discussion (the post's
 * author and the writers of the comments), so a name can contain spaces and nobody is looked up by guessing. Pure Java.
 */
public final class Mentions {

    private Mentions() {
    }

    /**
     * @param text       the comment as written
     * @param candidates display name to uid of the people that can be mentioned
     * @return the uids of the candidates written as {@code @name} in {@code text}, in order of appearance. The longest
     * name wins ({@code @Ann Lee} is not also {@code @Ann}), a name only counts as a whole word, and nobody is listed twice.
     */
    public static List<String> extract(String text, Map<String, String> candidates) {
        if (text == null || text.indexOf('@') < 0 || candidates.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(candidates.keySet());
        names.removeIf(name -> name == null || name.isBlank());
        names.sort(Comparator.comparingInt(String::length).reversed());
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> uids = new LinkedHashSet<>();
        int from = 0;
        while (true) {
            int at = lower.indexOf('@', from);
            if (at < 0) break;
            from = at + 1;
            // The @ must start a word: "me@host" is an address, not a mention
            if (at > 0 && Character.isLetterOrDigit(lower.charAt(at - 1))) continue;
            for (String name : names) {
                String needle = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith(needle, at + 1) && endsWord(lower, at + 1 + needle.length())) {
                    uids.add(candidates.get(name));
                    from = at + 1 + needle.length();
                    break;
                }
            }
        }
        return new ArrayList<>(uids);
    }

    private static boolean endsWord(String text, int end) {
        return end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
    }

    /** @return the part after the last {@code @} when the caret sits in a mention being typed, else null (for suggestions) */
    public static String typing(String textBeforeCaret) {
        if (textBeforeCaret == null) return null;
        int at = textBeforeCaret.lastIndexOf('@');
        if (at < 0) return null;
        if (at > 0 && Character.isLetterOrDigit(textBeforeCaret.charAt(at - 1))) return null;
        String after = textBeforeCaret.substring(at + 1);
        return after.contains("\n") || after.length() > 40 ? null : after;
    }
}
