package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The record of what the resolver changed, one folder per operation, so exactly that can be taken back later. It lists
 * the files it created (with a fingerprint of what it wrote) and the values files it added entries to (with a copy
 * of the file as it was). Taking an operation back never removes something the person has since changed: a created
 * file that was edited is kept, and added entries are removed one by one from a values file that was edited.
 */
public final class ResourceJournal {
    public enum State {PENDING, DONE, UNDONE}

    /** A file the operation created. */
    public record Created(@NonNull String path, @NonNull String hash) {
    }

    /** A values file the operation added entries to. */
    public record Modified(@NonNull String path, @NonNull String hashBefore, @NonNull String hashAfter, @NonNull String backup,
                           @NonNull List<String> addedLines) {
    }

    /**
     * @param id       the folder's name (the time, then a counter)
     * @param time     when it was applied
     * @param state    whether it is complete, interrupted, or already taken back
     * @param created  files created
     * @param modified values files edited
     * @param dir      its folder
     */
    public record Entry(@NonNull String id, long time, @NonNull State state, @NonNull List<Created> created, @NonNull List<Modified> modified, @NonNull File dir) {
        public int changeCount() {
            int added = 0;
            for (Modified m : modified) added += m.addedLines().size();
            return created.size() + added;
        }
    }

    /**
     * @param removed  what was taken back (paths of files, or {@code path: line} for entries)
     * @param kept     what was left because it changed after the resolver wrote it
     */
    public record UndoResult(@NonNull List<String> removed, @NonNull List<String> kept) {
    }

    static final String MANIFEST = "journal.txt";
    static final String BACKUPS = "backup";
    private static final String HEADER = "resolver-journal 1";
    private static final int KEEP = 20;

    private ResourceJournal() {
    }

    // ---- writing ----

