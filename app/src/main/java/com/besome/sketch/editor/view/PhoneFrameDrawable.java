package com.besome.sketch.editor.view;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Draws a {@link PhoneFrame} around the View editor's preview. The picture is cut into pieces so it fits
 * any preview size without distorting what must keep its shape: the corners and the camera / notch are only
 * scaled evenly, while the straight edges (and the side buttons on them) stretch.
 * <p>
 * The screen of the phone stays see-through, so the preview shows through the opening.
 */
public final class PhoneFrameDrawable extends Drawable {
    private final PhoneFrame frame;
    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private float scale = 1f;

    public PhoneFrameDrawable(Resources resources, PhoneFrame frame) {
        this.frame = frame;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        bitmap = BitmapFactory.decodeResource(resources, frame.drawableRes(), options);
    }

    public PhoneFrame getFrame() {
        return frame;
    }

    /** Scale used for the corners and the camera; it must be the one the bounds were computed with. */
    public void setScale(float scale) {
        this.scale = scale;
        invalidateSelf();
    }

    /**
     * Scale of the picture so that the side bezel is about {@code desiredBezel} pixels thick, without letting
     * the top or bottom bezel (thicker on some phones) take more than {@code maxVerticalBezel}.
     */
    public static float scaleFor(PhoneFrame frame, float desiredBezel, float maxVerticalBezel) {
        float scale = desiredBezel / frame.referenceInset();
        float vertical = Math.max(frame.insetTop(), frame.insetBottom());
        if (vertical > 0) {
            scale = Math.min(scale, maxVerticalBezel / vertical);
        }
        return Math.max(0f, scale);
    }

    /** The outer rectangle of a frame of the given scale that surrounds a screen. */
    public static RectF outerBounds(PhoneFrame frame, float screenLeft, float screenTop,
                                    float screenWidth, float screenHeight, float scale) {
        return new RectF(
                screenLeft - frame.insetLeft() * scale,
                screenTop - frame.insetTop() * scale,
                screenLeft + screenWidth + frame.insetRight() * scale,
                screenTop + screenHeight + frame.insetBottom() * scale);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bitmap == null || bounds.isEmpty()) {
            return;
        }
        int w = frame.width();
        int h = frame.height();
        float left = bounds.left;
        float top = bounds.top;
        float right = bounds.right;
        float bottom = bounds.bottom;

        // Fixed pieces are scaled evenly; the rest stretches. Never let the fixed parts overlap.
        float corner = Math.min(frame.corner() * scale, (right - left) / 2f);
        float topH = Math.min(frame.topFixed() * scale, (bottom - top) / 2f);
        float botH = Math.min(frame.bottomFixed() * scale, (bottom - top) / 2f);
        int cornerSrc = frame.corner();

        // Top row: corner, edge, camera (fixed), edge, corner.
        piece(canvas, 0, 0, cornerSrc, frame.topFixed(), left, top, left + corner, top + topH);
        piece(canvas, w - cornerSrc, 0, w, frame.topFixed(), right - corner, top, right, top + topH);
        if (frame.hasFeature()) {
            int featStart = Math.max(frame.featureStart(), cornerSrc);
            int featEnd = Math.min(frame.featureEnd(), w - cornerSrc);
            float featW = (featEnd - featStart) * scale;
            float edges = Math.max(0f, right - left - 2 * corner - featW);
            float leftShare = (float) (featStart - cornerSrc) / Math.max(1, (featStart - cornerSrc) + (w - cornerSrc - featEnd));
            float edgeA = edges * leftShare;
            float featLeft = left + corner + edgeA;
            piece(canvas, cornerSrc, 0, featStart, frame.topFixed(), left + corner, top, featLeft, top + topH);
            piece(canvas, featStart, 0, featEnd, frame.topFixed(), featLeft, top, featLeft + featW, top + topH);
            piece(canvas, featEnd, 0, w - cornerSrc, frame.topFixed(), featLeft + featW, top, right - corner, top + topH);
        } else {
            piece(canvas, cornerSrc, 0, w - cornerSrc, frame.topFixed(), left + corner, top, right - corner, top + topH);
        }
        // Sides (the buttons live here and stretch vertically).
        piece(canvas, 0, frame.topFixed(), cornerSrc, h - frame.bottomFixed(), left, top + topH, left + corner, bottom - botH);
        piece(canvas, w - cornerSrc, frame.topFixed(), w, h - frame.bottomFixed(), right - corner, top + topH, right, bottom - botH);
        // Bottom row.
        piece(canvas, 0, h - frame.bottomFixed(), cornerSrc, h, left, bottom - botH, left + corner, bottom);
        piece(canvas, cornerSrc, h - frame.bottomFixed(), w - cornerSrc, h, left + corner, bottom - botH, right - corner, bottom);
        piece(canvas, w - cornerSrc, h - frame.bottomFixed(), w, h, right - corner, bottom - botH, right, bottom);
    }

    private void piece(Canvas canvas, int sl, int st, int sr, int sb, float dl, float dt, float dr, float db) {
        if (dr <= dl || db <= dt || sr <= sl || sb <= st) {
            return;
        }
        src.set(sl, st, sr, sb);
        dst.set(dl, dt, dr, db);
        canvas.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
