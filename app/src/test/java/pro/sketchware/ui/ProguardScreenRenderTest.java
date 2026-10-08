package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

/** The code shrinking screen: three plain levels first, the expert options folded away. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class ProguardScreenRenderTest {
    private View inflate(boolean dark) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        Activity activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        View root = LayoutInflater.from(activity).inflate(R.layout.manage_proguard, null);
        root.measure(View.MeasureSpec.makeMeasureSpec(ThemeHarness.WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ThemeHarness.HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, ThemeHarness.WIDTH, ThemeHarness.HEIGHT);
        return root;
    }

    private static void save(View root, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(ThemeHarness.WIDTH, ThemeHarness.HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF111318);
        root.draw(canvas);
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void theThreeLevelsAreThereAndTheExpertOptionsAreFolded() throws Exception {
        View root = inflate(true);

        assertNotNull(root.findViewById(R.id.card_mode_off));
        assertNotNull(root.findViewById(R.id.card_mode_safe));
        assertNotNull(root.findViewById(R.id.card_mode_max));
        assertEquals(View.GONE, root.findViewById(R.id.ln_advanced_content).getVisibility());
        // The old controls are still reachable once the advanced section opens.
        assertNotNull(root.findViewById(R.id.r8_enabled));
        assertNotNull(root.findViewById(R.id.ln_pg_rules));
        assertNotNull(root.findViewById(R.id.ln_pg_fm));
        assertNotNull(root.findViewById(R.id.sw_pg_debug));
    }

    @Test
    public void rendersInDarkAndLight() throws Exception {
        save(inflate(true), "proguard-dark");
        save(inflate(false), "proguard-light");
    }
}
