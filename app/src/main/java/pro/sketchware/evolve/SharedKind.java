package pro.sketchware.evolve;

/** What an Evolve entry holds. The key is what the database stores; entries without one are plain block stacks. */
public enum SharedKind {
    /** A stack of blocks, picked into My Block Collection. */
    BLOCKS("blocks"),
    /** A more block (its spec and its body), picked into the open screen's more blocks. */
    MOREBLOCK("moreblock"),
    /** The XML of a layout, picked into the XML editor. */
    VIEW("view"),
    /** A Java source file, added to the project's Java files. */
    JAVA("java"),
    /** A Kotlin source file, added to the project's Java files (the build compiles Kotlin). */
    KOTLIN("kotlin"),
    /** A custom component, a JSON description added to the project's custom components. */
    COMPONENT("component");

    private final String key;

    SharedKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** @return the kind stored under {@code key}; a missing key means {@link #BLOCKS} (entries from before kinds existed) */
    public static SharedKind fromKey(String key) {
        for (SharedKind kind : values()) {
            if (kind.key.equals(key)) {
                return kind;
            }
        }
        return BLOCKS;
    }

    /** @return whether the payload of this kind is a list of blocks (as opposed to text) */
    public boolean hasBlocks() {
        return this == BLOCKS || this == MOREBLOCK;
    }

    /** @return whether the payload is source text stored under {@code code}: Java, Kotlin or a component's JSON */
    public boolean isCode() {
        return this == JAVA || this == KOTLIN || this == COMPONENT;
    }

    /** @return whether the payload is a file the user can save: XML, Java or Kotlin */
    public boolean isFile() {
        return this == VIEW || this == JAVA || this == KOTLIN;
    }

    /** @return the extension of the file this kind is saved as, or "" when it is not a file */
    public String extension() {
        return switch (this) {
            case VIEW -> "xml";
            case JAVA -> "java";
            case KOTLIN -> "kt";
            case COMPONENT -> "json";
            default -> "";
        };
    }
}
