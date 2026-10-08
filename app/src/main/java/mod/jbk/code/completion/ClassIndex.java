package mod.jbk.code.completion;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * The fully qualified names of the classes that code in a project can use, looked up by simple name
 * or by the start of the qualified name.
 */
public final class ClassIndex {
    private static final ClassIndex EMPTY = new ClassIndex(new TreeSet<>());

    private final List<String> names;
    private final Map<String, List<String>> bySimpleName = new HashMap<>();

    private ClassIndex(TreeSet<String> sorted) {
        names = new ArrayList<>(sorted);
        for (String name : names) {
            bySimpleName.computeIfAbsent(simpleName(name), k -> new ArrayList<>()).add(name);
        }
    }

    public static ClassIndex empty() {
        return EMPTY;
    }

    /** Names with {@code $} (nested and anonymous classes) are dropped. */
    public static ClassIndex of(Collection<String> qualifiedNames) {
        TreeSet<String> sorted = new TreeSet<>();
        for (String name : qualifiedNames) {
            if (!name.isEmpty() && name.indexOf('$') < 0) sorted.add(name);
        }
        return new ClassIndex(sorted);
    }

    public static ClassIndex merge(ClassIndex... indexes) {
        TreeSet<String> all = new TreeSet<>();
        for (ClassIndex index : indexes) all.addAll(index.names);
        return new ClassIndex(all);
    }

    /** Reads the class names from a jar's directory, without loading any class. */
    public static ClassIndex fromJar(File jar) throws IOException {
        List<String> found = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar)) {
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                String name = classNameOf(entries.nextElement().getName());
                if (name != null) found.add(name);
            }
        }
        return of(found);
    }

    /**
     * Reads the class names of the jars inside a zip archive, like the {@code android.jar.zip} shipped
     * as an app asset, one pass over the stream and without extracting anything.
     */
    public static ClassIndex fromArchiveOfJars(InputStream archive) throws IOException {
        List<String> found = new ArrayList<>();
        ZipInputStream outer = new ZipInputStream(archive);
        for (ZipEntry entry = outer.getNextEntry(); entry != null; entry = outer.getNextEntry()) {
            if (!entry.getName().endsWith(".jar")) continue;
            ZipInputStream jar = new ZipInputStream(new FilterInputStream(outer) {
                @Override
                public void close() {
                    // Closing the jar must not close the archive around it
                }
            });
            for (ZipEntry inner = jar.getNextEntry(); inner != null; inner = jar.getNextEntry()) {
                String name = classNameOf(inner.getName());
                if (name != null) found.add(name);
            }
        }
        return of(found);
    }

    /**
     * Class names of the Java files below {@code sourceRoot}, taken from the folder structure
     * ({@code com/my/app/Foo.java} is {@code com.my.app.Foo}).
     */
    public static ClassIndex fromSourceDir(File sourceRoot) {
        List<String> found = new ArrayList<>();
        collectSources(sourceRoot, "", found);
        return of(found);
    }

    /** {@code android/app/Activity.class} to {@code android.app.Activity}, null for anything else. */
    public static String classNameOf(String zipEntryName) {
        if (!zipEntryName.endsWith(".class") || zipEntryName.startsWith("META-INF/")) return null;
        String name = zipEntryName.substring(0, zipEntryName.length() - ".class".length()).replace('/', '.');
        String simple = simpleName(name);
        if (simple.equals("package-info") || simple.equals("module-info")) return null;
        return name;
    }

    private static void collectSources(File dir, String packagePrefix, List<String> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectSources(child, packagePrefix + child.getName() + ".", out);
            } else if (child.getName().endsWith(".java")) {
                out.add(packagePrefix + child.getName().substring(0, child.getName().length() - ".java".length()));
            }
        }
    }

    private static final java.util.regex.Pattern PROJECT_JAVA_FILE = java.util.regex.Pattern.compile("/data/(\\d+)/files/java/");

    /** The project ID for a file under {@code .sketch_nws/data/<id>/files/java/}, null for any other path. */
    public static String projectIdOfJavaFile(String path) {
        if (path == null) return null;
        java.util.regex.Matcher matcher = PROJECT_JAVA_FILE.matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    public static String packageOf(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? "" : qualifiedName.substring(0, dot);
    }

    public int size() {
        return names.size();
    }

    public List<String> all() {
        return Collections.unmodifiableList(names);
    }

    /** Every class with exactly this simple name, in different packages. */
    public List<String> withSimpleName(String simpleName) {
        return bySimpleName.getOrDefault(simpleName, Collections.emptyList());
    }

    /** Classes whose simple name starts with {@code prefix}, ignoring case. */
    public List<String> withSimpleNamePrefix(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (simpleName(name).toLowerCase(Locale.ROOT).startsWith(lower)) result.add(name);
        }
        return result;
    }

    /** Classes whose qualified name starts with {@code prefix}. */
    public List<String> withQualifiedPrefix(String prefix) {
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (name.startsWith(prefix)) result.add(name);
        }
        return result;
    }
}
