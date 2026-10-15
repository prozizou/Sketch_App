package pro.sketchware.resolver;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

/**
 * Reads XML that comes from outside (an imported file, a downloaded icon, the project's own files) without letting it
 * do anything: a document type declaration or an entity is refused outright, so nothing is fetched or expanded.
 * Uses the platform's SAX parser, so it behaves the same on a device and in the JVM tests.
 */
public final class SafeXml {
    /** The XML is not usable; the message is what is wrong, with the line when known. */
    public static final class InvalidXml extends Exception {
        private final int line;

        public InvalidXml(String message, int line) {
            super(message);
            this.line = line;
        }

        /** 1-based line of the problem, 0 when unknown. */
        public int line() {
            return line;
        }
    }

    private SafeXml() {
    }

    /** Parses {@code xml}, calling {@code handler} for its elements. */
    public static void parse(String xml, DefaultHandler handler) throws InvalidXml {
        if (xml == null || xml.isBlank()) throw new InvalidXml("The XML is empty", 0);
        String lower = xml.toLowerCase(Locale.ROOT);
        if (lower.contains("<!doctype") || lower.contains("<!entity")) {
            throw new InvalidXml("A DOCTYPE or ENTITY declaration is not allowed", 0);
        }
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            trySet(factory, "http://xml.org/sax/features/external-general-entities", false);
            trySet(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setContentHandler(handler);
            reader.setErrorHandler(handler);
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            reader.parse(new InputSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            throw new InvalidXml(clean(e.getMessage()), e.getLineNumber());
        } catch (SAXException e) {
            if (e.getCause() instanceof InvalidXml invalid) throw invalid;
            throw new InvalidXml(clean(e.getMessage()), 0);
        } catch (ParserConfigurationException | IOException e) {
            throw new InvalidXml(clean(e.getMessage()), 0);
        }
    }

    /** Checks that {@code xml} is one well-formed document and returns its root element's name. */
    public static String rootElement(String xml) throws InvalidXml {
        String[] root = new String[1];
        parse(xml, new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                if (root[0] == null) root[0] = qName;
            }
        });
        if (root[0] == null) throw new InvalidXml("No element found", 0);
        return root[0];
    }

    private static void trySet(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // The platform parser doesn't know this feature: it never resolves external entities anyway.
        }
    }

    private static String clean(String message) {
        if (message == null || message.isBlank()) return "Not valid XML";
        String text = message.trim();
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
