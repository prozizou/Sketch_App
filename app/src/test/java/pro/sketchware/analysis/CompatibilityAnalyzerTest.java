package pro.sketchware.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import pro.sketchware.analysis.ViewFacts.Kind;

public class CompatibilityAnalyzerTest {
    private static ProjectFacts facts(int min, int target, Set<String> permissions, List<SourceFile> sources) {
        return new ProjectFacts("com.my.app", min, target, permissions, sources, Set.of(), true, Set.of(), List.of(), true);
    }

    private static ProjectFacts withViews(List<ViewFacts> views, boolean nightResources) {
        return new ProjectFacts("com.my.app", 24, 35, Set.of(), List.of(), Set.of(), true, Set.of(), views, nightResources);
    }

    private static ProjectFacts withAbis(Set<String> abis) {
        return new ProjectFacts("com.my.app", 24, 35, Set.of(), List.of(), Set.of(), true, abis, List.of(), true);
    }

    private static List<String> ids(ProjectFacts facts) {
        return CompatibilityAnalyzer.analyze(facts).stream().map(Finding::id).collect(Collectors.toList());
    }

    private static Finding find(ProjectFacts facts, String id) {
        return CompatibilityAnalyzer.analyze(facts).stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
    }

    private static ViewFacts view(String id, Kind kind, boolean clickable, int w, int h, Integer text, Integer bg, int sp) {
        return new ViewFacts("main", id, kind, w, h, clickable, false, text, bg, sp, 0, 0, 0, 0, false);
    }

    @Test
    public void aModernProjectHasNothingToReport() {
        assertEquals(List.of("compat.edge-to-edge"), ids(facts(24, 35, Set.of("android.permission.INTERNET"), List.of())));
        assertEquals(List.of(), ids(facts(24, 34, Set.of("android.permission.INTERNET"), List.of())));
    }

    @Test
    public void sdkLevels() {
        assertTrue(ids(facts(30, 28, Set.of(), List.of())).contains("compat.min-above-target"));
        Finding old = find(facts(21, 28, Set.of(), List.of()), "compat.target-sdk-old");
        assertEquals(Severity.WARNING, old.severity());
        assertTrue(old.title().contains("28"));
        assertTrue(ids(facts(19, 34, Set.of(), List.of())).contains("compat.min-sdk-low-androidx"));
        assertFalse(ids(facts(21, 34, Set.of(), List.of())).contains("compat.min-sdk-low-androidx"));
        assertEquals(List.of(), ids(ProjectFacts.EMPTY));
    }

    @Test
    public void storagePermissionsDependOnTheTargetVersion() {
        Set<String> storage = Set.of("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE");
        assertEquals(List.of(), ids(facts(24, 28, storage, List.of())).stream().filter(i -> i.startsWith("compat.perm")).toList());
        List<String> onThirty = ids(facts(24, 30, storage, List.of()));
        assertTrue(onThirty.contains("compat.perm-write-storage"));
        assertFalse(onThirty.contains("compat.perm-read-storage"));
        assertTrue(ids(facts(24, 34, storage, List.of())).containsAll(List.of("compat.perm-write-storage", "compat.perm-read-storage")));
    }

    @Test
    public void restrictedPermissions() {
        List<String> ids = ids(facts(24, 34, Set.of("android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.READ_SMS",
                "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.QUERY_ALL_PACKAGES", "android.permission.ACCESS_FINE_LOCATION"), List.of()));
        assertTrue(ids.toString(), ids.containsAll(List.of("compat.perm-manage-storage", "compat.perm-restricted-read-sms",
                "compat.perm-background-location", "compat.perm-query-all-packages", "compat.perm-coarse-location")));
    }

    @Test
    public void notificationsNeedThePermissionFromAndroid13() {
        List<SourceFile> code = List.of(new SourceFile("Notify.java", "NotificationManager m = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);"));
        assertTrue(ids(facts(24, 34, Set.of(), code)).contains("compat.perm-post-notifications"));
        assertFalse(ids(facts(24, 34, Set.of("android.permission.POST_NOTIFICATIONS"), code)).contains("compat.perm-post-notifications"));
        assertFalse(ids(facts(24, 30, Set.of(), code)).contains("compat.perm-post-notifications"));
    }

    @Test
    public void deprecatedApisAreFoundWithTheirLocation() {
        String source = "package a;\n// AsyncTask is mentioned in a comment only\nclass A {\n  void f() {\n    new AsyncTask<Void,Void,Void>() {};\n"
                + "    new AsyncTask<Void,Void,Void>() {};\n    File f = Environment.getExternalStorageDirectory();\n  }\n}\n";
        ProjectFacts project = facts(24, 34, Set.of(), List.of(new SourceFile("A.java", source)));
        Finding asyncTask = find(project, "compat.async-task");
        assertEquals("AsyncTask is deprecated (2 uses)", asyncTask.title());
        assertTrue(asyncTask.evidence().startsWith("A.java:5:"));
        assertFalse(asyncTask.evidence().contains(":2:"));
        assertEquals(Severity.WARNING, find(project, "compat.external-storage-dir").severity());
        assertEquals(Severity.INFO, find(facts(24, 28, Set.of(), List.of(new SourceFile("A.java", source))), "compat.external-storage-dir").severity());
    }

