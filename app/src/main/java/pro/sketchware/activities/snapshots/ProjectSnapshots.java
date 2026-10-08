package pro.sketchware.activities.snapshots;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import pro.sketchware.utility.ZipSafety;

/**
 * Point-in-time copies of one project's data, kept next to it so a bad change can be undone.
 * <p>
 * A snapshot is a zip of everything Sketchware stores for the project under {@code .sketch_nws}
 * (the project data, its metadata file and its image, sound, font and icon resources). Entry names
 * are relative to that folder, so restoring is extracting into it.
 */
public class ProjectSnapshots {
    public enum Kind {
        /** Taken when the project is saved. */
        SAVE("save"),
        /** Taken when a build starts. */
        BUILD("build"),
        /** Taken by the user. */
        MANUAL("manual"),
        /** Taken just before a restore, so the restore itself can be undone. */
        BEFORE_RESTORE("before-restore");

        final String id;

        Kind(String id) {
            this.id = id;
        }

        static Kind of(String id) {
            for (Kind kind : values()) if (kind.id.equals(id)) return kind;
            return null;
        }
    }

    public record Snapshot(File file, long time, Kind kind, long size) {
    }

    public static final int MAX_AUTOMATIC = 15;
    public static final int MAX_BEFORE_RESTORE = 5;

    private static final String FINGERPRINT_PREFIX = "snapshot-v1:";
    private static final String[] RESOURCE_FOLDERS = {"fonts", "icons", "images", "sounds"};
    private static final Object LOCK = new Object();

    private final File root;
    private final String scId;
    private final File snapshotsDir;

    /**
     * @param root the {@code .sketch_nws} folder
     */
    public ProjectSnapshots(File root, String scId) {
        this.root = root;
        this.scId = scId;
        snapshotsDir = new File(root, "snapshots/" + scId);
    }

    /** What a snapshot covers, relative to {@link #root}. */
    private List<String> trackedPaths() {
        List<String> paths = new ArrayList<>();
        paths.add("data/" + scId);
        paths.add("mysc/list/" + scId);
        for (String folder : RESOURCE_FOLDERS) paths.add("resources/" + folder + "/" + scId);
        return paths;
    }

    /** Newest first. */
    public List<Snapshot> list() {
        List<Snapshot> snapshots = new ArrayList<>();
        File[] files = snapshotsDir.listFiles();
        if (files == null) return snapshots;
        for (File file : files) {
            Snapshot snapshot = parse(file);
            if (snapshot != null) snapshots.add(snapshot);
        }
        snapshots.sort(Comparator.comparingLong(Snapshot::time).reversed());
        return snapshots;
    }

    /**
     * Takes a snapshot unless the project is identical to the newest one, or the newest one is
     * more recent than {@code minIntervalMillis}.
     *
     * @return the new snapshot, or null if none was needed
     */
    public Snapshot createIfChanged(Kind kind, long minIntervalMillis) throws IOException {
        synchronized (LOCK) {
            List<Snapshot> existing = list();
            if (!existing.isEmpty()) {
                Snapshot newest = existing.get(0);
                if (System.currentTimeMillis() - newest.time() < minIntervalMillis) return null;
                if (fingerprint().equals(readFingerprint(newest.file()))) return null;
            }
            return create(kind);
        }
    }

    /** Takes a snapshot even if nothing changed. */
    public Snapshot create(Kind kind) throws IOException {
        synchronized (LOCK) {
            if (!snapshotsDir.isDirectory() && !snapshotsDir.mkdirs()) {
                throw new IOException("Couldn't create " + snapshotsDir);
            }

            long time = System.currentTimeMillis();
            File target = new File(snapshotsDir, time + "-" + kind.id + ".zip");
            while (target.exists()) {
                time++;
                target = new File(snapshotsDir, time + "-" + kind.id + ".zip");
            }
            File partial = new File(snapshotsDir, target.getName() + ".part");

            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(partial.toPath()))) {
                zip.setComment(FINGERPRINT_PREFIX + fingerprint());
                for (String path : trackedPaths()) {
                    File file = new File(root, path);
                    if (file.exists()) addToZip(zip, file, path);
                }
            } catch (IOException | RuntimeException e) {
                partial.delete();
                throw e;
            }
            if (!partial.renameTo(target)) {
                partial.delete();
                throw new IOException("Couldn't finish " + target);
            }

