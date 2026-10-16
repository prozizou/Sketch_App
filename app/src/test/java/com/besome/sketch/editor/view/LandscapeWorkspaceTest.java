package com.besome.sketch.editor.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.besome.sketch.editor.view.LandscapeWorkspace.Zoom;

public class LandscapeWorkspaceTest {
    @Test
    public void onlyWideLandscapeScreensGetTheWorkspace() {
        assertTrue(LandscapeWorkspace.isEnabled(true, 800));
        assertTrue(LandscapeWorkspace.isEnabled(true, LandscapeWorkspace.MIN_WIDTH_DP));
        assertFalse(LandscapeWorkspace.isEnabled(true, 480));
        assertFalse(LandscapeWorkspace.isEnabled(false, 1000));
    }

    @Test
    public void propertiesWidthStaysInItsLimits() {
        assertEquals(LandscapeWorkspace.PROPERTIES_MIN_DP, LandscapeWorkspace.clampPropertiesDp(10f, 900f, 72f), 0f);
        assertEquals(LandscapeWorkspace.PROPERTIES_MAX_DP, LandscapeWorkspace.clampPropertiesDp(900f, 1400f, 72f), 0f);
        assertEquals(320f, LandscapeWorkspace.clampPropertiesDp(320f, 900f, 72f), 0f);
    }

    @Test
    public void propertiesNeverTakeTheRoomTheRestNeeds() {
        // 640dp wide: 72 palette + 200 preview leaves 368 at most
        assertEquals(368f, LandscapeWorkspace.clampPropertiesDp(460f, 640f, 72f), 0f);
        // even on a screen so narrow that the room is below the minimum, the panel keeps its minimum
        assertEquals(LandscapeWorkspace.PROPERTIES_MIN_DP, LandscapeWorkspace.clampPropertiesDp(300f, 480f, 140f), 0f);
    }

    @Test
    public void fitUsesTheTighterSide() {
        // 1000x500 area with 10 margins, content 2000x1000 -> 0.49 by width and 0.48 by height
        assertEquals(0.48f, LandscapeWorkspace.fitScale(1000, 500, 2000, 1000, 10, 10), 1e-4f);
        assertEquals(1f, LandscapeWorkspace.fitScale(100, 100, 0, 0, 0, 0), 0f);
    }

    @Test
    public void fixedZoomsAreOfTheRealSizeAndFitFollowsTheArea() {
        assertEquals(0.37f, LandscapeWorkspace.scaleFor(Zoom.FIT, 0.37f), 0f);
        assertEquals(0.5f, LandscapeWorkspace.scaleFor(Zoom.PERCENT_50, 0.37f), 0f);
        assertEquals(0.75f, LandscapeWorkspace.scaleFor(Zoom.PERCENT_75, 0.9f), 0f);
        assertEquals(1f, LandscapeWorkspace.scaleFor(Zoom.PERCENT_100, 0.1f), 0f);
    }

    @Test
    public void pinchZoomIsClamped() {
        assertEquals(LandscapeWorkspace.ZOOM_MIN, LandscapeWorkspace.clampZoom(0.01f), 0f);
        assertEquals(LandscapeWorkspace.ZOOM_MAX, LandscapeWorkspace.clampZoom(9f), 0f);
        assertEquals(0.8f, LandscapeWorkspace.clampZoom(0.8f), 0f);
    }

    @Test
    public void smallContentIsCentredWhateverThePan() {
        assertEquals(250f, LandscapeWorkspace.origin(1000, 500, 10, 0), 0f);
        assertEquals(250f, LandscapeWorkspace.origin(1000, 500, 10, 300), 0f);
        assertEquals(0f, LandscapeWorkspace.clampPan(1000, 500, 10, 300), 0f);
    }

    @Test
    public void largeContentCanBePannedButNotLost() {
        // area 1000, content 1600: centred origin is -300, limits are +10 and 1000-1600-10=-610
        assertEquals(-300f, LandscapeWorkspace.origin(1000, 1600, 10, 0), 0f);
        assertEquals(10f, LandscapeWorkspace.origin(1000, 1600, 10, 9999), 0f);
        assertEquals(-610f, LandscapeWorkspace.origin(1000, 1600, 10, -9999), 0f);
        assertEquals(310f, LandscapeWorkspace.clampPan(1000, 1600, 10, 9999), 0f);
        assertEquals(-310f, LandscapeWorkspace.clampPan(1000, 1600, 10, -9999), 0f);
        assertEquals(40f, LandscapeWorkspace.clampPan(1000, 1600, 10, 40f), 0f);
    }

    @Test
    public void zoomKeysRoundTripAndUnknownMeansFit() {
        for (Zoom zoom : Zoom.values()) assertEquals(zoom, Zoom.fromKey(zoom.name()));
        assertEquals(Zoom.FIT, Zoom.fromKey("nonsense"));
        assertEquals(Zoom.FIT, Zoom.fromKey(null));
    }
}
