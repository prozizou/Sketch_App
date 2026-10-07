package com.besome.sketch.editor.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
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

import pro.sketchware.R;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, application = android.app.Application.class)
public class PhoneFrameDrawableTest {
    @Test
    public void bezelIsAsThickAsRequestedWhenThereIsRoom() {
        float scale = PhoneFrameDrawable.scaleFor(18f, 100f, 100f, 100f);

        assertEquals(18f / PhoneFrameDrawable.INSET_LEFT, scale, 0.0001f);
    }

    @Test
    public void bezelShrinksToFitTheRoomAroundTheScreen() {
        float scale = PhoneFrameDrawable.scaleFor(50f, 10f, 100f, 100f);

        assertEquals(10f / PhoneFrameDrawable.INSET_LEFT, scale, 0.0001f);
        assertTrue(scale * PhoneFrameDrawable.INSET_RIGHT <= 10f + 0.001f);
    }

    @Test
    public void thickerBottomChinNeedsMoreRoomBelow() {
        float scale = PhoneFrameDrawable.scaleFor(50f, 100f, 100f, 21f);

        assertEquals(21f / PhoneFrameDrawable.INSET_BOTTOM, scale, 0.0001f);
    }

    @Test
    public void outerBoundsSurroundTheScreenByTheBezel() {
        RectF outer = PhoneFrameDrawable.outerBounds(40f, 60f, 300f, 600f, 1f);

        assertEquals(40f - PhoneFrameDrawable.INSET_LEFT, outer.left, 0.001f);
        assertEquals(60f - PhoneFrameDrawable.INSET_TOP, outer.top, 0.001f);
        assertEquals(340f + PhoneFrameDrawable.INSET_RIGHT, outer.right, 0.001f);
        assertEquals(660f + PhoneFrameDrawable.INSET_BOTTOM, outer.bottom, 0.001f);
    }

    private Bitmap draw(int screenW, int screenH, float scale, String name) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PhoneFrameDrawable frame = new PhoneFrameDrawable(context.getResources(), R.drawable.phone_frame);
        frame.setScale(scale);
        int margin = 40;
        RectF outer = PhoneFrameDrawable.outerBounds(margin, margin, screenW, screenH, scale);
        int w = Math.round(outer.right) + margin;
        int h = Math.round(outer.bottom) + margin;

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF1F2027);
        Paint screen = new Paint();
        screen.setColor(Color.WHITE);
        canvas.drawRect(margin, margin, margin + screenW, margin + screenH, screen);
        frame.setBounds(Math.round(outer.left), Math.round(outer.top), Math.round(outer.right), Math.round(outer.bottom));
        frame.draw(canvas);

        File dir = new File("build/reports/theme-renders");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return bitmap;
    }

    @Test
    public void screenStaysSeeThroughAndTheBezelIsDark() throws Exception {
        float scale = 0.8f;
        Bitmap bitmap = draw(500, 1000, scale, "phone-frame-portrait");

        assertEquals("the screen shows the preview", Color.WHITE, bitmap.getPixel(40 + 250, 40 + 500));
        int bezel = bitmap.getPixel(40 - Math.round(PhoneFrameDrawable.INSET_LEFT * scale / 2f), 40 + 500);
        assertTrue("the bezel is dark, was " + Integer.toHexString(bezel), Color.red(bezel) < 140);
        assertEquals(255, Color.alpha(bezel));
    }

    @Test
    public void adaptsToAnyPreviewProportions() throws Exception {
        // Wide (landscape) and very tall previews must still draw a complete frame without exceptions.
        draw(1000, 500, 0.5f, "phone-frame-landscape");
        draw(300, 1300, 0.6f, "phone-frame-tall");
        draw(80, 120, 0.3f, "phone-frame-tiny");
    }
}
