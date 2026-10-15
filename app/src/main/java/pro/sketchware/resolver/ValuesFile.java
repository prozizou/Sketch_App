package pro.sketchware.resolver;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the entries of a {@code res/values*}/*.xml} file and adds new ones without touching the rest of the file:
 * the text, comments and order of what is already there stay exactly as they were.
 */
public final class ValuesFile {
    /**
     * One entry of a values file.
     *
     * @param type  {@code string}, {@code color}, {@code dimen}, ... ({@code style}, {@code array}, ... are read for their names)
     * @param name  its name
     * @param value its text as written in the file ("" for a style, an array, ...)
     */
    public record Entry(String type, String name, String value) {
    }

    private static final Set<String> SIMPLE = Set.of("string", "color", "dimen", "integer", "bool", "drawable");

    private ValuesFile() {
    }

    /** @throws SafeXml.InvalidXml when the file is not a well-formed {@code <resources>} document */
    public static List<Entry> parse(String xml) throws SafeXml.InvalidXml {
        List<Entry> entries = new ArrayList<>();
        SafeXml.parse(xml, new DefaultHandler() {
            private int depth;
            private String type;
            private String name;
            private StringBuilder text;

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                depth++;
                if (depth == 1) {
                    if (!qName.equals("resources")) {
                        throw new SAXException(new SafeXml.InvalidXml("The root element must be <resources>, not <" + qName + ">", 0));
                    }
                } else if (depth == 2) {
                    type = typeOf(qName, attributes);
                    name = attributes.getValue("name");
                    text = new StringBuilder();
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (depth >= 2 && text != null && depth == 2) text.append(ch, start, length);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if (depth == 2 && type != null && name != null) {
                    entries.add(new Entry(type, name, SIMPLE.contains(type) || "id".equals(type) ? text.toString().trim() : ""));
                }
                if (depth == 2) {
                    type = null;
                    name = null;
                    text = null;
                }
                depth--;
            }
        });
        return entries;
    }

    private static String typeOf(String tag, Attributes attributes) {
        return switch (tag) {
            case "string", "color", "dimen", "integer", "bool", "drawable", "style", "plurals", "attr" -> tag;
            case "string-array", "integer-array", "array" -> "array";
            case "declare-styleable" -> "styleable";
            case "item" -> attributes.getValue("type");
            default -> null;
        };
    }

    /**
     * Returns {@code xml} with {@code entries} added before the closing {@code </resources>}.
     *
     * @param xml the current file, or null/blank when there is none yet
     * @throws SafeXml.InvalidXml when {@code xml} is not valid: a file that can't be read is never rewritten
     */
    public static String append(String xml, List<Entry> entries) throws SafeXml.InvalidXml {
        StringBuilder added = new StringBuilder();
        for (Entry entry : entries) added.append(render(entry));
        if (xml == null || xml.isBlank()) {
            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" + added + "</resources>\n";
        }
        parse(xml); // refuses anything that isn't a valid resources file
        int close = xml.lastIndexOf("</resources>");
        if (close < 0) {
            // <resources/>: valid, with no closing tag
            int selfClosing = xml.lastIndexOf("/>");
            int open = xml.lastIndexOf("<resources");
            if (open < 0 || selfClosing < open) throw new SafeXml.InvalidXml("Can't find where the resources end", 0);
            return xml.substring(0, open) + "<resources>\n" + added + "</resources>" + xml.substring(selfClosing + 2);
        }
        String before = xml.substring(0, close);
        if (!before.endsWith("\n")) before = before.stripTrailing() + "\n";
        return before + added + xml.substring(close);
    }

    /** The XML line of one entry. */
    static String render(Entry entry) {
        return switch (entry.type()) {
            case "string", "color", "dimen", "integer", "bool", "drawable" ->
                    "    <" + entry.type() + " name=\"" + entry.name() + "\">" + entry.value() + "</" + entry.type() + ">\n";
            default -> "    <item name=\"" + entry.name() + "\" type=\"" + entry.type() + "\">" + entry.value() + "</item>\n";
        };
    }

    /** What to write inside {@code <string>} for a literal text: XML-escaped, with Android's own escapes. */
    public static String textValue(String literal) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '\'' -> out.append("\\'");
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '@', '?' -> out.append(i == 0 ? "\\" : "").append(c);
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** {@code @type/name}, which makes an entry an alias of another resource. */
    public static String reference(String type, String name) {
        return "@" + type + "/" + name;
    }
}
