package pro.sketchware.unityads;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classes the Unity Ads SDK can't start without, each with the library that provides it (the versions the SDK's own
 * POM asks for, 4.21.0). When the downloader fails on one of them the build still succeeds and the app only fails at
 * run time ("Failed resolution of: Landroidx/datastore/core/DataStore;"), so the settings screen checks them and can
 * download what is missing.
 * <p>
 * Pure Java, unit-tested on a plain JVM.
 */
public final class UnityAdsRequirements {
    public static final class Requirement {
        /** Path of a class inside a jar, like "androidx/datastore/core/DataStore.class". */
        public final String classFile;
        /** group:artifact:version to download when the class is missing. */
        public final String coordinate;

        Requirement(String classFile, String coordinate) {
            this.classFile = classFile;
            this.coordinate = coordinate;
        }

        public String artifactId() {
            return coordinate.split(":")[1];
        }
    }

    private static final List<Requirement> REQUIRED = Collections.unmodifiableList(java.util.Arrays.asList(
            new Requirement("androidx/datastore/core/DataStore.class", "androidx.datastore:datastore-core:1.0.0"),
            new Requirement("androidx/datastore/DataStoreFile.class", "androidx.datastore:datastore:1.0.0"),
            new Requirement("com/google/protobuf/GeneratedMessageLite.class", "com.google.protobuf:protobuf-javalite:3.25.6"),
            new Requirement("com/google/protobuf/kotlin/DslList.class", "com.google.protobuf:protobuf-kotlin-lite:3.25.6"),
            new Requirement("androidx/webkit/WebViewCompat.class", "androidx.webkit:webkit:1.6.1"),
            new Requirement("androidx/work/WorkManager.class", "androidx.work:work-runtime:2.7.0"),
            new Requirement("kotlinx/coroutines/android/HandlerDispatcherKt.class", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"),
            new Requirement("okhttp3/OkHttpClient.class", "com.squareup.okhttp3:okhttp:3.12.13"),
            new Requirement("okio/Okio.class", "com.squareup.okio:okio-jvm:3.18.2")));

    private static final Pattern SDK_VERSION = Pattern.compile("^" + Pattern.quote(UnityAdsConfig.SDK_LOCAL_LIBRARY_PREFIX)
            + "-[vV]?(\\d[\\w.\\-]*)$");

    private UnityAdsRequirements() {
    }

    public static List<Requirement> all() {
        return REQUIRED;
    }

    /** Class files to look for in the project's libraries. */
    public static List<String> classFiles() {
        List<String> files = new ArrayList<>();
        for (Requirement requirement : REQUIRED) {
            files.add(requirement.classFile);
        }
        return files;
    }

    /** @param available class files found in libraries that the build will include (with compiled code) */
    public static List<Requirement> missing(Collection<String> available) {
        List<Requirement> missing = new ArrayList<>();
        for (Requirement requirement : REQUIRED) {
            if (!available.contains(requirement.classFile)) {
                missing.add(requirement);
            }
        }
        return missing;
    }

    /** The SDK version from the project's libraries ("unity-ads-v4.21.0" gives "4.21.0"), or null. */
    public static String sdkVersion(List<? extends Map<String, Object>> projectLocalLibraries) {
        if (projectLocalLibraries == null) {
            return null;
        }
        for (Map<String, Object> library : projectLocalLibraries) {
            Object name = library.get("name");
            if (name != null) {
                Matcher matcher = SDK_VERSION.matcher(name.toString());
                if (matcher.matches()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }
}
