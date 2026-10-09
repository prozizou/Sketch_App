package pro.sketchware.export;

/**
 * Writes a GitHub Actions workflow for a project exported for Android Studio, so GitHub builds it (and optionally runs
 * lint and the unit tests) whenever it is pushed.
 * <p>
 * The exported project has no Gradle wrapper, so the workflow installs the Gradle version the exported Android Gradle
 * Plugin needs. {@link #GRADLE_VERSION} and {@link #JAVA_VERSION} must be kept in line with the plugin version the
 * export writes (8.12.0 needs Gradle 8.13 or newer and Java 17).
 */
public final class CiWorkflowGenerator {
    public static final String GRADLE_VERSION = "8.14.3";
    public static final int JAVA_VERSION = 17;
    public static final String WORKFLOW_PATH = ".github/workflows/android.yml";

    /**
     * @param runLint      also run Android lint
     * @param runUnitTests also run the project's unit tests
     */
    public record Options(boolean runLint, boolean runUnitTests) {
    }

    private CiWorkflowGenerator() {
    }

    public static String androidWorkflow(Options options) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("name: Android CI\n\n");
        yaml.append("on:\n");
        yaml.append("  push:\n");
        yaml.append("    branches: [\"main\", \"master\"]\n");
        yaml.append("  pull_request:\n");
        yaml.append("  workflow_dispatch:\n\n");
        yaml.append("permissions:\n");
        yaml.append("  contents: read\n\n");
        yaml.append("jobs:\n");
        yaml.append("  build:\n");
        yaml.append("    runs-on: ubuntu-latest\n");
        yaml.append("    steps:\n");
        yaml.append("      - uses: actions/checkout@v4\n\n");
        yaml.append("      - name: Set up JDK ").append(JAVA_VERSION).append('\n');
        yaml.append("        uses: actions/setup-java@v4\n");
        yaml.append("        with:\n");
        yaml.append("          distribution: temurin\n");
        yaml.append("          java-version: ").append(JAVA_VERSION).append("\n\n");
        yaml.append("      - name: Set up Gradle ").append(GRADLE_VERSION).append('\n');
        yaml.append("        uses: gradle/actions/setup-gradle@v4\n");
        yaml.append("        with:\n");
        yaml.append("          gradle-version: ").append(GRADLE_VERSION).append("\n\n");
        yaml.append("      - name: Build the debug APK\n");
        yaml.append("        run: gradle assembleDebug --stacktrace\n");
        if (options.runLint()) {
            yaml.append("\n      - name: Run lint\n");
            yaml.append("        run: gradle lintDebug --stacktrace\n");
        }
        if (options.runUnitTests()) {
            yaml.append("\n      - name: Run unit tests\n");
            yaml.append("        run: gradle testDebugUnitTest --stacktrace\n");
        }
        yaml.append("\n      - name: Keep the APK\n");
        yaml.append("        uses: actions/upload-artifact@v4\n");
        yaml.append("        with:\n");
        yaml.append("          name: debug-apk\n");
        yaml.append("          path: app/build/outputs/apk/debug/*.apk\n");
        yaml.append("          if-no-files-found: error\n");
        return yaml.toString();
    }
}
