package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class RebuildReportTest {
    private static List<MissingResource> log(String text) {
        return MissingResourceParser.parse(text);
    }

    @Test
    public void whatTheNewLogNoLongerAsksForIsFixed() {
        String after = "/p/a.xml:9: error: resource color/brand (aka x:color/brand) not found.\n";
        RebuildReport report = RebuildReport.compare(List.of("drawable/ic_add", "color/brand", "string/title"), log(after));
        assertEquals(List.of("drawable/ic_add", "string/title"), report.fixed());
        assertEquals(List.of("color/brand"), report.stillMissing());
        assertTrue(report.newlyMissing().isEmpty());
        assertFalse(report.allGone());
    }

    @Test
    public void theNextStageOfAaptShowsUpAsNew() {
        String after = Fixtures.read("aapt2-link-layout.log");
        RebuildReport report = RebuildReport.compare(List.of("mipmap/ic_launcher", "string/app_name"), log(after));
        assertEquals("the manifest's resources are no longer reported", List.of("mipmap/ic_launcher", "string/app_name"), report.fixed());
        assertTrue(report.stillMissing().isEmpty());
        assertTrue(report.newlyMissing().contains("drawable/ic_missing"));
        assertFalse("new errors mean it isn't over", report.allGone());
    }

    @Test
    public void aCleanBuildMeansAllGone() {
        RebuildReport report = RebuildReport.compare(List.of("drawable/a", "color/b"), log(""));
        assertEquals(List.of("drawable/a", "color/b"), report.fixed());
        assertTrue(report.allGone());
        assertTrue(RebuildReport.compare(List.of(), log("")).allGone());
    }

    @Test
    public void aLogWithOtherErrorsIsNotCountedAsFixed() {
        // The Java error means the build is not clean; the report only speaks about resources.
        RebuildReport report = RebuildReport.compare(List.of("drawable/a"), log("----------\n1. ERROR in /p/A.java (at line 2)\n\tfoo();\n\t^\nThe method foo() is undefined\n----------\n"));
        assertEquals(List.of("drawable/a"), report.fixed());
        assertTrue(report.allGone());
    }

    @Test
    public void duplicatesInTheBeforeListCountOnce() {
        RebuildReport report = RebuildReport.compare(List.of("color/a", "color/a"), log(""));
        assertEquals(List.of("color/a"), report.fixed());
    }
}
