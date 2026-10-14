package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Which loaded entries the Evolve list shows, and in which order. Pure Java. */
public final class EvolveFilter {

    /** The filter chips. */
    public enum Mode {
        /** Everything, newest first. */
        ALL,
        /** Everything, most picked first. */
        POPULAR,
        /** What was shared in the last {@link #RECENT_DAYS} days, newest first. */
        RECENT,
        /** What the signed-in user shared, newest first. */
        MINE
    }

    public static final int RECENT_DAYS = 7;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private EvolveFilter() {
    }

    /**
     * @param offered the kinds the open screen can use: others are never shown
     * @param kind    a single kind chosen with the chips, or null for all offered kinds
     * @param query   free text, matched against title, author, description, identifier, opcodes and XML
     * @param myUid   the signed-in user's uid, or "" when nobody is
     * @param now     the current time, milliseconds since the epoch
     */
    public static List<SharedBlock> apply(List<SharedBlock> all, EnumSet<SharedKind> offered, SharedKind kind, Mode mode,
                                          String query, String myUid, long now) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<SharedBlock> shown = new ArrayList<>();
        for (SharedBlock block : all) {
            if (!offered.contains(block.kind()) || kind != null && block.kind() != kind) {
                continue;
            }
            if (mode == Mode.MINE && !(!myUid.isEmpty() && myUid.equals(block.authorUid()))) {
                continue;
            }
            if (mode == Mode.RECENT && block.createdAt() < now - RECENT_DAYS * DAY_MS) {
                continue;
            }
            if (!needle.isEmpty() && !matches(block, needle)) {
                continue;
            }
            shown.add(block);
        }
        Comparator<SharedBlock> newest = Comparator.comparingLong(SharedBlock::createdAt).reversed();
        shown.sort(mode == Mode.POPULAR ? Comparator.comparingLong(SharedBlock::downloads).reversed().thenComparing(newest) : newest);
        return shown;
    }

    static boolean matches(SharedBlock block, String needle) {
        return contains(block.name(), needle)
                || contains(block.authorName(), needle)
                || contains(block.description(), needle)
                || contains(block.id(), needle)
                || contains(String.join(" ", block.opCodes()), needle)
                || block.kind() == SharedKind.VIEW && contains(block.xml(), needle);
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }
}
