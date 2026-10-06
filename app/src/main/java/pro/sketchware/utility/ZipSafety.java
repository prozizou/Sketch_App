package pro.sketchware.utility;

import java.io.File;
import java.io.IOException;

/**
 * Guards against "Zip Slip": archive entries whose names climb out of the extraction directory
 * (for example {@code ../../.sketch_nws/data/settings.json}) and would overwrite files elsewhere.
 */
public final class ZipSafety {
    private ZipSafety() {
    }

    /**
     * Resolves {@code entryName} inside {@code destinationDir} and makes sure the result really is inside it.
     * The check uses canonical paths, so {@code ..} segments, absolute names and symbolic links that point
     * outside the directory are all rejected.
     *
     * @return the file to write the entry to
     * @throws IOException if the entry would end up outside {@code destinationDir}
     */
    public static File resolveEntry(File destinationDir, String entryName) throws IOException {
        if (entryName == null || entryName.indexOf('\0') >= 0) {
            throw new IOException("Invalid archive entry name");
        }
        String root = destinationDir.getCanonicalPath();
        File destination = new File(destinationDir, entryName);
        String canonical = destination.getCanonicalPath();
        // The separator matters: /data/out must not accept /data/out-evil/file.
        if (!canonical.equals(root) && !canonical.startsWith(root + File.separator)) {
            throw new IOException("Archive entry escapes the target directory: " + entryName);
        }
        return destination;
    }
}
