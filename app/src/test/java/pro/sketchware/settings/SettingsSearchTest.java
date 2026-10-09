package pro.sketchware.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public class SettingsSearchTest {

    private static final String APP_NS = "http://schemas.android.com/apk/res-auto";

    /** Every setting App Settings had before it was split into categories. None may be lost. */
    private static final List<String> ORIGINAL_KEYS = Arrays.asList(
            "built-in-blocks", "always-show-blocks", "use-asd-highlighter", "auto-save-interval", "auto-save-mode",
            "use-new-version-control", "auto-version-code", "root-auto-install-projects",
            "root-auto-open-after-installing", "health-check", "auto-snapshots", "auto-backup",
            "auto-backup-frequency", "backup-retention", "restore-backup", "crash-recovery", "backup-dir",
            "backup-filename", "build-mode", "keystore-path", "keystore-alias", "keystore-verify", "keystore-create",
            "build-history", "clear-cache", "storage-usage", "show-every-single-block", "memory-alerts",
            "memory-alert-threshold", "app-version", "update-channel", "check-updates", "view-logs",
            "diagnostic-report", "export-settings", "import-settings", "reset-settings", "ff-build-doctor",
            "ff-memory-aware-build", "ff-force-low-memory-build", "ff-project-analysis", "ff-release-manager",
            "ff-device-preview", "ff-layout-guides", "ff-block-debugger", "ff-data-designer", "ff-auto-fix",
            "ff-export-ci", "ff-export-ci-lint", "ff-export-ci-tests");

    private static Element preferencesXml() throws Exception {
        File file = new File("src/main/res/xml/preferences_config_activity.xml");
        if (!file.isFile()) {
            file = new File("app/src/main/res/xml/preferences_config_activity.xml");
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file).getDocumentElement();
    }

    private static String attr(Element element, String name) {
        return element.hasAttributeNS(APP_NS, name) ? element.getAttributeNS(APP_NS, name) : null;
    }

    private static void feed(Element element, SettingsIndex index) {
        index.start(element.getTagName(), attr(element, "key"), attr(element, "title"), attr(element, "summary"));
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) {
                feed(childElement, index);
            }
        }
        index.end();
    }

    private static List<SettingsSearch.Entry> realIndex() throws Exception {
        SettingsIndex index = new SettingsIndex();
        feed(preferencesXml(), index);
        return index.entries();
    }

    private static List<String> keys(List<SettingsSearch.Entry> entries) {
        List<String> keys = new ArrayList<>();
        for (SettingsSearch.Entry entry : entries) {
            keys.add(entry.key());
        }
        return keys;
    }

    @Test
    public void everyOriginalSettingIsKeptExactlyOnce() throws Exception {
        List<String> all = new ArrayList<>();
        collectKeys(preferencesXml(), all);
        for (String key : ORIGINAL_KEYS) {
            assertEquals(key, 1, all.stream().filter(key::equals).count());
        }
        assertEquals("duplicate keys in " + all, new HashSet<>(all).size(), all.size());
    }

    private static void collectKeys(Element element, List<String> keys) {
        String key = attr(element, "key");
        if (key != null) {
            keys.add(key);
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) {
                collectKeys(childElement, keys);
            }
        }
    }

    @Test
    public void mainPageHasEightCategories() throws Exception {
        List<String> screens = new ArrayList<>();
        for (Node child = preferencesXml().getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals("PreferenceScreen")) {
                screens.add(attr(element, "key"));
                assertTrue(attr(element, "key") + " needs a description", attr(element, "summary") != null);
                assertTrue(attr(element, "key") + " needs an icon", attr(element, "icon") != null);
            }
        }
        assertEquals(Arrays.asList("screen-appearance", "screen-projects", "screen-backup", "screen-build",
                "screen-performance", "screen-updates", "screen-diagnostics", "screen-advanced"), screens);
    }

    /** A category screen only inflates its own rows, so a dependency must live in the same category. */
    @Test
    public void dependenciesStayInTheirCategory() throws Exception {
        Map<String, String> screenOf = new HashMap<>();
        Map<String, String> dependencies = new HashMap<>();
        for (Node child = preferencesXml().getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element screen && screen.getTagName().equals("PreferenceScreen")) {
                mapScreen(screen, attr(screen, "key"), screenOf, dependencies);
            }
        }
        assertFalse(dependencies.isEmpty());
        for (Map.Entry<String, String> dependency : dependencies.entrySet()) {
            assertEquals(dependency.getKey() + " depends on " + dependency.getValue(),
                    screenOf.get(dependency.getKey()), screenOf.get(dependency.getValue()));
        }
    }

    private static void mapScreen(Element element, String screen, Map<String, String> screenOf,
                                  Map<String, String> dependencies) {
        String key = attr(element, "key");
        if (key != null) {
            screenOf.put(key, screen);
            if (attr(element, "dependency") != null) {
                dependencies.put(key, attr(element, "dependency"));
            }
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) {
                mapScreen(childElement, screen, screenOf, dependencies);
            }
        }
    }

    @Test
    public void indexKnowsWhereEachSettingLives() throws Exception {
        List<SettingsSearch.Entry> entries = realIndex();
        SettingsSearch.Entry keystore = entries.stream().filter(e -> e.key().equals("keystore-path")).findFirst().orElseThrow();
        assertEquals("screen-build", keystore.screenKey());
        assertEquals("Build & Signing › Signing", keystore.location());
        assertFalse(keystore.isScreen());

        SettingsSearch.Entry build = entries.stream().filter(e -> e.key().equals("screen-build")).findFirst().orElseThrow();
        assertTrue(build.isScreen());

        SettingsSearch.Entry reset = entries.stream().filter(e -> e.key().equals("reset-settings")).findFirst().orElseThrow();
        assertEquals(null, reset.screenKey());

        Set<String> indexed = new HashSet<>(keys(entries));
        assertFalse(indexed.contains("settings-search"));
        assertFalse(indexed.contains("settings-footer"));
        assertFalse(indexed.contains("experimental-warning"));
        for (String key : ORIGINAL_KEYS) {
            assertTrue(key + " is not searchable", indexed.contains(key));
        }
    }

    @Test
    public void findsByTitleSynonymAndFrenchWithoutAccents() throws Exception {
        SettingsSearch search = new SettingsSearch(realIndex());
        assertEquals("keystore-path", search.search("keystore", 5).get(0).key());
        assertTrue(keys(search.search("dark", 5)).contains("app-theme"));
        assertTrue(keys(search.search("Thème", 5)).contains("app-theme"));
        assertTrue(keys(search.search("sauvegarde", 10)).contains("auto-backup"));
        assertTrue(keys(search.search("MÉMOIRE", 10)).contains("memory-alerts"));
        assertEquals("check-updates", search.search("check for updates", 5).get(0).key());
    }

    @Test
    public void everyWordMustMatch() throws Exception {
        SettingsSearch search = new SettingsSearch(realIndex());
        assertTrue(search.search("keystore banana", 10).isEmpty());
        assertTrue(search.search("   ", 10).isEmpty());
        assertTrue(search.search("", 10).isEmpty());
    }

    @Test
    public void titleMatchesComeBeforeDescriptionMatches() {
        List<SettingsSearch.Entry> entries = List.of(
                new SettingsSearch.Entry("a", "Logs", "View app logs", "s", "Diagnostics", ""),
                new SettingsSearch.Entry("b", "Crash recovery", "Mentions logs", "s", "Backup", ""),
                new SettingsSearch.Entry("c", "Log level", "", "s", "Diagnostics", ""));
        SettingsSearch search = new SettingsSearch(entries, Map.of());
        assertEquals(Arrays.asList("a", "c", "b"), keys(search.search("log", 10)));
        assertEquals(1, search.search("log", 1).size());
    }

    @Test
    public void settingBeatsItsCategoryOnEqualMatch() {
        List<SettingsSearch.Entry> entries = List.of(
                new SettingsSearch.Entry("screen-updates", "Updates", "", "screen-updates", "Updates", ""),
                new SettingsSearch.Entry("updates", "Updates", "", "screen-updates", "Updates", "Updates"));
        assertEquals("updates", new SettingsSearch(entries, Map.of()).search("updates", 5).get(0).key());
    }

    @Test
    public void normalizeDropsCaseAccentsAndPunctuation() {
        assertEquals("build signing", SettingsSearch.normalize("  Build & Signing "));
        assertEquals("theme eleve", SettingsSearch.normalize("Thème — ÉLEVÉ"));
        assertEquals("", SettingsSearch.normalize(null));
    }
}
