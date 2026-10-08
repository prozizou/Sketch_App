package com.besome.sketch.editor.property;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import pro.sketchware.properties.AttributeCatalog;

/** One attribute of the catalog: its name and its current value (or "Not set"). */
public class ExtraAttributeRow extends LinearLayout {
    private final AttributeCatalog.Attr attr;
    private final TextView value;

    public ExtraAttributeRow(Context context, AttributeCatalog.Attr attr, String notSetText) {
        super(context);
        this.attr = attr;
        float density = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Math.round(52 * density));
        int horizontal = Math.round(16 * density);
        int vertical = Math.round(8 * density);
        setPadding(horizontal, vertical, horizontal, vertical);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            setBackgroundResource(ripple.resourceId);
        }
        setClickable(true);
        setFocusable(true);
        setTag("extra:" + attr.name());

        TextView title = new TextView(context);
        title.setText(attr.label());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        addView(title);

        value = new TextView(context);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        value.setSingleLine();
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setTag(notSetText);
        addView(value);
        setValue(null);
    }

    public AttributeCatalog.Attr getAttr() {
        return attr;
    }

    /** Shows {@code newValue}; {@code null} means the attribute is not set. */
    public void setValue(@Nullable String newValue) {
        boolean isSet = newValue != null;
        value.setText(isSet ? newValue : (String) value.getTag());
        value.setTextColor(MaterialColors.getColor(this, isSet
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    public boolean isSet() {
        return !value.getText().toString().equals(value.getTag());
    }
}
