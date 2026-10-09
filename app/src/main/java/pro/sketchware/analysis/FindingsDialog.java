package pro.sketchware.analysis;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

/** Shows findings as a readable list: what is wrong, why, what to do and where it was found. */
public final class FindingsDialog {
    private FindingsDialog() {
    }

    public static Dialog show(Context context, CharSequence title, List<Finding> findings, CharSequence emptyMessage) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context).setTitle(title).setPositiveButton(android.R.string.ok, null);
        if (findings.isEmpty()) {
            builder.setMessage(emptyMessage);
        } else {
            builder.setView(content(context, findings));
        }
        return builder.show();
    }

    /** Shows findings with a button to go ahead anyway; Cancel leaves everything as it was. */
    public static Dialog confirm(Context context, CharSequence title, List<Finding> findings, CharSequence confirmLabel, Runnable onConfirm) {
        return new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(content(context, findings))
                .setPositiveButton(confirmLabel, (dialog, which) -> onConfirm.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    static ScrollView content(Context context, List<Finding> findings) {
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = SketchwareUtil.dpToPx(20);
        list.setPadding(padding, SketchwareUtil.dpToPx(8), padding, SketchwareUtil.dpToPx(8));
        for (Finding finding : findings) {
            TextView view = new TextView(context);
            view.setText(describe(context, finding));
            view.setTextIsSelectable(true);
            view.setPadding(0, SketchwareUtil.dpToPx(8), 0, SketchwareUtil.dpToPx(12));
            list.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        return scroll;
    }

    static CharSequence describe(Context context, Finding finding) {
        int color = switch (finding.severity()) {
            case ERROR -> MaterialColors.getColor(context, R.attr.colorError, 0);
            case WARNING -> MaterialColors.getColor(context, R.attr.colorAmber, 0);
            case INFO -> MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, 0);
        };
        SpannableStringBuilder text = new SpannableStringBuilder();
        int start = text.length();
        text.append(finding.title()).append('\n');
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(color), start, text.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        append(context, text, R.string.finding_cause, finding.cause());
        append(context, text, R.string.finding_solution, finding.solution());
        if (finding.evidence() != null) append(context, text, R.string.finding_evidence, finding.evidence());
        return text;
    }

    private static void append(Context context, SpannableStringBuilder text, int labelRes, String value) {
        int start = text.length();
        text.append(context.getString(labelRes)).append(": ");
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(value).append('\n');
    }
}
