package pro.sketchware.releases;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Keeps a record of every release a project is exported as, with the R8 mapping file that goes with it.
 * <p>
 * The mapping file is what turns an obfuscated crash report back into readable code, and it exists only for the build
 * that produced it. Without a copy kept for each release, crashes of released versions cannot be read later.
 * <p>
 * Layout: {@code <root>/<project id>/<time>-<version code>-<type>/release.json} and, when there is one, {@code mapping.txt}.
 */
public final class ReleaseArchive {
    /** What is known about one release. Public fields so it is stored as plain JSON. */
    public static final class Release {
        public String projectId;
        public String projectName;
        public String packageName;
        public String versionName;
        public String versionCode;
        /** "apk" or "aab" */
        public String type;
        public long time;
        public long sizeBytes;
        public String sha256;
        public boolean hasMapping;
        public String artifactPath;
        /** Name of this release's folder inside the project's folder. */
        public String folder;
    }

    private static final String METADATA = "release.json";
    private static final String MAPPING = "mapping.txt";
    private static final Gson GSON = new Gson();

    private final File root;

    public ReleaseArchive(File root) {
        this.root = root;
    }

    /**
     * Stores a release. The size and SHA-256 of {@code artifact} are measured here; the mapping is copied if it exists.
     *
     * @param mapping the R8 mapping file of this build, or null
     * @return the stored record
     */
    public Release record(Release release, File artifact, File mapping) throws IOException {
        if (release.projectId == null || release.projectId.isEmpty()) throw new IOException("A release needs a project id");
        File projectFolder = new File(root, release.projectId);
        String folderName = release.time + "-" + safe(release.versionCode) + "-" + safe(release.type);
        File folder = new File(projectFolder, folderName);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Couldn't create " + folder);

        release.folder = folderName;
        release.artifactPath = artifact.getAbsolutePath();
        release.sizeBytes = artifact.length();
        release.sha256 = sha256(artifact);
        release.hasMapping = mapping != null && mapping.isFile() && mapping.length() > 0;
        if (release.hasMapping) {
            Files.copy(mapping.toPath(), new File(folder, MAPPING).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(new File(folder, METADATA).toPath(), GSON.toJson(release).getBytes(StandardCharsets.UTF_8));
        return release;
    }

    /** The project's releases, newest first. Folders that can't be read are skipped. */
    public List<Release> list(String projectId) {
        List<Release> releases = new ArrayList<>();
        File[] folders = new File(root, projectId).listFiles(File::isDirectory);
        if (folders == null) return releases;
        for (File folder : folders) {
            File metadata = new File(folder, METADATA);
            if (!metadata.isFile()) continue;
            try {
                Release release = GSON.fromJson(new String(Files.readAllBytes(metadata.toPath()), StandardCharsets.UTF_8), Release.class);
                if (release != null) {
                    release.folder = folder.getName();
                    releases.add(release);
                }
            } catch (IOException | RuntimeException ignored) {
                // A damaged record shouldn't hide the others
            }
        }
        releases.sort(Comparator.comparingLong((Release r) -> r.time).reversed());
        return releases;
    }

    /** The R8 mapping stored for a release, or null if it has none. */
    public File mappingFile(Release release) {
        File file = new File(new File(new File(root, release.projectId), release.folder), MAPPING);
        return release.hasMapping && file.isFile() ? file : null;
    }

    /**
     * How much bigger (positive) or smaller (negative) a release is than the one of the same type before it,
     * or null if there is none to compare with.
     *
     * @param newestFirst the list as returned by {@link #list}
     */
    public static Long sizeChange(List<Release> newestFirst, Release release) {
        boolean seen = false;
        for (Release other : newestFirst) {
            if (other.folder.equals(release.folder)) {
                seen = true;
                continue;
            }
            if (seen && other.type.equals(release.type)) return release.sizeBytes - other.sizeBytes;
        }
        return null;
    }

    public void delete(Release release) {
        File folder = new File(new File(root, release.projectId), release.folder);
        File[] files = folder.listFiles();
        if (files != null) for (File file : files) file.delete();
        folder.delete();
    }

    private static String safe(String text) {
        return text == null ? "x" : text.replaceAll("[^A-Za-z0-9._-]", "_").toLowerCase(Locale.ROOT);
    }

    private static String sha256(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int read = in.read(buffer); read != -1; read = in.read(buffer)) digest.update(buffer, 0, read);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
