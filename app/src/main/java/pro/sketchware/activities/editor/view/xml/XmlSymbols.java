package pro.sketchware.activities.editor.view.xml;

import androidx.annotation.StringRes;

import java.util.List;

import pro.sketchware.R;

/** The keys of the quick-insert bar above the keyboard: what they show, what they type, and where the caret ends up. */
public final class XmlSymbols {

    /**
     * @param label     what the key shows
     * @param text      what it types
     * @param caretBack how many characters the caret moves back after typing (to land between a pair)
     * @param describe  what a screen reader says
     */
    public record Symbol(String label, String text, int caretBack, @StringRes int describe) {
    }

    /** What a key does to the text: what replaces the selection, and where the caret goes (from the start of it). */
    public record Edit(String text, int caret) {
    }

    public static final List<Symbol> ALL = List.of(
            new Symbol("<", "<", 0, R.string.xml_symbol_open_tag),
            new Symbol(">", ">", 0, R.string.xml_symbol_close_bracket),
            new Symbol("/>", " />", 0, R.string.xml_symbol_self_close),
            new Symbol("</", "</", 0, R.string.xml_symbol_end_tag),
            new Symbol("\"\"", "\"\"", 1, R.string.xml_symbol_quotes),
            new Symbol("=", "=", 0, R.string.xml_symbol_equals),
            new Symbol("@+id/", "@+id/", 0, R.string.xml_symbol_new_id),
            new Symbol("@string/", "@string/", 0, R.string.xml_symbol_string_ref),
            new Symbol("@color/", "@color/", 0, R.string.xml_symbol_color_ref),
            new Symbol("@drawable/", "@drawable/", 0, R.string.xml_symbol_drawable_ref),
            new Symbol("android:", "android:", 0, R.string.xml_symbol_android_ns),
            new Symbol("app:", "app:", 0, R.string.xml_symbol_app_ns),
            new Symbol("tools:", "tools:", 0, R.string.xml_symbol_tools_ns),
            new Symbol("⇥", "    ", 0, R.string.xml_symbol_tab));

    private XmlSymbols() {
    }

    /**
     * The edit a key makes. A pair (quotes) wraps the selection instead of replacing it; any other key replaces it.
     *
     * @param selected the selected text, "" when there is none
     */
    public static Edit edit(Symbol symbol, String selected) {
        String text = symbol.text();
        if (selected.isEmpty() || symbol.caretBack() == 0) {
            return new Edit(text, text.length() - symbol.caretBack());
        }
        int split = text.length() - symbol.caretBack();
        String wrapped = text.substring(0, split) + selected + text.substring(split);
        return new Edit(wrapped, wrapped.length());
    }
}
