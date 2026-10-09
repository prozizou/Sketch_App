package pro.sketchware.editor.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import pro.sketchware.editor.layout.Box;

public class PreviewTest {
    @Test
    public void landscapeSwapsWidthAndHeight() {
        DevicePreset phone = DevicePreset.byKey("phone");
        assertEquals(360, phone.width(Orientation.PORTRAIT));
        assertEquals(800, phone.height(Orientation.PORTRAIT));
        assertEquals(800, phone.width(Orientation.LANDSCAPE));
        assertEquals(360, phone.height(Orientation.LANDSCAPE));
        assertEquals(Orientation.LANDSCAPE, Orientation.PORTRAIT.flip());
    }

    @Test
    public void unknownKeyFallsBackToThisDevice() {
        assertTrue(DevicePreset.byKey("nope").isThisDevice());
        assertTrue(DevicePreset.byKey(null).isThisDevice());
        assertSame(DevicePreset.ALL.get(0), DevicePreset.byKey(DevicePreset.THIS_DEVICE_KEY));
    }

    @Test
    public void presetKeysAreUniqueAndSizesPositive() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (DevicePreset preset : DevicePreset.ALL) {
            assertTrue(preset.key(), keys.add(preset.key()));
            if (!preset.isThisDevice()) {
                assertTrue(preset.widthDp() > 0 && preset.heightDp() >= preset.widthDp());
            }
        }
    }

    @Test
    public void sizeClassesFollowMaterialBreakpoints() {
        assertEquals(WindowSizeClass.Bucket.COMPACT, WindowSizeClass.of(599, 800).width());
        assertEquals(WindowSizeClass.Bucket.MEDIUM, WindowSizeClass.of(600, 800).width());
        assertEquals(WindowSizeClass.Bucket.MEDIUM, WindowSizeClass.of(839, 800).width());
        assertEquals(WindowSizeClass.Bucket.EXPANDED, WindowSizeClass.of(840, 800).width());
        assertEquals(WindowSizeClass.Bucket.COMPACT, WindowSizeClass.of(800, 479).height());
        assertEquals(WindowSizeClass.Bucket.EXPANDED, WindowSizeClass.of(800, 900).height());
        assertEquals("Compact × Medium", WindowSizeClass.of(360, 640).label());
    }

    @Test
    public void aPhoneTurnedSidewaysIsMediumWidthAndCompactHeight() {
        DevicePreset phone = DevicePreset.byKey("phone");
        WindowSizeClass size = WindowSizeClass.of(phone.width(Orientation.LANDSCAPE), phone.height(Orientation.LANDSCAPE));
        assertEquals(WindowSizeClass.Bucket.MEDIUM, size.width());
        assertEquals(WindowSizeClass.Bucket.COMPACT, size.height());
    }

    @Test
    public void safeAreaOfAPhoneInPortraitReservesTopAndBottom() {
        SafeArea area = SafeArea.of(DevicePreset.byKey("phone-large"), Orientation.PORTRAIT, 0, 0);
        assertEquals(32, area.topInset());      // cut-out reaches further than the status bar
        Box safe = area.safeBox();
        assertEquals(32f, safe.top(), 0f);
        assertEquals(915f - 16f, safe.bottom(), 0f);
        assertNull(area.hinge());
    }

    @Test
    public void cutoutMovesToTheSideInLandscape() {
        SafeArea area = SafeArea.of(DevicePreset.byKey("phone-large"), Orientation.LANDSCAPE, 0, 0);
        assertEquals(915, area.width());
        assertEquals(24, area.topInset());
        assertEquals(32, area.cutoutSide());
        assertEquals(32f, area.safeBox().left(), 0f);
    }

    @Test
    public void thisDeviceUsesTheRealSize() {
        SafeArea area = SafeArea.of(DevicePreset.byKey("device"), Orientation.PORTRAIT, 393, 851);
        assertEquals(393, area.width());
        assertEquals(851, area.height());
    }

    @Test
    public void foldableHingeSplitsTheWidthInPortraitAndTheHeightInLandscape() {
        DevicePreset open = DevicePreset.byKey("foldable-open");
        Box portrait = SafeArea.of(open, Orientation.PORTRAIT, 0, 0).hinge();
        assertNotNull(portrait);
        assertEquals(350f - 12f, portrait.left(), 0.001f);
        assertEquals(350f + 12f, portrait.right(), 0.001f);
        Box landscape = SafeArea.of(open, Orientation.LANDSCAPE, 0, 0).hinge();
        assertEquals(350f - 12f, landscape.top(), 0.001f);
        assertEquals(840f, landscape.width(), 0.001f);
    }

    @Test
    public void detectsWidgetsCrossingTheHinge() {
        SafeArea area = SafeArea.of(DevicePreset.byKey("foldable-open"), Orientation.PORTRAIT, 0, 0);
        assertTrue(area.crossesHinge(new Box(300, 100, 400, 140)));
        assertFalse(area.crossesHinge(new Box(10, 100, 200, 140)));
        assertFalse(SafeArea.of(DevicePreset.byKey("phone"), Orientation.PORTRAIT, 0, 0).crossesHinge(new Box(0, 0, 360, 800)));
    }

    @Test
    public void unsafeBoxesIncludeTheHinge() {
        assertEquals(3, SafeArea.of(DevicePreset.byKey("foldable-open"), Orientation.PORTRAIT, 0, 0).unsafeBoxes().length);
    }
}
