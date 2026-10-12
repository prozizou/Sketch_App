package pro.sketchware.unityads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;

import javax.xml.parsers.DocumentBuilderFactory;

public class UnityAdsManifestTest {
    private static final String APP = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"com.my.app\">\n"
            + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
            + "    <application android:label=\"App\">\n"
            + "        <activity android:name=\".MainActivity\" />\n"
            + "        <provider android:name=\"androidx.startup.InitializationProvider\" android:authorities=\"com.my.app.androidx-startup\" android:exported=\"false\">\n"
            + "            <meta-data android:name=\"androidx.lifecycle.ProcessLifecycleInitializer\" android:value=\"androidx.startup\" />\n"
            + "        </provider>\n"
            + "    </application>\n"
            + "</manifest>\n";

    /** Shaped like the Unity Ads AAR manifest: permissions, ad activities, startup initializer, tools: attributes. */
    private static final String UNITY = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    xmlns:tools=\"http://schemas.android.com/tools\" package=\"com.unity3d.ads\">\n"
            + "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
            + "    <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n"
            + "    <uses-permission android:name=\"android.permission.ACCESS_WIFI_STATE\" tools:node=\"remove\" />\n"
            + "    <permission android:name=\"${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION\" android:protectionLevel=\"signature\" />\n"
            + "    <queries><intent><action android:name=\"android.intent.action.VIEW\" /></intent></queries>\n"
            + "    <application>\n"
            + "        <activity android:name=\"com.unity3d.services.ads.adunit.AdUnitActivity\" android:exported=\"false\"\n"
            + "            android:theme=\"@android:style/Theme.NoTitleBar.Fullscreen\" tools:replace=\"android:theme\" />\n"
            + "        <provider android:name=\"androidx.startup.InitializationProvider\" android:authorities=\"${applicationId}.androidx-startup\"\n"
            + "            android:exported=\"false\" tools:node=\"merge\">\n"
            + "            <meta-data android:name=\"com.unity3d.services.core.configuration.AdsSdkInitializer\" android:value=\"androidx.startup\" />\n"
            + "            <meta-data android:name=\"androidx.lifecycle.ProcessLifecycleInitializer\" android:value=\"androidx.startup\" />\n"
            + "        </provider>\n"
            + "        <provider android:name=\"com.unity3d.Other\" android:authorities=\"${applicationId}.other\" android:exported=\"false\" />\n"
            + "    </application>\n"
            + "</manifest>\n";

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static int count(Document document, String tag, String name) {
        NodeList nodes = document.getElementsByTagName(tag);
        int count = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            if (UnityAdsManifest.androidName((Element) nodes.item(i)).equals(name)) {
                count++;
            }
        }
        return count;
    }

    private static Document merged() throws Exception {
        return parse(UnityAdsManifest.merge(APP, Collections.singletonList(UNITY), "com.my.app"));
    }

    @Test
    public void addsTheSdkActivityWithoutToolsAttributes() throws Exception {
        String xml = UnityAdsManifest.merge(APP, Collections.singletonList(UNITY), "com.my.app");
        Document document = parse(xml);
        assertEquals(1, count(document, "activity", "com.unity3d.services.ads.adunit.AdUnitActivity"));
        assertEquals(1, count(document, "activity", ".MainActivity"));
        assertFalse(xml.contains("tools:"));
        assertFalse(xml.contains("schemas.android.com/tools"));
    }

    @Test
    public void addsMissingPermissionsOnceAndSkipsRemovedOnes() throws Exception {
        Document document = merged();
        assertEquals(1, count(document, "uses-permission", "android.permission.INTERNET"));
        assertEquals(1, count(document, "uses-permission", "android.permission.ACCESS_NETWORK_STATE"));
        assertEquals(0, count(document, "uses-permission", "android.permission.ACCESS_WIFI_STATE"));
    }

    @Test
    public void copiesPermissionsALibraryDeclaresOnce() throws Exception {
        String once = UnityAdsManifest.merge(APP, Collections.singletonList(UNITY), "com.my.app");
        String twice = UnityAdsManifest.merge(once, Collections.singletonList(UNITY), "com.my.app");
        assertEquals(1, count(parse(twice), "permission", "com.my.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"));
    }

    @Test
    public void mergesStartupInitializersIntoTheExistingProvider() throws Exception {
        Document document = merged();
        assertEquals(1, count(document, "provider", "androidx.startup.InitializationProvider"));
        assertEquals(1, count(document, "meta-data", "com.unity3d.services.core.configuration.AdsSdkInitializer"));
        assertEquals(1, count(document, "meta-data", "androidx.lifecycle.ProcessLifecycleInitializer"));
    }

    @Test
    public void replacesApplicationIdAndAddsTheInitProviderOnce() throws Exception {
        String once = UnityAdsManifest.merge(APP, Collections.singletonList(UNITY), "com.my.app");
        String twice = UnityAdsManifest.merge(once, Collections.singletonList(UNITY), "com.my.app");
        assertFalse(twice.contains("${applicationId}"));
        assertTrue(twice.contains("com.my.app.other"));
        Document document = parse(twice);
        assertEquals(1, count(document, "provider", "com.my.app.UnityAdsHelper$InitProvider"));
        assertEquals(1, count(document, "provider", "com.unity3d.Other"));
        assertEquals(1, count(document, "activity", "com.unity3d.services.ads.adunit.AdUnitActivity"));
        assertEquals(1, document.getElementsByTagName("application").getLength());
    }

    @Test
    public void libraryInitializersAreHandedToTheHelperAndTheAppsOwnAreNot() throws Exception {
        String app = APP.replace("</provider>", "<meta-data android:name=\"androidx.emoji2.text.EmojiCompatInitializer\" android:value=\"androidx.startup\" /></provider>");
        Document document = parse(UnityAdsManifest.merge(app, Collections.singletonList(UNITY), "com.my.app"));

        assertEquals("deferred: the SDK's initializer", UnityAdsManifest.DEFERRED_VALUE, metaDataValue(document, "com.unity3d.services.core.configuration.AdsSdkInitializer"));
        assertEquals("deferred: a library's initializer", UnityAdsManifest.DEFERRED_VALUE, metaDataValue(document, "androidx.lifecycle.ProcessLifecycleInitializer"));
        assertEquals("the app's own entry keeps running at start", "androidx.startup", metaDataValue(document, "androidx.emoji2.text.EmojiCompatInitializer"));
    }

    @Test
    public void theHelpersProviderStartsBeforeEverythingElse() throws Exception {
        Document document = merged();
        NodeList providers = document.getElementsByTagName("provider");
        for (int i = 0; i < providers.getLength(); i++) {
            Element provider = (Element) providers.item(i);
            if (UnityAdsManifest.androidName(provider).equals("com.my.app.UnityAdsHelper$InitProvider")) {
                assertEquals("1000", provider.getAttributeNS(UnityAdsManifest.ANDROID_NS, "initOrder"));
                return;
            }
        }
        throw new AssertionError("provider missing");
    }

    private static String metaDataValue(Document document, String name) {
        NodeList nodes = document.getElementsByTagName("meta-data");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (UnityAdsManifest.androidName(element).equals(name)) {
                return element.getAttributeNS(UnityAdsManifest.ANDROID_NS, "value");
            }
        }
        return null;
    }

    @Test
    public void copiesQueries() throws Exception {
        assertEquals(1, merged().getElementsByTagName("queries").getLength());
    }

    @Test
    public void unreadableLibraryManifestsAreSkipped() throws Exception {
        Document document = parse(UnityAdsManifest.merge(APP, Arrays.asList("not xml", "", UNITY), "com.my.app"));
        assertEquals(1, count(document, "activity", "com.unity3d.services.ads.adunit.AdUnitActivity"));
    }

    @Test
    public void unreadableAppManifestIsReturnedUnchanged() {
        assertEquals("<broken", UnityAdsManifest.merge("<broken", Collections.singletonList(UNITY), "com.my.app"));
    }

    @Test
    public void withoutLibrariesOnlyTheInitProviderIsAdded() throws Exception {
        Document document = parse(UnityAdsManifest.merge(APP, Collections.<String>emptyList(), "com.my.app"));
        assertEquals(1, count(document, "provider", "com.my.app.UnityAdsHelper$InitProvider"));
        assertEquals(1, count(document, "activity", ".MainActivity"));
    }
}
