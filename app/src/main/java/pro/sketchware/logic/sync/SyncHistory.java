package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversions of one event that were applied, so each can be taken back and done again. An entry keeps the blocks
 * and the Java before and after. Undo and redo move a cursor; applying something new drops what could have been redone.
 */
public final class SyncHistory {
    /** The most conversions kept for an event. */
    public static final int MAX_ENTRIES = 30;

    /**
     * One applied conversion.
     *
     * @param time         when it was applied
     * @param label        what it was ("Applied Java", "Undo", ...)
     * @param beforeBlocks the blocks before, as {@link BlockJson} text
     * @param beforeJava   the Java the panel held before
     * @param afterBlocks  the blocks after
     * @param afterJava    the Java the blocks make after
     */
    public record Entry(long time, @NonNull String label, @NonNull String beforeBlocks, @NonNull String beforeJava,
                        @NonNull String afterBlocks, @NonNull String afterJava) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private int cursor;

    public void push(Entry entry) {
        while (entries.size() > cursor) entries.remove(entries.size() - 1);
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
        cursor = entries.size();
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor < entries.size();
    }

    /** The entry an undo would take back, null when there is none. */
    @Nullable
    public Entry nextUndo() {
        return cursor > 0 ? entries.get(cursor - 1) : null;
    }

    /** The entry a redo would do again, null when there is none. */
    @Nullable
    public Entry nextRedo() {
        return cursor < entries.size() ? entries.get(cursor) : null;
    }

    public void undone() {
        if (cursor > 0) cursor--;
    }

    public void redone() {
        if (cursor < entries.size()) cursor++;
    }

    public int size() {
        return entries.size();
    }

    // ---- saving

    public String toJson() {
        JsonArray list = new JsonArray();
        for (Entry entry : entries) {
            JsonObject object = new JsonObject();
            object.addProperty("time", entry.time());
            object.addProperty("label", entry.label());
            object.addProperty("beforeBlocks", entry.beforeBlocks());
            object.addProperty("beforeJava", entry.beforeJava());
            object.addProperty("afterBlocks", entry.afterBlocks());
            object.addProperty("afterJava", entry.afterJava());
            list.add(object);
        }
        JsonObject root = new JsonObject();
        root.add("entries", list);
        root.addProperty("cursor", cursor);
        return root.toString();
    }

    /** @return the history in {@code json}, or an empty one when it can't be read */
    public static SyncHistory fromJson(@Nullable String json) {
        SyncHistory history = new SyncHistory();
        if (json == null || json.isBlank()) return history;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("entries")) {
                JsonObject object = element.getAsJsonObject();
                history.entries.add(new Entry(object.get("time").getAsLong(), object.get("label").getAsString(),
                        object.get("beforeBlocks").getAsString(), object.get("beforeJava").getAsString(),
                        object.get("afterBlocks").getAsString(), object.get("afterJava").getAsString()));
            }
            history.cursor = Math.max(0, Math.min(root.get("cursor").getAsInt(), history.entries.size()));
        } catch (RuntimeException e) {
            return new SyncHistory();
        }
        return history;
    }
}
