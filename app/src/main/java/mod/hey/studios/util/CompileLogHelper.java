package mod.hey.studios.util;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import com.google.android.material.color.MaterialColors;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.jbk.diagnostic.CompileDiagnosticParser.Diagnostic;
import pro.sketchware.resolver.MissingResource;
import pro.sketchware.R;

public class CompileLogHelper {
    public interface OnDiagnosticClickListener {
        void onDiagnosticClick(Diagnostic diagnostic);
    }

    public interface OnMissingResourceClickListener {
        void onMissingResourceClick(MissingResource resource);
    }

    private static final String TAG = "CompileLogHelper";
    private static final Pattern ERROR_PATTERN = Pattern.compile("----------\n([0-9]+\\. ERROR)", Pattern.MULTILINE);
    private static final Pattern WARNING_PATTERN = Pattern.compile("----------\n([0-9]+\\. WARNING)", Pattern.MULTILINE);
    private static final Pattern XML_PATTERN = Pattern.compile("error:", Pattern.MULTILINE);

    public static SpannableString getColoredLogs(Context context, String logs) {
        int errorColor = MaterialColors.getColor(context, R.attr.colorError, TAG);
        int warningColor = MaterialColors.getColor(context, R.attr.colorAmber, TAG);

        SpannableString spannable = new SpannableString(logs);

        Matcher errorMatcher = ERROR_PATTERN.matcher(logs);
        applyStyle(spannable, errorMatcher, errorColor);

        Matcher warningMatcher = WARNING_PATTERN.matcher(logs);
        applyStyle(spannable, warningMatcher, warningColor);

        Matcher xmlMatcher = XML_PATTERN.matcher(logs);
        applyStyleForXml(spannable, xmlMatcher, errorColor);

        return spannable;
    }

    /**
     * Same as {@link #getColoredLogs(Context, String)}, but also makes the file location of each
     * diagnostic clickable.
     */
    public static SpannableString getColoredLogs(Context context, String logs, List<Diagnostic> diagnostics, OnDiagnosticClickListener listener) {
        SpannableString spannable = getColoredLogs(context, logs);
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.start() < 0 || diagnostic.end() > logs.length() || diagnostic.start() >= diagnostic.end()) continue;
            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    listener.onDiagnosticClick(diagnostic);
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    ds.setUnderlineText(true);
                }
            }, diagnostic.start(), diagnostic.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    /**
     * Same as the overload above, and also makes the message of each "resource not found" error tappable: it opens the
     * resource fixer on that resource.
     */
    public static SpannableString getColoredLogs(Context context, String logs, List<Diagnostic> diagnostics, OnDiagnosticClickListener listener,
                                                 List<MissingResource> missing, OnMissingResourceClickListener missingListener) {
        SpannableString spannable = getColoredLogs(context, logs, diagnostics, listener);
        int linkColor = MaterialColors.getColor(context, R.attr.colorPrimary, TAG);
        for (MissingResource resource : missing) {
            for (MissingResource.Occurrence occurrence : resource.occurrences()) {
                if (occurrence.start() < 0 || occurrence.end() > logs.length() || occurrence.start() >= occurrence.end()) continue;
                spannable.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        missingListener.onMissingResourceClick(resource);
                    }

                    @Override
                    public void updateDrawState(TextPaint ds) {
                        ds.setUnderlineText(true);
                        ds.setColor(linkColor);
                    }
                }, occurrence.start(), occurrence.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return spannable;
    }

    private static void applyStyle(SpannableString spannable, Matcher matcher, int color) {
        while (matcher.find()) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(1), matcher.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new ForegroundColorSpan(color), matcher.start(1), matcher.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void applyStyleForXml(SpannableString spannable, Matcher matcher, int color) {
        while (matcher.find()) {
            spannable.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
