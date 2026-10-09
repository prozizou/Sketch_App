package pro.sketchware.analysis.fix;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Moves typed-in widget text into the project's {@code strings.xml}: reads the existing strings, picks a key for a
 * text (the existing one when the same text is already there), and writes new entries escaped as Android expects.
 */
public final class StringResources {
    /** Longest key made from a text, before a number is added to keep it unique. */
    static final int MAX_KEY_LENGTH = 32;

    private StringResources() {
    }

    /** The file could not be read as a resources file, so it must not be rewritten. */
    public static final class InvalidStringsFile extends Exception {
        InvalidStringsFile(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Key to text of every {@code <string>} in {@code xml}; empty for a missing or blank file. */
    public static Map<String, String> parse(String xml) throws InvalidStringsFile {
        Map<String, String> strings = new LinkedHashMap<>();
        if (xml == null || xml.isBlank()) return strings;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = document.getDocumentElement();
            if (!"resources".equals(root.getNodeName())) throw new InvalidStringsFile("The root element is not <resources>", null);
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && "string".equals(node.getNodeName())) {
                    strings.put(((Element) node).getAttribute("name"), unescape(node.getTextContent()));
                }
            }
            return strings;
        } catch (InvalidStringsFile e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidStringsFile("strings.xml could not be read: " + e.getMessage(), e);
        }
    }

    /** What Android shows for a stored value: the backslash escapes and surrounding quotes removed. */
    static String unescape(String stored) {
        String text = stored;
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length() - 1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(next == 'n' ? '\n' : next == 't' ? '\t' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * A key for {@code text}: the key that already holds exactly this text, or one made from its words
     * ({@code "Sign in!"} gives {@code sign_in}) made unique among {@code taken}.
     */
    public static String keyFor(String text, Map<String, String> existing, Set<String> taken) {
        for (Map.Entry<String, String> entry : existing.entrySet()) {
            if (entry.getValue().equals(text)) return entry.getKey();
        }
        String base = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.length() > MAX_KEY_LENGTH) base = base.substring(0, MAX_KEY_LENGTH).replaceAll("_+$", "");
        if (base.isEmpty() || Character.isDigit(base.charAt(0))) base = "text_" + base;
        base = base.replaceAll("_+$", "");
        String key = base;
        for (int n = 2; taken.contains(key) || existing.containsKey(key); n++) key = base + "_" + n;
        return key;
    }

    /** The value as it must be written inside {@code <string>}, so Android shows exactly {@code text}. */
    public static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("\\\"");
                case '\'' -> out.append("\\'");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        String value = out.toString();
        // A leading @ or ? would make Android read a reference; leading or trailing spaces would be trimmed
        if (value.startsWith("@") || value.startsWith("?")) value = "\\" + value;
        if (!text.equals(text.strip())) value = "\"" + value + "\"";
        return value;
    }

    /** {@code xml} with {@code entries} (key to text) added before {@code </resources>}; a new file when it is blank. */
    public static String append(String xml, Map<String, String> entries) {
        StringBuilder lines = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            lines.append("    <string name=\"").append(entry.getKey()).append('"');
            if (entry.getValue().contains("%")) lines.append(" formatted=\"false\"");
            lines.append('>').append(escape(entry.getValue())).append("</string>\n");
        }
        if (xml == null || xml.isBlank()) {
            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" + lines + "</resources>\n";
        }
        int end = xml.lastIndexOf("</resources>");
        if (end < 0) {
            // A self-closing <resources/>: open it
            int selfClosing = xml.lastIndexOf("<resources/>");
            if (selfClosing < 0) selfClosing = xml.lastIndexOf("<resources />");
            int length = xml.startsWith("<resources/>", selfClosing) ? "<resources/>".length() : "<resources />".length();
            return xml.substring(0, selfClosing) + "<resources>\n" + lines + "</resources>" + xml.substring(selfClosing + length);
        }
        String before = xml.substring(0, end);
        if (!before.endsWith("\n")) before += "\n";
        return before + lines + xml.substring(end);
    }
}
