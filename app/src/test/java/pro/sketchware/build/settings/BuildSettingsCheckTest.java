package pro.sketchware.build.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BuildSettingsCheckTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static final BuildSettingsCheck.Project PLAIN = new BuildSettingsCheck.Project(false, List.of());

    private static List<BuildSettingsCheck.Issue> check(ProjectBuildConfig config) {
        return BuildSettingsCheck.check(config, PLAIN);
    }

    private File jar(String name, String... entries) throws Exception {
        File file = temp.newFile(name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(1);
                zip.closeEntry();
            }
        }
        return file;
    }

    @Test
    public void anUntouchedProjectIsFine() {
        assertTrue(check(ProjectBuildConfig.DEFAULTS).isEmpty());
        assertEquals(ProjectBuildConfig.Profile.CUSTOM, ProjectBuildConfig.DEFAULTS.profile());
    }

    @Test
    public void dxCannotBuildJava8() {
        List<BuildSettingsCheck.Issue> issues = check(ProjectBuildConfig.DEFAULTS.withJavaVersion("1.8"));
        assertTrue(BuildSettingsCheck.hasErrors(issues));
        assertTrue(issues.get(0).title().contains("Dx"));
        assertFalse(BuildSettingsCheck.hasErrors(check(ProjectBuildConfig.DEFAULTS.withDexer("D8").withJavaVersion("1.8"))));
    }

    @Test
    public void switchingToDxBringsTheJavaLevelDown() {
        ProjectBuildConfig d8Java11 = ProjectBuildConfig.DEFAULTS.withDexer("D8").withJavaVersion("11");
        assertEquals("1.7", d8Java11.withDexer("Dx").javaVersion());
        assertEquals(List.of("1.7"), ProjectBuildConfig.javaVersionsFor("Dx"));
        assertEquals(ProjectBuildConfig.JAVA_VERSIONS, ProjectBuildConfig.javaVersionsFor("D8"));
    }

    @Test
    public void unknownValuesAreErrors() {
        assertTrue(BuildSettingsCheck.hasErrors(check(ProjectBuildConfig.DEFAULTS.withDexer("R8"))));
        assertTrue(BuildSettingsCheck.hasErrors(check(ProjectBuildConfig.DEFAULTS.withDexer("D8").withJavaVersion("17"))));
    }

    @Test
    public void androidJarMustExistAndHoldTheAndroidClasses() throws Exception {
        assertTrue(BuildSettingsCheck.hasErrors(check(ProjectBuildConfig.DEFAULTS.withAndroidJar("/nowhere/android.jar"))));
        File notAndroid = jar("other.jar", "com/example/A.class");
        assertTrue(BuildSettingsCheck.hasErrors(check(ProjectBuildConfig.DEFAULTS.withAndroidJar(notAndroid.getPath()))));
        File broken = temp.newFile("broken.jar");
        assertTrue(BuildSettingsCheck.hasErrors(check(ProjectBuildConfig.DEFAULTS.withAndroidJar(broken.getPath()))));
        File android = jar("android.jar", "android/app/Activity.class");
        assertTrue(check(ProjectBuildConfig.DEFAULTS.withAndroidJar(android.getPath())).isEmpty());
    }

    @Test
    public void classpathEntriesMustExist() throws Exception {
        File lib = jar("lib.jar", "a/B.class");
        File classes = temp.newFolder("classes");
        File text = temp.newFile("notes.txt");
        assertTrue(check(ProjectBuildConfig.DEFAULTS.withClasspath(lib.getPath() + ":" + classes.getPath())).isEmpty());

        List<BuildSettingsCheck.Issue> missing = check(ProjectBuildConfig.DEFAULTS.withClasspath(lib.getPath() + ":/nowhere/x.jar"));
        assertTrue(BuildSettingsCheck.hasErrors(missing));
        assertTrue(missing.get(0).detail().contains("/nowhere/x.jar"));

        List<BuildSettingsCheck.Issue> notJar = check(ProjectBuildConfig.DEFAULTS.withClasspath(text.getPath()));
        assertFalse(BuildSettingsCheck.hasErrors(notJar));
        assertEquals(1, notJar.size());
    }

    @Test
    public void warnsAboutRequestNetworkWithoutHttpLegacyAndLibraryVersionConflicts() {
        ProjectBuildConfig noLegacy = ProjectBuildConfig.DEFAULTS.withNoHttpLegacy(true);
        assertTrue(BuildSettingsCheck.check(noLegacy, PLAIN).isEmpty());
        List<BuildSettingsCheck.Issue> issues = BuildSettingsCheck.check(noLegacy,
                new BuildSettingsCheck.Project(true, List.of("core-1.9.0", "core-1.17.0", "gson-2.10.1")));
        assertEquals(2, issues.size());
        assertFalse(BuildSettingsCheck.hasErrors(issues));
    }

    @Test
    public void profilesSetTheirSettingsAndAreRecognised() {
        ProjectBuildConfig debug = ProjectBuildConfig.Profile.DEBUG.applyTo(ProjectBuildConfig.DEFAULTS);
        assertEquals("D8", debug.dexer());
        assertEquals("1.8", debug.javaVersion());
        assertFalse(debug.noWarnings());
        assertTrue(debug.logcat());
        assertEquals(ProjectBuildConfig.Profile.DEBUG, debug.profile());

        ProjectBuildConfig release = ProjectBuildConfig.Profile.RELEASE.applyTo(debug);
        assertTrue(release.noWarnings());
        assertFalse(release.logcat());
        assertEquals(ProjectBuildConfig.Profile.RELEASE, release.profile());

        // A profile keeps the paths: they belong to the project, not to the profile.
        ProjectBuildConfig withJar = ProjectBuildConfig.DEFAULTS.withAndroidJar("/a/android.jar");
        assertEquals("/a/android.jar", ProjectBuildConfig.Profile.RELEASE.applyTo(withJar).androidJar());
        assertEquals(ProjectBuildConfig.Profile.CUSTOM, release.withJavaVersion("11").profile());
        assertTrue(check(debug).isEmpty());
        assertTrue(check(release).isEmpty());
    }
}
