package pro.sketchware.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

import a.a.a.Rs;
import a.a.a.Ss;
import pro.sketchware.SketchApplication;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-night-xxhdpi", application = android.app.Application.class)
public class ColorArgSwatchTest {
    private Activity activity() throws Exception {
        java.lang.reflect.Field f = SketchApplication.class.getDeclaredField("mApplicationContext");
        f.setAccessible(true);
        f.set(null, androidx.test.core.app.ApplicationProvider.getApplicationContext());
        return Robolectric.buildActivity(Activity.class).setup().get();
    }

    private static Ss colorSlot(Rs block) {
        for (View arg : block.V) {
            if (arg instanceof Ss slot && "color".equals(slot.getMenuName())) {
                return slot;
            }
        }
        throw new AssertionError("no color slot");
    }

    @Test
    public void parsesColourCodesAndLeavesResourceNamesAlone() {
        assertEquals(Integer.valueOf(0xFFAA3366), ColorArgSwatch.parseColor("0xFFAA3366"));
        assertEquals(Integer.valueOf(0xFF112233), ColorArgSwatch.parseColor("0x112233"));
        assertEquals(Integer.valueOf(0xFF2CA5E2), ColorArgSwatch.parseColor("#2CA5E2"));
        assertEquals(Integer.valueOf(0), ColorArgSwatch.parseColor("Color.TRANSPARENT"));
        assertEquals(Integer.valueOf(0), ColorArgSwatch.parseColor(""));
        assertNull(ColorArgSwatch.parseColor("R.color.primary"));
        assertNull(ColorArgSwatch.parseColor("R.attr.colorPrimary"));
        assertNull(ColorArgSwatch.parseColor("0xZZ"));
    }

    @Test
    public void swatchReplacesTheCodeButTheValueIsKept() throws Exception {
        Rs block = new Rs(activity(), 7, "", " ", "setBgColor");
        ColorArgSwatch.attach(block);
        Ss slot = colorSlot(block);

        slot.setArgValue("0xFFAA3366");

        String shown = slot.V.getText().toString();
        assertEquals("the block reads its value back from the field", "0xFFAA3366", shown);
        assertEquals("the text is invisible", android.graphics.Color.TRANSPARENT, slot.V.getCurrentTextColor());
        assertNotNull(slot.V.getBackground());
        assertEquals("the real value is untouched", "0xFFAA3366", slot.getArgValue().toString());
    }

    @Test
    public void swatchFollowsANewColourImmediately() throws Exception {
        Rs block = new Rs(activity(), 7, "", " ", "setBgColor");
        ColorArgSwatch.attach(block);
        Ss slot = colorSlot(block);

        slot.setArgValue("0xFFAA3366");
        int first = ((ColorArgSwatch.SwatchDrawable) slot.V.getBackground()).getColor();
        slot.setArgValue("0xFF112233");
        int second = ((ColorArgSwatch.SwatchDrawable) slot.V.getBackground()).getColor();

        assertEquals(0xFFAA3366, first);
        assertEquals(0xFF112233, second);
        assertEquals("0xFF112233", slot.getArgValue().toString());
    }

    @Test
    public void resourceNamesStayReadable() throws Exception {
        Rs block = new Rs(activity(), 7, "", " ", "setBgColor");
        ColorArgSwatch.attach(block);
        Ss slot = colorSlot(block);

        slot.setArgValue("R.color.primary");

        assertEquals("R.color.primary", slot.V.getText().toString());
        assertNull(slot.V.getBackground());
        assertNotEquals(android.graphics.Color.TRANSPARENT, slot.V.getCurrentTextColor());
    }

    @Test
    public void rendersTheBlock() throws Exception {
        Activity activity = activity();
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121318);
        for (String value : new String[]{"0xFFAA3366", "0xFF2CA5E2", "Color.TRANSPARENT"}) {
            Rs block = new Rs(activity, 7, "", " ", "setBgColor");
            ColorArgSwatch.attach(block);
            colorSlot(block).setArgValue(value);
            block.k();
            root.addView(block);
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 600);
        Bitmap bitmap = Bitmap.createBitmap(1080, 600, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File("build/reports/theme-renders");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, "color-swatch.png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
