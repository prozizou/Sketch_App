package pro.sketchware.unityads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class UnityAdsSdkTest {
    @Test
    public void parsesVersionsFromMetadata() {
        String xml = "<metadata><groupId>com.unity3d.ads</groupId><artifactId>unity-ads</artifactId>"
                + "<versioning><latest>4.2.0</latest><versions><version>4.1.0</version>\n"
                + "<version> 4.2.0 </version></versions></versioning></metadata>";
        assertEquals(Arrays.asList("4.1.0", "4.2.0"), UnityAdsSdk.parseVersions(xml));
    }

    @Test
    public void noVersionsFromNullOrGarbage() {
        assertTrue(UnityAdsSdk.parseVersions(null).isEmpty());
        assertTrue(UnityAdsSdk.parseVersions("<html>429</html>").isEmpty());
    }

    @Test
    public void dependencyString() {
        assertEquals("com.unity3d.ads:unity-ads:4.2.0", UnityAdsSdk.dependency("4.2.0"));
    }

    @Test
    public void detectsInstalledSdkOnlyByItsFolderName() {
        List<HashMap<String, Object>> libs = new ArrayList<>();
        libs.add(entry("appcompat-1.6.1"));
        libs.add(entry("unity-ads-mediation-1.0"));
        assertFalse(UnityAdsSdk.isInstalled(libs));
        libs.add(entry("unity-ads-v4.12.5"));
        assertTrue(UnityAdsSdk.isInstalled(libs));
        assertTrue(UnityAdsSdk.isSdkFolderName("unity-ads-4.12.5"));
        assertFalse(UnityAdsSdk.isSdkFolderName("unity-ads-v"));
        assertFalse(UnityAdsSdk.isSdkFolderName("my-unity-ads-v4.1"));
        assertFalse(UnityAdsSdk.isInstalled(null));
    }

    private static HashMap<String, Object> entry(String name) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("name", name);
        return map;
    }
}
