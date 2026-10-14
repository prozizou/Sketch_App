package pro.sketchware.evolve;

/** What an Evolve entry holds. The key is what the database stores; entries without one are plain block stacks. */
public enum SharedKind {
    /** A stack of blocks, picked into My Block Collection. */
    BLOCKS("blocks"),
    /** A more block (its spec and its body), picked into the open screen's more blocks. */
    MOREBLOCK("moreblock"),
    /** The XML of a layout, picked into the XML editor. */
    VIEW("view");

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

    /** @return whether the payload of this kind is a list of blocks (as opposed to XML text) */
    public boolean hasBlocks() {
        return this != VIEW;
    }
}
