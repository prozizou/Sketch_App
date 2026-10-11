package pro.sketchware.colorwheel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ColorWheelMathTest {
    @Test
    public void angleIsTheHueAndDistanceTheSaturation() {
        float[] right = ColorWheelMath.hueSaturationAt(100, 0, 100);
        assertEquals(0, right[0], 0.01);
        assertEquals(1, right[1], 0.001);
        // Screen y grows downwards: straight down is 90°, as drawn by the sweep gradient.
        float[] down = ColorWheelMath.hueSaturationAt(0, 50, 100);
        assertEquals(90, down[0], 0.01);
        assertEquals(0.5, down[1], 0.001);
        assertEquals(180, ColorWheelMath.hueSaturationAt(-10, 0, 100)[0], 0.01);
        assertEquals(270, ColorWheelMath.hueSaturationAt(0, -10, 100)[0], 0.01);
    }

    @Test
    public void outsideTheWheelIsFullSaturation() {
        assertEquals(1, ColorWheelMath.hueSaturationAt(300, 400, 100)[1], 0.0001);
    }

    @Test
    public void positionAndHueSaturationRoundTrip() {
        for (float hue = 0; hue < 360; hue += 15) {
            for (float sat = 0.1f; sat <= 1f; sat += 0.3f) {
                float[] at = ColorWheelMath.positionOf(hue, sat, 200);
                float[] back = ColorWheelMath.hueSaturationAt(at[0], at[1], 200);
                assertEquals(hue, back[0], 0.05);
                assertEquals(sat, back[1], 0.001);
            }
        }
    }

    @Test
    public void hexFormatsAndParses() {
        assertEquals("#FF5722", ColorWheelMath.toHex(0xFFFF5722));
        assertEquals("#80FF5722", ColorWheelMath.toHex(0x80FF5722));
        assertEquals(Integer.valueOf(0xFFFF5722), ColorWheelMath.parseHex("#ff5722"));
        assertEquals(Integer.valueOf(0xFFFF5722), ColorWheelMath.parseHex("FF5722"));
        assertEquals(Integer.valueOf(0x80FF5722), ColorWheelMath.parseHex("#80FF5722"));
        assertEquals(Integer.valueOf(0xFFFFFFFF), ColorWheelMath.parseHex("#fff"));
        assertNull(ColorWheelMath.parseHex("#12345"));
        assertNull(ColorWheelMath.parseHex("NONE"));
        assertNull(ColorWheelMath.parseHex("@color/primary"));
        assertNull(ColorWheelMath.parseHex(null));
    }
}
