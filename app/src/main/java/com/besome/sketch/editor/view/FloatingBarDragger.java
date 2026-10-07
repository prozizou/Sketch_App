package com.besome.sketch.editor.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Lets the user drag a floating bar around its parent by a handle, and remembers where it was left.
 * The position is stored as a fraction of the free space, so it stays sensible after a rotation.
 */
public final class FloatingBarDragger {
    private static final String PREFS = "logic_editor_ui";
    private static final float REST_SCALE = 1f;
    private static final float DRAG_SCALE = 1.05f;
    private static final long FEEDBACK_MS = 150;

    private final View bar;
    private final ViewGroup parent;
    private final SharedPreferences prefs;
    private final String keyX;
    private final String keyY;
    private final float defaultMarginX;
    private final float defaultTopOffset;

    private float grabX;
    private float grabY;

    /**
     * @param handle           the part of the bar that starts a drag
     * @param name             prefix of the preference keys, one per bar
     * @param defaultMarginX   gap kept from the right edge before the user has moved the bar
     * @param defaultTopOffset where the bar sits from the top before the user has moved it
     */
    @SuppressLint("ClickableViewAccessibility")
    public FloatingBarDragger(View bar, View handle, ViewGroup parent, String name,
                              float defaultMarginX, float defaultTopOffset) {
        this.bar = bar;
        this.parent = parent;
        Context context = bar.getContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.keyX = name + "_x";
        this.keyY = name + "_y";
        this.defaultMarginX = defaultMarginX;
        this.defaultTopOffset = defaultTopOffset;

        handle.setOnTouchListener(this::onHandleTouch);
        // Placed on every layout pass: first display, rotation, window resize.
        parent.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) {
                place();
            }
        });
    }

    /** Where a bar goes: its saved spot if any, else the default, always fully inside the parent. */
    public static float[] position(float savedFractionX, float savedFractionY,
                                   int parentWidth, int parentHeight, int barWidth, int barHeight,
                                   float defaultX, float defaultY) {
        float maxX = Math.max(0, parentWidth - barWidth);
        float maxY = Math.max(0, parentHeight - barHeight);
        float x = savedFractionX >= 0 ? savedFractionX * maxX : defaultX;
        float y = savedFractionY >= 0 ? savedFractionY * maxY : defaultY;
        return new float[]{clamp(x, 0, maxX), clamp(y, 0, maxY)};
    }

    /** The fraction of the free space a coordinate stands for, 0 to 1. */
    public static float fraction(float coordinate, float freeSpace) {
        return freeSpace <= 0 ? 0 : clamp(coordinate / freeSpace, 0, 1);
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void place() {
        if (parent.getWidth() == 0 || bar.getWidth() == 0) {
            return;
        }
        float[] xy = position(
                prefs.getFloat(keyX, -1f), prefs.getFloat(keyY, -1f),
                parent.getWidth(), parent.getHeight(), bar.getWidth(), bar.getHeight(),
                parent.getWidth() - bar.getWidth() - defaultMarginX, defaultTopOffset);
        bar.setX(xy[0]);
        bar.setY(xy[1]);
    }

    private boolean onHandleTouch(View handle, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                grabX = event.getRawX() - bar.getX();
                grabY = event.getRawY() - bar.getY();
                handle.getParent().requestDisallowInterceptTouchEvent(true);
                bar.animate().scaleX(DRAG_SCALE).scaleY(DRAG_SCALE).setDuration(FEEDBACK_MS).start();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                float maxX = Math.max(0, parent.getWidth() - bar.getWidth());
                float maxY = Math.max(0, parent.getHeight() - bar.getHeight());
                bar.setX(clamp(event.getRawX() - grabX, 0, maxX));
                bar.setY(clamp(event.getRawY() - grabY, 0, maxY));
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                bar.animate().scaleX(REST_SCALE).scaleY(REST_SCALE).setDuration(FEEDBACK_MS).start();
                float maxX = Math.max(0, parent.getWidth() - bar.getWidth());
                float maxY = Math.max(0, parent.getHeight() - bar.getHeight());
                prefs.edit()
                        .putFloat(keyX, fraction(bar.getX(), maxX))
                        .putFloat(keyY, fraction(bar.getY(), maxY))
                        .apply();
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
