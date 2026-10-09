package com.besome.sketch.editor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import pro.sketchware.editor.layout.Box;
import pro.sketchware.editor.preview.SafeArea;

/**
 * Drawn over the preview screen: the parts of the screen that system UI, a camera cut-out or a foldable's
 * hinge can cover, and a label with the size and window size class. It never takes touches.
 */
final class PreviewOverlay extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hatch = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private SafeArea safeArea;
    private boolean showSafeArea;
    private String label = "";
    private float screenLeft;
    private float screenTop;
    /** Screen pixels for one dp of the previewed screen. */
    private float pixelsPerDp = 1f;

    PreviewOverlay(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        fill.setColor(0x33e53935);
        hatch.setColor(0x88e53935);
        hatch.setStyle(Paint.Style.STROKE);
        hatch.setStrokeWidth(2f);
        pill.setColor(0xcc202124);
        text.setColor(0xffffffff);
        text.setTextSize(11f * context.getResources().getDisplayMetrics().density);
    }

    void update(SafeArea safeArea, boolean showSafeArea, String label, float screenLeft, float screenTop, float pixelsPerDp) {
        this.safeArea = safeArea;
        this.showSafeArea = showSafeArea;
        this.label = label;
        this.screenLeft = screenLeft;
        this.screenTop = screenTop;
        this.pixelsPerDp = pixelsPerDp;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (safeArea == null) {
            return;
        }
        if (showSafeArea) {
            for (Box box : safeArea.unsafeBoxes()) {
                rect.set(screenLeft + box.left() * pixelsPerDp, screenTop + box.top() * pixelsPerDp,
                        screenLeft + box.right() * pixelsPerDp, screenTop + box.bottom() * pixelsPerDp);
                canvas.drawRect(rect, fill);
                canvas.drawRect(rect, hatch);
            }
        }
        if (!label.isEmpty()) {
            float pad = 4f * getResources().getDisplayMetrics().density;
            float width = text.measureText(label);
            float height = text.getTextSize();
            float x = screenLeft + safeArea.width() * pixelsPerDp - width - pad * 3;
            float y = screenTop + safeArea.height() * pixelsPerDp - height - pad * 3;
            rect.set(x - pad, y - pad, x + width + pad, y + height + pad);
            canvas.drawRoundRect(rect, pad, pad, pill);
            canvas.drawText(label, x, y + height * 0.82f, text);
        }
    }
}
