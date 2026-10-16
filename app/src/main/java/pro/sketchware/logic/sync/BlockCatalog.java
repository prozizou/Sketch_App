package pro.sketchware.logic.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The blocks the converter can recognise: the palette's built-in blocks (a list kept next to the code, checked against
 * the editor by a test) and the extra blocks, which carry their own spec and type (built-in extras, the user's block
 * file and the project's custom blocks).
 */
public final class BlockCatalog {
    private static final String RESOURCE = "/pro/sketchware/logic/sync/builtin-blocks.tsv";
    private static final Pattern SPEC_PARAM = Pattern.compile("%[bdsm](?:\\.\\w+)?");

    private final Map<String, BlockDef> defs = new LinkedHashMap<>();

    private BlockCatalog() {
    }

    /** The built-in blocks only. */
    public static BlockCatalog builtIn() {
        BlockCatalog catalog = new BlockCatalog();
        try (InputStream in = BlockCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] parts = line.split("\t", -1);
                if (parts.length < 2) continue;
                List<String> params = parts.length > 2 && !parts[2].isEmpty() ? List.of(parts[2].split(",")) : List.of();
                catalog.defs.put(parts[0], new BlockDef(parts[0], parts[1], "", params, true));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return catalog;
    }

    /**
     * The built-in blocks plus the extra blocks of {@code extras} (maps with {@code name}, {@code type},
     * {@code typeName} and {@code spec}). A built-in block is not replaced by an extra of the same name: the generator
     * handles the built-in one first.
     */
    public static BlockCatalog with(Collection<? extends Map<String, Object>> extras) {
        BlockCatalog catalog = builtIn();
        for (Map<String, Object> extra : extras) {
            Object name = extra.get("name");
            Object type = extra.get("type");
            Object spec = extra.get("spec");
            if (!(name instanceof String opCode) || !(type instanceof String blockType) || !(spec instanceof String specText)) continue;
            if (catalog.defs.containsKey(opCode)) continue;
            Object typeName = extra.get("typeName");
            catalog.defs.put(opCode, new BlockDef(opCode, blockType, typeName instanceof String t ? t : "", paramsOf(specText), false));
        }
        return catalog;
    }

    /** The parameter types in an extra block's spec text, in order. */
    static List<String> paramsOf(String spec) {
        List<String> params = new ArrayList<>();
        Matcher matcher = SPEC_PARAM.matcher(spec);
        while (matcher.find()) params.add(matcher.group());
        return params;
    }

    public Collection<BlockDef> all() {
        return defs.values();
    }

    @Nullable
    public BlockDef find(@NonNull String opCode) {
        return defs.get(opCode);
    }

    public int size() {
        return defs.size();
    }
}
