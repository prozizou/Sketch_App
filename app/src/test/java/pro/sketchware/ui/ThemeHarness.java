package pro.sketchware.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.test.core.app.ApplicationProvider;

import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

import pro.sketchware.SketchApplication;

/**
 * Renders an activity in light or dark mode on the JVM (Robolectric, native graphics) so that both themes
 * can be checked without a device. Images land in {@code app/build/reports/theme-renders/}.
 */
final class ThemeHarness {
    static final int WIDTH = 1080;
    static final int HEIGHT = 2340;
    static final File OUTPUT_DIR = new File("build/reports/theme-renders");

    private ThemeHarness() {
    }

    /** What a rendering produced. */
    record Result(Activity activity, Bitmap bitmap, File file, boolean dark) {
        /** Mean relative luminance of the picture, 0 (black) to 1 (white). */
        double meanLuminance() {
            long step = 7; // sampling is plenty for a mean and keeps big screens fast
            double sum = 0;
            long count = 0;
            for (int y = 0; y < bitmap.getHeight(); y += step) {
                for (int x = 0; x < bitmap.getWidth(); x += step) {
                    sum += luminance(bitmap.getPixel(x, y));
                    count++;
                }
            }
            return sum / count;
        }

        int themeColor(@AttrRes int attr) {
            return ThemeHarness.themeColor(activity, attr);
        }
    }

    /**
     * Launches {@code activityClass} with the system in light or dark mode, lays it out at a typical
     * phone size and saves a PNG named {@code <name>-light|dark.png}.
     */
    static <T extends Activity> Result render(Class<T> activityClass, String name, boolean dark) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        prepareApplication();

        Context app = ApplicationProvider.getApplicationContext();
        T activity = Robolectric.buildActivity(activityClass, new Intent(app, activityClass)).setup().get();
        Robolectric.flushForegroundThreadScheduler();

        View root = activity.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, WIDTH, HEIGHT);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));

        //noinspection ResultOfMethodCallIgnored
        OUTPUT_DIR.mkdirs();
        File file = new File(OUTPUT_DIR, name + (dark ? "-dark" : "-light") + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return new Result(activity, bitmap, file, dark);
    }

    /** Gives the app what a real launch would: the application context and the storage permission. */
    private static void prepareApplication() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        Field context = SketchApplication.class.getDeclaredField("mApplicationContext");
        context.setAccessible(true);
        context.set(null, app);

        // ShadowApplication is referenced reflectively: it isn't on the compile classpath of the unit tests.
        Object shadow = Class.forName("org.robolectric.Shadows").getMethod("shadowOf", Application.class).invoke(null, app);
        shadow.getClass().getMethod("grantPermissions", String[].class).invoke(shadow,
                (Object) new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE});
    }

    static int themeColor(Context context, @AttrRes int attr) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            throw new IllegalStateException("Theme attribute not found: " + attr);
        }
        return value.data;
    }

    /** WCAG relative luminance of an sRGB color. */
    static double luminance(int color) {
        double r = channel((color >> 16) & 0xFF);
        double g = channel((color >> 8) & 0xFF);
        double b = channel(color & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** WCAG contrast ratio, 1 (none) to 21 (black on white). */
    static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }
}
