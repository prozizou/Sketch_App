package com.besome.sketch.editor.property;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import pro.sketchware.R;
import pro.sketchware.properties.AttributeCatalog;

/** The dialog that edits one catalog attribute; the right editor is picked from the attribute's type. */
final class ExtraAttributeEditor {
    private ExtraAttributeEditor() {
    }

    /**
     * @param current  the value now, or {@code null} if not set
     * @param onResult called with the new value, or {@code null} to clear the attribute
     */
    static void show(Context context, AttributeCatalog.Attr attr, @Nullable String current, Consumer<String> onResult) {
        switch (attr.type()) {
            case BOOLEAN -> choose(context, attr, current, List.of("true", "false"), onResult);
            case ENUM -> choose(context, attr, current, attr.options(), onResult);
            case FLAGS -> chooseFlags(context, attr, current, onResult);
            default -> type(context, attr, current, onResult);
        }
    }

    private static void choose(Context context, AttributeCatalog.Attr attr, @Nullable String current,
                               List<String> options, Consumer<String> onResult) {
        List<String> labels = new ArrayList<>();
        labels.add(context.getString(R.string.property_extra_not_set));
        labels.addAll(options);
        int checked = current == null ? 0 : Math.max(0, options.indexOf(current) + 1);
        new MaterialAlertDialogBuilder(context)
                .setTitle(attr.label())
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                    onResult.accept(which == 0 ? null : options.get(which - 1));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private static void chooseFlags(Context context, AttributeCatalog.Attr attr, @Nullable String current,
                                    Consumer<String> onResult) {
        List<String> options = attr.options();
        List<String> active = current == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(current.split("\\|")));
        boolean[] checked = new boolean[options.size()];
        for (int i = 0; i < checked.length; i++) {
            checked[i] = active.contains(options.get(i));
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(attr.label())
                .setMultiChoiceItems(options.toArray(new String[0]), checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> {
                    List<String> picked = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            picked.add(options.get(i));
                        }
                    }
                    // "none" means nothing else; it is meaningless next to other flags.
                    if (picked.size() > 1) {
                        picked.remove("none");
                    }
                    onResult.accept(picked.isEmpty() ? null : String.join("|", picked));
                })
                .setNeutralButton(R.string.common_word_clear, (dialog, which) -> onResult.accept(null))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private static void type(Context context, AttributeCatalog.Attr attr, @Nullable String current, Consumer<String> onResult) {
        float density = context.getResources().getDisplayMetrics().density;
        TextInputLayout layout = new TextInputLayout(context);
        layout.setHint(attr.example().isEmpty() ? attr.label() : attr.label() + " (e.g. " + attr.example() + ")");
        EditText input = new EditText(context);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (current != null) {
            input.setText(current);
            input.setSelection(current.length());
        }
        layout.addView(input);
        FrameLayout frame = new FrameLayout(context);
        int pad = Math.round(20 * density);
        frame.setPadding(pad, Math.round(8 * density), pad, 0);
        frame.addView(layout);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(attr.name())
                .setView(frame)
                .setPositiveButton(R.string.common_word_ok, null)
                .setNeutralButton(R.string.common_word_clear, (d, which) -> onResult.accept(null))
                .setNegativeButton(R.string.common_word_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            String problem = attr.problemWith(text);
            if (problem != null) {
                layout.setError(problem);
                return;
            }
            onResult.accept(text.isEmpty() ? null : text);
            dialog.dismiss();
        }));
        dialog.show();
    }
}
