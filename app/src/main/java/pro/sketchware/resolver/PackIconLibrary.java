package pro.sketchware.resolver;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Material Icons pack the app ships ({@code assets/icons/icon_pack.zip}, Apache License 2.0), read from the folder
 * it is unpacked to: {@code svg/<name>/<style>.svg}. The same pack the Import Icon screen uses.
 */
public final class PackIconLibrary implements IconLibrary {
    private final File root;
    private final Map<String, IconInfo> icons = new LinkedHashMap<>();

    /** @param svgRoot the unpacked {@code svg} folder */
    public PackIconLibrary(File svgRoot) {
        this.root = svgRoot;
        File[] folders = svgRoot.listFiles(File::isDirectory);
        if (folders == null) return;
        Arrays.sort(folders, Comparator.comparing(File::getName));
        for (File folder : folders) {
            String[] files = folder.list((dir, name) -> name.endsWith(".svg"));
            if (files == null || files.length == 0) continue;
            Arrays.sort(files);
            List<String> styles = new ArrayList<>();
            for (String file : files) styles.add(file.substring(0, file.length() - 4));
            icons.put(folder.getName(), new IconInfo(folder.getName(), List.copyOf(styles)));
        }
    }

    public int size() {
        return icons.size();
    }

    @Override
    public List<IconInfo> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        List<IconInfo> startsWith = new ArrayList<>();
        List<IconInfo> contains = new ArrayList<>();
        IconInfo exact = null;
        for (IconInfo icon : icons.values()) {
            if (q.isEmpty() || icon.name().equals(q)) {
                if (!q.isEmpty()) exact = icon;
                else contains.add(icon);
            } else if (icon.name().startsWith(q)) {
                startsWith.add(icon);
            } else if (icon.name().contains(q)) {
                contains.add(icon);
            }
        }
        List<IconInfo> result = new ArrayList<>();
        if (exact != null) result.add(exact);
        result.addAll(startsWith);
        result.addAll(contains);
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    @Nullable
    @Override
    public IconInfo find(String name) {
        return name == null ? null : icons.get(name);
    }

    @Override
    public String svg(String name, String style) throws IOException {
        IconInfo icon = icons.get(name);
        if (icon == null || !icon.styles().contains(style)) throw new IOException("No icon " + name + " in style " + style);
        return new String(Files.readAllBytes(new File(new File(root, name), style + ".svg").toPath()), StandardCharsets.UTF_8);
    }
}
