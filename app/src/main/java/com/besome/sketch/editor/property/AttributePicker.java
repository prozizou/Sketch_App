package com.besome.sketch.editor.property;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import pro.sketchware.R;
import pro.sketchware.properties.AttributeCatalog;

/** A searchable list of the catalog attributes that fit a view, grouped by section; picks one. */
final class AttributePicker {
    private AttributePicker() {
    }

    static void show(Context context, List<AttributeCatalog.Section> sections, Collection<String> alreadySet,
                     Consumer<AttributeCatalog.Attr> onPick) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * density);
        root.setPadding(pad, Math.round(8 * density), pad, 0);

        EditText search = new EditText(context);
        search.setHint(R.string.property_search_hint);
        search.setSingleLine();
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(360 * density)));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.property_attribute_pick_title)
                .setView(root)
                .setNegativeButton(R.string.common_word_cancel, null)
                .create();

        Runnable fill = () -> {
            list.removeAllViews();
            for (AttributeCatalog.Section section : AttributeCatalog.search(sections, search.getText().toString(), alreadySet)) {
                TextView header = new TextView(context);
                header.setText(section.titleRes());
                header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                header.setTextColor(MaterialColors.getColor(list, androidx.appcompat.R.attr.colorPrimary));
                header.setPadding(0, Math.round(12 * density), 0, Math.round(4 * density));
                list.addView(header);
                for (AttributeCatalog.Attr attr : section.attrs()) {
                    TextView row = new TextView(context);
                    row.setText(attr.label());
                    row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    row.setTextColor(MaterialColors.getColor(list, com.google.android.material.R.attr.colorOnSurface));
                    row.setPadding(0, Math.round(10 * density), 0, Math.round(10 * density));
                    row.setClickable(true);
                    TypedValue ripple = new TypedValue();
                    if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
                        row.setBackgroundResource(ripple.resourceId);
                    }
                    row.setOnClickListener(v -> {
                        dialog.dismiss();
                        onPick.accept(attr);
                    });
                    list.addView(row);
                }
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                fill.run();
            }
        });
        fill.run();
        dialog.show();
    }
}
