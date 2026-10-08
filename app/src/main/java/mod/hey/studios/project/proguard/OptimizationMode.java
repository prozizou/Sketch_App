package mod.hey.studios.project.proguard;

import androidx.annotation.Nullable;

/**
 * How much the build changes an app's code. Three plain levels replace the old pile of switches:
 * <ul>
 *     <li>{@link #OFF}: nothing is touched, the fastest build.</li>
 *     <li>{@link #SAFE}: unused code is removed but every name is kept, so libraries that look classes up by
 *     name (reflection, JSON mapping) keep working. The app is smaller; very little can break.</li>
 *     <li>{@link #MAX}: unused code is removed and names are scrambled. The app is smallest and hardest to read
 *     once decompiled, but a library may need an extra rule.</li>
 * </ul>
 */
public enum OptimizationMode {
    OFF("off"),
    SAFE("safe"),
    MAX("max");

    private final String key;

    OptimizationMode(String key) {
        this.key = key;
    }

    /** The value stored in the project's proguard config. */
    public String key() {
        return key;
    }

    /** Whether the build runs the shrinker at all. */
    public boolean isShrinking() {
        return this != OFF;
    }

    /** Extra shrinker rules this level adds on top of the project's own. */
    public String extraRules() {
        return switch (this) {
            case SAFE -> "# Added by the \"Smaller\" level: keep every name, only remove unused code.\n-dontobfuscate\n";
            default -> "";
        };
    }

    /**
     * The level a config means. Older configs only have an "enabled" switch, which was the full
     * shrink-and-scramble, so that maps to {@link #MAX}.
     */
    public static OptimizationMode fromConfig(@Nullable String storedKey, boolean enabled) {
        if (storedKey != null) {
            for (OptimizationMode mode : values()) {
                if (mode.key.equals(storedKey)) {
                    return mode;
                }
            }
        }
        return enabled ? MAX : OFF;
    }
}
