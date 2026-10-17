package pro.sketchware.evolve;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The last feed, kept in a file so the community page opens with something to read when the phone is offline or the
 * database is slow. It is a copy, never the truth: the live data replaces it as soon as it arrives. Any damage to the
 * file just means an empty list. Pure Java apart from the file it is given.
 */
public final class EvolveDiskCache {

    /** At most this many posts are kept, and at most {@link #MAX_CHARS} characters of them, to stay small on a 2 GB phone. */
    public static final int MAX_POSTS = 40;
    public static final int MAX_CHARS = 1_500_000;

    private static final Type TYPE = new TypeToken<List<Entry>>() {
    }.getType();
    private static final Gson GSON = new Gson();

    private static final class Entry {
        String id;
        Map<String, Object> fields;
    }

    private EvolveDiskCache() {
    }

    /** Writes {@code posts} (the newest first, trimmed to the limits) with a temporary file and a rename, so a crash can't leave half a file. */
    public static void save(File file, List<SharedBlock> posts) {
        List<Entry> entries = new ArrayList<>();
        int chars = 0;
        for (SharedBlock post : posts) {
            if (entries.size() >= MAX_POSTS) break;
            Entry entry = new Entry();
            entry.id = post.id();
            entry.fields = post.toStoredMap();
            int size = post.blocksJson().length() + post.xml().length() + post.meta().code().length() + 400;
            if (chars + size > MAX_CHARS && !entries.isEmpty()) break;
            chars += size;
            entries.add(entry);
        }
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (Writer writer = new FileWriter(temp)) {
            GSON.toJson(entries, TYPE, writer);
        } catch (IOException | RuntimeException e) {
            temp.delete();
            return;
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException again) {
                temp.delete();
            }
        }
    }

    /** @return the posts saved last, or an empty list when there is no file or it can't be read */
    public static List<SharedBlock> load(File file) {
        List<SharedBlock> posts = new ArrayList<>();
        if (!file.isFile()) return posts;
        try (Reader reader = new FileReader(file)) {
            List<Entry> entries = GSON.fromJson(reader, TYPE);
            if (entries == null) return posts;
            for (Entry entry : entries) {
                if (entry == null || entry.id == null || entry.fields == null) continue;
                SharedBlock post = SharedBlock.fromMap(entry.id, entry.fields);
                if (post != null) posts.add(post);
            }
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
        return posts;
    }

    public static void clear(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
