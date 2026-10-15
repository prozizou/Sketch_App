package pro.sketchware.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Answers captured from the real service (api.iconify.design) are what the parsing is tested on. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class IconifyClientTest {
    private static final class Recorder implements HttpFetcher {
        final List<URL> urls = new ArrayList<>();
        final List<String> answers;

        Recorder(String... answers) {
            this.answers = new ArrayList<>(List.of(answers));
        }

        @Override
        public byte[] get(URL url, long maxBytes, Set<String> contentTypes) throws IOException {
            DownloadPolicy.ICON_SERVICE.requireTrusted(url);
            urls.add(url);
            return answers.remove(0).getBytes(StandardCharsets.UTF_8);
        }
    }

    @Test
    public void readsTheRealSearchAnswer() throws Exception {
        List<OnlineIcon> icons = IconifyClient.parseSearch(Fixtures.read("iconify-search.json"), 50);
        assertEquals(20, icons.size());
        OnlineIcon first = icons.get(0);
        assertEquals("ic:baseline-arrow-back", first.id());
        assertEquals("Google Material Icons", first.setName());
        assertEquals("Apache-2.0", first.license());
        assertTrue(first.licenseUrl().startsWith("https://"));
        assertTrue(icons.stream().anyMatch(i -> i.prefix().equals("mdi")));
        OnlineIcon tabler = icons.stream().filter(i -> i.prefix().equals("tabler")).findFirst().orElseThrow();
        assertEquals("MIT", tabler.license());
        assertEquals(3, IconifyClient.parseSearch(Fixtures.read("iconify-search.json"), 3).size());
    }

    @Test
    public void anIconOfASetThatIsNotAllowedIsLeftOut() throws Exception {
        String json = "{\"icons\":[\"ic:baseline-add\",\"emojione:smile\",\"mdi:home\"],\"collections\":{"
                + "\"ic\":{\"name\":\"Google\",\"license\":{\"spdx\":\"Apache-2.0\",\"url\":\"https://x\"}},"
                + "\"emojione\":{\"name\":\"E\",\"license\":{\"spdx\":\"CC-BY-4.0\",\"url\":\"https://x\"}},"
                + "\"mdi\":{\"name\":\"M\",\"license\":{\"spdx\":\"Apache-2.0\",\"url\":\"https://x\"}}}}";
        assertEquals(List.of("ic:baseline-add", "mdi:home"), IconifyClient.parseSearch(json, 10).stream().map(OnlineIcon::id).toList());
    }

    @Test
    public void aSetWhoseLicenseChangedIsLeftOut() throws Exception {
        String json = "{\"icons\":[\"mdi:home\",\"tabler:home\"],\"collections\":{"
                + "\"mdi\":{\"name\":\"M\",\"license\":{\"spdx\":\"GPL-3.0\",\"url\":\"https://x\"}},"
                + "\"tabler\":{\"name\":\"T\",\"license\":{\"spdx\":\"MIT\",\"url\":\"https://x\"}}}}";
        assertEquals(List.of("tabler:home"), IconifyClient.parseSearch(json, 10).stream().map(OnlineIcon::id).toList());
        String noLicense = "{\"icons\":[\"mdi:home\"],\"collections\":{\"mdi\":{\"name\":\"M\"}}}";
        assertTrue("no licence stated, so no icon", IconifyClient.parseSearch(noLicense, 10).isEmpty());
    }

    @Test
    public void namesThatCouldChangeAnAddressAreLeftOut() throws Exception {
        String json = "{\"icons\":[\"mdi:../../x\",\"mdi:a/b\",\"mdi:UPPER\",\"mdi:a?b=c\",\"mdi:ok-name\",\"mdi:\",\"nocolon\"],\"collections\":{"
                + "\"mdi\":{\"name\":\"M\",\"license\":{\"spdx\":\"Apache-2.0\",\"url\":\"https://x\"}}}}";
        assertEquals(List.of("mdi:ok-name"), IconifyClient.parseSearch(json, 10).stream().map(OnlineIcon::id).toList());
    }

    @Test
    public void anAnswerThatIsNotJsonIsAnError() {
        assertThrows(IOException.class, () -> IconifyClient.parseSearch("<html>", 5));
        assertThrows(IOException.class, () -> IconifyClient.parseIcons("nope"));
        assertThrows(IOException.class, () -> IconifyClient.parseSearch("", 5));
    }

    @Test
    public void searchBuildsATrustedAddressWithTheAllowedSetsOnly() throws Exception {
        Recorder recorder = new Recorder(Fixtures.read("iconify-search.json"));
        List<OnlineIcon> icons = new IconifyClient(recorder).search("arrow back", 10);
        assertEquals(10, icons.size());
        URL url = recorder.urls.get(0);
        assertEquals("api.iconify.design", url.getHost());
        assertEquals("/search", url.getPath());
        assertTrue(url.getQuery(), url.getQuery().contains("query=arrow+back"));
        assertTrue(url.getQuery(), url.getQuery().contains("limit=10"));
        assertTrue(url.getQuery(), url.getQuery().contains("prefixes=bi,carbon,ic,lucide,material-symbols,mdi,ph,tabler"));
    }

    @Test
    public void aQueryIsCleanedAndLimited() throws Exception {
        Recorder recorder = new Recorder("{\"icons\":[],\"collections\":{}}", "{\"icons\":[],\"collections\":{}}");
        IconifyClient client = new IconifyClient(recorder);
        assertTrue(client.search("   ", 10).isEmpty());
        assertTrue(client.search(null, 10).isEmpty());
        assertTrue("an empty query makes no request", recorder.urls.isEmpty());
        client.search("a&b=c#d /../x", 1000);
        String query = recorder.urls.get(0).getQuery();
        assertFalse(query, query.contains("&b=c"));
        assertTrue("the number of results is capped", query.contains("limit=48"));
        client.search("x".repeat(500), 5);
        assertTrue(recorder.urls.get(1).getQuery().length() < 400);
    }

    @Test
    public void readsTheRealIconsAnswerIntoSvgs() throws Exception {
        Map<String, String> svgs = IconifyClient.parseIcons(Fixtures.read("iconify-icons.json"));
        assertEquals(2, svgs.size());
        String back = svgs.get("baseline-arrow-back");
        assertTrue(back, back.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\">"));
        assertTrue(back.contains("d=\"M20 11H7.83l5.59-5.59L12 4l-8 8l8 8l1.41-1.41L7.83 13H20z\""));
    }

    @Test
    public void aDownloadedIconBecomesAVectorInTheChosenColour() throws Exception {
        String svg = IconifyClient.parseIcons(Fixtures.read("iconify-icons.json")).get("outline-add");
        FileInspector.Inspection inspection = FileInspector.inspect("drawable", svg.getBytes(StandardCharsets.UTF_8), "#FF2196F3");
        assertTrue(inspection.problem(), inspection.ok());
        assertEquals(FileInspector.Format.SVG, inspection.format());
        String xml = new String(inspection.content(), StandardCharsets.UTF_8);
        assertTrue("currentColor takes the chosen colour", xml.contains("android:fillColor=\"#FF2196F3\""));
        assertTrue(xml.contains("android:pathData=\"M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z\""));
        assertEquals(FileInspector.Format.VECTOR_XML, FileInspector.inspect("drawable", inspection.content(), null).format());
    }

    @Test
    public void fetchingDrawingsAsksOncePerSetForOnlyAllowedNames() throws Exception {
        List<OnlineIcon> icons = IconifyClient.parseSearch(Fixtures.read("iconify-search.json"), 50);
        Recorder recorder = new Recorder(Fixtures.read("iconify-icons.json"), "{\"prefix\":\"mdi\",\"width\":24,\"height\":24,\"icons\":{\"arrow-back\":{\"body\":\"<path d=\\\"M1 1h2v2z\\\"/>\"}}}",
                "{\"prefix\":\"tabler\",\"width\":24,\"height\":24,\"icons\":{}}");
        List<OnlineIcon> some = new ArrayList<>();
        for (OnlineIcon icon : icons) {
            if (icon.id().equals("ic:baseline-arrow-back") || icon.id().equals("ic:outline-add") || icon.id().equals("mdi:arrow-back") || icon.id().equals("tabler:arrow-back")) some.add(icon);
        }
        some.add(new OnlineIcon("ic", "outline-add", "G", "Apache-2.0", ""));
        some.add(new OnlineIcon("evil", "x", "E", "MIT", ""));
        some.add(new OnlineIcon("ic", "../x", "G", "Apache-2.0", ""));
        Map<OnlineIcon, String> svgs = new IconifyClient(recorder).fetchSvgs(some);
        assertEquals("one request per allowed set", 3, recorder.urls.size());
        assertEquals("/ic.json", recorder.urls.get(0).getPath());
        assertTrue(recorder.urls.get(0).getQuery(), recorder.urls.get(0).getQuery().startsWith("icons=baseline-arrow-back,"));
        assertEquals("/mdi.json", recorder.urls.get(1).getPath());
        assertFalse("the unknown set and the bad name never reach an address", recorder.urls.stream().anyMatch(u -> u.toString().contains("evil") || u.toString().contains("..")));
        assertTrue(svgs.keySet().stream().anyMatch(i -> i.id().equals("mdi:arrow-back")));
        assertFalse("an icon the service doesn't return is just absent", svgs.keySet().stream().anyMatch(i -> i.id().equals("tabler:arrow-back")));
    }

    @Test
    public void iconsGetUsableResourceNames() {
        assertEquals("ic_baseline_add", new OnlineIcon("ic", "baseline-add", "G", "Apache-2.0", "").suggestedResourceName());
        assertEquals("ic_mdi_home_variant", new OnlineIcon("mdi", "home-variant", "M", "Apache-2.0", "").suggestedResourceName());
        assertEquals("ic_material_symbols_home", new OnlineIcon("material-symbols", "home", "M", "Apache-2.0", "").suggestedResourceName());
        assertTrue(ResourceNames.isValidFor("drawable", new OnlineIcon("material-symbols", "home-2", "M", "Apache-2.0", "").suggestedResourceName()));
    }
}
