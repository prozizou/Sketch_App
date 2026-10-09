package pro.sketchware.editor.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class ArrangeTest {
    @Test
    public void alignLeftUsesTheSelectionBounds() {
        List<Box> boxes = List.of(new Box(10, 0, 50, 20), new Box(30, 40, 90, 60));
        List<Arrange.Offset> offsets = Arrange.align(boxes, Arrange.Align.LEFT);
        assertEquals(0f, offsets.get(0).dx(), 0.001f);
        assertEquals(-20f, offsets.get(1).dx(), 0.001f);
        assertEquals(0f, offsets.get(1).dy(), 0.001f);
    }

    @Test
    public void alignCentreAndBottom() {
        List<Box> boxes = List.of(new Box(0, 0, 100, 20), new Box(20, 50, 40, 90));
        assertEquals(20f, Arrange.align(boxes, Arrange.Align.CENTER_HORIZONTAL).get(1).dx(), 0.001f); // 50 - 30
    }

    @Test
    public void alignBottomMovesTheHigherBoxDown() {
        List<Box> boxes = List.of(new Box(0, 0, 100, 20), new Box(20, 50, 40, 90));
        List<Arrange.Offset> offsets = Arrange.align(boxes, Arrange.Align.BOTTOM);
        assertEquals(70f, offsets.get(0).dy(), 0.001f);
        assertTrue(offsets.get(1).isZero());
    }

    @Test
    public void distributeGivesEqualGapsAndKeepsTheEnds() {
        List<Box> boxes = List.of(new Box(0, 0, 20, 10), new Box(30, 0, 50, 10), new Box(100, 0, 120, 10));
        List<Arrange.Offset> offsets = Arrange.distribute(boxes, Axis.X);
        assertTrue(offsets.get(0).isZero());
        assertTrue(offsets.get(2).isZero());
        assertEquals(20f, offsets.get(1).dx(), 0.001f); // gaps of 30: the middle box moves from 30 to 50
    }

    @Test
    public void distributeWorksOnUnorderedInput() {
        List<Box> boxes = List.of(new Box(100, 0, 120, 10), new Box(0, 0, 20, 10), new Box(30, 0, 50, 10));
        List<Arrange.Offset> offsets = Arrange.distribute(boxes, Axis.X);
        assertTrue(offsets.get(0).isZero());
        assertTrue(offsets.get(1).isZero());
        assertEquals(20f, offsets.get(2).dx(), 0.001f);
    }

    @Test
    public void fewerThanThreeBoxesAreNotMoved() {
        List<Arrange.Offset> offsets = Arrange.distribute(List.of(new Box(0, 0, 10, 10), new Box(50, 0, 60, 10)), Axis.X);
        assertTrue(offsets.stream().allMatch(Arrange.Offset::isZero));
    }
}
