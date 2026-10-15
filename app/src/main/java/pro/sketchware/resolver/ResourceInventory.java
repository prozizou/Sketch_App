package pro.sketchware.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the project already has: the files and values in its {@code res} folder (every qualifier) and the images added
 * in the image manager. It is what the resolver looks at first, before offering anything new, and what it checks a
 * new name against.
 */
public final class ResourceInventory {
    public enum Kind {FILE, VALUE}

    public enum Origin {PROJECT_RES, PROJECT_IMAGES}

    /**
     * One definition of a resource.
     *
     * @param type      {@code drawable}, {@code layout}, {@code string}, ...
     * @param name      the resource name
     * @param qualifier the configuration with its dash ({@code -night}, {@code -xhdpi}), "" for the default
     * @param kind      a file, or an entry of a values file
     * @param path      absolute path of the file (of the values file for a value)
     * @param value     the text of a value, null for a file
     */
    public record Definition(@NonNull String type, @NonNull String name, @NonNull String qualifier, @NonNull Kind kind,
                             @NonNull String path, @Nullable String value, @NonNull Origin origin) {
        /** The extension of a file definition with its dot (".png", ".xml"), "" otherwise. */
        public String extension() {
            if (kind != Kind.FILE) return "";
            String file = new File(path).getName();
            int dot = file.indexOf('.');
            return dot < 0 ? "" : file.substring(dot);
        }
    }

    private final List<Definition> all = new ArrayList<>();
    private final Map<String, List<Definition>> byName = new HashMap<>();
    /** Values files that could not be read: their entries are unknown, so they are never edited. */
    private final List<String> unreadableValuesFiles = new ArrayList<>();

    private ResourceInventory() {
    }

    /**
     * @param resDir    the project's own {@code res} folder ({@code data/<id>/files/resource}); may not exist
     * @param imagesDir the image manager's folder ({@code resources/images/<id>}), or null
     */
    @NonNull
    public static ResourceInventory scan(@Nullable File resDir, @Nullable File imagesDir) {
        ResourceInventory inventory = new ResourceInventory();
        if (resDir != null) inventory.scanRes(resDir);
        if (imagesDir != null) inventory.scanImages(imagesDir);
        return inventory;
    }

    private void scanRes(File resDir) {
        File[] folders = resDir.listFiles(File::isDirectory);
        if (folders == null) return;
        Arrays.sort(folders, Comparator.comparing(File::getName));
        for (File folder : folders) {
            String folderName = folder.getName();
            int dash = folderName.indexOf('-');
            String type = dash < 0 ? folderName : folderName.substring(0, dash);
            String qualifier = dash < 0 ? "" : folderName.substring(dash);
            File[] files = folder.listFiles(File::isFile);
            if (files == null) continue;
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                if (file.getName().startsWith(".")) continue;
                if (type.equals("values")) {
                    scanValues(file, qualifier);
                } else {
                    add(new Definition(type, baseName(file), qualifier, Kind.FILE, file.getAbsolutePath(), null, Origin.PROJECT_RES));
                }
            }
        }
    }

    private void scanValues(File file, String qualifier) {
        if (!file.getName().endsWith(".xml")) return;
        try {
            String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            for (ValuesFile.Entry entry : ValuesFile.parse(xml)) {
                add(new Definition(entry.type(), entry.name(), qualifier, Kind.VALUE, file.getAbsolutePath(), entry.value(), Origin.PROJECT_RES));
            }
        } catch (IOException | SafeXml.InvalidXml e) {
            unreadableValuesFiles.add(file.getAbsolutePath());
        }
    }

    private void scanImages(File imagesDir) {
        File[] files = imagesDir.listFiles(File::isFile);
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (file.getName().startsWith(".")) continue;
            add(new Definition("drawable", baseName(file), "", Kind.FILE, file.getAbsolutePath(), null, Origin.PROJECT_IMAGES));
        }
    }

    private static String baseName(File file) {
        String name = file.getName();
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private void add(Definition definition) {
        all.add(definition);
        byName.computeIfAbsent(definition.type() + "/" + ResourceNames.foldedKey(definition.name()), k -> new ArrayList<>()).add(definition);
    }

    /** Everything defined, in folder order. */
    public List<Definition> all() {
        return List.copyOf(all);
    }

    public List<Definition> ofType(String type) {
        List<Definition> result = new ArrayList<>();
        for (Definition definition : all) if (definition.type().equals(type)) result.add(definition);
        return result;
    }

    /** The definitions of this resource in every qualifier; names are compared as R and the storage see them. */
    public List<Definition> find(String type, String name) {
        List<Definition> found = byName.get(type + "/" + ResourceNames.foldedKey(name));
        return found == null ? List.of() : List.copyOf(found);
    }

    /** Whether {@code type/name} exists in this very qualifier (collision), counting file name case-insensitively. */
    public boolean has(String type, String name, String qualifier) {
        for (Definition definition : find(type, name)) {
            if (definition.qualifier().equals(qualifier)) return true;
        }
        return false;
    }

    /** Values files whose content could not be read; they are never edited. */
    public List<String> unreadableValuesFiles() {
        return List.copyOf(unreadableValuesFiles);
    }
}
