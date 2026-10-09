package com.besome.sketch.editor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import pro.sketchware.editor.layout.Axis;
import pro.sketchware.editor.layout.Box;
import pro.sketchware.editor.layout.GuideEngine;

/**
 * Alignment guides and distances around the selected widget. It has the same size and transform as the
 * preview pane, so everything is drawn in the pane's own pixels. It never takes touches.
 */
final class GuidesOverlay extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gapLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dip;

    private Box target;
    private List<GuideEngine.Guide> guides = Collections.emptyList();
    private List<GuideEngine.Gap> gaps = Collections.emptyList();

    GuidesOverlay(Context context) {
        super(context);
        dip = context.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setColor(0xffe91e63);
        line.setStrokeWidth(Math.max(1f, dip));
        gapLine.setColor(0xff1e88e5);
        gapLine.setStrokeWidth(Math.max(1f, dip));
        pill.setColor(0xcc1e88e5);
        text.setColor(0xffffffff);
        text.setTextSize(10f * dip);
    }

    void show(Box target, List<GuideEngine.Guide> guides, List<GuideEngine.Gap> gaps) {
        this.target = target;
        this.guides = guides;
        this.gaps = gaps;
        invalidate();
    }

    void clear() {
        if (target != null) {
            target = null;
            guides = Collections.emptyList();
            gaps = Collections.emptyList();
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (target == null) {
            return;
        }
        for (GuideEngine.Guide guide : guides) {
            if (guide.axis() == Axis.X) {
                canvas.drawLine(guide.position(), guide.from(), guide.position(), guide.to(), line);
            } else {
                canvas.drawLine(guide.from(), guide.position(), guide.to(), guide.position(), line);
            }
        }
        for (GuideEngine.Gap gap : gaps) {
            if (gap.size() < 1f) {
                continue;
            }
            float x1, y1, x2, y2;
            switch (gap.side()) {
                case LEFT -> {
                    x1 = target.left() - gap.size();
                    x2 = target.left();
                    y1 = y2 = target.centerY();
                }
                case RIGHT -> {
                    x1 = target.right();
                    x2 = target.right() + gap.size();
                    y1 = y2 = target.centerY();
                }
                case TOP -> {
                    y1 = target.top() - gap.size();
                    y2 = target.top();
                    x1 = x2 = target.centerX();
                }
                default -> {
                    y1 = target.bottom();
                    y2 = target.bottom() + gap.size();
                    x1 = x2 = target.centerX();
                }
            }
            canvas.drawLine(x1, y1, x2, y2, gapLine);
            String label = String.format(Locale.ROOT, "%d", Math.round(gap.size() / dip));
            float width = text.measureText(label);
            float pad = 2f * dip;
            float cx = (x1 + x2) / 2f;
            float cy = (y1 + y2) / 2f;
            canvas.drawRoundRect(cx - width / 2 - pad, cy - text.getTextSize() / 2 - pad,
                    cx + width / 2 + pad, cy + text.getTextSize() / 2 + pad, pad, pad, pill);
            canvas.drawText(label, cx - width / 2, cy + text.getTextSize() * 0.35f, text);
        }
    }
}
