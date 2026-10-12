package pro.sketchware.unityads;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Adds what the Unity Ads SDK (and the libraries downloaded with it) declare in their manifests to the project's
 * generated manifest. The build does not merge local libraries' manifests, so without this the SDK's ad activities
 * are missing and full-screen ads cannot open.
 * <p>
 * Pure Java (javax.xml is on Android too), so it is unit-tested on a plain JVM.
 */
public final class UnityAdsManifest {
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    static final String TOOLS_NS = "http://schemas.android.com/tools";

    /** Children of {@code <application>} copied from library manifests, matched by android:name. */
    private static final Set<String> APPLICATION_CHILDREN = new HashSet<>(Arrays.asList(
            "activity", "activity-alias", "service", "receiver", "provider", "meta-data", "uses-library"));
    static final String STARTUP_PROVIDER = "androidx.startup.InitializationProvider";
    static final String STARTUP_VALUE = "androidx.startup";
    /**
     * Marks a start-up initializer that UnityAdsHelper runs itself, inside try/catch. The value stops androidx.startup
     * from running it, but the entry stays readable from the manifest.
     */
    public static final String DEFERRED_VALUE = "unityads.deferred";

    private static final Set<String> PERMISSIONS = new HashSet<>(Arrays.asList(
            "uses-permission", "uses-permission-sdk-23"));
    /** Permissions a library declares itself (androidx.core's DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION...). */
    private static final Set<String> PERMISSION_DECLARATIONS = new HashSet<>(Arrays.asList(
            "permission", "permission-group", "permission-tree"));

    private UnityAdsManifest() {
    }

    public static String initProviderClass(String packageName) {
        return packageName + ".UnityAdsHelper$InitProvider";
    }

    /**
     * @param appManifest       the generated AndroidManifest.xml
     * @param libraryManifests  contents of the SDK libraries' AndroidManifest.xml files; unreadable ones are skipped
     * @param packageName       the app's package, replaces {@code ${applicationId}}
     * @return the merged manifest, or {@code appManifest} unchanged if it can't be parsed
     */
    public static String merge(String appManifest, List<String> libraryManifests, String packageName) {
        Document app;
        try {
            app = parse(appManifest);
        } catch (Exception e) {
            return appManifest;
        }
        Element root = app.getDocumentElement();
        Element application = firstChild(root, "application");
        if (application == null) {
            application = app.createElement("application");
            root.appendChild(application);
        }

        Set<String> libraryInitializers = new HashSet<>();
        for (String libraryManifest : libraryManifests) {
            Document library;
            try {
                library = parse(libraryManifest);
            } catch (Exception e) {
                continue;
            }
            collectInitializers(library.getDocumentElement(), libraryInitializers);
            mergeLibrary(app, root, application, library.getDocumentElement(), packageName);
        }
        deferInitializers(application, libraryInitializers);

        addInitProvider(app, application, packageName);

        try {
            return serialize(app);
        } catch (Exception e) {
            return appManifest;
        }
    }

    /** Names of the start-up initializers a library manifest declares. */
    private static void collectInitializers(Element libraryRoot, Set<String> names) {
        for (Element child : children(libraryRoot)) {
            if (!child.getTagName().equals("application")) {
                continue;
            }
            for (Element component : children(child)) {
                if (component.getTagName().equals("provider") && androidName(component).equals(STARTUP_PROVIDER)) {
                    for (Element metaData : children(component)) {
                        if (metaData.getTagName().equals("meta-data") && !isRemoved(metaData)
                                && STARTUP_VALUE.equals(metaData.getAttributeNS(ANDROID_NS, "value"))) {
                            names.add(androidName(metaData));
                        }
                    }
                }
            }
        }
    }

    /**
     * Initializers the libraries added to androidx.startup (WorkManager, the Unity SDK...) run at app start before
     * anything of the app exists, so a failure in one is a bare "app stopped". UnityAdsHelper runs them itself
     * instead and reports what went wrong.
     */
    private static void deferInitializers(Element application, Set<String> names) {
        Element provider = findByName(application, "provider", STARTUP_PROVIDER);
        if (provider == null) {
            return;
        }
        for (Element metaData : children(provider)) {
            if (metaData.getTagName().equals("meta-data") && names.contains(androidName(metaData))
                    && STARTUP_VALUE.equals(metaData.getAttributeNS(ANDROID_NS, "value"))) {
                metaData.setAttributeNS(ANDROID_NS, "android:value", DEFERRED_VALUE);
            }
        }
    }

