package pro.sketchware.logic.sync;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import java.lang.reflect.Field;

import pro.sketchware.SketchApplication;

/** Gives the app what a launch would: its application context. */
final class ThemeHarnessAccess {
    private ThemeHarnessAccess() {
    }

    static void prepare() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        Field context = SketchApplication.class.getDeclaredField("mApplicationContext");
        context.setAccessible(true);
        context.set(null, app);
    }
}