            prune();
            return parse(target);
        }
    }

    /**
     * Replaces the project's current data with the snapshot. A {@link Kind#BEFORE_RESTORE}
     * snapshot of the current state is taken first.
     */
    public void restore(Snapshot snapshot) throws IOException {
        synchronized (LOCK) {
            List<String> tracked = trackedPaths();
            File staging = new File(snapshotsDir, ".restoring");
            deleteRecursively(staging);
            if (!staging.mkdirs()) throw new IOException("Couldn't create " + staging);

            try {
                extract(snapshot.file(), staging, tracked);
                create(Kind.BEFORE_RESTORE);

                for (String path : tracked) {
                    File current = new File(root, path);
                    File restored = new File(staging, path);
                    deleteRecursively(current);
                    if (restored.exists()) {
                        File parent = current.getParentFile();
                        if (parent != null) parent.mkdirs();
                        if (!restored.renameTo(current)) {
                            throw new IOException("Couldn't move " + restored + " to " + current);
                        }
                    }
                }
            } finally {
                deleteRecursively(staging);
            }
        }
    }

    public void delete(Snapshot snapshot) {
        synchronized (LOCK) {
            snapshot.file().delete();
        }
    }

    private void prune() {
        List<Snapshot> automatic = new ArrayList<>();
        List<Snapshot> beforeRestore = new ArrayList<>();
        for (Snapshot snapshot : list()) {
            switch (snapshot.kind()) {
                case SAVE, BUILD -> automatic.add(snapshot);
                case BEFORE_RESTORE -> beforeRestore.add(snapshot);
                default -> {
                    // Manual snapshots are never removed behind the user's back
                }
            }
        }
        for (int i = MAX_AUTOMATIC; i < automatic.size(); i++) automatic.get(i).file().delete();
        for (int i = MAX_BEFORE_RESTORE; i < beforeRestore.size(); i++) beforeRestore.get(i).file().delete();
    }

    private static Snapshot parse(File file) {
        String name = file.getName();
        if (!name.endsWith(".zip")) return null;
        String[] parts = name.substring(0, name.length() - ".zip".length()).split("-", 2);
        if (parts.length != 2) return null;
        Kind kind = Kind.of(parts[1]);
        if (kind == null) return null;
        try {
            return new Snapshot(file, Long.parseLong(parts[0]), kind, file.length());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String readFingerprint(File zipFile) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            String comment = zip.getComment();
            return comment != null && comment.startsWith(FINGERPRINT_PREFIX) ? comment.substring(FINGERPRINT_PREFIX.length()) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** A hash of the names and contents of every tracked file. */
    private String fingerprint() throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[16 * 1024];
            for (String path : trackedPaths()) {
                File file = new File(root, path);
                if (file.exists()) hash(digest, buffer, file, path);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static void hash(MessageDigest digest, byte[] buffer, File file, String name) throws IOException {
        digest.update((name + (file.isDirectory() ? "/" : "")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (file.isDirectory()) {
            for (File child : sortedChildren(file)) hash(digest, buffer, child, name + "/" + child.getName());
        } else {
            try (InputStream in = Files.newInputStream(file.toPath())) {
                int read;
                while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
        }
    }

    private static void addToZip(ZipOutputStream zip, File file, String name) throws IOException {
        if (file.isDirectory()) {
            File[] children = sortedChildren(file);
            if (children.length == 0) {
                zip.putNextEntry(new ZipEntry(name + "/"));
                zip.closeEntry();
            }
            for (File child : children) addToZip(zip, child, name + "/" + child.getName());
        } else {
            zip.putNextEntry(new ZipEntry(name));
            Files.copy(file.toPath(), zip);
            zip.closeEntry();
        }
    }

    private static void extract(File zipFile, File destination, List<String> allowed) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            // Check every name first so a damaged or foreign archive leaves nothing behind
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                String name = entries.nextElement().getName();
                ZipSafety.resolveEntry(destination, name);
                if (allowed.stream().noneMatch(path -> name.equals(path + "/") || name.startsWith(path + "/"))) {
                    throw new IOException("Unexpected entry in snapshot: " + name);
                }
            }
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                File out = ZipSafety.resolveEntry(destination, entry.getName());
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(entry); OutputStream os = Files.newOutputStream(out.toPath())) {
                    in.transferTo(os);
                }
            }
        }
    }

    private static File[] sortedChildren(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return new File[0];
        Arrays.sort(children);
        return children;
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
