package pro.sketchware.build.settings;

import static mod.hey.studios.build.BuildSettings.SETTING_DEXER_D8;
import static mod.hey.studios.build.BuildSettings.SETTING_DEXER_DX;
import static mod.hey.studios.build.BuildSettings.SETTING_JAVA_VERSION_1_7;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipFile;

import pro.sketchware.analysis.DependencyInspector;
import pro.sketchware.analysis.Finding;

/**
 * Checks build settings before they are saved, so a combination that cannot build is caught on the settings screen
 * instead of halfway through a build. Errors block saving; warnings are shown and the settings can still be saved.
 */
public final class BuildSettingsCheck {

    public enum Severity {ERROR, WARNING}

    /** One problem: what is wrong and what to do about it. */
    public record Issue(Severity severity, String title, String detail) {
    }

    /**
     * What the check knows about the project, besides the settings.
     *
     * @param usesRequestNetwork the project has a RequestNetwork component, which needs http-legacy
     * @param libraryNames       local libraries enabled in the project, with their version in the name
     */
    public record Project(boolean usesRequestNetwork, Collection<String> libraryNames) {
    }

    private BuildSettingsCheck() {
    }

    public static List<Issue> check(ProjectBuildConfig config, Project project) {
        List<Issue> issues = new ArrayList<>();

        if (!SETTING_DEXER_D8.equals(config.dexer()) && !SETTING_DEXER_DX.equals(config.dexer())) {
            issues.add(new Issue(Severity.ERROR, "Unknown dexer \"" + config.dexer() + "\"", "Pick D8 or Dx."));
        }
        if (!ProjectBuildConfig.JAVA_VERSIONS.contains(config.javaVersion())) {
            issues.add(new Issue(Severity.ERROR, "Unsupported Java version " + config.javaVersion(),
                    "The bundled compiler accepts " + String.join(", ", ProjectBuildConfig.JAVA_VERSIONS) + "."));
        } else if (!ProjectBuildConfig.javaVersionsFor(config.dexer()).contains(config.javaVersion())) {
            issues.add(new Issue(Severity.ERROR, "Dx cannot build Java " + config.javaVersion(),
                    "Dx only turns Java 7 bytecode into dex. Choose D8, or Java " + SETTING_JAVA_VERSION_1_7 + "."));
        }

        String androidJar = config.androidJar();
        if (!androidJar.isEmpty()) {
            File jar = new File(androidJar);
            if (!jar.isFile()) {
                issues.add(new Issue(Severity.ERROR, "android.jar not found", androidJar
                        + " does not exist. Pick the file again, or clear the field to use the built-in android.jar."));
            } else if (!isAndroidJar(jar)) {
                issues.add(new Issue(Severity.ERROR, "Not an android.jar", androidJar
                        + " is not a readable jar with the Android classes (android/app/Activity.class)."));
            }
        }

        List<String> missing = new ArrayList<>();
        List<String> notJars = new ArrayList<>();
        for (String entry : config.classpath().split(":")) {
            String path = entry.trim();
            if (path.isEmpty()) continue;
            File file = new File(path);
            if (!file.exists()) {
                missing.add(path);
            } else if (file.isFile() && !path.endsWith(".jar") && !path.endsWith(".zip")) {
                notJars.add(path);
            }
        }
        if (!missing.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "Classpath entries not found", String.join("\n", missing)));
        }
        if (!notJars.isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "Classpath entries that are not jars",
                    "The Java compiler only reads jars and class folders:\n" + String.join("\n", notJars)));
        }

        if (config.noHttpLegacy() && project.usesRequestNetwork()) {
            issues.add(new Issue(Severity.WARNING, "RequestNetwork needs http-legacy",
                    "This project uses a RequestNetwork component, which relies on the Apache HTTP classes that "
                            + "http-legacy-28.dex provides. Leaving it out may make the app crash when it goes online."));
        }

        for (Finding finding : DependencyInspector.findVersionConflicts(project.libraryNames())) {
            issues.add(new Issue(Severity.WARNING, finding.title(), finding.evidence() + "\n" + finding.solution()));
        }
        return issues;
    }

    public static boolean hasErrors(List<Issue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
    }

    /** A jar the compiler can use as android.jar: it opens and has the Android framework classes. */
    static boolean isAndroidJar(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            return zip.getEntry("android/app/Activity.class") != null;
        } catch (IOException e) {
            return false;
        }
    }
}
