package dev.aldi.sayuti.editor.manage;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * Checks that a downloaded JAR or AAR is a complete ZIP archive. A download cut off by the network, or an error page
 * (Maven Central answers "rate limit exceeded" with HTML), used to reach the unzip step and crash the app with
 * "zip END header not found".
 */
public final class ArchiveCheck {
    private ArchiveCheck() {
    }

    public static boolean isValidZip(File file) {
        if (file == null || !file.isFile() || file.length() < 22) {
            return false;
        }
        try (ZipFile ignored = new ZipFile(file)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
