package pro.sketchware.flags;

/**
 * Switches for features that are new or risky, so they can be turned off from App Settings if one misbehaves.
 * The key is the setting's name in the app's settings store.
 */
public enum FeatureFlag {
    /** Explains build failures in the compile log. */
    BUILD_DOCTOR("ff-build-doctor", true),
    /** The project analysis screen: compatibility, security, dependencies and health score. */
    PROJECT_ANALYSIS("ff-project-analysis", true),
    /** Limits the compilers' threads on phones with little memory. */
    MEMORY_AWARE_BUILD("ff-memory-aware-build", true),
    /** Uses the smallest build settings whatever the device. */
    FORCE_LOW_MEMORY_BUILD("ff-force-low-memory-build", false),
    /** Keeps each exported release with its R8 mapping, and the Releases screen. */
    RELEASE_MANAGER("ff-release-manager", true),
    /** Preview a screen on other devices: tablets, foldables, orientation, safe areas. Does nothing until a device is picked. */
    DEVICE_PREVIEW("ff-device-preview", true),
    /** Alignment guides and distances around the selected widget in the View editor. */
    LAYOUT_GUIDES("ff-layout-guides", false),
    /** Adds a GitHub Actions workflow to the project exported for Android Studio. */
    EXPORT_CI_WORKFLOW("ff-export-ci", false),
    /** With the workflow: also run lint. */
    EXPORT_CI_LINT("ff-export-ci-lint", false),
    /** With the workflow: also run the unit tests. */
    EXPORT_CI_TESTS("ff-export-ci-tests", false);

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
