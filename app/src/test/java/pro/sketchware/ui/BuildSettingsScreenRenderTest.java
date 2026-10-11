package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;

/** The Build Settings screen: Simple / Advanced tabs, every setting on a card, Cancel / Save fixed at the bottom. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class BuildSettingsScreenRenderTest {
    private View inflate(boolean dark, int width, int height) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        Activity activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        View root = LayoutInflater.from(activity).inflate(R.layout.activity_build_settings, null);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        return root;
    }

    private static void save(View root, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // The window background the theme paints behind the layout on a device.
        canvas.drawColor(ThemeHarness.themeColor(root.getContext(), android.R.attr.colorBackground));
        root.draw(canvas);
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void everySettingHasItsControl() throws Exception {
        View root = inflate(true, ThemeHarness.WIDTH, ThemeHarness.HEIGHT);
        int[] ids = {R.id.tabs, R.id.profile_debug, R.id.profile_release, R.id.profile_custom, R.id.dexer_d8, R.id.dexer_dx,
                R.id.java_versions, R.id.switch_warnings, R.id.switch_logcat, R.id.android_jar, R.id.classpath,
                R.id.switch_http_legacy, R.id.row_shrinking, R.id.row_export, R.id.row_history, R.id.row_memory,
                R.id.restore_defaults, R.id.check_result, R.id.btn_cancel, R.id.btn_save};
        for (int id : ids) {
            assertNotNull(root.getResources().getResourceEntryName(id), root.findViewById(id));
        }
        // Simple first; Advanced opens from its tab.
        assertEquals(View.VISIBLE, root.findViewById(R.id.simple_page).getVisibility());
        assertEquals(View.GONE, root.findViewById(R.id.advanced_page).getVisibility());
    }

    @Test
    public void cancelAndSaveStayOnScreenAndTheContentScrolls() throws Exception {
        View root = inflate(true, ThemeHarness.WIDTH, ThemeHarness.HEIGHT);
        View bottomBar = root.findViewById(R.id.bottom_bar);
        View scroll = root.findViewById(R.id.scroll);
        assertEquals(ThemeHarness.HEIGHT, bottomBar.getBottom());
        assertTrue(scroll.getBottom() <= bottomBar.getTop());
        assertTrue(root.findViewById(R.id.btn_save).getWidth() > 0);
    }

    @Test
    public void fitsANarrowPhone() throws Exception {
        // About a 320 dp wide phone at xxhdpi.
        View root = inflate(true, 960, 1700);
        View group = root.findViewById(R.id.profile_group);
        assertTrue(group.getWidth() <= 960);
        assertTrue(root.findViewById(R.id.btn_cancel).getWidth() > 0);
        save(root, "build-settings-narrow-dark");
    }

    @Test
    public void rendersInDarkAndLight() throws Exception {
        save(inflate(true, ThemeHarness.WIDTH, ThemeHarness.HEIGHT), "build-settings-dark");
        save(inflate(false, ThemeHarness.WIDTH, ThemeHarness.HEIGHT), "build-settings-light");
    }
}
