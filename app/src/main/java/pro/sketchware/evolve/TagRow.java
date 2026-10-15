package pro.sketchware.evolve;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.List;

import pro.sketchware.R;

/**
 * One line of small tags. The tags that fit are shown in order; when some don't, a "+N" tag stands for the rest, so
 * the row never wraps, never overflows and never leaves a tag cut in half. Screen readers hear the full list.
 */
public class TagRow extends ViewGroup {
    /** At most this many tags are created, however many the entry has; the rest only count towards "+N". */
    static final int MAX_TAGS = 8;

    private final int gap;
    private final TextView overflow;
    private int shown;
    /** How many tags the entry has, including those never created ({@link #MAX_TAGS}). */
    private int total;
    /** Widths of the tags, allocated when the tags are set, not while measuring. */
    private int[] widths = new int[0];

    public TagRow(@NonNull Context context) {
        this(context, null);
    }

    public TagRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gap = dp(6);
        overflow = newTag(context);
        addView(overflow);
    }

    /**
     * How many tags fit, in order, in {@code available} with {@code gap} between them.
     *
     * @param widths        each tag's width
     * @param total         how many tags there are in all (can exceed {@code widths.length})
     * @param overflowWidth the width of the "+N" tag for a given N
     * @return the number of tags to show; when it is below {@code total}, a "+N" tag fits after them
     */
    public static int fitCount(int[] widths, int total, int available, int gap, java.util.function.IntUnaryOperator overflowWidth) {
        int used = 0;
        for (int i = 0; i < widths.length; i++) {
            int next = used + (i > 0 ? gap : 0) + widths[i];
            int hidden = total - (i + 1);
            int reserve = hidden > 0 ? gap + overflowWidth.applyAsInt(hidden) : 0;
            if (next + reserve > available) {
                // Tag i doesn't fit (with room for "+N" after it): the rest goes into "+N", if even that fits.
                return i;
            }
            used = next;
        }
        return widths.length;
    }

    /** Shows {@code tags}; an empty list hides the row. */
    public void setTags(List<String> tags) {
        removeViews(1, getChildCount() - 1);
        int count = Math.min(tags.size(), MAX_TAGS);
        for (int i = 0; i < count; i++) {
            TextView tag = newTag(getContext());
            tag.setText(tags.get(i));
            tag.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(tag);
        }
        total = tags.size();
        widths = new int[count];
        overflow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentDescription(TextUtils.join(", ", tags));
        setVisibility(tags.isEmpty() ? GONE : VISIBLE);
        requestLayout();
    }

    /** @return how many tags are shown after the last layout (not counting "+N") */
    public int getShownCount() {
        return shown;
    }

    private TextView newTag(Context context) {
        TextView tag = new TextView(context);
        tag.setSingleLine(true);
        tag.setEllipsize(TextUtils.TruncateAt.END);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tag.setTypeface(android.graphics.Typeface.MONOSPACE);
        tag.setTextColor(ContextCompat.getColor(context, R.color.evolve_tag_text));
        tag.setBackgroundResource(R.drawable.evolve_tag_bg);
        tag.setGravity(Gravity.CENTER_VERTICAL);
        tag.setPadding(dp(8), dp(3), dp(8), dp(3));
        return tag;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int overflowWidth(int hidden) {
        overflow.setText(getContext().getString(R.string.evolve_tags_more, hidden));
        overflow.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return overflow.getMeasuredWidth();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        boolean bounded = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED;
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int tags = getChildCount() - 1;
        int height = 0;
        for (int i = 0; i < tags; i++) {
            View tag = getChildAt(i + 1);
            tag.measure(unspecified, unspecified);
            widths[i] = tag.getMeasuredWidth();
            height = Math.max(height, tag.getMeasuredHeight());
        }
        shown = bounded ? fitCount(widths, total, available, gap, this::overflowWidth) : tags;
        if (shown == 0 && tags > 0 && bounded && total == tags && widths[0] > available) {
            // Not even one tag fits whole: show the first one cut with an ellipsis rather than nothing.
            shown = 1;
            getChildAt(1).measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.EXACTLY), unspecified);
        }
        int hidden = total - shown;
        int width = 0;
        for (int i = 0; i < shown; i++) {
            width += (i > 0 ? gap : 0) + getChildAt(i + 1).getMeasuredWidth();
        }
        if (hidden > 0) {
            int overflowWidth = overflowWidth(hidden);
            if (shown > 0) width += gap;
            if (bounded && width + overflowWidth > available) {
                overflow.measure(MeasureSpec.makeMeasureSpec(Math.max(0, available - width), MeasureSpec.EXACTLY), unspecified);
            }
            width += overflow.getMeasuredWidth();
            height = Math.max(height, overflow.getMeasuredHeight());
        }
        overflow.setVisibility(hidden > 0 ? VISIBLE : INVISIBLE);
        width += getPaddingLeft() + getPaddingRight();
        height += getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x = rtl ? r - l - getPaddingRight() : getPaddingLeft();
        int top = getPaddingTop();
        int innerHeight = b - t - getPaddingTop() - getPaddingBottom();
        for (int i = 1; i < getChildCount(); i++) {
            View tag = getChildAt(i);
            if (i > shown) {
                tag.layout(0, 0, 0, 0);
                continue;
            }
            x = place(tag, x, top, innerHeight, rtl);
        }
        if (overflow.getVisibility() == VISIBLE) {
            place(overflow, x, top, innerHeight, rtl);
        } else {
            overflow.layout(0, 0, 0, 0);
        }
    }

    private int place(View tag, int x, int top, int innerHeight, boolean rtl) {
        int w = tag.getMeasuredWidth();
        int y = top + (innerHeight - tag.getMeasuredHeight()) / 2;
        if (rtl) {
            tag.layout(x - w, y, x, y + tag.getMeasuredHeight());
            return x - w - gap;
        }
        tag.layout(x, y, x + w, y + tag.getMeasuredHeight());
        return x + w + gap;
    }
}
