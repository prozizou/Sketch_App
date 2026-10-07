package com.besome.sketch.editor.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, application = android.app.Application.class)
public class PhoneFrameDrawableTest {
    private static final PhoneFrame WATERDROP = PhoneFrame.byKey("waterdrop");
    private static final PhoneFrame CLASSIC = PhoneFrame.byKey("classic");

    private Bitmap asset(PhoneFrame frame) {
        Context context = ApplicationProvider.getApplicationContext();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(context.getResources(), frame.drawableRes(), options);
    }

    @Test
    public void bezelIsAsThickAsRequestedWhenThereIsRoom() {
        float scale = PhoneFrameDrawable.scaleFor(WATERDROP, 18f, 100f);

        assertEquals(18f / 27f, scale, 0.0001f);
    }

    @Test
    public void thickTopAndBottomBezelsShrinkTheScaleSoTheyStayInRoom() {
        float scale = PhoneFrameDrawable.scaleFor(CLASSIC, 50f, 40f);

        assertTrue(scale * Math.max(CLASSIC.insetTop(), CLASSIC.insetBottom()) <= 40f + 0.001f);
    }

    @Test
    public void outerBoundsSurroundTheScreenByTheBezel() {
        RectF outer = PhoneFrameDrawable.outerBounds(WATERDROP, 40f, 60f, 300f, 600f, 1f);

        assertEquals(40f - WATERDROP.insetLeft(), outer.left, 0.001f);
        assertEquals(60f - WATERDROP.insetTop(), outer.top, 0.001f);
        assertEquals(340f + WATERDROP.insetRight(), outer.right, 0.001f);
        assertEquals(660f + WATERDROP.insetBottom(), outer.bottom, 0.001f);
    }

    @Test
    public void everyFrameIsDescribedConsistently() {
        HashSet<String> keys = new HashSet<>();
        for (PhoneFrame frame : PhoneFrame.ALL) {
            assertTrue("duplicate key " + frame.key(), keys.add(frame.key()));
            assertTrue(frame.key(), frame.insetLeft() > 0 && frame.insetRight() > 0 && frame.insetTop() > 0 && frame.insetBottom() > 0);
            assertTrue(frame.key() + " corners must not meet", frame.corner() * 2 < frame.width());
            assertTrue(frame.key() + " fixed rows must not meet", frame.topFixed() + frame.bottomFixed() < frame.height());
            if (frame.hasFeature()) {
                assertTrue(frame.key() + " feature inside the picture", frame.featureStart() >= 0 && frame.featureEnd() <= frame.width());
            }
        }
    }

    @Test
    public void everyAssetMatchesItsDescriptionAndHasASeeThroughScreen() {
        for (PhoneFrame frame : PhoneFrame.ALL) {
            Bitmap bitmap = asset(frame);
            assertNotNull(frame.key(), bitmap);
            assertEquals(frame.key() + " width", frame.width(), bitmap.getWidth());
            assertEquals(frame.key() + " height", frame.height(), bitmap.getHeight());

            int mid = frame.height() / 2;
            assertEquals(frame.key() + " screen centre must be see-through", 0, Color.alpha(bitmap.getPixel(frame.width() / 2, mid)));
            assertEquals(frame.key() + " outside the phone must be see-through", 0, Color.alpha(bitmap.getPixel(0, 0)));
            // Half-way into the left bezel there has to be something drawn.
            assertTrue(frame.key() + " bezel must be opaque", Color.alpha(bitmap.getPixel(frame.insetLeft() / 2, mid)) > 200);
        }
    }

    @Test
    public void unknownKeysFallBackAndNoneMeansNoFrame() {
        assertNull(PhoneFrame.byKey(PhoneFrame.NONE_KEY));
        assertSame(PhoneFrame.byKey(PhoneFrame.DEFAULT_KEY), PhoneFrame.byKey("does-not-exist"));
        assertSame(PhoneFrame.ALL.get(0), PhoneFrame.byKey(PhoneFrame.DEFAULT_KEY));
    }

    private Bitmap draw(PhoneFrame frame, int screenW, int screenH, String name) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PhoneFrameDrawable drawable = new PhoneFrameDrawable(context.getResources(), frame);
        float scale = PhoneFrameDrawable.scaleFor(frame, 7 * 2.6f, 40 * 2.6f);
        drawable.setScale(scale);
        int margin = 60;
        RectF outer = PhoneFrameDrawable.outerBounds(frame, margin, margin, screenW, screenH, scale);
        Bitmap bitmap = Bitmap.createBitmap(Math.round(outer.right) + margin, Math.round(outer.bottom) + margin, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF1F2027);
        Paint screen = new Paint();
        screen.setColor(Color.WHITE);
        canvas.drawRect(margin, margin, margin + screenW, margin + screenH, screen);
        drawable.setBounds(Math.round(outer.left), Math.round(outer.top), Math.round(outer.right), Math.round(outer.bottom));
        drawable.draw(canvas);
        File dir = new File("build/reports/theme-renders");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return bitmap;
    }

    @Test
    public void screenStaysSeeThroughAndTheBezelIsDrawnForEveryFrame() throws Exception {
        for (PhoneFrame frame : PhoneFrame.ALL) {
            Bitmap bitmap = draw(frame, 400, 800, "phone-frame-" + frame.key());
            assertEquals(frame.key() + " shows the preview", Color.WHITE, bitmap.getPixel(60 + 200, 60 + 400));
        }
    }

    @Test
    public void adaptsToAnyPreviewProportions() throws Exception {
        for (PhoneFrame frame : PhoneFrame.ALL) {
            draw(frame, 800, 400, "tmp-landscape");
            draw(frame, 250, 900, "tmp-tall");
            draw(frame, 40, 60, "tmp-tiny");
        }
    }
}
