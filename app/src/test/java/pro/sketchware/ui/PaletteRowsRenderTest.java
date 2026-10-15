package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.besome.sketch.editor.view.palette.PaletteWidget;

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

/** The widget palette lists each widget as a full row (icon, name, drag handle) under accordion headers. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class PaletteRowsRenderTest {
    private PaletteWidget palette(boolean dark) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        Activity activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        PaletteWidget palette = new PaletteWidget(activity);
        palette.a(PaletteWidget.a.a, "");
        palette.a(PaletteWidget.a.b, "");
        palette.a(PaletteWidget.a.c, "");
        palette.a(PaletteWidget.a.d, "");
        palette.extraTitle("AndroidX", 0);
        palette.extraWidgetLayout("", "TabLayout");
        palette.extraWidgetLayout("", "CardView");
        palette.a(PaletteWidget.b.b, "", "TextView", "TextView");
        palette.a(PaletteWidget.b.c, "", "EditText", "EditText");
        palette.a(PaletteWidget.b.a, "", "Button", "Button");
        palette.a(PaletteWidget.b.d, "", "ImageView", "ImageView");
        palette.a(PaletteWidget.b.h, "", "WebView", "WebView");
        palette.refreshSections();
        return palette;
    }

    private static int layout(View view, int widthDp, int heightDp) {
        float density = view.getResources().getDisplayMetrics().density;
        int w = Math.round(widthDp * density);
        int h = Math.round(heightDp * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        return h;
    }

    private static void save(View view, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff000000);
        view.draw(new Canvas(bitmap));
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void rowsShowIconNameAndHandle() throws Exception {
        for (boolean dark : new boolean[]{true, false}) {
            PaletteWidget palette = palette(dark);
            layout(palette, 152, 700);
            ViewGroup widgets = palette.findViewById(R.id.widget);
            View row = widgets.getChildAt(0);
            float density = row.getResources().getDisplayMetrics().density;
            assertTrue(row.getHeight() >= Math.round(46 * density));
            TextView name = row.findViewById(R.id.tv_widget);
            assertEquals("TextView", name.getText().toString());
            assertEquals(1, name.getLayout().getLineCount());
            View handle = row.findViewById(R.id.img_drag_handle);
            assertTrue("the drag handle is at the end of the row", handle.getLeft() > name.getLeft());
            TextView header = palette.findViewById(R.id.tv_layout);
            assertNotNull(header.getCompoundDrawablesRelative()[0]);
            assertNotNull(header.getCompoundDrawablesRelative()[2]);
            save(palette, "palette-rows-" + (dark ? "dark" : "light"));
        }
    }
}
