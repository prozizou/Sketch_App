package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import pro.sketchware.colorwheel.ColorWheelView;

/** The color wheel dialog: a round wheel with a ball, brightness and opacity, and the hex field. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class ColorWheelRenderTest {
    private View inflate(boolean dark, int width) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        Activity activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_color_wheel, null);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        return root;
    }

    @Test
    public void wheelIsRoundAndEveryControlIsThere() throws Exception {
        View root = inflate(true, 960);
        ColorWheelView wheel = root.findViewById(R.id.wheel);
        assertEquals(wheel.getWidth(), wheel.getHeight());
        assertTrue(wheel.getWidth() > 0);
        for (int id : new int[]{R.id.brightness, R.id.opacity, R.id.preview, R.id.hex}) {
            assertNotNull(root.findViewById(id));
        }
        save(root, "color-wheel-dark");
        save(inflate(false, 960), "color-wheel-light");
    }

    @Test
    public void wheelStaysAWheelOnTablets() throws Exception {
        View root = inflate(false, 2400);
        View wheel = root.findViewById(R.id.wheel);
        assertTrue(wheel.getWidth() <= Math.round(320 * root.getResources().getDisplayMetrics().density));
    }

    @Test
    public void draggingTheBallPicksHueAndSaturation() throws Exception {
        View root = inflate(false, 960);
        ColorWheelView wheel = root.findViewById(R.id.wheel);
        float[] picked = new float[2];
        wheel.setOnColorChangedListener((hue, saturation, fromUser) -> {
            picked[0] = hue;
            picked[1] = saturation;
        });
        float cx = wheel.getWidth() / 2f;
        float cy = wheel.getHeight() / 2f;
        long t = SystemClock.uptimeMillis();
        // Far below the centre: hue 90° (yellow-green side), full saturation.
        wheel.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy + wheel.getHeight(), 0));
        assertEquals(90, picked[0], 0.5);
        assertEquals(1, picked[1], 0.001);
        // The centre is white: no saturation.
        wheel.dispatchTouchEvent(MotionEvent.obtain(t, t + 16, MotionEvent.ACTION_MOVE, cx, cy, 0));
        assertEquals(0, picked[1], 0.001);
    }

    private static void save(View root, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ThemeHarness.themeColor(root.getContext(), com.google.android.material.R.attr.colorSurfaceContainerHigh));
        root.draw(canvas);
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
