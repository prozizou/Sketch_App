package pro.sketchware.admobads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.besome.sketch.beans.ProjectLibraryBean;

import org.junit.Test;

public class AdMobAdsTest {
    @Test
    public void adUnitIsThePartAfterTheLastSeparator() {
        assertEquals("ca-app-pub-1/2", AdMobAds.adUnit("My banner : ca-app-pub-1/2"));
        assertEquals("ca-app-pub-1/2", AdMobAds.adUnit("a : b : ca-app-pub-1/2"));
        assertEquals("ca-app-pub-1/2", AdMobAds.adUnit("ca-app-pub-1/2"));
        assertEquals("", AdMobAds.adUnit(""));
        assertEquals("", AdMobAds.adUnit(null));
    }

    @Test
    public void theBlocksComeWithTheLibrary() {
        ProjectLibraryBean library = new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_ADMOB);
        assertFalse(AdMobAds.isActive(library));
        library.useYn = ProjectLibraryBean.LIB_USE_Y;
        assertTrue(AdMobAds.isActive(library));
        assertFalse(AdMobAds.isActive((ProjectLibraryBean) null));
    }

    @Test
    public void theHelperUsesTheAdUnitsSavedInTheSettings() {
        ProjectLibraryBean library = new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_ADMOB);
        library.reserved1 = "Banner : ca-app-pub-9/1";
        library.reserved2 = "Inter : ca-app-pub-9/2";
        String source = AdMobAds.helperSource("com.my.app", library);
        assertTrue(source.contains("LIB_BANNER = \"ca-app-pub-9/1\""));
        assertTrue(source.contains("LIB_INTERSTITIAL = \"ca-app-pub-9/2\""));
        assertTrue(source.contains("LIB_REWARDED = \"" + AdMobHelperSource.TEST_REWARDED + "\""));
    }
}
