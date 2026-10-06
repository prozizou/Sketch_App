package pro.sketchware.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Keeps the security settings of the manifest and its XML resources from quietly regressing.
 * Unit tests run with the module directory as working directory.
 */
public class ManifestHardeningTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static Document parse(String path) throws Exception {
        File file = new File(path);
        assertTrue(path + " not found from " + new File(".").getAbsolutePath(), file.isFile());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file);
    }

    private static Element application() throws Exception {
        return (Element) parse("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0);
    }

    @Test
    public void cleartextTrafficIsDisabled() throws Exception {
        Element app = application();
        assertEquals("false", app.getAttributeNS(ANDROID_NS, "usesCleartextTraffic"));
        assertEquals("@xml/network_security_config", app.getAttributeNS(ANDROID_NS, "networkSecurityConfig"));
    }

    @Test
    public void networkSecurityConfigForbidsCleartextEverywhere() throws Exception {
        Document config = parse("src/main/res/xml/network_security_config.xml");
        Element base = (Element) config.getElementsByTagName("base-config").item(0);
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"));
        NodeList domainConfigs = config.getElementsByTagName("domain-config");
        for (int i = 0; i < domainConfigs.getLength(); i++) {
            assertEquals("false", ((Element) domainConfigs.item(i)).getAttribute("cleartextTrafficPermitted"));
        }
    }

    @Test
    public void backupRulesAreConfigured() throws Exception {
        Element app = application();
        assertEquals("@xml/backup_rules", app.getAttributeNS(ANDROID_NS, "fullBackupContent"));
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(ANDROID_NS, "dataExtractionRules"));

        for (String path : new String[]{"src/main/res/xml/backup_rules.xml", "src/main/res/xml/data_extraction_rules.xml"}) {
            Document rules = parse(path);
            NodeList excludes = rules.getElementsByTagName("exclude");
            boolean updaterStateExcluded = false;
            boolean externalExcluded = false;
            for (int i = 0; i < excludes.getLength(); i++) {
                Element exclude = (Element) excludes.item(i);
                updaterStateExcluded |= "update_checker.xml".equals(exclude.getAttribute("path"));
                externalExcluded |= "external".equals(exclude.getAttribute("domain"));
            }
            assertTrue(path + " must exclude the updater state", updaterStateExcluded);
            assertTrue(path + " must exclude external storage (downloaded updates)", externalExcluded);
        }
    }

    @Test
    public void fileProviderDoesNotShareAllOfExternalStorage() throws Exception {
        Document paths = parse("src/main/res/xml/provider_paths.xml");
        NodeList external = paths.getElementsByTagName("external-path");
        assertTrue(external.getLength() > 0);
        for (int i = 0; i < external.getLength(); i++) {
            String path = ((Element) external.item(i)).getAttribute("path");
            assertNotNull(path);
            assertFalse("external-path must not expose the whole storage: '" + path + "'",
                    path.isEmpty() || path.equals(".") || path.equals("/") || path.equals("./"));
        }
        for (String tag : new String[]{"root-path", "external-media-path", "cache-path", "files-path"}) {
            assertEquals("unexpected <" + tag + "> in provider_paths.xml", 0, paths.getElementsByTagName(tag).getLength());
        }
    }

    @Test
    public void fileProviderIsNotExported() throws Exception {
        NodeList providers = parse("src/main/AndroidManifest.xml").getElementsByTagName("provider");
        assertTrue(providers.getLength() > 0);
        for (int i = 0; i < providers.getLength(); i++) {
            Element provider = (Element) providers.item(i);
            if ("androidx.core.content.FileProvider".equals(provider.getAttributeNS(ANDROID_NS, "name"))) {
                assertEquals("false", provider.getAttributeNS(ANDROID_NS, "exported"));
            }
        }
    }
}
