package pro.sketchware.evolve;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The checks shown before something shared is picked: whether this version of the app can be expected to understand
 * it, what it needs from the project, what raw Java it carries, and whether the user already has it. Pure Java.
 */
public final class EvolveChecks {

    /** One block that writes Java: the opcode and the text typed into it. */
    public record RawSnippet(String opCode, String code) {
    }

    private EvolveChecks() {
    }

    /** @return whether the entry was made with a newer app than {@code currentVersion}, so may use blocks it lacks */
    public static boolean madeWithNewerApp(int entryVersion, int currentVersion) {
        return entryVersion > currentVersion;
    }

    /** @return the variables and lists the blocks read ({@code getVar}), in first-use order */
    public static List<String> variables(List<BlockBean> blocks) {
        Set<String> names = new LinkedHashSet<>();
        for (BlockBean block : blocks) {
            if ("getVar".equals(block.opCode) && block.spec != null && !block.spec.isBlank()) {
                names.add(block.spec.trim());
            }
        }
        return new ArrayList<>(names);
    }

    /** @return the Java the raw-code blocks of {@code blocks} would write into the app, to read before picking */
    public static List<RawSnippet> rawCode(List<BlockBean> blocks) {
        List<RawSnippet> snippets = new ArrayList<>();
        for (BlockBean block : blocks) {
            if (!SharedBlockRules.RAW_CODE_OPCODES.contains(block.opCode) || block.parameters == null) {
                continue;
            }
            StringBuilder code = new StringBuilder();
            for (String parameter : block.parameters) {
                if (parameter != null && !parameter.isEmpty() && parameter.charAt(0) != '@') {
                    if (code.length() > 0) code.append('\n');
                    code.append(parameter);
                }
            }
            snippets.add(new RawSnippet(block.opCode, code.toString()));
        }
        return snippets;
    }

    /**
     * @param entryJson    the entry's blocks as shared
     * @param existingJson the blocks the user already has, each encoded the same way
     * @return whether the user already has exactly these blocks
     */
    public static boolean alreadyHave(String entryJson, List<String> existingJson) {
        return entryJson != null && existingJson.contains(entryJson);
    }
}
