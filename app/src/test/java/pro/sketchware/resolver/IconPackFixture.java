package pro.sketchware.resolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Unpacks (part of) the Material Icons pack the app ships, the real one, for the tests. */
final class IconPackFixture {
    static final File ZIP = new File("src/main/assets/icons/icon_pack.zip");

    private IconPackFixture() {
    }

    static boolean available() {
        return ZIP.isFile() && ZIP.length() > 100_000;
    }

    /** Unpacks the icons whose folder name is in {@code names} (all when empty) and returns the {@code svg} folder. */
    static File unpack(File into, String... names) throws IOException {
        java.util.Set<String> wanted = java.util.Set.of(names);
        try (ZipFile zip = new ZipFile(ZIP)) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                if (entry.isDirectory() || !entry.getName().startsWith("svg/")) continue;
                String[] parts = entry.getName().split("/");
                if (parts.length != 3 || (!wanted.isEmpty() && !wanted.contains(parts[1]))) continue;
                File target = new File(into, entry.getName());
                target.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target.toPath());
                }
            }
        }
        return new File(into, "svg");
    }
}
