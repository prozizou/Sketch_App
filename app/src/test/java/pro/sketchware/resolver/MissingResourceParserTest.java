package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import pro.sketchware.resolver.MissingResource.Occurrence;

public class MissingResourceParserTest {
    private static Map<String, MissingResource> byKey(List<MissingResource> list) {
        return list.stream().collect(Collectors.toMap(MissingResource::key, r -> r, (a, b) -> a, java.util.LinkedHashMap::new));
    }

    @Test
    public void findsEveryTypeInTheRealLayoutStageLog() {
        List<MissingResource> found = MissingResourceParser.parse(Fixtures.read("aapt2-link-layout.txt"));
        Map<String, MissingResource> map = byKey(found);

        assertEquals(List.of("color/missing_bg", "drawable/ic_missing", "mipmap/ic_gone", "string/hello_missing",
                "android:drawable/nonexistent_framework".replace("android:", ""), "style/MissingStyle", "dimen/missing_size"),
                found.stream().map(MissingResource::key).toList());

        MissingResource color = map.get("color/missing_bg");
        assertEquals("the same colour used twice is one entry with two places", 2, color.occurrences().size());
        assertEquals(2, color.occurrences().get(0).line());
        assertEquals(10, color.occurrences().get(1).line());
        assertEquals("com.my.app", color.packageName());
        assertTrue(color.occurrences().get(0).path().endsWith("/files/resource/layout/main.xml"));

        assertEquals(2, map.get("dimen/missing_size").occurrences().size());
        assertEquals(6, map.get("drawable/ic_missing").occurrences().get(0).line());
    }

    @Test
    public void theTappableRegionIsTheMessageNotThePath() {
        String log = Fixtures.read("aapt2-link-layout.txt");
        Occurrence o = MissingResourceParser.parse(log).get(1).occurrences().get(0);
        assertEquals("error: resource drawable/ic_missing (aka com.my.app:drawable/ic_missing) not found.", log.substring(o.start(), o.end()));
    }

    @Test
    public void findsTheManifestAndStylesStages() {
        List<MissingResource> manifest = MissingResourceParser.parse(Fixtures.read("aapt2-link-manifest.txt"));
        assertEquals(List.of("mipmap/ic_launcher", "string/app_name"), manifest.stream().map(MissingResource::key).toList());
        assertEquals("AndroidManifest.xml", manifest.get(0).occurrences().get(0).path());
        assertEquals(3, manifest.get(0).occurrences().get(0).line());

        List<MissingResource> styles = MissingResourceParser.parse(Fixtures.read("aapt2-link-styles.txt"));
        assertEquals("a colour used by two lines, and an unknown attribute", List.of("color/brand", "attr/colorAccentX"),
                styles.stream().map(MissingResource::key).toList());
        assertEquals("AAPT2 printed line 3 twice: both log lines stay tappable", 3, styles.get(0).occurrences().size());
        assertEquals("but it is two places", 2, styles.get(0).distinctLocations().size());
    }

    @Test
    public void frameworkResourcesAndAttributesAreReportedButNotFixable() {
        String log = "/p/res/layout/a.xml:16: error: resource android:drawable/nonexistent_framework not found.\n"
                + "/p/res/values/s.xml:4: error: style attribute 'attr/colorAccentX (aka com.my.app:attr/colorAccentX)' not found.\n";
        List<MissingResource> found = MissingResourceParser.parse(log);
        assertEquals(2, found.size());
        assertTrue(found.get(0).framework());
        assertFalse(found.get(0).fixable());
        assertEquals(MissingResource.Reason.FRAMEWORK, found.get(0).unfixableReason());
        assertEquals("attr", found.get(1).type());
        assertEquals(MissingResource.Reason.UNSUPPORTED_TYPE, found.get(1).unfixableReason());
    }

    @Test
    public void supportedTypesAreFixableAndOthersAreOnlyReported() {
        Map<String, MissingResource> map = byKey(MissingResourceParser.parse(Fixtures.read("aapt2-link-layout.txt")));
        for (String key : new String[]{"color/missing_bg", "drawable/ic_missing", "mipmap/ic_gone", "string/hello_missing", "dimen/missing_size"}) {
            assertTrue(key, map.get(key).fixable());
            assertNull(key, map.get(key).unfixableReason());
        }
        assertFalse(map.get("style/MissingStyle").fixable());
        assertEquals(MissingResource.Reason.UNSUPPORTED_TYPE, map.get("style/MissingStyle").unfixableReason());
    }

