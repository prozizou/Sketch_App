package pro.sketchware.blocks.manager;

import java.util.ArrayDeque;
import java.util.Deque;

/** Undo / redo for a text: states are recorded as it changes, at most {@code limit} of them. */
public final class TextHistory {
    public record State(String text, int cursor) {
    }

    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private final int limit;
    private State current;

    public TextHistory(String initial, int limit) {
        current = new State(initial, initial.length());
        this.limit = Math.max(1, limit);
    }

    /** Records a new state (typing); clears what could be redone. */
    public void record(String text, int cursor) {
        if (text.equals(current.text())) return;
        undo.push(current);
        while (undo.size() > limit) undo.removeLast();
        redo.clear();
        current = new State(text, cursor);
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** @return the state to show, or null when there's nothing to undo */
    public State undo() {
        if (undo.isEmpty()) return null;
        redo.push(current);
        current = undo.pop();
        return current;
    }

    public State redo() {
        if (redo.isEmpty()) return null;
        undo.push(current);
        current = redo.pop();
        return current;
    }
}
