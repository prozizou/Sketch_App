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

/** The View editor's layouts inflate with the resizable sidebar and the movable zoom bar. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class ViewEditorRenderTest {
    private View inflate(int layout, boolean dark) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        Activity activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        View root = LayoutInflater.from(activity).inflate(layout, null);
        root.measure(View.MeasureSpec.makeMeasureSpec(ThemeHarness.WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ThemeHarness.HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, ThemeHarness.WIDTH, ThemeHarness.HEIGHT);
        return root;
    }

    private static void save(View root, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(ThemeHarness.WIDTH, ThemeHarness.HEIGHT, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void sidebarIsNarrowAndHasAGrabber() throws Exception {
        View root = inflate(R.layout.view_editor, true);

        View palette = root.findViewById(R.id.layout_palette);
        assertNotNull(root.findViewById(R.id.palette_resize_handle));
        assertEquals(72, Math.round(palette.getWidth() / root.getResources().getDisplayMetrics().density));
        save(root, "view-editor-dark");
    }

    @Test
    public void mainScreenKeepsTabsAndTheRunBar() throws Exception {
        View root = inflate(R.layout.design, true);

        assertNotNull(root.findViewById(R.id.tab_layout));
        assertNotNull(root.findViewById(R.id.btn_run));
        assertNotNull(root.findViewById(R.id.file_name_container));
        save(root, "design-dark");
    }
}
