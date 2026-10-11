package pro.sketchware.build.settings;

import static mod.hey.studios.build.BuildSettings.SETTING_DEXER_D8;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER_DX;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_10;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_11;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_7;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_8;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_9;

import java.util.List;
import java.util.Objects;

/**
 * The project's build settings as the build screen edits them. Every field is one of the keys
 * {@link mod.hey.studios.build.BuildSettings} stores and the compilers read; the defaults are the ones the compilers
 * fall back to when a key is missing, so an untouched project builds exactly as before.
 *
 * @param androidJar   custom android.jar, empty for the built-in one
 * @param classpath    extra classpath entries, separated by ':'
 * @param dexer        {@code Dx} or {@code D8}
 * @param javaVersion  the -source/-target level given to the Java compiler
 * @param noWarnings   hide compiler warnings (the compiler then skips -deprecation)
 * @param noHttpLegacy leave out http-legacy-28.dex and its classes
 * @param logcat       let the built app send its logs to the Logcat reader
 */
public record ProjectBuildConfig(String androidJar, String classpath, String dexer, String javaVersion,
                          boolean noWarnings, boolean noHttpLegacy, boolean logcat) {

    /** What the compilers use when nothing was set. */
    public static final ProjectBuildConfig DEFAULTS = new ProjectBuildConfig("", "", SETTING_DEXER_DX, SETTING_JAVA_VERSION_1_7,
            true, false, true);

    /** Java levels the bundled compiler accepts, oldest first. Dx can only dex Java 7 bytecode. */
    public static final List<String> JAVA_VERSIONS = List.of(SETTING_JAVA_VERSION_1_7, SETTING_JAVA_VERSION_1_8,
            SETTING_JAVA_VERSION_1_9, SETTING_JAVA_VERSION_10, SETTING_JAVA_VERSION_11);

    /** @return the Java levels this dexer can turn into dex: only Java 7 for Dx, all of them for D8. */
    public static List<String> javaVersionsFor(String dexer) {
        return SETTING_DEXER_D8.equals(dexer) ? JAVA_VERSIONS : List.of(SETTING_JAVA_VERSION_1_7);
    }

    /** Ready-made combinations of the settings above. Custom is any other combination. */
    public enum Profile {
        /** For running and testing: compiler warnings and logs on. */
        DEBUG,
        /** For an app to hand out: no warnings in the log, no Logcat from the app. */
        RELEASE,
        CUSTOM;

        /** @return {@code base} with this profile's settings; paths and http-legacy are kept. */
        public ProjectBuildConfig applyTo(ProjectBuildConfig base) {
            return switch (this) {
                case DEBUG -> new ProjectBuildConfig(base.androidJar, base.classpath, SETTING_DEXER_D8, SETTING_JAVA_VERSION_1_8,
                        false, base.noHttpLegacy, true);
                case RELEASE -> new ProjectBuildConfig(base.androidJar, base.classpath, SETTING_DEXER_D8, SETTING_JAVA_VERSION_1_8,
                        true, base.noHttpLegacy, false);
                case CUSTOM -> base;
            };
        }
    }

    /** @return the profile these settings match, CUSTOM when they match none. */
    public Profile profile() {
        if (equals(Profile.DEBUG.applyTo(this))) return Profile.DEBUG;
        if (equals(Profile.RELEASE.applyTo(this))) return Profile.RELEASE;
        return Profile.CUSTOM;
    }

    public ProjectBuildConfig withDexer(String value) {
        // Dx cannot dex anything newer than Java 7: switching to it brings the level down with it.
        String java = javaVersionsFor(value).contains(javaVersion) ? javaVersion : SETTING_JAVA_VERSION_1_7;
        return new ProjectBuildConfig(androidJar, classpath, value, java, noWarnings, noHttpLegacy, logcat);
    }

    public ProjectBuildConfig withJavaVersion(String value) {
        return new ProjectBuildConfig(androidJar, classpath, dexer, value, noWarnings, noHttpLegacy, logcat);
    }

    public ProjectBuildConfig withAndroidJar(String value) {
        return new ProjectBuildConfig(Objects.requireNonNullElse(value, "").trim(), classpath, dexer, javaVersion, noWarnings, noHttpLegacy, logcat);
    }

    public ProjectBuildConfig withClasspath(String value) {
        return new ProjectBuildConfig(androidJar, Objects.requireNonNullElse(value, "").trim(), dexer, javaVersion, noWarnings, noHttpLegacy, logcat);
    }

    public ProjectBuildConfig withNoWarnings(boolean value) {
        return new ProjectBuildConfig(androidJar, classpath, dexer, javaVersion, value, noHttpLegacy, logcat);
    }

    public ProjectBuildConfig withNoHttpLegacy(boolean value) {
        return new ProjectBuildConfig(androidJar, classpath, dexer, javaVersion, noWarnings, value, logcat);
    }

    public ProjectBuildConfig withLogcat(boolean value) {
        return new ProjectBuildConfig(androidJar, classpath, dexer, javaVersion, noWarnings, noHttpLegacy, value);
    }
}
