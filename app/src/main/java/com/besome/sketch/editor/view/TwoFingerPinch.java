package com.besome.sketch.editor.view;

import android.content.Context;
import android.view.ScaleGestureDetector;

/** Pinch detection shared by the View and Logic editors: zooming always takes two fingers. */
public final class TwoFingerPinch {
    private TwoFingerPinch() {
    }

    /**
     * "Quick scale" (double tap, then drag with one finger) is on by default in {@link ScaleGestureDetector}; it
     * turned quick taps and drags on widgets and blocks into zooming, so it is turned off.
     */
    public static ScaleGestureDetector create(Context context, ScaleGestureDetector.OnScaleGestureListener listener) {
        ScaleGestureDetector detector = new ScaleGestureDetector(context, listener);
        detector.setQuickScaleEnabled(false);
        return detector;
    }
}
