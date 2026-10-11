package com.besome.sketch.editor.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The View and Logic editors zoom with two fingers only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class TwoFingerPinchTest {
    private int scales;

    private ScaleGestureDetector detector() {
        Context context = ApplicationProvider.getApplicationContext();
        return TwoFingerPinch.create(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scales++;
                return true;
            }
        });
    }

    private static MotionEvent event(long downTime, long time, int action, float x, float y) {
        return MotionEvent.obtain(downTime, time, action, x, y, 0);
    }

    @Test
    public void quickScaleIsOff() {
        assertFalse(detector().isQuickScaleEnabled());
    }

    /** Double tap, then drag with the same finger: what used to zoom the preview while moving widgets. */
    @Test
    public void doubleTapThenDragWithOneFingerDoesNotZoom() {
        ScaleGestureDetector detector = detector();
        long t = SystemClock.uptimeMillis();
        detector.onTouchEvent(event(t, t, MotionEvent.ACTION_DOWN, 300, 600));
        detector.onTouchEvent(event(t, t + 50, MotionEvent.ACTION_UP, 300, 600));
        long t2 = t + 120;
        detector.onTouchEvent(event(t2, t2, MotionEvent.ACTION_DOWN, 300, 600));
        for (int i = 1; i <= 10; i++) {
            detector.onTouchEvent(event(t2, t2 + i * 16L, MotionEvent.ACTION_MOVE, 300, 600 + i * 40));
        }
        detector.onTouchEvent(event(t2, t2 + 200, MotionEvent.ACTION_UP, 300, 1000));
        assertFalse(detector.isInProgress());
        assertEquals(0, scales);
    }
}
