package pro.sketchware.settings;

import android.content.Context;
import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

import mod.jbk.util.LogUtil;

/**
 * Builds the search index of App Settings from its preferences XML, so a row added there is searchable
 * without touching the search code.
 */
public final class SettingsIndex {

    private static final String APP_NS = "http://schemas.android.com/apk/res-auto";

    /** Main-page rows that are not settings: the search bar and the version footer. */
    private static final List<String> SKIPPED = List.of("settings-search", "settings-footer", "experimental-warning");

    private final List<SettingsSearch.Entry> entries = new ArrayList<>();
    private int depth;
    private int screenDepth = -1;
    private String screenKey;
    private String screenTitle = "";
    private String section = "";
    private int sectionDepth = -1;

    /**
     * Feeds the opening tag of an element.
     */
    public void start(String tag, String key, String title, String summary) {
        depth++;
        String safeTitle = title == null ? "" : title;
        String safeSummary = summary == null ? "" : summary;
        if (depth == 1) {
            return;
        }
        if (tag.endsWith("PreferenceScreen")) {
            screenDepth = depth;
            screenKey = key;
            screenTitle = safeTitle;
            if (key != null) {
                entries.add(new SettingsSearch.Entry(key, safeTitle, safeSummary, key, safeTitle, ""));
            }
        } else if (tag.endsWith("PreferenceCategory")) {
            sectionDepth = depth;
            section = safeTitle;
        } else if (key != null && !SKIPPED.contains(key) && !safeTitle.isEmpty()) {
            entries.add(new SettingsSearch.Entry(key, safeTitle, safeSummary, screenKey,
                    screenKey == null ? "" : screenTitle, section));
        }
    }

    /**
     * Feeds the closing tag of an element.
     */
    public void end() {
        if (depth == sectionDepth) {
            sectionDepth = -1;
            section = "";
        }
        if (depth == screenDepth) {
            screenDepth = -1;
            screenKey = null;
            screenTitle = "";
        }
        depth--;
    }

    public List<SettingsSearch.Entry> entries() {
        return entries;
    }

    /**
     * Reads every row of a preferences XML resource. Returns what was read so far if the XML can't be parsed.
     */
    public static List<SettingsSearch.Entry> read(Context context, int xmlRes) {
        SettingsIndex index = new SettingsIndex();
        try (XmlResourceParser parser = context.getResources().getXml(xmlRes)) {
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG) {
                    index.start(parser.getName(), parser.getAttributeValue(APP_NS, "key"),
                            text(context, parser, "title"), text(context, parser, "summary"));
                } else if (event == XmlPullParser.END_TAG) {
                    index.end();
                }
            }
        } catch (Exception e) {
            LogUtil.e("SettingsIndex", "Couldn't index App Settings", e);
        }
        return index.entries();
    }

    private static String text(Context context, XmlResourceParser parser, String attribute) {
        int res = parser.getAttributeResourceValue(APP_NS, attribute, 0);
        if (res != 0) {
            return context.getString(res);
        }
        return parser.getAttributeValue(APP_NS, attribute);
    }
}