    private static void mergeLibrary(Document app, Element root, Element application, Element libraryRoot, String packageName) {
        for (Element child : children(libraryRoot)) {
            String tag = child.getTagName();
            if (isRemoved(child)) {
                continue;
            }
            if (PERMISSIONS.contains(tag) || PERMISSION_DECLARATIONS.contains(tag)) {
                if (findByName(root, tag, androidName(child).replace("${applicationId}", packageName)) == null) {
                    root.insertBefore(copy(app, child, packageName), application);
                }
            } else if (tag.equals("queries")) {
                Element queries = firstChild(root, "queries");
                if (queries == null) {
                    queries = app.createElement("queries");
                    root.insertBefore(queries, application);
                }
                for (Element query : children(child)) {
                    queries.appendChild(copy(app, query, packageName));
                }
            } else if (tag.equals("application")) {
                for (Element component : children(child)) {
                    mergeComponent(app, application, component, packageName);
                }
            }
        }
    }

    private static void mergeComponent(Document app, Element application, Element component, String packageName) {
        String tag = component.getTagName();
        if (!APPLICATION_CHILDREN.contains(tag) || isRemoved(component)) {
            return;
        }
        String name = androidName(component);
        if (name.isEmpty()) {
            return;
        }
        Element existing = findByName(application, tag, name);
        if (existing == null) {
            application.appendChild(copy(app, component, packageName));
            return;
        }
        // Same component already declared (e.g. androidx.startup's InitializationProvider): add its missing
        // meta-data, which is how libraries register their startup initializers.
        for (Element metaData : children(component)) {
            if (metaData.getTagName().equals("meta-data") && !isRemoved(metaData)
                    && findByName(existing, "meta-data", androidName(metaData)) == null) {
                existing.appendChild(copy(app, metaData, packageName));
            }
        }
    }

    private static void addInitProvider(Document app, Element application, String packageName) {
        String name = initProviderClass(packageName);
        if (findByName(application, "provider", name) != null) {
            return;
        }
        Element provider = app.createElement("provider");
        provider.setAttributeNS(ANDROID_NS, "android:name", name);
        provider.setAttributeNS(ANDROID_NS, "android:authorities", packageName + ".unityadshelper");
        provider.setAttributeNS(ANDROID_NS, "android:exported", "false");
        // Highest first: this provider must start before androidx.startup and the others.
        provider.setAttributeNS(ANDROID_NS, "android:initOrder", "1000");
        application.appendChild(provider);
    }

    /** Imports {@code node} into {@code app}, dropping tools: attributes and filling in ${applicationId}. */
    private static Node copy(Document app, Element node, String packageName) {
        Element imported = (Element) app.importNode(node, true);
        clean(imported, packageName);
        return imported;
    }

    private static void clean(Element element, String packageName) {
        NamedNodeMap attributes = element.getAttributes();
        List<Attr> toRemove = new ArrayList<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attribute = (Attr) attributes.item(i);
            String qualifiedName = attribute.getName();
            if (TOOLS_NS.equals(attribute.getNamespaceURI()) || qualifiedName.equals("xmlns:tools")) {
                toRemove.add(attribute);
            } else if (attribute.getValue().contains("${applicationId}")) {
                attribute.setValue(attribute.getValue().replace("${applicationId}", packageName));
            }
        }
        for (Attr attribute : toRemove) {
            element.removeAttributeNode(attribute);
        }
        for (Element child : children(element)) {
            clean(child, packageName);
        }
    }

    private static boolean isRemoved(Element element) {
        String node = element.getAttributeNS(TOOLS_NS, "node");
        return node.equals("remove") || node.equals("removeAll");
    }

    static String androidName(Element element) {
        return element.getAttributeNS(ANDROID_NS, "name");
    }

    private static Element findByName(Element parent, String tag, String name) {
        for (Element child : children(parent)) {
            if (child.getTagName().equals(tag) && androidName(child).equals(name)) {
                return child;
            }
        }
        return null;
    }

    private static Element firstChild(Element parent, String tag) {
        for (Element child : children(parent)) {
            if (child.getTagName().equals(tag)) {
                return child;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element) {
                elements.add((Element) nodes.item(i));
            }
        }
        return elements;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        // Errors are thrown and handled by the callers; without this the parser also prints them to stderr.
        builder.setErrorHandler(new DefaultHandler());
        return builder.parse(new InputSource(new StringReader(xml.trim())));
    }

    private static String serialize(Document document) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
