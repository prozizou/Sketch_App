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

import com.besome.sketch.editor.view.ViewLogicEditor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import a.a.a.Rs;
import a.a.a.Ss;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.blocks.ColorArgSwatch;

/**
 * The Logic editor's own layout: it must inflate with the new zoom bar and bottom navigation, show the
 * blocks, and Fit has to bring every block into view. Pictures land in build/reports/theme-renders/.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class LogicEditorRenderTest {
    private static final int WIDTH = ThemeHarness.WIDTH;
    private static final int HEIGHT = ThemeHarness.HEIGHT;

    private record Screen(Activity activity, View root, ViewLogicEditor editor, ArrayList<Rs> blocks) {
    }

    private Screen inflate(boolean dark) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        // Any activity of the app gives the real theme to inflate with.
        Activity activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        View root = LayoutInflater.from(activity).inflate(R.layout.logic_editor, null);
        ViewLogicEditor editor = root.findViewById(R.id.editor);

        ArrayList<Rs> blocks = new ArrayList<>();
        String[][] sample = {{"c", "if"}, {"c", "elseIf"}, {"c", "else"}, {" ", "setBgColor"}};
        int y = 40;
        int id = 1;
        for (String[] kind : sample) {
            Rs block = new Rs(activity, id++, "", kind[0], kind[1]);
            ColorArgSwatch.attach(block);
            if (kind[1].equals("setBgColor")) {
                for (View arg : block.V) {
                    if (arg instanceof Ss slot && "color".equals(slot.getMenuName())) {
                        slot.setArgValue("0xFF7C4DFF");
                    }
                }
            }
            block.k();
            editor.getBlockPane().a(block, 40, y);
            blocks.add(block);
            y += 200;
        }
        layout(root);
        return new Screen(activity, root, editor, blocks);
    }

    private static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH, HEIGHT);
    }

    private static void save(View root, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void zoomBarOnlyHasTheHandleThePercentageAndFit() throws Exception {
        Screen screen = inflate(true);

        assertNotNull(screen.root.findViewById(R.id.canvas_controls_handle));
        assertNotNull(screen.root.findViewById(R.id.tv_zoom));
        assertNotNull(screen.root.findViewById(R.id.btn_canvas_fit));
        assertEquals(3, ((android.view.ViewGroup) screen.root.findViewById(R.id.canvas_controls)).getChildCount());
    }

    @Test
    public void rendersInDarkAndLight() throws Exception {
        save(inflate(true).root, "logic-editor-dark");
        save(inflate(false).root, "logic-editor-light");
    }

    @Test
    public void fitBringsEveryBlockIntoView() throws Exception {
        Screen screen = inflate(true);
        // In the app the canvas grows with its blocks; give it room for blocks spread well beyond the screen.
        screen.editor.getBlockPane().getLayoutParams().width = 9000;
        screen.editor.getBlockPane().getLayoutParams().height = 9000;
        int y = 40;
        for (Rs block : screen.blocks) {
            screen.editor.getBlockPane().removeView(block);
            screen.editor.getBlockPane().a(block, 3000, y);
            y += 2500;
        }
        layout(screen.root);

        screen.editor.fitToContent();
        layout(screen.root);

        float zoom = screen.editor.getZoom();
        assertTrue("fit must zoom out, was " + zoom, zoom < 1f);
        assertEquals("the blocks have been laid out", screen.blocks.size(), screen.blocks.stream().filter(b -> b.getWidth() > 0).count());
        for (Rs block : screen.blocks) {
            float left = block.getX() * zoom - screen.editor.getScrollX();
            float top = block.getY() * zoom - screen.editor.getScrollY();
            assertTrue("block left in view: " + left, left >= -1 && left + block.getWidth() * zoom <= screen.editor.getWidth() + 1);
            assertTrue("block top in view: " + top, top >= -1 && top + block.getHeight() * zoom <= screen.editor.getHeight() + 1);
        }
        save(screen.root, "logic-editor-fit");
    }

    @Test
    public void fitWithoutBlocksGoesBackToOneHundredPercent() throws Exception {
        Screen screen = inflate(true);
        screen.editor.getBlockPane().removeAllViews();
        screen.editor.setZoom(0.5f);

        screen.editor.fitToContent();

        assertEquals(1f, screen.editor.getZoom(), 0.0001f);
    }
}
