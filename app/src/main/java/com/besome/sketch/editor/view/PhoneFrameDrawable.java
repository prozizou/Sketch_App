package com.besome.sketch.editor.view;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A phone bezel drawn around the View editor's preview. The picture is cut into pieces so it fits any
 * preview size without distorting what must keep its shape: the corners and the notch are only scaled
 * evenly, while the straight edges (and the side buttons on them) stretch.
 * <p>
 * The screen of the phone stays see-through, so the preview shows through the opening.
 */
public final class PhoneFrameDrawable extends Drawable {
    // Geometry of res/drawable-nodpi/phone_frame.png, in its own pixels.
    static final int SRC_WIDTH = 480;
    static final int SRC_HEIGHT = 1025;
    /** Thickness of the bezel on each side, from the outer edge to the screen opening. */
    public static final int INSET_LEFT = 27;
    public static final int INSET_RIGHT = 27;
    public static final int INSET_TOP = 27;
    public static final int INSET_BOTTOM = 42;
    /** Size of a corner piece: it contains the whole rounded corner of the body and of the screen. */
    private static final int CORNER = 80;
    /** Horizontal span of the notch on the top edge; this part is never stretched. */
    private static final int NOTCH_START = 200;
    private static final int NOTCH_END = 280;

    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private float scale = 1f;

    public PhoneFrameDrawable(Resources resources, @DrawableRes int id) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        bitmap = BitmapFactory.decodeResource(resources, id, options);
    }

    /** Scale used for the corners and the notch; it must be the one the bounds were computed with. */
    public void setScale(float scale) {
        this.scale = scale;
        invalidateSelf();
    }

    /**
     * Scale of the picture so that the bezel is about {@code desiredBezel} pixels thick, but never so thick
     * that it would not fit in the room around the screen.
     *
     * @param roomSides  free space on the left and right of the screen
     * @param roomTop    free space above the screen
     * @param roomBottom free space below the screen
     */
    public static float scaleFor(float desiredBezel, float roomSides, float roomTop, float roomBottom) {
        float scale = desiredBezel / INSET_LEFT;
        scale = Math.min(scale, roomSides / Math.max(INSET_LEFT, INSET_RIGHT));
        scale = Math.min(scale, roomTop / INSET_TOP);
        scale = Math.min(scale, roomBottom / INSET_BOTTOM);
        return Math.max(0f, scale);
    }

    /** The outer rectangle of a bezel of the given scale that surrounds a screen. */
    public static RectF outerBounds(float screenLeft, float screenTop, float screenWidth, float screenHeight, float scale) {
        return new RectF(
                screenLeft - INSET_LEFT * scale,
                screenTop - INSET_TOP * scale,
                screenLeft + screenWidth + INSET_RIGHT * scale,
                screenTop + screenHeight + INSET_BOTTOM * scale);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bitmap == null || bounds.isEmpty()) {
            return;
        }
        float corner = Math.min(CORNER * scale, Math.min(bounds.width(), bounds.height()) / 2f);
        float left = bounds.left;
        float top = bounds.top;
        float right = bounds.right;
        float bottom = bounds.bottom;

        float notch = (NOTCH_END - NOTCH_START) * (corner / CORNER);
        float topEdges = right - left - 2 * corner - notch;
        float edgeA = topEdges / 2f;
        float notchLeft = left + corner + edgeA;

        // Top row: corner, edge, notch, edge, corner.
        piece(canvas, 0, 0, CORNER, CORNER, left, top, left + corner, top + corner);
        piece(canvas, CORNER, 0, NOTCH_START, CORNER, left + corner, top, notchLeft, top + corner);
        piece(canvas, NOTCH_START, 0, NOTCH_END, CORNER, notchLeft, top, notchLeft + notch, top + corner);
        piece(canvas, NOTCH_END, 0, SRC_WIDTH - CORNER, CORNER, notchLeft + notch, top, right - corner, top + corner);
        piece(canvas, SRC_WIDTH - CORNER, 0, SRC_WIDTH, CORNER, right - corner, top, right, top + corner);
        // Sides.
        piece(canvas, 0, CORNER, CORNER, SRC_HEIGHT - CORNER, left, top + corner, left + corner, bottom - corner);
        piece(canvas, SRC_WIDTH - CORNER, CORNER, SRC_WIDTH, SRC_HEIGHT - CORNER, right - corner, top + corner, right, bottom - corner);
        // Bottom row.
        piece(canvas, 0, SRC_HEIGHT - CORNER, CORNER, SRC_HEIGHT, left, bottom - corner, left + corner, bottom);
        piece(canvas, CORNER, SRC_HEIGHT - CORNER, SRC_WIDTH - CORNER, SRC_HEIGHT, left + corner, bottom - corner, right - corner, bottom);
        piece(canvas, SRC_WIDTH - CORNER, SRC_HEIGHT - CORNER, SRC_WIDTH, SRC_HEIGHT, right - corner, bottom - corner, right, bottom);
    }

    private void piece(Canvas canvas, int sl, int st, int sr, int sb, float dl, float dt, float dr, float db) {
        if (dr <= dl || db <= dt) {
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
