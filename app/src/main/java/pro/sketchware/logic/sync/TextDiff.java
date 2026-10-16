package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** A line by line difference between two texts, for showing what a change would do before it is applied. */
public final class TextDiff {
    public enum Kind {SAME, ADDED, REMOVED}

    public record Line(@NonNull Kind kind, @NonNull String text) {
    }

    /** Texts longer than this many lines on each side are compared by their common start and end only. */
    private static final int MAX_LINES = 2000;

    private final List<Line> lines;
    private final int added;
    private final int removed;

    private TextDiff(List<Line> lines) {
        this.lines = List.copyOf(lines);
        int plus = 0;
        int minus = 0;
        for (Line line : lines) {
            if (line.kind() == Kind.ADDED) plus++;
            else if (line.kind() == Kind.REMOVED) minus++;
        }
        added = plus;
        removed = minus;
    }

    public static TextDiff of(String before, String after) {
        List<String> a = split(before);
        List<String> b = split(after);
        List<Line> out = new ArrayList<>();
        int start = 0;
        while (start < a.size() && start < b.size() && a.get(start).equals(b.get(start))) {
            out.add(new Line(Kind.SAME, a.get(start)));
            start++;
        }
        int endA = a.size();
        int endB = b.size();
        List<Line> tail = new ArrayList<>();
        while (endA > start && endB > start && a.get(endA - 1).equals(b.get(endB - 1))) {
            tail.add(0, new Line(Kind.SAME, a.get(endA - 1)));
            endA--;
            endB--;
        }
        List<String> middleA = a.subList(start, endA);
        List<String> middleB = b.subList(start, endB);
        if (middleA.size() > MAX_LINES || middleB.size() > MAX_LINES) {
            for (String line : middleA) out.add(new Line(Kind.REMOVED, line));
            for (String line : middleB) out.add(new Line(Kind.ADDED, line));
        } else {
            out.addAll(lcs(middleA, middleB));
        }
        out.addAll(tail);
        return new TextDiff(out);
    }

    private static List<Line> lcs(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int[][] table = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                table[i][j] = a.get(i).equals(b.get(j)) ? table[i + 1][j + 1] + 1 : Math.max(table[i + 1][j], table[i][j + 1]);
            }
        }
        List<Line> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                out.add(new Line(Kind.SAME, a.get(i)));
                i++;
                j++;
            } else if (table[i + 1][j] >= table[i][j + 1]) {
                out.add(new Line(Kind.REMOVED, a.get(i++)));
            } else {
                out.add(new Line(Kind.ADDED, b.get(j++)));
            }
        }
        while (i < n) out.add(new Line(Kind.REMOVED, a.get(i++)));
        while (j < m) out.add(new Line(Kind.ADDED, b.get(j++)));
        return out;
    }

    private static List<String> split(String text) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) return lines;
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) lines.add(line);
        return lines;
    }

    public List<Line> lines() {
        return lines;
    }

    public int added() {
        return added;
    }

    public int removed() {
        return removed;
    }

    public boolean isEmpty() {
        return added == 0 && removed == 0;
    }

    /** The difference as text with {@code +} and {@code -} in front of changed lines, unchanged lines shown only near a change. */
    public String toUnified(int context) {
        StringBuilder out = new StringBuilder();
        boolean[] show = new boolean[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).kind() == Kind.SAME) continue;
            for (int j = Math.max(0, i - context); j <= Math.min(lines.size() - 1, i + context); j++) show[j] = true;
        }
        boolean skipped = false;
        for (int i = 0; i < lines.size(); i++) {
            if (!show[i]) {
                skipped = true;
                continue;
            }
            if (skipped && out.length() > 0) out.append("…\n");
            skipped = false;
            Line line = lines.get(i);
            out.append(switch (line.kind()) {
                case ADDED -> "+ ";
                case REMOVED -> "- ";
                case SAME -> "  ";
            }).append(line.text()).append('\n');
        }
        return out.toString();
    }
}
