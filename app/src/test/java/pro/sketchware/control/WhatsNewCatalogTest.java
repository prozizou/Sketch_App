package pro.sketchware.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

public class WhatsNewCatalogTest {

    @Test
    public void parsesTargetTitleAndDescription() {
        List<WhatsNewCatalog.Item> items = WhatsNewCatalog.parse(new String[]{
                "project_health|Open from Project health|Goes to the widget.",
                "app_settings|Settings|",
                "unknown_place|Still shown|But opens nothing",
                "none|Info only",
                "data_designer| |skipped, no title",
                "no separator at all"});
        assertEquals(4, items.size());
        assertEquals(new WhatsNewCatalog.Item("Open from Project health", "Goes to the widget.",
                WhatsNewCatalog.Target.PROJECT_HEALTH), items.get(0));
        assertEquals("", items.get(1).description());
        assertEquals(WhatsNewCatalog.Target.NONE, items.get(2).target());
        assertEquals("Info only", items.get(3).title());
    }

    @Test
    public void descriptionMayContainTheSeparator() {
        assertEquals("a | b", WhatsNewCatalog.parse(new String[]{"none|T|a | b"}).get(0).description());
    }

    @Test
    public void projectFeaturesAskForAProject() {
        assertFalse(WhatsNewCatalog.Target.APP_SETTINGS.needsProject);
        assertFalse(WhatsNewCatalog.Target.NONE.needsProject);
        assertTrue(WhatsNewCatalog.Target.AUTO_FIX.needsProject);
        assertTrue(WhatsNewCatalog.Target.DEVICE_PREVIEW.needsProject);
        assertEquals(WhatsNewCatalog.Target.BLOCK_DEBUGGER, WhatsNewCatalog.Target.byKey("block_debugger"));
    }

    /** Every entry shipped in strings.xml has a title, a description and a known target. */
    @Test
    public void shippedEntriesAllOpenSomething() throws Exception {
        File file = new File("src/main/res/values/strings.xml");
        if (!file.isFile()) {
            file = new File("app/src/main/res/values/strings.xml");
        }
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getDocumentElement();
        List<String> raw = new ArrayList<>();
        NodeList arrays = root.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if (array.getAttribute("name").equals("whats_new_entries")) {
                NodeList entries = array.getElementsByTagName("item");
                for (int j = 0; j < entries.getLength(); j++) {
                    raw.add(entries.item(j).getTextContent().replace("\\'", "'"));
                }
            }
        }
        assertFalse(raw.isEmpty());
        List<WhatsNewCatalog.Item> items = WhatsNewCatalog.parse(raw.toArray(new String[0]));
        assertEquals(raw.size(), items.size());
        for (int i = 0; i < raw.size(); i++) {
            // A misspelt target would silently open nothing: every key must be a real one ("none" for news only).
            String key = raw.get(i).substring(0, raw.get(i).indexOf('|'));
            assertNotNull(key, WhatsNewCatalog.Target.byKey(key));
            assertFalse(items.get(i).title(), items.get(i).description().isEmpty());
        }
    }
}
