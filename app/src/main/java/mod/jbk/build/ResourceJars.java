package mod.jbk.build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Two libraries can ship the same resource file (two versions of Kotlin both have
 * {@code kotlin/annotation/annotation.kotlin_builtins}). The APK builder refuses that with "Duplicate files from two
 * libraries". This keeps the first copy, like the DEX merge already keeps the first class: it writes a copy of a
 * library's jar without the resources an earlier library already provided, and without class files, which the APK
 * builder does not take from jars anyway.
 */
public final class ResourceJars {
    private ResourceJars() {
    }

    /**
     * @param jar          a library's jar
     * @param alreadyAdded names of resources earlier libraries provided; this call adds the ones it keeps
     * @param outDir       where the copy is written
     * @return the copy, or {@code jar} itself when it can't be read (the builder reports that as before)
     */
    public static File withoutEntries(File jar, Set<String> alreadyAdded, File outDir) {
        String owner = jar.getParentFile() == null ? "" : jar.getParentFile().getName();
        File copy = new File(outDir, alreadyAdded.size() + "-" + owner + "-" + jar.getName());
        try (ZipFile zip = new ZipFile(jar)) {
            if (!outDir.isDirectory() && !outDir.mkdirs()) {
                return jar;
            }
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(copy))) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || name.endsWith(".class") || !alreadyAdded.add(name)) {
                        continue;
                    }
                    out.putNextEntry(new ZipEntry(name));
                    try (InputStream in = zip.getInputStream(entry)) {
                        in.transferTo(out);
                    }
                    out.closeEntry();
                }
            }
            return copy;
        } catch (IOException e) {
            return jar;
        }
    }
}
