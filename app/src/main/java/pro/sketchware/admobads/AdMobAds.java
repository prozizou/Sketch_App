package pro.sketchware.admobads;

import com.besome.sketch.beans.ProjectLibraryBean;

import a.a.a.jC;

/** Whether a project gets the AdMob blocks and helper, and the ad units it saved in the AdMob library settings. */
public final class AdMobAds {
    private AdMobAds() {
    }

    /** The blocks and the helper come with the AdMob library: switch it on and they are there. */
    public static boolean isActive(String scId) {
        return isActive(jC.c(scId).b());
    }

    public static boolean isActive(ProjectLibraryBean adMob) {
        return adMob != null && adMob.isEnabled();
    }

    /** The helper source of a project, with the ad units of its AdMob library settings as the "library" ids. */
    public static String helperSource(String packageName, ProjectLibraryBean adMob) {
        return AdMobHelperSource.generate(packageName, adUnit(adMob == null ? null : adMob.reserved1),
                adUnit(adMob == null ? null : adMob.reserved2), adUnit(adMob == null ? null : adMob.reserved3));
    }

    /** The settings keep "name : ca-app-pub-…/…"; only the part after the last " : " is the ad unit. */
    static String adUnit(String saved) {
        if (saved == null || saved.isEmpty()) {
            return "";
        }
        int separator = saved.lastIndexOf(" : ");
        return (separator < 0 ? saved : saved.substring(separator + 3)).trim();
    }
}
