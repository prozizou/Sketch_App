package pro.sketchware.evolve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import com.besome.sketch.beans.BlockBean;
import java.util.Set;

import pro.sketchware.evolve.ImportPlanner.Code;
import pro.sketchware.evolve.ImportPlanner.Context;
import pro.sketchware.evolve.ImportPlanner.OnConflict;
import pro.sketchware.evolve.ImportPlanner.Plan;
import pro.sketchware.evolve.ImportPlanner.Severity;
import pro.sketchware.evolve.ImportPlanner.Target;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class ImportPlannerTest {
    private static final String JAVA = "package com.other.app;\n\nimport android.os.Bundle;\nimport okhttp3.OkHttpClient;\n\npublic class Helper {\n    void f() {}\n}\n";

    private static Context ctx(Set<String> java, Set<String> layouts, Set<String> libs, int minSdk) {
        return new Context("Demo", "com.my.app", minSdk, java, layouts, libs, Set.of("Toast"), Set.of("calc"), Set.of("Chart"), Set.of("chart1"), List.of());
    }

    private static SharedBlock code(SharedKind kind, String name, String file, String code, int minSdk, List<String> deps) {
        PostMeta meta = new PostMeta(List.of(), "1.0", minSdk, deps, List.of(), 0, 0, 0, code, file);
        return new SharedBlock("id", name, "", "u", "A", 1, 100, 1, List.of(), false, 0, "", kind, "", "", meta);
    }

    private static boolean has(Plan plan, Code code) {
        return plan.issues().stream().anyMatch(i -> i.code() == code);
    }

    private static boolean has(Plan plan, Code code, Severity severity) {
        return plan.issues().stream().anyMatch(i -> i.code() == code && i.severity() == severity);
    }

    @Test
    public void everyKindHasATarget() {
        for (SharedKind kind : SharedKind.values()) {
            assertTrue(kind.name(), ImportPlanner.targetOf(kind) != null);
        }
        assertEquals(Target.JAVA_FILE, ImportPlanner.targetOf(SharedKind.JAVA));
        assertTrue(ImportPlanner.needsProject(Target.JAVA_FILE));
        assertFalse(ImportPlanner.needsProject(Target.BLOCK_COLLECTION));
        assertFalse(ImportPlanner.needsProject(Target.COMPONENT_LIST));
    }

    @Test
    public void javaIsAddedWithTheProjectsPackage() {
        Plan plan = ImportPlanner.plan(code(SharedKind.JAVA, "Helper", "Helper.java", JAVA, 0, List.of()), ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertTrue(plan.canApply());
        assertEquals("Helper.java", plan.name());
        assertTrue(plan.content().startsWith("package com.my.app;"));
        assertTrue(plan.content().contains("public class Helper"));
        assertTrue(has(plan, Code.PACKAGE_CHANGED, Severity.INFO));
        assertFalse(plan.replaces());
    }

    @Test
    public void aTakenJavaFileNameBlocksUntilTheUserChoosesToReplace() {
        SharedBlock post = code(SharedKind.JAVA, "Helper", "Helper.java", JAVA, 0, List.of());
        Context context = ctx(Set.of("helper.java"), Set.of(), Set.of(), 21);
        Plan blocked = ImportPlanner.plan(post, context, 160, 34, OnConflict.RENAME);
        assertFalse(blocked.canApply());
        assertTrue(has(blocked, Code.FILE_EXISTS, Severity.BLOCKER));
        Plan replace = ImportPlanner.plan(post, context, 160, 34, OnConflict.REPLACE);
        assertTrue(replace.canApply());
        assertTrue(replace.replaces());
        assertTrue(has(replace, Code.REPLACES_EXISTING, Severity.WARNING));
    }

    @Test
    public void javaThatDoesNotParseCannotBeAdded() {
        Plan plan = ImportPlanner.plan(code(SharedKind.JAVA, "Bad", "Bad.java", "class Bad { void f( }", 0, List.of()), ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertFalse(plan.canApply());
        assertTrue(has(plan, Code.INVALID_CODE, Severity.BLOCKER));
    }

    @Test
    public void aFileNameThatEscapesTheFolderIsRefused() {
        Plan plan = ImportPlanner.plan(code(SharedKind.JAVA, "Evil", "../Evil.java", "class Evil {}", 0, List.of()), ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertFalse(plan.canApply());
    }

    @Test
    public void kotlinPackageIsRewrittenOrAdded() {
        assertEquals("package com.my.app\n\nclass A", ImportPlanner.withKotlinPackage("package x.y\n\nclass A", "com.my.app"));
        assertEquals("package com.my.app\n\nclass A", ImportPlanner.withKotlinPackage("class A", "com.my.app"));
        assertEquals("package com.my.app\nclass A", ImportPlanner.withKotlinPackage("package com.my.app\nclass A", "com.my.app"));
    }

    @Test
    public void javaPackageIsRewrittenOrAdded() {
        assertEquals("package a.b;\n\nclass A {}", ImportPlanner.withJavaPackage("package x;\n\nclass A {}", "a.b"));
        assertEquals("package a.b;\n\nclass A {}", ImportPlanner.withJavaPackage("class A {}", "a.b"));
        assertEquals("package a.b;\nclass A {}", ImportPlanner.withJavaPackage("package a.b;\nclass A {}", "a.b"));
        assertEquals("class A { void f( }", ImportPlanner.withJavaPackage("class A { void f( }", "a.b"));
    }

    @Test
    public void projectMinSdkAndLibrariesAreChecked() {
        SharedBlock post = code(SharedKind.JAVA, "Helper", "Helper.java", JAVA, 28, List.of("com.squareup.okhttp3:okhttp:4.12.0", "gson"));
        Plan plan = ImportPlanner.plan(post, ctx(Set.of(), Set.of(), Set.of("gson-2.10"), 21), 160, 34, OnConflict.RENAME);
        assertTrue(has(plan, Code.PROJECT_MIN_SDK_TOO_LOW, Severity.WARNING));
        assertTrue(has(plan, Code.MISSING_LIBRARIES, Severity.WARNING));
        assertEquals("com.squareup.okhttp3:okhttp:4.12.0", plan.issues().stream().filter(i -> i.code() == Code.MISSING_LIBRARIES).findFirst().get().detail());
        assertTrue(plan.canApply());
    }

    @Test
    public void aLibraryThatIsPresentIsNeverReportedMissing() {
        assertTrue(ImportPlanner.missingLibraries(List.of("com.squareup.okhttp3:okhttp:4.12.0"), Set.of("okhttp-4.12.0")).isEmpty());
        assertTrue(ImportPlanner.missingLibraries(List.of("gson"), Set.of("Gson")).isEmpty());
        assertEquals(List.of("glide"), ImportPlanner.missingLibraries(List.of("glide", "gson"), Set.of("gson")));
    }

    @Test
    public void aPostForANewerAndroidThanTheDeviceIsOnlyInformation() {
        SharedBlock post = code(SharedKind.KOTLIN, "Util", "Util.kt", "fun f() {}", 35, List.of());
        Plan plan = ImportPlanner.plan(post, ctx(Set.of(), Set.of(), Set.of(), 21), 160, 30, OnConflict.RENAME);
        assertTrue(has(plan, Code.NEEDS_NEWER_ANDROID_THAN_DEVICE, Severity.INFO));
        assertTrue(plan.canApply());
    }

    @Test
    public void anEntryFromANewerAppWarns() {
        SharedBlock post = new SharedBlock("id", "n", "", "u", "a", 1, 999, 1, List.of(), false, 0, "[]", SharedKind.JAVA, "", "",
                new PostMeta(List.of(), "", 0, List.of(), List.of(), 0, 0, 0, "class A {}", "A.java"));
        assertTrue(has(ImportPlanner.plan(post, ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME), Code.NEWER_APP, Severity.WARNING));
    }

    private static SharedBlock xml(String name, String xml) {
        return new SharedBlock("id", name, "", "u", "a", 1, 100, 3, List.of(), false, 0, "", SharedKind.VIEW, "", xml);
    }

    @Test
    public void aLayoutGetsAFreeFileNameAndListsItsResources() {
        String xml = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"><ImageView android:src=\"@drawable/logo\"/><TextView android:text=\"@string/hello\"/></LinearLayout>";
        Plan plan = ImportPlanner.plan(xml("Login Screen", xml), ctx(Set.of(), Set.of("login_screen.xml", "login_screen_2.xml"), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertTrue(plan.canApply());
        assertEquals("login_screen_3.xml", plan.name());
        assertTrue(has(plan, Code.RENAMED, Severity.INFO));
        assertTrue(has(plan, Code.REFERENCES_RESOURCES, Severity.INFO));
        assertEquals(xml, plan.content());
    }

    @Test
    public void aLayoutCanReplaceTheFileOfTheSameName() {
        Plan plan = ImportPlanner.plan(xml("Main", "<a/>"), ctx(Set.of(), Set.of("main.xml"), Set.of(), 21), 160, 34, OnConflict.REPLACE);
        assertEquals("main.xml", plan.name());
        assertTrue(plan.replaces());
    }

    @Test
    public void brokenXmlCannotBeAdded() {
        assertFalse(ImportPlanner.plan(xml("Bad", "<a><b></a>"), ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME).canApply());
    }

    @Test
    public void layoutNamesAreValidResourceNames() {
        assertEquals("login_screen", ImportPlanner.layoutName("Login Screen"));
        assertEquals("shared_123", ImportPlanner.layoutName("123"));
        assertEquals("shared_", ImportPlanner.layoutName("éé"));
        assertEquals("a_b", ImportPlanner.layoutName("  A -- B  "));
        assertTrue(ImportPlanner.layoutName("x".repeat(100)).length() <= 40);
    }

    private static SharedBlock blocks(String name, String json, boolean raw) {
        return new SharedBlock("id", name, "", "u", "a", 1, 100, 1, List.of("if"), raw, 0, json);
    }

    @Test
    public void blocksAreAddedUnderAFreeNameToTheCollection() {
        String json = SharedBlockCodec.encode(new java.util.ArrayList<>(List.of(new com.besome.sketch.beans.BlockBean("1", "toast", " ", "doToast"))));
        Plan plan = ImportPlanner.plan(blocks("Toast", json, false), ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertEquals(Target.BLOCK_COLLECTION, plan.target());
        assertEquals("Toast (2)", plan.name());
        assertTrue(plan.canApply());
        assertTrue(plan.preview().contains("doToast"));
    }

    @Test
    public void anIdenticalStackIsFlaggedAsADuplicate() {
        String json = SharedBlockCodec.encode(new java.util.ArrayList<>(List.of(new com.besome.sketch.beans.BlockBean("1", "toast", " ", "doToast"))));
        Context context = new Context("", "", 0, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), List.of(json));
        assertTrue(has(ImportPlanner.plan(blocks("New", json, false), context, 160, 34, OnConflict.RENAME), Code.DUPLICATE_CONTENT, Severity.WARNING));
    }

    @Test
    public void rawJavaBlocksAreFlaggedWhateverTheAuthorSaid() {
        BlockBean rawBlock = new BlockBean("1", "", " ", "addSourceDirectly");
        rawBlock.parameters.add("Runtime.getRuntime();");
        String json = SharedBlockCodec.encode(new java.util.ArrayList<>(List.of(rawBlock)));
        Plan plan = ImportPlanner.plan(blocks("Sneaky", json, false), ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertTrue(has(plan, Code.RAW_JAVA, Severity.WARNING));
        assertTrue(plan.needsAttention());
    }

    @Test
    public void damagedBlocksCannotBeAdded() {
        Plan plan = ImportPlanner.plan(blocks("Broken", "not json", false), ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertFalse(plan.canApply());
        assertTrue(has(plan, Code.DAMAGED, Severity.BLOCKER));
    }

    private static final String COMPONENT = "[{\"name\":\"Chart\",\"id\":\"c9\",\"icon\":\"i\",\"varName\":\"x\",\"typeName\":\"X\",\"buildClass\":\"X\",\"class\":\"X\","
            + "\"description\":\"d\",\"url\":\"u\",\"additionalVar\":\"\",\"defineAdditionalVar\":\"\",\"imports\":\"\"}]";

    @Test
    public void aComponentWithANameInUseNeedsAChoice() {
        SharedBlock post = code(SharedKind.COMPONENT, "Chart", "", COMPONENT, 0, List.of());
        Plan blocked = ImportPlanner.plan(post, ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertFalse(blocked.canApply());
        assertTrue(has(blocked, Code.COMPONENT_EXISTS, Severity.BLOCKER));
        assertTrue(ImportPlanner.plan(post, ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.REPLACE).canApply());
    }

    @Test
    public void aFreshComponentIsSimplyAdded() {
        String fresh = COMPONENT.replace("\"Chart\"", "\"Gauge\"").replace("\"c9\"", "\"g1\"");
        Plan plan = ImportPlanner.plan(code(SharedKind.COMPONENT, "Gauge", "", fresh, 0, List.of()), ctx(Set.of(), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertTrue(plan.canApply());
        assertEquals("Gauge", plan.name());
        assertFalse(plan.replaces());
    }

    @Test
    public void issuesComeBlockersFirst() {
        SharedBlock post = code(SharedKind.JAVA, "Helper", "Helper.java", JAVA, 28, List.of("zzz"));
        Plan plan = ImportPlanner.plan(post, ctx(Set.of("Helper.java"), Set.of(), Set.of(), 21), 160, 34, OnConflict.RENAME);
        assertEquals(Severity.BLOCKER, plan.issues().get(0).severity());
    }
}
