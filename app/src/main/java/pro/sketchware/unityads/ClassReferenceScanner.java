package pro.sketchware.unityads;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds classes that library code refers to but no library provides: the app compiles and installs fine, then crashes
 * with NoClassDefFoundError the first time that code runs (datastore-core, then OMID, in the Unity Ads SDK).
 * <p>
 * Reads the class files' constant pools directly; no Android or bytecode library needed, so it runs on a plain JVM.
 */
public final class ClassReferenceScanner {
    /** Classes the Android platform or the JDK provides; not expected in any library. */
    private static final String[] PLATFORM = {"java/", "javax/", "android/", "dalvik/", "org/w3c/", "org/xml/",
            "org/xmlpull/", "org/json/", "org/apache/http/", "libcore/", "sun/", "com/android/",
            // Optional integrations the libraries only use if present (OkHttp's Conscrypt, Room's paging).
            "org/conscrypt/", "androidx/paging/"};

    private ClassReferenceScanner() {
    }

    /** @return referenced class name (slash form) mapped to one class that refers to it, sorted by name */
    public static Map<String, String> findMissing(Collection<File> programJars, Collection<File> providerJars) {
        Set<String> provided = new HashSet<>();
        for (File jar : providerJars) {
            provided.addAll(classNamesIn(jar));
        }
        Map<String, String> missing = new TreeMap<>();
        for (File jar : programJars) {
            try (ZipFile zip = new ZipFile(jar)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) {
                        continue;
                    }
                    String owner = entry.getName().substring(0, entry.getName().length() - 6);
                    try (InputStream in = zip.getInputStream(entry)) {
                        for (String reference : referencedClasses(in)) {
                            if (!isPlatform(reference) && !provided.contains(reference) && !missing.containsKey(reference)) {
                                missing.put(reference, owner);
                            }
                        }
                    } catch (IOException | RuntimeException ignored) {
                        // A class file this simple reader can't parse is skipped.
                    }
                }
            } catch (IOException ignored) {
                // An unreadable jar has nothing to report.
            }
        }
        return missing;
    }

    /** Resource classes (androidx/core/R, R$id...) are generated when the app is built, not found in libraries. */
    static boolean isResourceClass(String className) {
        int slash = className.lastIndexOf('/');
        String simple = className.substring(slash + 1);
        return simple.equals("R") || simple.startsWith("R$");
    }

    static boolean isPlatform(String className) {
        if (className.equals("module-info") || isResourceClass(className)) {
            return true;
        }
        for (String prefix : PLATFORM) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static Set<String> classNamesIn(File jar) {
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class")) {
                    names.add(name.substring(0, name.length() - 6));
                }
            }
        } catch (IOException ignored) {
            // Provides nothing.
        }
        return names;
    }

    /** Class names a class file refers to: its CONSTANT_Class entries, and classes named in array types. */
    static List<String> referencedClasses(InputStream classFile) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(classFile));
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        List<Integer> classIndexes = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                case 5, 6 -> {
                    in.skipBytes(8);
                    i++;
                }
                case 7 -> classIndexes.add(in.readUnsignedShort());
                case 8, 16, 19, 20 -> in.skipBytes(2);
                case 15 -> in.skipBytes(3);
                default -> throw new IOException("unknown constant tag " + tag);
            }
        }
        List<String> names = new ArrayList<>();
        for (int index : classIndexes) {
            String name = index < count ? utf8[index] : null;
            if (name == null) {
                continue;
            }
            if (name.startsWith("[")) {
                int from = 0;
                while ((from = name.indexOf('L', from)) >= 0) {
                    int end = name.indexOf(';', from);
                    if (end < 0) {
                        break;
                    }
                    names.add(name.substring(from + 1, end));
                    from = end;
                }
            } else {
                names.add(name);
            }
        }
        return names;
    }
}
