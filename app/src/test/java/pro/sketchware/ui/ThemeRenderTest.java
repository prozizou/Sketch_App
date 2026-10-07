package pro.sketchware.ui;

import static org.junit.Assert.assertTrue;

import android.app.Activity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import com.besome.sketch.projects.MyProjectSettingActivity;

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.activities.onboarding.OnboardingActivity;

/**
 * Every key screen has to follow the system theme: rendered in light and in dark, the two pictures must
 * really differ in brightness. A screen that forces one theme (or ignores the other) fails here, and the
 * images are kept in build/reports/theme-renders/ for a visual check.
 * <p>
 * To cover another screen, add a line to {@link #screens()} style tests below.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class ThemeRenderTest {
    /** Light screens are bright, dark ones dim; anything closer than this isn't following the theme. */
    private static final double MIN_BRIGHTNESS_GAP = 0.25;

    private void assertFollowsSystemTheme(Class<? extends Activity> screen, String name) throws Exception {
        double light = ThemeHarness.render(screen, name, false).meanLuminance();
        double dark = ThemeHarness.render(screen, name, true).meanLuminance();
        assertTrue(name + " must be brighter in light mode than in dark mode (light=" + light + ", dark=" + dark + ")",
                light - dark >= MIN_BRIGHTNESS_GAP);
    }

    @Test
    public void onboardingFollowsSystemTheme() throws Exception {
        assertFollowsSystemTheme(OnboardingActivity.class, "onboarding");
    }

    @Test
    public void newProjectFollowsSystemTheme() throws Exception {
        assertFollowsSystemTheme(MyProjectSettingActivity.class, "new-project");
    }

    @Test
    public void appSettingsFollowsSystemTheme() throws Exception {
        assertFollowsSystemTheme(ConfigActivity.class, "app-settings");
    }
}
