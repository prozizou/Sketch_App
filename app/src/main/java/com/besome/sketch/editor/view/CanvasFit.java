package com.besome.sketch.editor.view;

/**
 * Works out the zoom and scroll position that bring a rectangle of blocks fully into view.
 * Pure arithmetic, kept apart from the view so it can be tested on its own.
 */
public final class CanvasFit {
    private CanvasFit() {
    }

    /** Zoom to apply and where to scroll to (in zoomed pixels, from the top-left of the canvas). */
    public record Result(float zoom, int scrollX, int scrollY) {
    }

    /**
     * @param left       left edge of the blocks, in unzoomed canvas pixels
     * @param top        top edge of the blocks
     * @param right      right edge of the blocks
     * @param bottom     bottom edge of the blocks
     * @param viewWidth  width of the visible canvas area
     * @param viewHeight height of the visible canvas area
     * @param padding    free space kept around the blocks
     * @param minZoom    smallest zoom allowed
     * @param maxZoom    largest zoom allowed; fitting never enlarges blocks beyond it
     */
    public static Result compute(float left, float top, float right, float bottom,
                                 int viewWidth, int viewHeight, float padding,
                                 float minZoom, float maxZoom) {
        float contentWidth = Math.max(1f, right - left);
        float contentHeight = Math.max(1f, bottom - top);
        float availableWidth = Math.max(1f, viewWidth - 2 * padding);
        float availableHeight = Math.max(1f, viewHeight - 2 * padding);

        float zoom = Math.min(availableWidth / contentWidth, availableHeight / contentHeight);
        zoom = Math.max(minZoom, Math.min(maxZoom, zoom));

        // Centre the blocks when they are smaller than the view, otherwise start at the top-left corner.
        float scaledWidth = contentWidth * zoom;
        float scaledHeight = contentHeight * zoom;
        float scrollX = left * zoom - (scaledWidth < availableWidth ? (viewWidth - scaledWidth) / 2f : padding);
        float scrollY = top * zoom - (scaledHeight < availableHeight ? (viewHeight - scaledHeight) / 2f : padding);
        return new Result(zoom, Math.max(0, Math.round(scrollX)), Math.max(0, Math.round(scrollY)));
    }
}
