package pro.sketchware.branding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import pro.sketchware.R;

/** The app is called NWS and its logo is the see-through emblem in every place it is used. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "xxxhdpi", application = android.app.Application.class)
public class BrandingTest {
    private Bitmap decode(int id) {
        Context context = ApplicationProvider.getApplicationContext();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), id, options);
        assertNotNull(bitmap);
        return bitmap;
    }

    @Test
    public void theAppIsCalledNws() {
        Context context = ApplicationProvider.getApplicationContext();

        assertEquals("NWS", context.getString(R.string.app_name));
        assertEquals("NWS", context.getString(R.string.title_sketchware));
    }

    @Test
    public void launcherForegroundIsACenteredEmblemWithATransparentSurround() {
        Bitmap icon = decode(R.mipmap.ic_launcher_foreground);

        assertEquals(432, icon.getWidth());
        assertEquals(432, icon.getHeight());
        assertEquals("corner is see-through", 0, Color.alpha(icon.getPixel(2, 2)));
        assertEquals("centre is the emblem", 255, Color.alpha(icon.getPixel(216, 216)));
        // The emblem has to stay inside the 72dp circle every launcher mask shows (432px canvas = 108dp).
        float maxRadius = 36f * 4f;
        for (int angle = 0; angle < 360; angle += 15) {
            double rad = Math.toRadians(angle);
            int x = (int) Math.round(216 + Math.cos(rad) * (maxRadius + 3));
            int y = (int) Math.round(216 + Math.sin(rad) * (maxRadius + 3));
            assertEquals("nothing outside the safe circle at " + angle + " degrees", 0, Color.alpha(icon.getPixel(x, y)));
        }
    }

    @Test
    public void splashLogoFitsInsideTheCircleAndroidShows() {
        Bitmap splash = decode(R.drawable.splash_logo);

        assertEquals(576, splash.getWidth());
        // The system shows a circle two thirds of the 288dp icon area.
        int visibleRadius = splash.getWidth() / 3;
        int cx = splash.getWidth() / 2;
        assertEquals(0, Color.alpha(splash.getPixel(2, 2)));
        assertEquals(255, Color.alpha(splash.getPixel(cx, cx)));
        assertEquals("emblem edge stays inside the visible circle", 0, Color.alpha(splash.getPixel(cx + visibleRadius + 4, cx)));
    }

    @Test
    public void inAppLogoIsATransparentCircle() {
        Bitmap logo = decode(R.drawable.nws_logo);

        assertEquals(logo.getWidth(), logo.getHeight());
        assertEquals(0, Color.alpha(logo.getPixel(1, 1)));
        assertTrue(Color.alpha(logo.getPixel(logo.getWidth() / 2, logo.getHeight() / 2)) == 255);
    }
}
