package pro.sketchware.ui;

import static org.junit.Assert.assertTrue;

import android.app.Activity;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import pro.sketchware.activities.onboarding.OnboardingActivity;

/**
 * Text and button colors have to stay readable in both themes (WCAG AA: 4.5:1 for text).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class ThemeContrastTest {
    private static final double TEXT_CONTRAST = 4.5;

    private void check(boolean dark) throws Exception {
        ThemeHarness.Result result = ThemeHarness.render(OnboardingActivity.class, "contrast-probe", dark);
        Activity activity = result.activity();
        String mode = dark ? "dark" : "light";

        assertContrast(mode + " body text on surface", result.themeColor(R.attr.colorOnSurface), result.themeColor(R.attr.colorSurface));
        assertContrast(mode + " secondary text on surface", result.themeColor(R.attr.colorOnSurfaceVariant), result.themeColor(R.attr.colorSurface));
        assertContrast(mode + " section titles (primary) on surface", result.themeColor(androidx.appcompat.R.attr.colorPrimary), result.themeColor(R.attr.colorSurface));
        assertContrast(mode + " text on primary", result.themeColor(R.attr.colorOnPrimary), result.themeColor(androidx.appcompat.R.attr.colorPrimary));
        assertContrast(mode + " text on card", result.themeColor(R.attr.colorOnSurface), result.themeColor(R.attr.colorSurfaceContainerLowest));

        // The brand button (Next / Create): label on the accent fill.
        assertContrast(mode + " label on the accent button", color(activity, pro.sketchware.R.color.event_on_accent), color(activity, pro.sketchware.R.color.event_accent));
        // Small text on the tinted container (e.g. "Change icon").
        assertContrast(mode + " text on the accent container", color(activity, pro.sketchware.R.color.event_on_accent_container), color(activity, pro.sketchware.R.color.event_accent_container));
        // The accent itself as text on a plain surface (section titles, links).
        assertContrast(mode + " accent text on surface", color(activity, pro.sketchware.R.color.event_accent), result.themeColor(R.attr.colorSurface));
    }

    private static int color(Activity activity, @ColorRes int id) {
        return ContextCompat.getColor(activity, id);
    }

    private static void assertContrast(String what, int foreground, int background) {
        double ratio = ThemeHarness.contrast(foreground, background);
        assertTrue(what + ": contrast " + String.format("%.2f", ratio) + ":1 is below " + TEXT_CONTRAST + ":1", ratio >= TEXT_CONTRAST);
    }

    @Test
    public void lightThemeIsReadable() throws Exception {
        check(false);
    }

    @Test
    public void darkThemeIsReadable() throws Exception {
        check(true);
    }
}
