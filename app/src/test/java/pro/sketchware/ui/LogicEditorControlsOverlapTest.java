package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;

/**
 * The floating zoom bar of the Logic editor and the right drawer: the bar is never wider than the space beside the
 * drawer, even with a long translation of "Fit", and the open drawer is drawn above the bar.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w320dp-h640dp-xxhdpi", application = android.app.Application.class)
public class LogicEditorControlsOverlapTest {
    private View inflate(int width, int height, String fitLabel) throws Exception {
        RuntimeEnvironment.setQualifiers("+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        Activity activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        View root = LayoutInflater.from(activity).inflate(R.layout.logic_editor, null);
        if (fitLabel != null) {
            ((TextView) root.findViewById(R.id.btn_canvas_fit)).setText(fitLabel);
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        return root;
    }

    private static void assertBarBesideDrawer(View root) {
        View bar = root.findViewById(R.id.canvas_controls);
        View drawer = root.findViewById(R.id.right_drawer);
        assertTrue("zoom bar " + bar.getWidth() + "px must fit beside the drawer at " + drawer.getLeft(),
                bar.getWidth() <= drawer.getLeft());
        assertTrue(bar.getWidth() > 0);
        TextView fit = root.findViewById(R.id.btn_canvas_fit);
        assertEquals(1, fit.getLineCount());
    }

    @Test
    public void barFitsBesideTheDrawerOnASmallPhone() throws Exception {
        // 320 dp wide at xxhdpi.
        assertBarBesideDrawer(inflate(960, 1920, null));
    }

    @Test
    public void longTranslationDoesNotPushTheBarUnderTheDrawer() throws Exception {
        assertBarBesideDrawer(inflate(960, 1920, "Ajuster tous les blocs à l'écran maintenant"));
    }

    @Test
    public void barFitsOnALargeScreenToo() throws Exception {
        assertBarBesideDrawer(inflate(2560, 1600, null));
    }

    @Test
    public void openDrawerIsDrawnAboveTheBar() throws Exception {
        View root = inflate(960, 1920, null);
        View bar = root.findViewById(R.id.canvas_controls);
        View drawer = root.findViewById(R.id.right_drawer);
        assertTrue(drawer.getZ() > bar.getZ());
    }

    /** One floating button above the palette, and it opens the blocks collection (the tabs open the palette). */
    @Test
    public void theBlocksButtonIsTheCollectionButton() throws Exception {
        View root = inflate(960, 1920, null);
        android.view.ViewGroup row = root.findViewById(R.id.fab_row);
        assertEquals(1, row.getChildCount());
        View blocks = root.findViewById(R.id.fab_toggle_palette);
        assertEquals(root.getResources().getString(R.string.common_word_blocks_collection), blocks.getContentDescription());
    }
}
