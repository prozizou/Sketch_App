package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * What the Java panel keeps between sessions, one file per event: the Java the person is editing and has not applied
 * yet, and the history of what was applied. Files are written whole and then moved into place, so a crash leaves the
 * old content, never half of a new one.
 */
public final class SyncStore {
    /**
     * @param text     the Java as typed
     * @param baseHash fingerprint of the blocks the text was started from; tells whether the blocks changed since
     * @param time     when it was saved
     */
    public record Draft(@NonNull String text, @NonNull String baseHash, long time) {
    }

    private record Saved(@Nullable Draft draft, @Nullable String history) {
    }

    private final File file;

    /**
     * @param folder the project's folder for this
     * @param key    names the event ({@code MainActivity_textview1_onClick}); characters a file name can't hold are replaced
     */
    public SyncStore(File folder, String key) {
        this.file = new File(folder, key.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    @Nullable
    public Draft loadDraft() {
        Saved saved = read();
        Draft draft = saved == null ? null : saved.draft();
        return draft == null || draft.text() == null || draft.baseHash() == null ? null : draft;
    }

    public SyncHistory loadHistory() {
        Saved saved = read();
        return SyncHistory.fromJson(saved == null ? null : saved.history());
    }

    /** Keeps the draft; null (or a draft equal to what the blocks make) removes it. */
    public void saveDraft(@Nullable Draft draft) throws IOException {
        Saved saved = read();
        write(new Saved(draft, saved == null ? null : saved.history()));
    }

    public void saveHistory(SyncHistory history) throws IOException {
        Saved saved = read();
        write(new Saved(saved == null ? null : saved.draft(), history.toJson()));
    }

    /** Removes everything kept for the event. */
    public void clear() {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    @Nullable
    private Saved read() {
        if (!file.isFile()) return null;
        try {
            JsonObject root = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            Draft draft = null;
            if (root.has("draft") && root.get("draft").isJsonObject()) {
                JsonObject object = root.getAsJsonObject("draft");
                draft = new Draft(object.get("text").getAsString(), object.get("baseHash").getAsString(), object.get("time").getAsLong());
            }
            String history = root.has("history") && root.get("history").isJsonPrimitive() ? root.get("history").getAsString() : null;
            return new Saved(draft, history);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void write(Saved saved) throws IOException {
        if (saved.draft() == null && saved.history() == null) {
            clear();
            return;
        }
        File folder = file.getParentFile();
        if (folder != null && !folder.isDirectory() && !folder.mkdirs()) throw new IOException("Couldn't create " + folder);
        File temp = new File(folder, file.getName() + ".tmp");
        JsonObject root = new JsonObject();
        if (saved.draft() != null) {
            JsonObject draft = new JsonObject();
            draft.addProperty("text", saved.draft().text());
            draft.addProperty("baseHash", saved.draft().baseHash());
            draft.addProperty("time", saved.draft().time());
            root.add("draft", draft);
        }
        if (saved.history() != null) root.addProperty("history", saved.history());
        Files.write(temp.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
