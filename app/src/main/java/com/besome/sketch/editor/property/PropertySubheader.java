package com.besome.sketch.editor.property;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import pro.sketchware.databinding.PropertySubheaderBinding;

public class PropertySubheader extends RelativeLayout {

    public PropertySubheaderBinding binding;
    private ImageView imgAdd;
    private ImageView imgExpand;
    private TextView tvName;
    private String headerName = "";
    private boolean collapsed;

    public PropertySubheader(Context context) {
        super(context);
        initialize(context);
    }

    private void initialize(Context context) {
        binding = PropertySubheaderBinding.inflate(LayoutInflater.from(context), this, true);
        tvName = binding.tvName;
        imgAdd = binding.imgAdd;
        imgExpand = binding.imgExpand;
    }

    public void setHeaderName(String str) {
        headerName = str;
        tvName.setText(str);
    }

    /** The name the section is known by, even when the shown title carries a count. */
    public String getHeaderName() {
        return headerName;
    }

    /** Shows the title with extra text after it, such as how many attributes are set. */
    public void setTitleSuffix(String suffix) {
        tvName.setText(suffix == null || suffix.isEmpty() ? headerName : headerName + " · " + suffix);
    }

    /** Makes the header fold and unfold its section when tapped. */
    public void setToggleListener(Runnable onToggle) {
        imgExpand.setVisibility(VISIBLE);
        setOnTouchClickListener(v -> {
            collapsed = !collapsed;
            applyChevron();
            onToggle.run();
        });
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    /** Sets the folded state without animating, for when the section is first shown. */
    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        imgExpand.setRotation(collapsed ? -90f : 0f);
    }

    private void applyChevron() {
        imgExpand.animate().rotation(collapsed ? -90f : 0f).setDuration(150).start();
    }

    private void setOnTouchClickListener(View.OnClickListener listener) {
        super.setOnClickListener(listener);
    }

    @Override
    public void setOnClickListener(View.OnClickListener onClickListener) {
        imgAdd.setVisibility(VISIBLE);
        imgAdd.setOnClickListener(onClickListener);
    }
}
