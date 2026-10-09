package pro.sketchware.flags;

/**
 * Switches for features that are new or risky, so they can be turned off from App Settings if one misbehaves.
 * The key is the setting's name in the app's settings store.
 */
public enum FeatureFlag {
    /** Explains build failures in the compile log. */
    BUILD_DOCTOR("ff-build-doctor", true),
    /** The project analysis screen: compatibility, security, dependencies and health score. */
    PROJECT_ANALYSIS("ff-project-analysis", true);

    private final String key;
    private final boolean enabledByDefault;

    FeatureFlag(String key, boolean enabledByDefault) {
        this.key = key;
        this.enabledByDefault = enabledByDefault;
    }

    public String key() {
        return key;
    }

    public boolean enabledByDefault() {
        return enabledByDefault;
    }
}
