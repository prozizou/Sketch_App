package pro.sketchware.evolve;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.core.content.ContextCompat;

import pro.sketchware.R;
import pro.sketchware.evolve.CodeHighlighter.Language;
import pro.sketchware.evolve.CodeHighlighter.Span;
import pro.sketchware.evolve.CodeHighlighter.Token;

/** Paints the spans of {@link CodeHighlighter} with the Evolve code colours (light and dark). */
public final class HighlightedText {
    /** A card shows the start of the code: highlighting more than this would cost time nobody sees. */
    public static final int CARD_CHARS = 1_200;
    public static final int DETAIL_CHARS = SharedBlockRules.MAX_JSON_LENGTH;

    private HighlightedText() {
    }

    public static CharSequence of(Context context, Language language, String text, int maxChars) {
        if (text == null) return "";
        String shown = text.length() > maxChars ? text.substring(0, maxChars) : text;
        SpannableStringBuilder builder = new SpannableStringBuilder(shown);
        for (Span span : CodeHighlighter.highlight(language, shown, maxChars)) {
            builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, color(span.token()))),
                    span.start(), Math.min(span.end(), shown.length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    private static int color(Token token) {
        return switch (token) {
            case COMMENT -> R.color.evolve_code_comment;
            case STRING -> R.color.evolve_code_string;
            case NUMBER -> R.color.evolve_code_number;
            case KEYWORD -> R.color.evolve_code_keyword;
            case ANNOTATION -> R.color.evolve_code_annotation;
            case TYPE -> R.color.evolve_code_type;
            case TAG -> R.color.evolve_code_keyword;
            case ATTRIBUTE -> R.color.evolve_code_attribute;
        };
    }
}
