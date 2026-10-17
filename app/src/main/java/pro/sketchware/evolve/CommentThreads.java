package pro.sketchware.evolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the flat list of comments under a post into the rows shown: comments oldest first, each followed by its
 * replies, indented one level per generation up to {@link #MAX_DEPTH}. A reply to a reply deeper than that is shown at
 * the deepest level, still under the same top comment, so a long exchange never squeezes into a sliver. Pure Java.
 */
public final class CommentThreads {

    public static final int MAX_DEPTH = 3;

    /** One row of the discussion. @param depth 0 for a comment under the post */
    public record Row(Comment comment, int depth) {
    }

    private CommentThreads() {
    }

    public static List<Row> rows(List<Comment> comments) {
        Map<String, Comment> byId = new HashMap<>();
        for (Comment comment : comments) {
            byId.put(comment.id(), comment);
        }
        Map<String, List<Comment>> children = new HashMap<>();
        List<Comment> roots = new ArrayList<>();
        for (Comment comment : comments) {
            // An answer to a comment that is not (or no longer) there is shown as a comment of its own
            if (comment.parentId().isEmpty() || !byId.containsKey(comment.parentId()) || comment.parentId().equals(comment.id())) {
                roots.add(comment);
            } else {
                children.computeIfAbsent(comment.parentId(), key -> new ArrayList<>()).add(comment);
            }
        }
        Comparator<Comment> oldestFirst = Comparator.comparingLong(Comment::createdAt).thenComparing(Comment::id);
        roots.sort(oldestFirst);
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Comment root : roots) {
            walk(root, 0, children, oldestFirst, rows, seen);
        }
        return rows;
    }

    private static void walk(Comment comment, int depth, Map<String, List<Comment>> children, Comparator<Comment> order,
                             List<Row> rows, Set<String> seen) {
        // A cycle (a answers b, b answers a) can only come from hand-made data; it must not loop forever
        if (!seen.add(comment.id())) return;
        rows.add(new Row(comment, Math.min(depth, MAX_DEPTH)));
        List<Comment> replies = children.get(comment.id());
        if (replies == null) return;
        replies.sort(order);
        for (Comment reply : replies) {
            walk(reply, depth + 1, children, order, rows, seen);
        }
    }

    /** @return the id a reply to {@code target} is filed under: the comment itself */
    public static String parentFor(Comment target) {
        return target.id();
    }
}
