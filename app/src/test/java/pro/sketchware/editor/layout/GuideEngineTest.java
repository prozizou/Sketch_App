package pro.sketchware.editor.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class GuideEngineTest {
    private static final Box PARENT = new Box(0, 0, 360, 640);

    @Test
    public void reportsAlignedLeftEdgesOnce() {
        Box target = new Box(16, 100, 116, 140);
        List<Box> others = List.of(new Box(16, 200, 200, 240), new Box(16, 300, 100, 340));
        List<GuideEngine.Guide> guides = GuideEngine.alignments(target, others, PARENT, 0.5f);
        long onLeft = guides.stream().filter(g -> g.axis() == Axis.X && g.position() == 16f).count();
        assertEquals(1, onLeft);
        GuideEngine.Guide guide = guides.stream().filter(g -> g.position() == 16f).findFirst().orElseThrow();
        assertEquals(100f, guide.from(), 0.001f);
        assertEquals(340f, guide.to(), 0.001f);
        assertFalse(guide.parent());
    }

    @Test
    public void centreOfTargetLinesUpWithCentreOfParent() {
        Box target = new Box(130, 50, 230, 90); // centre 180 = parent's centre
        List<GuideEngine.Guide> guides = GuideEngine.alignments(target, List.of(), PARENT, 0.5f);
        assertEquals(1, guides.size());
        assertEquals(GuideEngine.Feature.CENTER, guides.get(0).feature());
        assertTrue(guides.get(0).parent());
    }

    @Test
    public void toleranceDecidesWhatCountsAsAligned() {
        Box target = new Box(18, 10, 60, 40);
        List<Box> others = List.of(new Box(16, 100, 80, 120));
        assertEquals(0, GuideEngine.alignments(target, others, null, 1f).size());
        assertEquals(1, GuideEngine.alignments(target, others, null, 2f).size());
    }

    @Test
    public void gapsToNeighboursAndParent() {
        Box target = new Box(100, 100, 200, 140);
        List<Box> others = List.of(new Box(0, 100, 80, 140),      // left neighbour, gap 20
                new Box(220, 90, 300, 150),                       // right neighbour, gap 20
                new Box(100, 10, 200, 60));                      // above, gap 40
        List<GuideEngine.Gap> gaps = GuideEngine.gaps(target, others, PARENT);
        assertGap(gaps, GuideEngine.Side.LEFT, 20f, false);
        assertGap(gaps, GuideEngine.Side.RIGHT, 20f, false);
        assertGap(gaps, GuideEngine.Side.TOP, 40f, false);
        assertGap(gaps, GuideEngine.Side.BOTTOM, 500f, true);
    }

    @Test
    public void aBoxNotFacingTheTargetIsIgnored() {
        Box target = new Box(100, 100, 200, 140);
        List<Box> others = List.of(new Box(0, 300, 80, 340)); // not level with the target
        List<GuideEngine.Gap> gaps = GuideEngine.gaps(target, others, PARENT);
        assertGap(gaps, GuideEngine.Side.LEFT, 100f, true);
    }

    @Test
    public void targetOutsideItsParentHasNoGapOnThatSide() {
        Box target = new Box(-10, 100, 50, 140);
        List<GuideEngine.Gap> gaps = GuideEngine.gaps(target, List.of(), PARENT);
        assertTrue(gaps.stream().noneMatch(g -> g.side() == GuideEngine.Side.LEFT));
    }

    @Test
    public void snapsToANeighboursEdgeWithinTheThreshold() {
        Box moving = new Box(21, 100, 121, 140);
        List<Box> others = List.of(new Box(16, 200, 100, 240));
        GuideEngine.Snap snap = GuideEngine.snapMove(moving, others, null, 0f, 8f);
        assertEquals(-5f, snap.dx(), 0.001f);
        assertEquals(0f, snap.dy(), 0.001f);
        assertFalse(snap.guides().isEmpty());
    }

    @Test
    public void doesNotSnapBeyondTheThreshold() {
        Box moving = new Box(40, 100, 140, 140);
        List<Box> others = List.of(new Box(16, 200, 100, 240));
        GuideEngine.Snap snap = GuideEngine.snapMove(moving, others, null, 0f, 8f);
        assertEquals(0f, snap.dx(), 0.001f);
    }

    @Test
    public void snapsToTheGridWhenNothingIsNearer() {
        Box moving = new Box(19, 37, 119, 77);
        GuideEngine.Snap snap = GuideEngine.snapMove(moving, List.of(), null, 8f, 4f);
        assertEquals(-3f, snap.dx(), 0.001f);  // 19 -> 16
        assertEquals(3f, snap.dy(), 0.001f);   // 37 -> 40
    }

    @Test
    public void aNeighbourBeatsTheGridWhenCloser() {
        Box moving = new Box(18, 100, 118, 140);   // grid says 16 (delta -2); neighbour edge at 19 (delta +1)
        List<Box> others = List.of(new Box(19, 300, 80, 340));
        GuideEngine.Snap snap = GuideEngine.snapMove(moving, others, null, 8f, 4f);
        assertEquals(1f, snap.dx(), 0.001f);
    }

    @Test
    public void readingOrderIsTopThenLeft() {
        List<Box> sorted = GuideEngine.inReadingOrder(List.of(new Box(50, 100, 60, 110), new Box(10, 100, 20, 110), new Box(0, 0, 5, 5)));
        assertEquals(0f, sorted.get(0).top(), 0f);
        assertEquals(10f, sorted.get(1).left(), 0f);
    }

    private static void assertGap(List<GuideEngine.Gap> gaps, GuideEngine.Side side, float size, boolean toParent) {
        GuideEngine.Gap gap = gaps.stream().filter(g -> g.side() == side).findFirst().orElseThrow();
        assertEquals(size, gap.size(), 0.001f);
        assertEquals(toParent, gap.toParent());
    }
}
