package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        MINE,
        /** What the authors the signed-in user follows shared, newest first. */
        FOLLOWING,
        /** What the signed-in user saved, newest first. */
        SAVED
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
        return apply(all, offered, kind, mode, query, myUid, now, Set.of(), Set.of());
    }

    /**
     * @param following uids of the authors the signed-in user follows, for {@link Mode#FOLLOWING}
     * @param saved     ids of the posts the signed-in user saved, for {@link Mode#SAVED}
     */
    public static List<SharedBlock> apply(List<SharedBlock> all, EnumSet<SharedKind> offered, SharedKind kind, Mode mode,
                                          String query, String myUid, long now, Set<String> following, Set<String> saved) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<SharedBlock> shown = new ArrayList<>();
        for (SharedBlock block : all) {
            if (!offered.contains(block.kind()) || kind != null && block.kind() != kind) {
                continue;
            }
            if (mode == Mode.MINE && !(!myUid.isEmpty() && myUid.equals(block.authorUid()))) {
                continue;
            }
            if (mode == Mode.FOLLOWING && !following.contains(block.authorUid())) {
                continue;
            }
            if (mode == Mode.SAVED && !saved.contains(block.id())) {
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
        shown.sort(mode == Mode.POPULAR ? Comparator.comparingLong(EvolveFilter::popularity).reversed().thenComparing(newest) : newest);
        return shown;
    }

    /** How popular an entry is: every pick counts one, a like three, a comment two. */
    public static long popularity(SharedBlock block) {
        return block.downloads() + 3 * block.meta().likes() + 2 * block.meta().commentCount();
    }

    static boolean matches(SharedBlock block, String needle) {
        return contains(block.name(), needle)
                || contains(block.authorName(), needle)
                || contains(block.description(), needle)
                || contains(block.id(), needle)
                || contains(String.join(" ", block.opCodes()), needle)
                || contains(String.join(" ", block.meta().tags()), needle)
                || contains(block.meta().fileName(), needle)
                || block.kind() == SharedKind.VIEW && contains(block.xml(), needle)
                || block.kind().isCode() && contains(block.meta().code(), needle);
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }
}
