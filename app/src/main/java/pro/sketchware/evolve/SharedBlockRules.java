package pro.sketchware.evolve;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What may be shared on Evolve, and the checks before publishing and picking. Pure Java, so it is tested directly.
 */
public final class SharedBlockRules {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_DESCRIPTION_LENGTH = 300;
    public static final int MAX_BLOCKS = 200;
    /** Well under the Realtime Database's 16 MB node limit, and quick to download on mobile data. */
    public static final int MAX_JSON_LENGTH = 200_000;

    /**
     * Opcodes of the blocks that write the Java text typed into them straight into the generated app. A shared stack
     * with one of them can run anything the author wrote, so picking it asks for confirmation first.
     */
    public static final Set<String> RAW_CODE_OPCODES = Set.of("addSourceDirectly", "customImport");

    /** Why a block can't be published, or {@link #NONE}. */
    public enum Problem {
        NONE,
        NAME_EMPTY,
        NAME_TOO_LONG,
        NAME_INVALID,
        DESCRIPTION_TOO_LONG,
        NO_BLOCKS,
        TOO_MANY_BLOCKS,
        TOO_LARGE
    }

    private SharedBlockRules() {
    }

    /** @return the distinct opcodes of {@code opCodes}, in first-use order, without blanks. */
    public static List<String> distinctOpCodes(Collection<String> opCodes) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String opCode : opCodes) {
            if (opCode != null && !opCode.isBlank()) {
                distinct.add(opCode);
            }
        }
        return List.copyOf(distinct);
    }

    /** @return whether any of {@code opCodes} injects Java source. */
    public static boolean usesRawCode(Collection<String> opCodes) {
        for (String opCode : opCodes) {
            if (opCode != null && RAW_CODE_OPCODES.contains(opCode)) {
                return true;
            }
        }
        return false;
    }

    /** @return the first reason {@code name} can't be used, or {@link Problem#NONE}. */
    public static Problem checkName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return Problem.NAME_EMPTY;
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return Problem.NAME_TOO_LONG;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                return Problem.NAME_INVALID;
            }
        }
        return Problem.NONE;
    }

    /** @return whether {@code description} fits (empty is fine). */
    public static Problem checkDescription(String description) {
        return description != null && description.trim().length() > MAX_DESCRIPTION_LENGTH
                ? Problem.DESCRIPTION_TOO_LONG : Problem.NONE;
    }

    /** @return the first reason a stack of {@code blockCount} blocks encoded as {@code blocksJson} can't be shared. */
    public static Problem checkStack(int blockCount, String blocksJson) {
        if (blockCount < 1) {
            return Problem.NO_BLOCKS;
        }
        if (blockCount > MAX_BLOCKS) {
            return Problem.TOO_MANY_BLOCKS;
        }
        return blocksJson == null || blocksJson.length() > MAX_JSON_LENGTH ? Problem.TOO_LARGE : Problem.NONE;
    }

    /**
     * @return {@code wanted}, or {@code wanted (2)}, {@code wanted (3)}... the first not in {@code taken}
     * (names compare case-insensitively), so picking a block never overwrites one of the user's own.
     */
    public static String uniqueName(String wanted, Collection<String> taken) {
        String base = wanted == null ? "" : wanted.trim();
        Set<String> used = new LinkedHashSet<>();
        for (String name : taken) {
            used.add(name.toLowerCase(Locale.ROOT));
        }
        if (!used.contains(base.toLowerCase(Locale.ROOT))) {
            return base;
        }
        for (int i = 2; ; i++) {
            String candidate = base + " (" + i + ")";
            if (!used.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
    }
}
