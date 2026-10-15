package pro.sketchware.activities.editor.view.xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

/**
 * Checks that a layout is one well-formed XML document and says where it stops being one. Pure Java, so the editor can
 * run it as the user types and the tests can run it on the JVM.
 */
public final class XmlDiagnostics {

    /**
     * @param valid    whether the text is well-formed with at least one element
     * @param line     zero-based line of the first problem, or -1 when valid
     * @param column   zero-based column of the first problem, or -1 when valid
     * @param message  what is wrong, without the parser's internal position details ("" when valid)
     * @param elements how many elements the text opens (counted up to the problem when invalid)
     */
    public record Result(boolean valid, int line, int column, @NonNull String message, int elements) {
        public static Result ok(int elements) {
            return new Result(true, -1, -1, "", elements);
        }
    }

    private XmlDiagnostics() {
    }

    @NonNull
    public static Result check(@Nullable String xml) {
        if (xml == null || xml.isBlank()) {
            return new Result(false, 0, 0, "The layout is empty", 0);
        }
        XmlPullParser parser = null;
        int elements = 0;
        try {
            parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new StringReader(xml));
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG) {
                    elements++;
                }
            }
            if (elements < 1) {
                return new Result(false, 0, 0, "No element found", 0);
            }
            return Result.ok(elements);
        } catch (XmlPullParserException e) {
            // The parsers report 1-based positions; -1 means unknown.
            int line = e.getLineNumber() > 0 ? e.getLineNumber() - 1 : parser == null ? 0 : Math.max(0, parser.getLineNumber() - 1);
            int column = e.getColumnNumber() > 0 ? e.getColumnNumber() - 1 : parser == null ? 0 : Math.max(0, parser.getColumnNumber() - 1);
            return new Result(false, line, column, clean(e.getMessage()), elements);
        } catch (Exception e) {
            return new Result(false, 0, 0, clean(e.getMessage()), elements);
        }
    }

    /** Parser messages end with "(position:... @3:5 in java.io.StringReader@1f)": drop it, keep what was wrong. */
    @NonNull
    static String clean(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return "Not valid XML";
        }
        String text = message;
        int position = text.indexOf("(position:");
        if (position > 0) {
            text = text.substring(0, position);
        }
        // Some parsers prefix the position: "@3:5 in ..." or "[row,col]:[3,5]".
        text = text.replaceAll("^\\s*@\\d+:\\d+\\s*", "").replaceAll("\\[row,col]:\\[\\d+,\\d+]\\s*", "").trim();
        if (text.isEmpty()) {
            return "Not valid XML";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
