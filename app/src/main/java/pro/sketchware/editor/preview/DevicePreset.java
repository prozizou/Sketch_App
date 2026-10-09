package pro.sketchware.editor.preview;

import java.util.List;

/**
 * A screen to preview a layout on, in dp. Sizes are those of common Android classes of device, not of one
 * brand. A foldable's hinge is a strip that content should not cross.
 *
 * @param key           stable name saved in the preferences
 * @param name          shown in the picker
 * @param kind          phone, tablet or foldable
 * @param widthDp       width in portrait
 * @param heightDp      height in portrait
 * @param cutoutDp      height of a camera cut-out at the top in portrait (0 for none)
 * @param hingeWidthDp  width of the hinge strip in the middle of the screen (0 for none); runs along the
 *                      long side in portrait (a book held upright) and so splits the width in two
 */
public record DevicePreset(String key, String name, Kind kind, int widthDp, int heightDp, int cutoutDp, int hingeWidthDp) {
    public enum Kind {
        THIS_DEVICE, PHONE, TABLET, FOLDABLE
    }

    /** The key meaning "use the real screen of the device running the app"; its size is not stored here. */
    public static final String THIS_DEVICE_KEY = "device";

    public static final List<DevicePreset> ALL = List.of(
            new DevicePreset(THIS_DEVICE_KEY, "This device", Kind.THIS_DEVICE, 0, 0, 0, 0),
            new DevicePreset("phone-small", "Small phone", Kind.PHONE, 320, 568, 0, 0),
            new DevicePreset("phone", "Phone", Kind.PHONE, 360, 800, 24, 0),
            new DevicePreset("phone-large", "Large phone", Kind.PHONE, 412, 915, 32, 0),
            new DevicePreset("tablet-7", "Tablet 7\"", Kind.TABLET, 600, 960, 0, 0),
            new DevicePreset("tablet-10", "Tablet 10\"", Kind.TABLET, 800, 1280, 0, 0),
            new DevicePreset("foldable-closed", "Foldable, closed", Kind.FOLDABLE, 360, 800, 24, 0),
            new DevicePreset("foldable-open", "Foldable, open", Kind.FOLDABLE, 700, 840, 0, 24));

    public boolean isThisDevice() {
        return kind == Kind.THIS_DEVICE;
    }

    /** Width in dp for the orientation. Not meaningful for "this device". */
    public int width(Orientation orientation) {
        return orientation == Orientation.PORTRAIT ? widthDp : heightDp;
    }

    public int height(Orientation orientation) {
        return orientation == Orientation.PORTRAIT ? heightDp : widthDp;
    }

    /** The preset saved under {@code key}; "this device" for an unknown key. */
    public static DevicePreset byKey(String key) {
        for (DevicePreset preset : ALL) {
            if (preset.key.equals(key)) {
                return preset;
            }
        }
        return ALL.get(0);
    }
}
