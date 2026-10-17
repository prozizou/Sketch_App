package pro.sketchware.evolve;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import androidx.annotation.Nullable;

/**
 * The file operations behind "Add to project": a file written into a folder, a component list merged into the
 * components file, and each one undone from what it replaced. Names are checked to be plain file names, so a shared
 * name can never write outside the folder it is meant for. Pure Java with the files it is given.
 */
public final class ImportFiles {

    private ImportFiles() {
    }

    /** @return whether {@code name} is one file name: no folders, no dots-only, no control characters */
    public static boolean isPlainFileName(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || Character.isISOControl(c)) return false;
        }
        return name.equals(new File(name).getName());
    }

    /**
     * Writes {@code content} to {@code dir/name} through a temporary file, creating the folder.
     *
     * @return what the file held before, or null when it did not exist (for {@link #restore})
     */
    @Nullable
    public static String write(File dir, String name, String content) throws IOException {
        if (!isPlainFileName(name)) throw new IOException("not a plain file name: " + name);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File target = new File(dir, name);
        String previous = target.isFile() ? Files.readString(target.toPath(), StandardCharsets.UTF_8) : null;
        File temp = new File(dir, name + ".evolve-tmp");
        Files.writeString(temp.toPath(), content, StandardCharsets.UTF_8);
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return previous;
    }

    /** Puts {@code dir/name} back as it was: its old content, or gone when {@code previous} is null. */
    public static void restore(File dir, String name, @Nullable String previous) throws IOException {
        if (!isPlainFileName(name)) throw new IOException("not a plain file name: " + name);
        File target = new File(dir, name);
        if (previous == null) {
            Files.deleteIfExists(target.toPath());
        } else {
            write(dir, name, previous);
        }
    }

    /**
     * Adds the components of {@code newJson} to the components file {@code list}. With {@code replace}, components
     * that have the same name or id as a new one are dropped first; otherwise they are kept and nothing is added twice.
     *
     * @return the file's previous content, or null when it did not exist (for {@link #restore})
     */
    @Nullable
    public static String mergeComponents(File list, String newJson, boolean replace) throws IOException {
        JsonArray existing = new JsonArray();
        String previous = null;
        if (list.isFile()) {
            previous = Files.readString(list.toPath(), StandardCharsets.UTF_8);
            try {
                JsonElement parsed = JsonParser.parseString(previous);
                if (parsed.isJsonArray()) existing = parsed.getAsJsonArray();
            } catch (RuntimeException ignored) {
                // An unreadable list is not touched: nothing below could keep what is in it
                throw new IOException("the components file is damaged");
            }
        }
        JsonElement incoming = JsonParser.parseString(newJson);
        JsonArray add = incoming.isJsonArray() ? incoming.getAsJsonArray() : new JsonArray();
        if (incoming.isJsonObject()) add.add(incoming);
        Set<String> names = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : add) {
            JsonObject object = element.getAsJsonObject();
            names.add(object.get("name").getAsString().toLowerCase(Locale.ROOT));
            ids.add(object.get("id").getAsString().toLowerCase(Locale.ROOT));
        }
        JsonArray merged = new JsonArray();
        for (JsonElement element : existing) {
            JsonObject object = element.isJsonObject() ? element.getAsJsonObject() : null;
            boolean clash = object != null && object.has("name") && names.contains(object.get("name").getAsString().toLowerCase(Locale.ROOT))
                    || object != null && object.has("id") && ids.contains(object.get("id").getAsString().toLowerCase(Locale.ROOT));
            if (!clash || !replace) merged.add(element);
        }
        for (JsonElement element : add) {
            JsonObject object = element.getAsJsonObject();
            boolean already = false;
            for (JsonElement kept : merged) {
                if (kept.isJsonObject() && kept.getAsJsonObject().has("name")
                        && kept.getAsJsonObject().get("name").getAsString().equalsIgnoreCase(object.get("name").getAsString())) {
                    already = true;
                }
            }
            if (!already) merged.add(element);
        }
        File parent = list.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
        Files.writeString(list.toPath(), new Gson().toJson(merged), StandardCharsets.UTF_8);
        return previous;
    }

    /** Puts the components file back as it was; removes it when it did not exist before. */
    public static void restoreList(File list, @Nullable String previous) throws IOException {
        if (previous == null) {
            Files.deleteIfExists(list.toPath());
        } else {
            Files.writeString(list.toPath(), previous, StandardCharsets.UTF_8);
        }
    }

    /** @return {@code name} made unique in {@code dir} by adding -2, -3... before the extension (for downloads) */
    public static String uniqueFileName(File dir, String name) {
        if (!new File(dir, name).exists()) return name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; ; i++) {
            String candidate = base + "-" + i + extension;
            if (!new File(dir, candidate).exists()) return candidate;
        }
    }
}