    @Test
    public void pendingIntentsWithoutAMutabilityFlagCrashOnAndroid12() {
        String bad = "PendingIntent p = PendingIntent.getActivity(this, 0, intent, 0);";
        String good = "PendingIntent p = PendingIntent.getActivity(this, 0, intent,\n    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);";
        assertEquals(Severity.ERROR, find(facts(24, 34, Set.of(), List.of(new SourceFile("A.java", bad))), "compat.pending-intent-flag").severity());
        assertEquals(Severity.INFO, find(facts(24, 30, Set.of(), List.of(new SourceFile("A.java", bad))), "compat.pending-intent-flag").severity());
        assertFalse(ids(facts(24, 34, Set.of(), List.of(new SourceFile("A.java", good)))).contains("compat.pending-intent-flag"));
    }

    @Test
    public void theOldSupportLibrary() {
        ProjectFacts androidX = facts(24, 34, Set.of(), List.of(new SourceFile("A.java", "import android.support.v7.app.AppCompatActivity;\nclass A {}")));
        assertEquals(Severity.ERROR, find(androidX, "compat.support-library").severity());
        assertTrue(find(androidX, "compat.support-library").evidence().startsWith("A.java:1:"));
    }

    @Test
    public void nativeLibrariesNeed64BitCode() {
        assertEquals(Severity.ERROR, find(withAbis(Set.of("armeabi-v7a")), "compat.abi-no-64bit").severity());
        assertEquals(Severity.INFO, find(withAbis(Set.of("arm64-v8a")), "compat.abi-no-arm32").severity());
        assertFalse(ids(withAbis(Set.of("arm64-v8a", "armeabi-v7a"))).stream().anyMatch(i -> i.startsWith("compat.abi")));
        assertFalse(ids(withAbis(Set.of())).stream().anyMatch(i -> i.startsWith("compat.abi")));
    }

    @Test
    public void accessibilityChecks() {
        ProjectFacts project = withViews(List.of(
                view("logo", Kind.IMAGE, false, 100, 100, null, null, 0),
                view("tiny", Kind.BUTTON, true, 30, 30, 0xFF000000, 0xFFFFFFFF, 14),
                view("faded", Kind.TEXT, false, -2, -2, 0xFFCCCCCC, 0xFFFFFFFF, 14),
                view("fine", Kind.BUTTON, true, 120, 56, 0xFF000000, 0xFFFFFFFF, 14),
                view("small", Kind.TEXT, false, -2, -2, null, null, 9)), true);
        assertEquals("main/logo", find(project, "a11y.content-description").evidence());
        assertEquals("main/tiny (30dp x 30dp)", find(project, "a11y.touch-target").evidence());
        assertTrue(find(project, "a11y.contrast").evidence().startsWith("main/faded (1."));
        assertEquals("main/small (9sp)", find(project, "a11y.small-text").evidence());
    }

    @Test
    public void largeTextNeedsLessContrast() {
        int grey = 0xFF8A8A8A;
        assertTrue(CompatibilityAnalyzer.contrastRatio(grey, 0xFFFFFFFF) > 3.0);
        assertTrue(CompatibilityAnalyzer.contrastRatio(grey, 0xFFFFFFFF) < 4.5);
        assertTrue(ids(withViews(List.of(view("a", Kind.TEXT, false, -2, -2, grey, 0xFFFFFFFF, 14)), true)).contains("a11y.contrast"));
        assertFalse(ids(withViews(List.of(view("a", Kind.TEXT, false, -2, -2, grey, 0xFFFFFFFF, 20)), true)).contains("a11y.contrast"));
    }

    @Test
    public void contrastMathMatchesTheWcagReferenceValues() {
        assertEquals(21.0, CompatibilityAnalyzer.contrastRatio(0xFF000000, 0xFFFFFFFF), 0.01);
        assertEquals(1.0, CompatibilityAnalyzer.contrastRatio(0xFF123456, 0xFF123456), 0.0001);
    }

    @Test
    public void rightToLeftAndDarkMode() {
        ViewFacts uneven = new ViewFacts("main", "card", Kind.OTHER, -1, -2, false, true, null, 0xFFFFFFFF, 0, 16, 0, 0, 0, false);
        ProjectFacts project = withViews(List.of(uneven), false);
        assertEquals("main/card", find(project, "rtl.asymmetric-spacing").evidence());
        assertEquals(Severity.INFO, find(project, "dark.fixed-light-colors").severity());
        assertFalse(ids(withViews(List.of(uneven), true)).contains("dark.fixed-light-colors"));

        java.util.ArrayList<ViewFacts> many = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) many.add(new ViewFacts("main", "w" + i, Kind.OTHER, -1, -2, false, true, null, 0xFFFFFFFF, 0, 0, 0, 0, 0, false));
        assertEquals(Severity.WARNING, find(withViews(many, false), "dark.fixed-light-colors").severity());
    }

    @Test
    public void hardcodedTextIsAQualityNoteNotACompatibilityOne() {
        ViewFacts typed = new ViewFacts("main", "title", Kind.TEXT, -2, -2, false, true, null, null, 14, 0, 0, 0, 0, true);
        Finding f = find(withViews(List.of(typed), true), "quality.hardcoded-text");
        assertEquals(Category.QUALITY, f.category());
    }
}
