package pro.sketchware.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DesignSystemCheckerTest {
    private static ViewFacts view(String id, int textSize, Integer textColor, Integer background, int margin, int padding) {
        return new ViewFacts("main", id, ViewFacts.Kind.TEXT, ViewFacts.WRAP_CONTENT, ViewFacts.WRAP_CONTENT, false, true,
                textColor, background, textSize, margin, margin, padding, padding, false);
    }

    private static boolean has(List<Finding> findings, String id) {
        return findings.stream().anyMatch(f -> f.id().equals(id));
    }

    @Test
    public void aCleanScreenHasNoFinding() {
        assertTrue(DesignSystemChecker.analyze(List.of(view("a", 16, 0xff202020, 0xffffffff, 16, 8))).isEmpty());
    }

    @Test
    public void flagsSpacingOffTheRhythm() {
        List<Finding> findings = DesignSystemChecker.analyze(List.of(view("a", 16, null, null, 15, 8), view("b", 16, null, null, 16, 8)));
        assertTrue(has(findings, "design.spacing-off-scale"));
        Finding f = findings.stream().filter(x -> x.id().equals("design.spacing-off-scale")).findFirst().orElseThrow();
        assertTrue(f.evidence().contains("main/a"));
        assertTrue(!f.evidence().contains("main/b"));
    }

    @Test
    public void zeroSpacingIsFine() {
        assertTrue(DesignSystemChecker.analyze(List.of(view("a", 16, null, null, 0, 0))).isEmpty());
    }

    @Test
    public void flagsTextSizesOutsideTheTypeScale() {
        List<Finding> findings = DesignSystemChecker.analyze(List.of(view("a", 15, null, null, 8, 8)));
        assertTrue(has(findings, "design.text-size-off-scale"));
    }

    @Test
    public void flagsTooManyTextSizes() {
        List<ViewFacts> views = new ArrayList<>();
        int[] sizes = {11, 12, 14, 16, 22, 24};
        for (int i = 0; i < sizes.length; i++) views.add(view("v" + i, sizes[i], null, null, 8, 8));
        assertTrue(has(DesignSystemChecker.analyze(views), "design.too-many-text-sizes"));
    }

    @Test
    public void flagsTooManyColoursAndNearDuplicates() {
        List<ViewFacts> views = new ArrayList<>();
        for (int i = 0; i < 9; i++) views.add(view("v" + i, 16, 0xff000000 | (i * 0x1a1a1a), null, 8, 8));
        assertTrue(has(DesignSystemChecker.analyze(views), "design.too-many-colors"));
        List<Finding> twins = DesignSystemChecker.analyze(List.of(view("a", 16, 0xff336699, null, 8, 8), view("b", 16, 0xff346699, null, 8, 8)));
        assertTrue(has(twins, "design.near-duplicate-colors"));
    }

    @Test
    public void transparentColoursAreNotCounted() {
        List<Finding> findings = DesignSystemChecker.analyze(List.of(view("a", 16, 0x80336699, 0x80346699, 8, 8)));
        assertEquals(0, findings.size());
    }

    @Test
    public void identicalColoursAreNotDuplicates() {
        assertTrue(DesignSystemChecker.nearDuplicates(List.of(0x336699, 0xffffff)).isEmpty());
    }
}