    @Test
    public void aFileResourceNameThatAaptCannotCreateIsNotFixable() {
        MissingResource dotted = MissingResourceParser.parse("/p/a.xml:3: error: resource drawable/ic.add (aka x:drawable/ic.add) not found.").get(0);
        assertFalse("AAPT2 refuses a '.' in a file name", dotted.fixable());
        assertEquals(MissingResource.Reason.INVALID_NAME, dotted.unfixableReason());
        MissingResource value = MissingResourceParser.parse("/p/a.xml:3: error: resource string/app.title (aka x:string/app.title) not found.").get(0);
        assertTrue("a '.' is fine in a value name", value.fixable());
    }

    @Test
    public void aLineWithoutLocationOrAkaStillCounts() {
        List<MissingResource> found = MissingResourceParser.parse("error: resource color/accent not found.\n");
        assertEquals(1, found.size());
        assertEquals("", found.get(0).occurrences().get(0).path());
        assertEquals(0, found.get(0).occurrences().get(0).line());
        assertEquals("", found.get(0).packageName());
    }

    @Test
    public void findsResourcesJavaCodeAsksFor() {
        String log = "----------\n"
                + "1. ERROR in /sdcard/.sketch_nws/data/605/files/java/com/my/app/MainActivity.java (at line 42)\n"
                + "\timageView.setImageResource(R.drawable.ic_add);\n"
                + "\t                                       ^^^^^^\n"
                + "ic_add cannot be resolved or is not a field\n"
                + "----------\n"
                + "2. ERROR in /sdcard/x/Other.java (at line 7)\n"
                + "\tint c = getColor(R.color.brand) + R.mipmap.ic_launcher;\n"
                + "\t                   ^^^^^\n"
                + "brand cannot be resolved or is not a field\n"
                + "----------\n"
                + "3. ERROR in /sdcard/x/Third.java (at line 9)\n"
                + "\tint x = R.mipmap.ic_launcher;\n"
                + "\t          ^^^^^^\n"
                + "mipmap cannot be resolved or is not a field\n"
                + "----------\n"
                + "4. ERROR in /sdcard/x/Other.java (at line 9)\n"
                + "\tfoo.bar();\n\t    ^^^\nThe method bar() is undefined for the type Foo\n----------\n";
        List<MissingResource> found = MissingResourceParser.parse(log);
        assertEquals(List.of("drawable/ic_add", "color/brand", "mipmap/ic_launcher"), found.stream().map(MissingResource::key).toList());
        MissingResource drawable = found.get(0);
        assertEquals(42, drawable.occurrences().get(0).line());
        assertEquals("ic_add cannot be resolved or is not a field", log.substring(drawable.occurrences().get(0).start(), drawable.occurrences().get(0).end()));
        assertTrue(drawable.occurrences().get(0).path().endsWith("MainActivity.java"));
    }

    @Test
    public void javaAndAaptReportsOfTheSameResourceAreMerged() {
        String log = "/p/res/layout/a.xml:5: error: resource drawable/ic_add (aka x:drawable/ic_add) not found.\n"
                + "----------\n1. ERROR in /p/Main.java (at line 3)\n\tint a = R.drawable.ic_add;\n\t                  ^^^^^^\nic_add cannot be resolved or is not a field\n----------\n";
        List<MissingResource> found = MissingResourceParser.parse(log);
        assertEquals(1, found.size());
        assertEquals(2, found.get(0).occurrences().size());
        assertEquals("x", found.get(0).packageName());
    }

    @Test
    public void unrelatedLogsGiveNothing() {
        assertTrue(MissingResourceParser.parse(null).isEmpty());
        assertTrue(MissingResourceParser.parse("").isEmpty());
        assertTrue(MissingResourceParser.parse("java.lang.RuntimeException: boom\n\tat a.b(C.java:5)").isEmpty());
        assertTrue(MissingResourceParser.parse("/p/a.xml:3: error: attribute android:foo not found.\n").isEmpty());
        assertTrue("an ECJ error about something else is not a resource",
                MissingResourceParser.parse("----------\n1. ERROR in /p/A.java (at line 2)\n\tR.foo.bar;\n\t^\nbar cannot be resolved or is not a field\n----------\n").isEmpty());
    }

    @Test
    public void namesWithDotsAndDashesAreKept() {
        MissingResource theme = MissingResourceParser.parse("/p/a.xml:3: error: resource style/Theme.MyApp (aka x:style/Theme.MyApp) not found.").get(0);
        assertEquals("Theme.MyApp", theme.name());
        assertNotNull(theme.key());
    }
}
