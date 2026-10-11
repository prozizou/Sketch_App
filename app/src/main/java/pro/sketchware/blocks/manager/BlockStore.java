package pro.sketchware.blocks.manager;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Reads and writes {@code palette.json} and {@code block.json}. A file that can't be parsed is never overwritten;
 * every save keeps the previous version as {@code .bak} and replaces the file in one step, so an interrupted save
 * (crash, battery) leaves the old file intact.
 */
public final class BlockStore {
    private static final Type MAP_LIST = new TypeToken<ArrayList<HashMap<String, Object>>>() {
    }.getType();

    /** A file exists but isn't a list of blocks or palettes: nothing is written until it is fixed. */
    public static final class UnreadableFile extends IOException {
        public final File file;

        UnreadableFile(File file, Throwable cause) {
            super(file.getName() + " can't be read: " + (cause == null ? "not a JSON list" : cause.getMessage()), cause);
            this.file = file;
        }
    }

    private final File paletteFile;
    private final File blockFile;
    private final Gson gson;

    public BlockStore(File paletteFile, File blockFile, Gson gson) {
        this.paletteFile = paletteFile;
        this.blockFile = blockFile;
        this.gson = gson;
    }

    public File paletteFile() {
        return paletteFile;
    }

    public File blockFile() {
        return blockFile;
    }

    public BlockLibrary load() throws IOException {
        return new BlockLibrary(readList(paletteFile), readList(blockFile));
    }

    public List<HashMap<String, Object>> readList(File file) throws IOException {
        if (!file.isFile()) return new ArrayList<>();
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) return new ArrayList<>();
        try {
            List<HashMap<String, Object>> list = gson.fromJson(content, MAP_LIST);
            if (list == null) throw new UnreadableFile(file, null);
            list.removeIf(java.util.Objects::isNull);
            return list;
        } catch (JsonParseException | IllegalStateException e) {
            throw new UnreadableFile(file, e);
        }
    }

    public void save(BlockLibrary library) throws IOException {
        write(paletteFile, gson.toJson(library.palettes()));
        write(blockFile, gson.toJson(library.blocks()));
    }

    /** Writes {@code content} next to the file, keeps the old file as .bak, then puts the new one in its place. */
    public static void write(File file, String content) throws IOException {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create " + dir);
        File temp = new File(dir, file.getName() + ".tmp");
        Files.writeString(temp.toPath(), content, StandardCharsets.UTF_8);
        if (file.isFile()) {
            Files.copy(file.toPath(), new File(dir, file.getName() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
