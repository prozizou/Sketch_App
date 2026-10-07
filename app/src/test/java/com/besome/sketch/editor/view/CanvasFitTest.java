package com.besome.sketch.editor.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CanvasFitTest {
    private static final int VIEW_W = 1000;
    private static final int VIEW_H = 2000;

    @Test
    public void bigContentIsShrunkSoItFitsWithPadding() {
        CanvasFit.Result fit = CanvasFit.compute(100, 100, 2100, 1100, VIEW_W, VIEW_H, 20, 0.25f, 1f);

        // 2000 wide content in 960 available: 0.48
        assertEquals(0.48f, fit.zoom(), 0.001f);
        assertEquals(Math.round(100 * 0.48f - 20), fit.scrollX());
    }

    @Test
    public void everyBlockEndsUpInsideTheView() {
        float left = 300, top = 500, right = 3300, bottom = 4500;
        CanvasFit.Result fit = CanvasFit.compute(left, top, right, bottom, VIEW_W, VIEW_H, 20, 0.25f, 1f);

        assertTrue(left * fit.zoom() - fit.scrollX() >= 0);
        assertTrue(top * fit.zoom() - fit.scrollY() >= 0);
        assertTrue(right * fit.zoom() - fit.scrollX() <= VIEW_W);
        assertTrue(bottom * fit.zoom() - fit.scrollY() <= VIEW_H);
    }

    @Test
    public void smallContentIsNeverEnlargedPastTheLimit() {
        CanvasFit.Result fit = CanvasFit.compute(200, 200, 400, 300, VIEW_W, VIEW_H, 20, 0.25f, 1f);

        assertEquals(1f, fit.zoom(), 0.0001f);
    }

    @Test
    public void zoomNeverGoesBelowTheMinimum() {
        CanvasFit.Result fit = CanvasFit.compute(0, 0, 40000, 40000, VIEW_W, VIEW_H, 20, 0.25f, 1f);

        assertEquals(0.25f, fit.zoom(), 0.0001f);
    }

    @Test
    public void scrollIsNeverNegative() {
        CanvasFit.Result fit = CanvasFit.compute(0, 0, 200, 100, VIEW_W, VIEW_H, 20, 0.25f, 1f);

        assertTrue(fit.scrollX() >= 0);
        assertTrue(fit.scrollY() >= 0);
    }
}
