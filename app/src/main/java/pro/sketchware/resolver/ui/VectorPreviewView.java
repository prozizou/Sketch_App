package pro.sketchware.resolver.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.resolver.VectorModel;

/**
 * A small tile that shows what a resource looks like: a vector drawn from its path data, a bitmap, or a colour. The
 * tile is light, or dark when the drawing itself is light, so a white icon is never lost on a white tile.
 */
public class VectorPreviewView extends View {
    private static final int LIGHT_TILE = 0xFFF1F1F4;
    private static final int DARK_TILE = 0xFF2B2B30;

    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF inner = new RectF();
    private final RectF target = new RectF();
    private final android.graphics.DashPathEffect dashed = new android.graphics.DashPathEffect(new float[]{8, 6}, 0);
    @Nullable
    private VectorModel model;
    private final List<Path> paths = new ArrayList<>();
    @Nullable
    private Bitmap bitmap;
    private int swatch;
    private boolean darkTile;

    public VectorPreviewView(@NonNull Context context) {
        this(context, null);
    }

    public VectorPreviewView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(getResources().getDisplayMetrics().density);
        border.setColor(0x33808080);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Shows a vector; null clears the tile. */
    public void showVector(@Nullable VectorModel vector) {
        clear();
        model = vector;
        if (vector != null) {
            for (VectorModel.Shape shape : vector.shapes()) {
                Path path = null;
                try {
                    path = PathParser.createPathFromPathData(shape.pathData());
                } catch (RuntimeException ignored) {
                    // a path that can't be drawn is left out of the preview, not of the file
                }
                paths.add(path);
            }
            darkTile = isLight(vector);
        }
        invalidate();
    }

    public void showBitmap(@Nullable Bitmap image) {
        clear();
        bitmap = image;
        darkTile = false;
        invalidate();
    }

    public void showSwatch(int argb) {
        clear();
        swatch = argb;
        invalidate();
    }

    public void clear() {
        model = null;
        paths.clear();
        bitmap = null;
        swatch = 0;
        darkTile = false;
        invalidate();
    }

    /** Whether everything the drawing paints is light (so it is drawn on a dark tile). */
    static boolean isLight(VectorModel vector) {
        boolean any = false;
        for (VectorModel.Shape shape : vector.shapes()) {
            for (int color : new int[]{shape.fillColor(), shape.strokeColor()}) {
                if (color == 0) continue;
                any = true;
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                if (0.299 * r + 0.587 * g + 0.114 * b < 200) return false;
            }
        }
        return any;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float radius = 10 * getResources().getDisplayMetrics().density;
        rect.set(0, 0, getWidth(), getHeight());
        if (swatch != 0) {
            tile.setColor(swatch);
            canvas.drawRoundRect(rect, radius, radius, tile);
            canvas.drawRoundRect(rect, radius, radius, border);
            return;
        }
        tile.setColor(darkTile ? DARK_TILE : LIGHT_TILE);
        canvas.drawRoundRect(rect, radius, radius, tile);
        float pad = Math.min(getWidth(), getHeight()) * 0.16f;
        inner.set(pad, pad, getWidth() - pad, getHeight() - pad);
        if (bitmap != null) {
            float scale = Math.min(inner.width() / bitmap.getWidth(), inner.height() / bitmap.getHeight());
            float w = bitmap.getWidth() * scale;
            float h = bitmap.getHeight() * scale;
            target.set(inner.centerX() - w / 2, inner.centerY() - h / 2, inner.centerX() + w / 2, inner.centerY() + h / 2);
            canvas.drawBitmap(bitmap, null, target, null);
        } else if (model != null) {
            float scale = Math.min(inner.width() / model.viewportWidth(), inner.height() / model.viewportHeight());
            canvas.save();
            canvas.translate(inner.centerX() - model.viewportWidth() * scale / 2, inner.centerY() - model.viewportHeight() * scale / 2);
            canvas.scale(scale, scale);
            for (int i = 0; i < model.shapes().size(); i++) {
                Path path = paths.get(i);
                if (path == null) continue;
                VectorModel.Shape shape = model.shapes().get(i);
                canvas.save();
                canvas.translate(shape.translateX(), shape.translateY());
                path.setFillType(shape.evenOdd() ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
                if (shape.fillColor() != 0) {
                    fill.setColor(shape.fillColor());
                    canvas.drawPath(path, fill);
                }
                if (shape.strokeColor() != 0) {
                    stroke.setColor(shape.strokeColor());
                    stroke.setStrokeWidth(shape.strokeWidth());
                    stroke.setStrokeCap(shape.strokeCap().equals("round") ? Paint.Cap.ROUND : shape.strokeCap().equals("square") ? Paint.Cap.SQUARE : Paint.Cap.BUTT);
                    stroke.setStrokeJoin(shape.strokeJoin().equals("round") ? Paint.Join.ROUND : shape.strokeJoin().equals("bevel") ? Paint.Join.BEVEL : Paint.Join.MITER);
                    canvas.drawPath(path, stroke);
                }
                canvas.restore();
            }
            canvas.restore();
        } else {
            // nothing to show: a dashed outline of the tile says "no preview"
            border.setPathEffect(dashed);
            canvas.drawRoundRect(rect, radius, radius, border);
            border.setPathEffect(null);
        }
    }
}
