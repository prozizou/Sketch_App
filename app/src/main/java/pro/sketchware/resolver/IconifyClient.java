package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Searches Iconify (api.iconify.design) for icons and gets their drawings. Only a few icon sets are used, each with a
 * permissive licence that the resolver knows (the service must say the same licence, or the icon is left out), and
 * only names made of lowercase letters, digits and dashes are used to build an address. What comes back is an SVG
 * text: it is checked and converted by {@link SvgToVector}, never run.
 */
public final class IconifyClient {
    /** Icon sets used, and the licence of each as the resolver knows it. */
    public static final Map<String, String> ALLOWED_SETS = Map.of(
            "ic", "Apache-2.0", "material-symbols", "Apache-2.0", "mdi", "Apache-2.0", "carbon", "Apache-2.0",
            "tabler", "MIT", "ph", "MIT", "bi", "MIT", "lucide", "ISC");

    private static final String BASE = "https://" + DownloadPolicy.ICON_HOST;
    private static final Pattern NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final long MAX_SEARCH_BYTES = 256 * 1024;
    private static final long MAX_ICONS_BYTES = 512 * 1024;
    private static final Set<String> JSON = Set.of("application/json");
    private static final int MAX_QUERY = 60;
    private static final int MAX_RESULTS = 48;

    private final HttpFetcher fetcher;

    public IconifyClient(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** @return the icons matching {@code query} from the allowed sets, in the service's order */
    @NonNull
    public List<OnlineIcon> search(String query, int limit) throws IOException {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) return new ArrayList<>();
        if (text.length() > MAX_QUERY) text = text.substring(0, MAX_QUERY);
        int count = Math.max(1, Math.min(limit, MAX_RESULTS));
        URL url = url("/search?query=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&limit=" + count
                + "&prefixes=" + String.join(",", new java.util.TreeSet<>(ALLOWED_SETS.keySet())));
        return parseSearch(new String(fetcher.get(url, MAX_SEARCH_BYTES, JSON), StandardCharsets.UTF_8), count);
    }

    /** Reads a search answer. Public so the answer's handling can be tested on captured service answers. */
    @NonNull
    public static List<OnlineIcon> parseSearch(String json, int limit) throws IOException {
        List<OnlineIcon> icons = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray ids = root.optJSONArray("icons");
            JSONObject collections = root.optJSONObject("collections");
            if (ids == null || collections == null) return icons;
            for (int i = 0; i < ids.length() && icons.size() < limit; i++) {
                String id = ids.optString(i, "");
                int colon = id.indexOf(':');
                if (colon <= 0) continue;
                String prefix = id.substring(0, colon);
                String name = id.substring(colon + 1);
                String expected = ALLOWED_SETS.get(prefix);
                if (expected == null || !NAME.matcher(name).matches()) continue;
                JSONObject collection = collections.optJSONObject(prefix);
                if (collection == null) continue;
                JSONObject license = collection.optJSONObject("license");
                // the licence must be the one the resolver knows: if the service says otherwise, the set is left out
                if (license == null || !expected.equals(license.optString("spdx", ""))) continue;
                icons.add(new OnlineIcon(prefix, name, collection.optString("name", prefix), expected, license.optString("url", "")));
            }
        } catch (JSONException e) {
            throw new IOException("The search answer wasn't understood");
        }
        return icons;
    }

    /**
     * Gets the drawings of icons, one request per icon set.
     *
     * @return the SVG of each icon the service returned; an icon it didn't return is missing from the map
     */
    @NonNull
    public Map<OnlineIcon, String> fetchSvgs(List<OnlineIcon> icons) throws IOException {
        Map<String, List<OnlineIcon>> bySet = new LinkedHashMap<>();
        for (OnlineIcon icon : icons) {
            if (!ALLOWED_SETS.containsKey(icon.prefix()) || !NAME.matcher(icon.name()).matches()) continue;
            bySet.computeIfAbsent(icon.prefix(), k -> new ArrayList<>()).add(icon);
        }
        Map<OnlineIcon, String> svgs = new HashMap<>();
        for (Map.Entry<String, List<OnlineIcon>> set : bySet.entrySet()) {
            List<String> names = new ArrayList<>();
            for (OnlineIcon icon : set.getValue()) names.add(icon.name());
            URL url = url("/" + set.getKey() + ".json?icons=" + String.join(",", names));
            Map<String, String> bodies = parseIcons(new String(fetcher.get(url, MAX_ICONS_BYTES, JSON), StandardCharsets.UTF_8));
            for (OnlineIcon icon : set.getValue()) {
                String svg = bodies.get(icon.name());
                if (svg != null) svgs.put(icon, svg);
            }
        }
        return svgs;
    }

    /** Reads an icons answer into the SVG text of each icon. */
    @NonNull
    public static Map<String, String> parseIcons(String json) throws IOException {
        Map<String, String> svgs = new HashMap<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONObject icons = root.optJSONObject("icons");
            if (icons == null) return svgs;
            double defaultWidth = root.optDouble("width", 24);
            double defaultHeight = root.optDouble("height", 24);
            for (Iterator<String> it = icons.keys(); it.hasNext(); ) {
                String name = it.next();
                JSONObject icon = icons.optJSONObject(name);
                if (icon == null || !NAME.matcher(name).matches()) continue;
                String body = icon.optString("body", "");
                if (body.isEmpty()) continue;
                double width = icon.optDouble("width", defaultWidth);
                double height = icon.optDouble("height", defaultHeight);
                if (!(width > 0 && width <= 4096 && height > 0 && height <= 4096)) continue;
                svgs.put(name, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + VectorModel.number((float) width) + "\" height=\""
                        + VectorModel.number((float) height) + "\" viewBox=\"0 0 " + VectorModel.number((float) width) + " "
                        + VectorModel.number((float) height) + "\">" + body + "</svg>");
            }
        } catch (JSONException e) {
            throw new IOException("The icons answer wasn't understood");
        }
        return svgs;
    }

    private static URL url(String pathAndQuery) throws IOException {
        try {
            return new URI(BASE + pathAndQuery).toURL();
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException("Not a valid address: " + e.getMessage());
        }
    }
}
