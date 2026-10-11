package pro.sketchware.blocks.editor;

import android.content.Context;
import android.content.res.TypedArray;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.google.android.material.color.MaterialColors;

import pro.sketchware.R;

/**
 * A titled block of content that folds away: the views put inside it in the layout are its content, the header
 * toggles them. The state is announced to accessibility services and the chevron turns with a short animation.
 */
public class CollapsibleSection extends LinearLayout {
    private final TextView titleView;
    private final ImageView chevron;
    private final LinearLayout header;
    private final LinearLayout content;
    private boolean expanded = true;
    private boolean contentMoved;
    private final String expandedText;
    private final String collapsedText;

    public CollapsibleSection(Context context) {
        this(context, null);
    }

    public CollapsibleSection(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        expandedText = context.getString(R.string.section_expanded);
        collapsedText = context.getString(R.string.section_collapsed);
        float density = getResources().getDisplayMetrics().density;

        header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(Math.round(48 * density));
        header.setClickable(true);
        header.setFocusable(true);
        int[] attr = {android.R.attr.selectableItemBackground};
        TypedArray ripple = context.obtainStyledAttributes(attr);
        header.setForeground(ripple.getDrawable(0));
        ripple.recycle();
        header.setPadding(Math.round(4 * density), 0, Math.round(4 * density), 0);

        titleView = new TextView(context);
        titleView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        titleView.setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary));
        titleView.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ViewCompat.setAccessibilityHeading(titleView, true);
        header.addView(titleView);

        chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_mtrl_expand);
        chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        chevron.setColorFilter(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        header.addView(chevron, new LayoutParams(Math.round(24 * density), Math.round(24 * density)));

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);

        super.addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        super.addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean startExpanded = true;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CollapsibleSection);
            titleView.setText(a.getString(R.styleable.CollapsibleSection_sectionTitle));
            startExpanded = a.getBoolean(R.styleable.CollapsibleSection_sectionExpanded, true);
            a.recycle();
        }
        header.setOnClickListener(v -> setExpanded(!expanded, true));
        applyState(startExpanded, false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Everything the layout put inside this view, besides the header and the content holder, is the content.
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child != header && child != content) {
                super.removeViewAt(i);
                content.addView(child, 0);
            }
        }
        contentMoved = true;
    }

    /** The views to fold: add to this, not to the section itself, from code. */
    public LinearLayout contentView() {
        return content;
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        // Before inflation ends, children are collected by onFinishInflate; afterwards they go straight into the content.
        if (contentMoved && child != header && child != content) {
            content.addView(child, index, params);
        } else {
            super.addView(child, index, params);
        }
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expand, boolean animate) {
        if (expand == expanded) return;
        applyState(expand, animate);
    }

    private void applyState(boolean expand, boolean animate) {
        if (animate && getParent() instanceof ViewGroup parent) {
            TransitionManager.beginDelayedTransition(parent, new AutoTransition().setDuration(200));
        }
        expanded = expand;
        content.setVisibility(expand ? VISIBLE : GONE);
        chevron.animate().rotation(expand ? 180f : 0f).setDuration(animate ? 200 : 0).start();
        if (!animate) chevron.setRotation(expand ? 180f : 0f);
        ViewCompat.setStateDescription(header, expand ? expandedText : collapsedText);
        header.setContentDescription(titleView.getText());
    }

    @NonNull
    public TextView titleView() {
        return titleView;
    }
}
