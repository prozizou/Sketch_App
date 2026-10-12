package pro.sketchware.unityads;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Facts about the Unity Ads SDK package: where it comes from and how to tell it is in a project. */
public final class UnityAdsSdk {
    public static final String METADATA_URL = "https://repo1.maven.org/maven2/"
            + UnityAdsConfig.SDK_GROUP.replace('.', '/') + "/" + UnityAdsConfig.SDK_ARTIFACT + "/maven-metadata.xml";

    private static final Pattern FOLDER_NAME = Pattern.compile(
            "^" + Pattern.quote(UnityAdsConfig.SDK_LOCAL_LIBRARY_PREFIX) + "-[vV]?\\d.*$");
    private static final Pattern VERSION_TAG = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>");

    private UnityAdsSdk() {
    }

    /** @return every {@code <version>} in a Maven {@code maven-metadata.xml}, in file order. */
    public static List<String> parseVersions(String metadataXml) {
        List<String> versions = new ArrayList<>();
        if (metadataXml == null) {
            return versions;
        }
        Matcher matcher = VERSION_TAG.matcher(metadataXml);
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }
        return versions;
    }

    public static String dependency(String version) {
        return UnityAdsConfig.SDK_GROUP + ":" + UnityAdsConfig.SDK_ARTIFACT + ":" + version;
    }

    /**
     * @param projectLocalLibraries the project's {@code local_library} entries, each with a "name" like
     *                              "unity-ads-4.12.5"
     */
    public static boolean isInstalled(List<? extends Map<String, Object>> projectLocalLibraries) {
        if (projectLocalLibraries == null) {
            return false;
        }
        for (Map<String, Object> library : projectLocalLibraries) {
            Object name = library.get("name");
            if (name != null && isSdkFolderName(name.toString())) {
                return true;
            }
        }
        return false;
    }

    static boolean isSdkFolderName(String name) {
        // The downloader names folders "<artifactId>-v<version>": "unity-ads-v4.12.5". "unity-ads-4.12.5" is accepted
        // too; "unity-ads-mediation-v1.0" or "unity-ads-foo" must not count as the SDK itself.
        return FOLDER_NAME.matcher(name).matches();
    }
}
