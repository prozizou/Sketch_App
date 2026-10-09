package pro.sketchware.releases;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import pro.sketchware.analysis.Category;
import pro.sketchware.analysis.Finding;
import pro.sketchware.analysis.Severity;

/**
 * Says what an APK or AAB is made of and where its size comes from: code, resources, assets and native libraries, the
 * biggest files, and what could be removed. It reads the archive's directory only; nothing is extracted.
 */
public final class SizeAnalyzer {
    public enum Part {CODE, RESOURCES, ASSETS, NATIVE_LIBRARIES, OTHER}

    /** One file in the archive. {@code compressed} is what it takes on disk, {@code uncompressed} what it is once opened. */
    public record Entry(String name, long compressed, long uncompressed) {
    }

    /**
     * @param fileSize   size of the archive itself
     * @param byPart     compressed bytes per part
     * @param byAbi      compressed bytes of native libraries per ABI, like {@code arm64-v8a}
     * @param largest    the biggest files, biggest first
     */
    public record Report(long fileSize, Map<Part, Long> byPart, Map<String, Long> byAbi, List<Entry> largest, List<Finding> findings) {
    }

    private static final long LARGE_FILE_BYTES = 1024 * 1024;
    private static final long MIN_WORTH_REPORTING_BYTES = 200 * 1024;
    private static final List<String> USEFUL_ABIS = List.of("arm64-v8a", "armeabi-v7a");

    private SizeAnalyzer() {
    }

    public static Report analyze(File archive, int largestCount) throws IOException {
        Map<Part, Long> byPart = new EnumMap<>(Part.class);
        Map<String, Long> byAbi = new TreeMap<>();
        List<Entry> entries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive)) {
            for (Enumeration<? extends ZipEntry> all = zip.entries(); all.hasMoreElements(); ) {
                ZipEntry entry = all.nextElement();
                if (entry.isDirectory()) continue;
                long compressed = entry.getCompressedSize() >= 0 ? entry.getCompressedSize() : Math.max(0, entry.getSize());
                long uncompressed = Math.max(0, entry.getSize());
                entries.add(new Entry(entry.getName(), compressed, uncompressed));
                byPart.merge(partOf(entry.getName()), compressed, Long::sum);
                String abi = abiOf(entry.getName());
                if (abi != null) byAbi.merge(abi, compressed, Long::sum);
            }
        }
        entries.sort(Comparator.comparingLong(Entry::compressed).reversed().thenComparing(Entry::name));
        List<Entry> largest = new ArrayList<>(entries.subList(0, Math.min(largestCount, entries.size())));
        return new Report(archive.length(), byPart, byAbi, largest, findings(entries, byAbi));
    }

    /** Which part of the app a file belongs to. App bundles keep everything under a module folder such as {@code base/}. */
    static Part partOf(String name) {
        String path = withoutModule(name);
        if (path.matches("classes\\d*\\.dex") || path.startsWith("dex/")) return Part.CODE;
        if (path.startsWith("res/") || path.equals("resources.arsc") || path.equals("AndroidManifest.xml") || path.equals("resources.pb") || path.equals("manifest/AndroidManifest.xml")) return Part.RESOURCES;
        if (path.startsWith("assets/")) return Part.ASSETS;
        if (path.startsWith("lib/")) return Part.NATIVE_LIBRARIES;
        return Part.OTHER;
    }

    static String abiOf(String name) {
        String path = withoutModule(name);
        if (!path.startsWith("lib/")) return null;
        String[] parts = path.split("/");
        return parts.length >= 3 ? parts[1] : null;
    }

    private static String withoutModule(String name) {
        int slash = name.indexOf('/');
        if (slash > 0) {
            String first = name.substring(0, slash);
            // In an app bundle the first folder is the module; in an APK it is a real folder like res or lib
            boolean apkFolder = first.equals("res") || first.equals("lib") || first.equals("assets") || first.equals("META-INF") || first.equals("dex");
            if (!apkFolder && !first.endsWith(".dex")) return name.substring(slash + 1);
        }
        return name;
    }

    private static List<Finding> findings(List<Entry> entries, Map<String, Long> byAbi) {
        List<Finding> findings = new ArrayList<>();

        long unneeded = 0;
        List<String> unneededAbis = new ArrayList<>();
        for (Map.Entry<String, Long> abi : byAbi.entrySet()) {
            if (!USEFUL_ABIS.contains(abi.getKey())) {
                unneeded += abi.getValue();
                unneededAbis.add(abi.getKey());
            }
        }
        if (unneeded >= MIN_WORTH_REPORTING_BYTES) {
            findings.add(new Finding("size.extra-abis", Category.QUALITY, Severity.INFO,
                    "Native libraries for architectures almost no phone uses (" + format(unneeded) + ")",
                    "Nearly all current phones are arm64-v8a, and older ones armeabi-v7a. Libraries for " + String.join(", ", unneededAbis) + " only make the app larger.",
                    "Remove those folders from the project's native libraries unless you target emulators or Chromebooks (x86, x86_64).",
                    String.join(", ", unneededAbis)));
        }

        List<String> large = new ArrayList<>();
        for (Entry entry : entries) {
            Part part = partOf(entry.name());
            if ((part == Part.ASSETS || part == Part.RESOURCES) && entry.compressed() >= LARGE_FILE_BYTES) {
                large.add(entry.name() + " (" + format(entry.compressed()) + ")");
            }
        }
        if (!large.isEmpty()) {
            findings.add(new Finding("size.large-files", Category.QUALITY, Severity.INFO, "Large resource or asset files (" + large.size() + ")",
                    "Files over 1 MB inside the app are downloaded by everyone who installs it. Uncompressed images and audio are the usual cause.",
                    "Resize or convert images (WebP), compress audio, or download rarely used files on first use instead of shipping them.",
                    String.join("\n", large.subList(0, Math.min(large.size(), 5)))));
        }

        long dex = entries.stream().filter(e -> partOf(e.name()) == Part.CODE).count();
        if (dex > 1) {
            findings.add(new Finding("size.multidex", Category.QUALITY, Severity.INFO, "The code is split into " + dex + " dex files",
                    "Apps with more than 65,536 methods need several dex files; each adds start-up work on old phones.",
                    "Turn on the code shrinker and remove libraries you don't use to bring the code under one dex file.", null));
        }
        return findings;
    }

    /** Sizes in the units people read: 512 B, 3.4 KB, 2.1 MB. */
    public static String format(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