    /** Starts an operation's folder; its state is {@link State#PENDING} until {@link #write} marks it done. */
    static File newDirectory(File root, long time) throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Couldn't create " + root);
        for (int counter = 0; counter < 1000; counter++) {
            File dir = new File(root, time + (counter == 0 ? "" : "-" + counter));
            if (dir.mkdir()) {
                if (!new File(dir, BACKUPS).mkdir()) throw new IOException("Couldn't create " + dir);
                return dir;
            }
        }
        throw new IOException("Couldn't create a folder in " + root);
    }

    static void write(File dir, long time, State state, List<Created> created, List<Modified> modified) throws IOException {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        text.append("time\t").append(time).append('\n');
        text.append("state\t").append(state.name().toLowerCase(java.util.Locale.ROOT)).append('\n');
        for (Created c : created) text.append("created\t").append(c.path()).append('\t').append(c.hash()).append('\n');
        for (Modified m : modified) {
            text.append("modified\t").append(m.path()).append('\t').append(m.hashBefore()).append('\t').append(m.hashAfter()).append('\t').append(m.backup()).append('\n');
            for (String line : m.addedLines()) text.append("added\t").append(m.path()).append('\t').append(escape(line)).append('\n');
        }
        File temp = new File(dir, MANIFEST + ".tmp");
        Files.write(temp.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), new File(dir, MANIFEST).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Removes the oldest finished operations beyond the number kept; unfinished ones are kept for the person to see. */
    static void prune(File root) {
        List<Entry> entries = list(root);
        int kept = 0;
        for (Entry entry : entries) {
            if (entry.state() == State.PENDING) continue;
            if (++kept > KEEP) deleteRecursively(entry.dir());
        }
    }

    // ---- reading ----

    /** The operations of a project, newest first; folders that can't be read are skipped. */
    @NonNull
    public static List<Entry> list(File root) {
        List<Entry> entries = new ArrayList<>();
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return entries;
        for (File dir : dirs) {
            Entry entry = read(dir);
            if (entry != null) entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(Entry::time).reversed().thenComparing(Entry::id, Comparator.reverseOrder()));
        return entries;
    }

    @Nullable
    static Entry read(File dir) {
        File manifest = new File(dir, MANIFEST);
        if (!manifest.isFile()) return null;
        try {
            List<String> lines = Files.readAllLines(manifest.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).equals(HEADER)) return null;
            long time = 0;
            State state = State.PENDING;
            List<Created> created = new ArrayList<>();
            List<String[]> modifiedRaw = new ArrayList<>();
            List<String[]> added = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                String[] parts = line.split("\t", -1);
                switch (parts[0]) {
                    case "time" -> time = Long.parseLong(parts[1]);
                    case "state" -> state = State.valueOf(parts[1].toUpperCase(java.util.Locale.ROOT));
                    case "created" -> created.add(new Created(parts[1], parts[2]));
                    case "modified" -> modifiedRaw.add(parts);
                    case "added" -> added.add(parts);
                    default -> {
                    }
                }
            }
            List<Modified> modified = new ArrayList<>();
            for (String[] m : modifiedRaw) {
                List<String> lines2 = new ArrayList<>();
                for (String[] a : added) if (a[1].equals(m[1])) lines2.add(unescape(a[2]));
                modified.add(new Modified(m[1], m[2], m[3], m[4], List.copyOf(lines2)));
            }
            return new Entry(dir.getName(), time, state, List.copyOf(created), List.copyOf(modified), dir);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---- taking an operation back ----

    /**
     * Takes back an operation: deletes the files it created (unless they changed since), puts back the values files as
     * they were (or, if they changed since, removes just the entries it added).
     *
     * @throws IOException when the operation was already taken back, or a file can't be written
     */
    @NonNull
    public static UndoResult undo(File resDir, Entry entry) throws IOException {
        if (entry.state() == State.UNDONE) throw new IOException("Already taken back");
        List<String> removed = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (Created created : entry.created()) {
            File file = safeFile(resDir, created.path());
            if (!file.isFile()) continue; // already gone
            if (hash(Files.readAllBytes(file.toPath())).equals(created.hash())) {
                Files.delete(file.toPath());
                pruneEmptyParents(resDir, file);
                removed.add(created.path());
            } else {
                kept.add(created.path() + " (changed since)");
            }
        }
        for (Modified modified : entry.modified()) {
            File file = safeFile(resDir, modified.path());
            File backup = new File(new File(entry.dir(), BACKUPS), modified.backup());
            if (!file.isFile()) continue;
            byte[] currentBytes = Files.readAllBytes(file.toPath());
            String current = new String(currentBytes, StandardCharsets.UTF_8);
            if (hash(currentBytes).equals(modified.hashAfter())) {
                if (modified.hashBefore().isEmpty()) {
                    // the file didn't exist before: it was created by the operation
                    Files.delete(file.toPath());
                    pruneEmptyParents(resDir, file);
                } else if (backup.isFile()) {
                    Files.copy(backup.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    kept.add(modified.path() + " (the backup is missing)");
                    continue;
                }
                for (String line : modified.addedLines()) removed.add(modified.path() + ": " + line.strip());
            } else {
                String result = current;
                for (String line : modified.addedLines()) {
                    int at = result.indexOf(line);
                    if (at >= 0) {
                        result = result.substring(0, at) + result.substring(at + line.length());
                        removed.add(modified.path() + ": " + line.strip());
                    } else {
                        kept.add(modified.path() + ": " + line.strip() + " (edited since)");
                    }
                }
                if (!result.equals(current)) {
                    try {
                        ValuesFile.parse(result);
                    } catch (SafeXml.InvalidXml e) {
                        throw new IOException(modified.path() + " would no longer be valid XML; nothing changed there");
                    }
                    Files.write(file.toPath(), result.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        write(entry.dir(), entry.time(), State.UNDONE, entry.created(), entry.modified());
        return new UndoResult(removed, kept);
    }

    private static File safeFile(File resDir, String path) throws IOException {
        File file = new File(resDir, path);
        if (!file.getCanonicalPath().startsWith(resDir.getCanonicalPath() + File.separator)) throw new IOException("Not a path under res: " + path);
        return file;
    }

    /** Removes the folders an undo left empty, up to (not including) {@code res}. */
    private static void pruneEmptyParents(File resDir, File file) throws IOException {
        File parent = file.getParentFile();
        File stop = resDir.getCanonicalFile();
        while (parent != null && !parent.getCanonicalFile().equals(stop)) {
            String[] children = parent.list();
            if (children == null || children.length > 0 || !parent.delete()) return;
            parent = parent.getParentFile();
        }
    }

    // ---- helpers ----

    static String hash(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String escape(String line) {
        return line.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : Arrays.asList(children)) deleteRecursively(child);
        file.delete();
    }
}
