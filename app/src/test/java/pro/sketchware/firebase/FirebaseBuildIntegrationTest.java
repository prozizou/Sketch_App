package pro.sketchware.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import a.a.a.Lx;
import a.a.a.jq;
import mod.jbk.build.BuiltInLibraries;
import pro.sketchware.util.library.BuiltInLibraryManager;

/** Firebase in the build: each dependency is declared once, whatever combination of services a project uses. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class FirebaseBuildIntegrationTest {
    /** The build code reads the application context, which a real launch sets. */
    @Before
    public void giveTheAppItsContext() throws Exception {
        android.app.Application app = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        java.lang.reflect.Field context = pro.sketchware.SketchApplication.class.getDeclaredField("mApplicationContext");
        context.setAccessible(true);
        context.set(null, app);
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) n++;
        return n;
    }

    private static jq metadata(boolean auth, boolean database, boolean storage) {
        jq m = new jq();
        m.sc_id = "999";
        m.packageName = "com.demo.app";
        m.isFirebaseEnabled = true;
        m.isFirebaseAuthUsed = auth;
        m.isFirebaseDatabaseUsed = database;
        m.isFirebaseStorageUsed = storage;
        return m;
    }

    @Test
    public void theExportedGradleFileDeclaresTheBomAndEachServiceOnce() {
        String gradle = Lx.getBuildGradleString(34, 26, "34", metadata(true, true, true), false);
        assertEquals(1, count(gradle, "firebase-bom"));
        assertEquals(1, count(gradle, "com.google.firebase:firebase-auth'"));
        assertEquals(1, count(gradle, "com.google.firebase:firebase-database'"));
        assertEquals(1, count(gradle, "com.google.firebase:firebase-storage'"));
    }

    @Test
    public void onlyTheServicesAProjectUsesAreDeclared() {
        String gradle = Lx.getBuildGradleString(34, 26, "34", metadata(false, true, false), false);
        assertEquals(1, count(gradle, "firebase-bom"));
        assertEquals(1, count(gradle, "firebase-database"));
        assertFalse(gradle.contains("firebase-auth"));
        assertFalse(gradle.contains("firebase-storage"));
    }

    @Test
    public void aProjectWithFirebaseOffDeclaresNothing() {
        jq m = metadata(true, true, true);
        m.isFirebaseEnabled = false;
        assertFalse(Lx.getBuildGradleString(34, 26, "34", m, false).contains("firebase-bom"));
    }

    @Test
    public void theBuiltInLibraryListNeverHoldsTheSameLibraryTwice() {
        BuiltInLibraryManager manager = new BuiltInLibraryManager("999");
        manager.addLibrary(BuiltInLibraries.FIREBASE_COMMON);
        manager.addLibrary(BuiltInLibraries.FIREBASE_COMMON);
        manager.addLibrary(BuiltInLibraries.FIREBASE_AUTH);
        manager.addLibrary(BuiltInLibraries.FIREBASE_AUTH);
        manager.addLibrary(BuiltInLibraries.FIREBASE_DATABASE);
        manager.addLibrary(BuiltInLibraries.FIREBASE_STORAGE);
        manager.addLibrary(BuiltInLibraries.FIREBASE_STORAGE);
        long common = manager.getLibraries().stream().filter(l -> l.getName().equals(BuiltInLibraries.FIREBASE_COMMON)).count();
        long auth = manager.getLibraries().stream().filter(l -> l.getName().equals(BuiltInLibraries.FIREBASE_AUTH)).count();
        long storage = manager.getLibraries().stream().filter(l -> l.getName().equals(BuiltInLibraries.FIREBASE_STORAGE)).count();
        assertEquals(1, common);
        assertEquals(1, auth);
        assertEquals(1, storage);
        assertTrue(manager.containsLibrary(BuiltInLibraries.FIREBASE_DATABASE));
    }
}
