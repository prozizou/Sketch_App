package pro.sketchware.evolve;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Holds one preview (a block stack drawn by the editor) at its natural size, enlarged up to {@link #MAX_SCALE} and
 * shrunk when it would not fit: the same factor on both axes, so it is never stretched and never overflows the card.
 * The child is measured as large as it wants and scaled from its top-left corner; the frame takes the scaled size.
 */
public class PreviewFrame extends FrameLayout {
    /** The preview is drawn larger than in the drawer, so blocks are readable on a card. */
    public static final float MAX_SCALE = 1.3f;
    /** No preview is taller than this, whatever the stack: a long stack is shown smaller rather than cut. */
    public static final int MAX_HEIGHT_DP = 180;

    private float scale = 1f;

    public PreviewFrame(@NonNull Context context) {
        super(context);
    }

    public PreviewFrame(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param childWidth      the child's natural width
     * @param childHeight     the child's natural height
     * @param availableWidth  room across, in the same unit
     * @param maxHeight       room down, in the same unit
     * @return the factor that fits the child in the room, at most {@link #MAX_SCALE}; 1 when the child is empty
     */
    public static float fit(int childWidth, int childHeight, int availableWidth, int maxHeight) {
        if (childWidth <= 0 || childHeight <= 0) return 1f;
        float scale = MAX_SCALE;
        if (availableWidth > 0) scale = Math.min(scale, availableWidth / (float) childWidth);
        if (maxHeight > 0) scale = Math.min(scale, maxHeight / (float) childHeight);
        return Math.max(scale, 0.01f);
    }

    /** @return the factor the preview is drawn at, after the last measure */
    public float getPreviewScale() {
        return scale;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        int horizontalPadding = getPaddingLeft() + getPaddingRight();
        int verticalPadding = getPaddingTop() + getPaddingBottom();
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (child == null || child.getVisibility() == GONE) {
            setMeasuredDimension(MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? horizontalPadding : width, verticalPadding);
            return;
        }
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        child.measure(unspecified, unspecified);
        int availableWidth = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? 0 : width - horizontalPadding;
        int maxHeight = Math.round(MAX_HEIGHT_DP * getResources().getDisplayMetrics().density) - verticalPadding;
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            maxHeight = Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec) - verticalPadding);
        }
        scale = fit(child.getMeasuredWidth(), child.getMeasuredHeight(), availableWidth, maxHeight);
        child.setPivotX(0);
        child.setPivotY(0);
        child.setScaleX(scale);
        child.setScaleY(scale);
        int measuredWidth = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                ? width : Math.round(child.getMeasuredWidth() * scale) + horizontalPadding;
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) measuredWidth = Math.min(measuredWidth, width);
        setMeasuredDimension(measuredWidth, Math.round(child.getMeasuredHeight() * scale) + verticalPadding);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child == null || child.getVisibility() == GONE) return;
        int x = getPaddingLeft();
        int y = getPaddingTop();
        child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
    }
}
