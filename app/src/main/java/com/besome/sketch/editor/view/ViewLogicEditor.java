package com.besome.sketch.editor.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.besome.sketch.editor.logic.BlockPane;
import com.google.android.material.color.MaterialColors;

import androidx.core.graphics.ColorUtils;

import pro.sketchware.R;

public class ViewLogicEditor extends LogicEditorScrollView {
    private final BlockPane blockPane;
    private final int[] posArea = new int[2];
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float gridSpacing;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 1.5f;

    /** Called with the new zoom (1 = 100%) whenever it changes. */
    public interface OnZoomChangedListener {
        void onZoomChanged(float zoom);
    }

    private final ScaleGestureDetector pinchDetector;
    private OnZoomChangedListener onZoomChangedListener;
    private float zoom = 1f;
    private boolean isFirst = true;

    public ViewLogicEditor(Context context) {
        this(context, null);
    }

    public ViewLogicEditor(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ViewLogicEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ViewLogicEditor(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        blockPane = new BlockPane(context);
        blockPane.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(blockPane);

        float density = getResources().getDisplayMetrics().density;
        gridSpacing = 20 * density;
        gridPaint.setColor(ColorUtils.setAlphaComponent(MaterialColors.getColor(this, R.attr.colorOnSurface), 0x14));
        gridPaint.setStrokeWidth(1.5f * density);
        gridPaint.setStrokeCap(Paint.Cap.ROUND);

        pinchDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                setZoom(zoom * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        // Zoom only with two fingers. "Quick scale" (double tap then drag with one finger) is on by default and
        // turned quick successive swipes, made to scroll, into zooming.
        pinchDetector.setQuickScaleEnabled(false);
    }

    public void setOnZoomChangedListener(OnZoomChangedListener listener) {
        onZoomChangedListener = listener;
    }

    /** Two fingers pinch the canvas; every other gesture is left to the scroll view and the blocks. */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        pinchDetector.onTouchEvent(ev);
        if (pinchDetector.isInProgress() || ev.getPointerCount() > 1) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        pinchDetector.onTouchEvent(event);
        if (pinchDetector.isInProgress() || event.getPointerCount() > 1) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Draws a discreet dot grid behind the blocks. The canvas is already translated by the
     * scroll offset here, so the dots move together with the content. Purely visual.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        float spacing = gridSpacing * zoom;
        float startX = (float) Math.floor(getScrollX() / spacing) * spacing;
        float startY = (float) Math.floor(getScrollY() / spacing) * spacing;
        float endX = getScrollX() + getWidth();
        float endY = getScrollY() + getHeight();
        for (float x = startX; x <= endX; x += spacing) {
            for (float y = startY; y <= endY; y += spacing) {
                canvas.drawPoint(x, y, gridPaint);
            }
        }
        super.dispatchDraw(canvas);
    }

    public float getZoom() {
        return zoom;
    }

    /**
     * Visual-only zoom: the block pane is scaled around its top-left corner and the scroll offset is
     * adjusted to keep the viewport centre. Callers must reset it to 1 before the engine reads block
     * positions (drag and drop), because those are computed from unscaled screen coordinates.
     */
    public void setZoom(float newZoom) {
        setZoom(newZoom, getWidth() / 2f, getHeight() / 2f);
    }

    /** Zooms while keeping the canvas point under ({@code focusX}, {@code focusY}) (view pixels) in place. */
    public void setZoom(float newZoom, float focusX, float focusY) {
        newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        if (newZoom == zoom) return;
        float oldZoom = zoom;
        zoom = newZoom;

        float anchorX = getScrollX() + focusX;
        float anchorY = getScrollY() + focusY;
        applyScale();

        int x = Math.round(anchorX / oldZoom * zoom - focusX);
        int y = Math.round(anchorY / oldZoom * zoom - focusY);
        scrollTo(clampScrollX(x), clampScrollY(y));
        invalidate();
        notifyZoomChanged();
    }

    /**
     * Zooms and scrolls so that every visible block is on screen at once (never enlarging past 100%).
     * With no blocks it just goes back to 100% at the top-left corner.
     */
    public void fitToContent() {
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int i = 0; i < blockPane.getChildCount(); i++) {
            View child = blockPane.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || child.getWidth() == 0 || child.getHeight() == 0) {
                continue;
            }
            left = Math.min(left, child.getX());
            top = Math.min(top, child.getY());
            right = Math.max(right, child.getX() + child.getWidth());
            bottom = Math.max(bottom, child.getY() + child.getHeight());
        }

        CanvasFit.Result fit;
        if (left > right) {
            fit = new CanvasFit.Result(1f, 0, 0);
        } else {
            float padding = 16 * getResources().getDisplayMetrics().density;
            fit = CanvasFit.compute(left, top, right, bottom, getWidth(), getHeight(), padding, MIN_ZOOM, 1f);
        }
        zoom = fit.zoom();
        applyScale();
        scrollTo(clampScrollX(fit.scrollX()), clampScrollY(fit.scrollY()));
        invalidate();
        notifyZoomChanged();
    }

    private void applyScale() {
        blockPane.setPivotX(0);
        blockPane.setPivotY(0);
        blockPane.setScaleX(zoom);
        blockPane.setScaleY(zoom);
    }

    private int clampScrollX(int x) {
        int max = Math.max(0, Math.round(blockPane.getWidth() * zoom) - getWidth());
        return Math.max(0, Math.min(x, max));
    }

    private int clampScrollY(int y) {
        int max = Math.max(0, Math.round(blockPane.getHeight() * zoom) - getHeight());
        return Math.max(0, Math.min(y, max));
    }

    private void notifyZoomChanged() {
        if (onZoomChangedListener != null) {
            onZoomChangedListener.onZoomChanged(zoom);
        }
    }

    public BlockPane getBlockPane() {
        return blockPane;
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (isFirst) {
            blockPane.getLayoutParams().width = right - left;
            blockPane.getLayoutParams().height = bottom - top;
            blockPane.b();
            isFirst = false;
        }
    }

    public boolean hitTest(float x, float y) {
        getLocationOnScreen(posArea);
        if (!(x > posArea[0])) return false;
        if (!(x < posArea[0] + getWidth())) return false;
        if (!(y > posArea[1])) return false;
        if (!(y < posArea[1] + getHeight())) return false;
        return true;
    }
}
