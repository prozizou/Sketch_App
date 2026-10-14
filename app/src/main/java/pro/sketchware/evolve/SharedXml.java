package pro.sketchware.evolve;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

import pro.sketchware.evolve.SharedBlockRules.Problem;

/**
 * Checks the XML of a layout before it is shared or picked: it must be one well-formed document that fits the
 * limits. Nothing else is judged here (the XML editor itself validates what it saves).
 */
public final class SharedXml {

    /** @param problem why the XML can't be used, or {@link Problem#NONE}; @param elements how many tags it opens */
    public record Result(Problem problem, int elements) {
    }

    private SharedXml() {
    }

    public static Result check(String xml) {
        if (xml == null || xml.isBlank()) {
            return new Result(Problem.XML_EMPTY, 0);
        }
        if (xml.length() > SharedBlockRules.MAX_JSON_LENGTH) {
            return new Result(Problem.TOO_LARGE, 0);
        }
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new StringReader(xml));
            int elements = 0;
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG) {
                    elements++;
                }
            }
            if (elements < 1) {
                return new Result(Problem.XML_EMPTY, 0);
            }
            if (elements > SharedBlockRules.MAX_BLOCKS * 5) {
                return new Result(Problem.TOO_MANY_BLOCKS, elements);
            }
            return new Result(Problem.NONE, elements);
        } catch (Exception e) {
            return new Result(Problem.XML_INVALID, 0);
        }
    }
}
