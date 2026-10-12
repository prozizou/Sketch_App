package dev.aldi.sayuti.editor.manage;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * An AAR can carry extra JARs in its {@code libs/} folder (Unity Ads ships the OMID measurement SDK this way).
 * The downloader only compiles {@code classes.jar}, so those classes never reached the APK and the app crashed the
 * first time it used them. Each such JAR is compiled to its own {@code classes_<jar name>.dex} in the library's
 * folder: the build merges every {@code classes*.dex} it finds there.
 * <p>
 * This class decides what to compile and where to put it; it has no Android or D8 dependency, so it is unit-tested.
 */
public final class EmbeddedLibs {
    private static final String DEX_PREFIX = "classes_";

    private EmbeddedLibs() {
    }

    /** The target dex file for an embedded jar (the first one when D8 splits it into several). */
    public static File dexFor(File libraryFolder, File embeddedJar, int index) {
        String name = embeddedJar.getName();
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        String suffix = index == 0 ? "" : "_" + (index + 1);
        return new File(libraryFolder, DEX_PREFIX + name + suffix + ".dex");
    }

    /** Embedded JARs of this library folder that have no compiled dex yet, in a stable order. */
    public static List<File> jarsToCompile(File libraryFolder) {
        List<File> jars = new ArrayList<>();
        File[] files = new File(libraryFolder, "libs").listFiles();
        if (files == null) {
            return jars;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".jar") && !dexFor(libraryFolder, file, 0).isFile()) {
                jars.add(file);
            }
        }
        return jars;
    }

    /** True when the library folder has embedded JARs that were never compiled. */
    public static boolean hasUncompiled(File libraryFolder) {
        return !jarsToCompile(libraryFolder).isEmpty();
    }
}
