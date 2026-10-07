package com.besome.sketch.editor.view;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class FloatingBarDraggerTest {
    @Test
    public void usesTheDefaultUntilTheUserMovesTheBar() {
        float[] xy = FloatingBarDragger.position(-1, -1, 1000, 2000, 200, 60, 780, 150);

        assertArrayEquals(new float[]{780, 150}, xy, 0.001f);
    }

    @Test
    public void savedFractionsMapOntoTheFreeSpace() {
        float[] xy = FloatingBarDragger.position(0.5f, 1f, 1000, 2000, 200, 60, 780, 150);

        assertArrayEquals(new float[]{400, 1940}, xy, 0.001f);
    }

    @Test
    public void positionAlwaysStaysInsideTheParent() {
        // A parent smaller than before (rotation, split screen) must not push the bar out of sight.
        float[] xy = FloatingBarDragger.position(-1, -1, 300, 400, 200, 60, 780, 900);

        assertArrayEquals(new float[]{100, 340}, xy, 0.001f);
    }

    @Test
    public void fractionIsClampedToZeroAndOne() {
        assertEquals(0f, FloatingBarDragger.fraction(-50, 800), 0f);
        assertEquals(1f, FloatingBarDragger.fraction(900, 800), 0f);
        assertEquals(0.25f, FloatingBarDragger.fraction(200, 800), 0f);
        assertEquals(0f, FloatingBarDragger.fraction(10, 0), 0f);
    }

    private static MotionEvent event(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, action, x, y, 0);
    }

    private static void layout(FrameLayout parent) {
        parent.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1000, 2000);
    }

    private static FrameLayout.LayoutParams size(int w, int h) {
        return new FrameLayout.LayoutParams(w, h);
    }

    @Test
    public void draggedPositionIsRememberedForTheNextTime() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("logic_editor_ui", Context.MODE_PRIVATE).edit().clear().commit();

        FrameLayout parent = new FrameLayout(context);
        View bar = new FrameLayout(context);
        View handle = new View(context);
        parent.addView(bar, size(200, 60));
        parent.addView(handle, size(10, 10));
        new FloatingBarDragger(bar, handle, parent, "test_bar", 12, 150);
        layout(parent);
        assertEquals("starts at the default spot", 788f, bar.getX(), 0.5f);

        // Drag the bar to the middle-left of the screen.
        handle.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 800, 160));
        handle.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 300, 1000));
        handle.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 300, 1000));
        float movedX = bar.getX();
        float movedY = bar.getY();

        // A brand new screen with the same bar finds it where it was left.
        FrameLayout parent2 = new FrameLayout(context);
        View bar2 = new FrameLayout(context);
        View handle2 = new View(context);
        parent2.addView(bar2, size(200, 60));
        parent2.addView(handle2, size(10, 10));
        new FloatingBarDragger(bar2, handle2, parent2, "test_bar", 12, 150);
        layout(parent2);

        assertEquals(movedX, bar2.getX(), 1f);
        assertEquals(movedY, bar2.getY(), 1f);
    }

    @Test
    public void barCannotBeDraggedOutOfTheScreen() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("logic_editor_ui", Context.MODE_PRIVATE).edit().clear().commit();
        FrameLayout parent = new FrameLayout(context);
        View bar = new FrameLayout(context);
        View handle = new View(context);
        parent.addView(bar, size(200, 60));
        parent.addView(handle, size(10, 10));
        new FloatingBarDragger(bar, handle, parent, "test_bar2", 12, 150);
        layout(parent);

        handle.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 800, 160));
        handle.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 5000, -5000));

        assertEquals(800f, bar.getX(), 0.5f);
        assertEquals(0f, bar.getY(), 0.5f);
    }
}
