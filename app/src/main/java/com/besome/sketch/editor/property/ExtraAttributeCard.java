package com.besome.sketch.editor.property;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;

import pro.sketchware.databinding.PropertyGridItemBinding;

/** A card of the bottom (horizontal) properties panel: an attribute set on the view, or the "add" button. */
public class ExtraAttributeCard extends LinearLayout {
    private final PropertyGridItemBinding binding;

    public ExtraAttributeCard(Context context, @DrawableRes int icon, String title, @Nullable String value) {
        super(context);
        binding = PropertyGridItemBinding.inflate(LayoutInflater.from(context), this, true);
        binding.imgIcon.setImageResource(icon);
        binding.imgIcon.setColorFilter(com.google.android.material.color.MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        binding.tvTitle.setText(title);
        if (value != null) {
            binding.tvSubTitle.setText(value);
            binding.tvSubTitle.setVisibility(View.VISIBLE);
        }
    }

    public MaterialCardView getCard() {
        return binding.propertyMenuItem;
    }

    public String getTitle() {
        return binding.tvTitle.getText().toString();
    }

    @Nullable
    public String getValue() {
        return binding.tvSubTitle.getVisibility() == View.VISIBLE ? binding.tvSubTitle.getText().toString() : null;
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener listener) {
        binding.propertyMenuItem.setOnClickListener(listener);
    }
}
