package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.firebase.ManageFirebaseActivity;
import com.google.android.material.chip.ChipGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;

import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import pro.sketchware.R;
import pro.sketchware.firebase.GoogleServicesConfig;

/** The Firebase Manager screen: one editor, the summary, the status, saving, leaving, deleting and migration. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class FirebaseManagerScreenTest {
    private static final String KEY = "AIza" + "A".repeat(35);
    private static final String APP_ID = "1:123456789012:android:0123456789abcdef";
    private static final String HOST = "demo-app-12345-default-rtdb.firebaseio.com";
    private static final String BUCKET = "demo-app-12345.appspot.com";
    private static final String PACKAGE = "com.demo.app";

    private static ProjectLibraryBean legacyEntry(boolean enabled) {
        ProjectLibraryBean bean = new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE);
        bean.data = HOST;
        bean.reserved1 = APP_ID;
        bean.reserved2 = KEY;
        bean.reserved3 = BUCKET;
        bean.useYn = enabled ? "Y" : "N";
        return bean;
    }

    private static ActivityController<ManageFirebaseActivity> open(ProjectLibraryBean bean, String projectPackage, boolean dark) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(app, ManageFirebaseActivity.class)
                .putExtra("sc_id", "601")
                .putExtra("project_package", projectPackage)
                .putExtra("firebase", bean);
        return Robolectric.buildActivity(ManageFirebaseActivity.class, intent).setup();
    }

    private static <T extends View> T view(Activity a, int id) {
        return a.findViewById(id);
    }

    private static void idle() {
        ShadowLooper.idleMainLooper(800, TimeUnit.MILLISECONDS);
    }

    private static String text(Activity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private static String editor(Activity a) {
        return ((CodeEditor) view(a, R.id.json_editor)).getText().toString();
    }

    private static void clip(String text) {
        ClipboardManager clipboard = ApplicationProvider.getApplicationContext().getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("json", text));
    }

    private static String allText(View v) {
        StringBuilder sb = new StringBuilder();
        collect(v, sb);
        return sb.toString();
    }

    private static void collect(View v, StringBuilder sb) {
        if (v instanceof TextView t && !(v instanceof android.widget.EditText)) sb.append(t.getText()).append('\n');
        if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), sb);
    }

    private static AlertDialog dialog() {
        idle();
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    private static void press(AlertDialog d, int which) {
        d.getButton(which).performClick();
        idle();
    }

    /** The result the screen handed back, read reflectively: Robolectric's Shadows isn't on the test compile classpath. */
    private static Intent resultOf(Activity a) throws Exception {
        Object shadow = Class.forName("org.robolectric.Shadows").getMethod("shadowOf", Activity.class).invoke(null, a);
        return (Intent) shadow.getClass().getMethod("getResultIntent").invoke(shadow);
    }

    private static int resultCode(Activity a) throws Exception {
        Object shadow = Class.forName("org.robolectric.Shadows").getMethod("shadowOf", Activity.class).invoke(null, a);
        return (Integer) shadow.getClass().getMethod("getResultCode").invoke(shadow);
    }

    // ---- opening

    @Test
    public void anExistingEntryOpensAsAFileWithTheKeysHidden() throws Exception {
        Activity a = open(legacyEntry(true), PACKAGE, true).get();
        String shown = editor(a);
        assertTrue(shown.contains("\"project_id\": \"demo-app-12345\""));
        assertFalse(shown.contains(KEY));
        assertFalse(shown.contains("0123456789abcdef"));
        assertFalse(((CodeEditor) view(a, R.id.json_editor)).isEditable());
        // The screen shows what was stored, and none of it in the clear.
        String page = allText(view(a, R.id.content));
        assertTrue(page.contains("demo-app-12345"));
        assertTrue(page.contains(HOST));
        assertTrue(page.contains(BUCKET));
        assertFalse(page.contains(KEY));
        assertFalse(page.contains(APP_ID));
        assertTrue(page.contains(a.getString(R.string.fb_package_matches, PACKAGE)) || page.contains(PACKAGE));
        assertTrue(text(a, R.id.status_text).startsWith("Valid configuration"));
        assertTrue(((com.google.android.material.materialswitch.MaterialSwitch) view(a, R.id.lib_switch)).isChecked());
    }

    @Test
    public void theFourOldFieldsAreGone() throws Exception {
        Activity a = open(legacyEntry(true), PACKAGE, false).get();
        assertNull(a.findViewById(a.getResources().getIdentifier("tv_project_id", "id", a.getPackageName())));
        assertNull(a.findViewById(a.getResources().getIdentifier("tv_api_key", "id", a.getPackageName())));
        assertEquals(View.VISIBLE, view(a, R.id.btn_console).getVisibility());
        assertEquals(View.VISIBLE, view(a, R.id.btn_save).getVisibility());
        assertEquals("Save Configuration", text(a, R.id.btn_save));
    }

    @Test
    public void theEditorHighlightsJsonWithATextMateLanguage() throws Exception {
        Activity a = open(legacyEntry(true), PACKAGE, true).get();
        CodeEditor editor = view(a, R.id.json_editor);
        assertTrue("language was " + editor.getEditorLanguage(), editor.getEditorLanguage() instanceof TextMateLanguage);
    }

    @Test
    public void revealingShowsTheRealTextAndStoppingHidesItAgain() throws Exception {
        ActivityController<ManageFirebaseActivity> c = open(legacyEntry(true), PACKAGE, true);
        Activity a = c.get();
        view(a, R.id.btn_reveal).performClick();
        idle();
        assertTrue(editor(a).contains(KEY));
        assertTrue(((CodeEditor) view(a, R.id.json_editor)).isEditable());
        c.pause().stop();
        idle();
        assertFalse(editor(a).contains(KEY));
        assertFalse(((CodeEditor) view(a, R.id.json_editor)).isEditable());
    }

    @Test
    public void anEmptyEntryShowsTheEmptyStateAndCannotBeSavedOrEnabled() throws Exception {
        Activity a = open(new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE), PACKAGE, true).get();
        assertEquals(View.VISIBLE, view(a, R.id.editor_empty).getVisibility());
        assertEquals("No configuration", text(a, R.id.status_text));
        assertFalse(view(a, R.id.btn_save).isEnabled());
        assertEquals(View.VISIBLE, view(a, R.id.summary_empty).getVisibility());
        view(a, R.id.layout_switch).performClick();
        idle();
        assertFalse(((com.google.android.material.materialswitch.MaterialSwitch) view(a, R.id.lib_switch)).isChecked());
    }

    // ---- pasting, validating, saving

    @Test
    public void aPastedFileIsReadValidatedAndSaved() throws Exception {
        ActivityController<ManageFirebaseActivity> c = open(new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE), PACKAGE, true);
        Activity a = c.get();
        String json = pro.sketchware.firebase.GoogleServicesConfigTestFixtures.file(PACKAGE);
        clip(json);
        view(a, R.id.btn_paste).performClick();
        idle();
        assertTrue(text(a, R.id.status_text).startsWith("Valid configuration"));
        assertTrue(view(a, R.id.btn_save).isEnabled());
        // Pasted keys are hidden at once.
        assertFalse(editor(a).contains(pro.sketchware.firebase.GoogleServicesConfigTestFixtures.KEY));
        // Turn it on, then save.
        view(a, R.id.layout_switch).performClick();
        idle();
        assertTrue(((com.google.android.material.materialswitch.MaterialSwitch) view(a, R.id.lib_switch)).isChecked());
        view(a, R.id.btn_save).performClick();
        idle();
        assertTrue(a.isFinishing());
        assertEquals(Activity.RESULT_OK, resultCode(a));
        ProjectLibraryBean saved = resultOf(a).getParcelableExtra("firebase");
        assertNotNull(saved);
        // The build still reads the same four values.
        assertEquals("demo-app-12345-default-rtdb.firebaseio.com", saved.data);
        assertEquals("1:123456789012:android:0123456789abcdef", saved.reserved1);
        assertEquals(pro.sketchware.firebase.GoogleServicesConfigTestFixtures.KEY, saved.reserved2);
        assertEquals("demo-app-12345.appspot.com", saved.reserved3);
        assertEquals("Y", saved.useYn);
        assertEquals(json, saved.configurations.get(ManageFirebaseActivity.CONFIG_JSON));
    }

    @Test
    public void invalidJsonShowsPreciseErrorsAndBlocksSaving() throws Exception {
        Activity a = open(new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE), PACKAGE, false).get();
        clip("{\n  \"project_info\": {\n    \"project_id\": \"demo-app-12345\",\n  }\n}");
        view(a, R.id.btn_paste).performClick();
        idle();
        assertTrue(text(a, R.id.status_text).startsWith("1 error"));
        assertFalse(view(a, R.id.btn_save).isEnabled());
        String issues = allText(view(a, R.id.issues_list));
        assertTrue(issues, issues.contains("Line 4"));
        assertTrue(issues, issues.contains("Invalid JSON"));
        // Clicking Save anyway changes nothing.
        view(a, R.id.btn_save).performClick();
        idle();
        assertFalse(a.isFinishing());
    }

    @Test
    public void aFileForAnotherPackageWarnsAndAsksBeforeSaving() throws Exception {
        Activity a = open(new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE), "com.other.pkg", true).get();
        clip(pro.sketchware.firebase.GoogleServicesConfigTestFixtures.file(PACKAGE));
        view(a, R.id.btn_paste).performClick();
        idle();
        assertTrue(view(a, R.id.btn_save).isEnabled());
        assertTrue(allText(view(a, R.id.issues_list)).contains("com.other.pkg"));
        view(a, R.id.btn_save).performClick();
        AlertDialog d = dialog();
        assertEquals(a.getString(R.string.fb_mismatch_message), ((TextView) d.findViewById(android.R.id.message)).getText().toString());
        assertFalse(a.isFinishing());
        press(d, AlertDialog.BUTTON_POSITIVE);
        assertTrue(a.isFinishing());
    }

    @Test
    public void severalClientsCanBeToldApartAndTheProjectsOneIsChosen() throws Exception {
        Activity a = open(new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE), "com.demo.second", true).get();
        clip(pro.sketchware.firebase.GoogleServicesConfigTestFixtures.twoClients());
        view(a, R.id.btn_paste).performClick();
        idle();
        ChipGroup chips = view(a, R.id.client_chips);
        assertEquals(View.VISIBLE, view(a, R.id.client_scroll).getVisibility());
        assertEquals(2, chips.getChildCount());
        assertTrue(((com.google.android.material.chip.Chip) chips.getChildAt(1)).isChecked());
        assertTrue(allText(view(a, R.id.summary_rows)).contains("bbbb"));
        chips.getChildAt(0).performClick();
        idle();
        assertTrue(allText(view(a, R.id.summary_rows)).contains("aaaa"));
        assertTrue(allText(view(a, R.id.summary_rows)).contains("com.demo.first"));
    }

    @Test
    public void formatPrettyPrintsAndRefusesBrokenJson() throws Exception {
        Activity a = open(new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE), PACKAGE, true).get();
        clip("{\"a\":1}");
        view(a, R.id.btn_paste).performClick();
        idle();
        view(a, R.id.btn_reveal).performClick();
        idle();
        view(a, R.id.btn_format).performClick();
        idle();
        assertEquals("{\n  \"a\": 1\n}\n", editor(a));
        clip("{\"a\":");
        view(a, R.id.btn_paste).performClick();
        AlertDialog d = dialog();
        press(d, AlertDialog.BUTTON_POSITIVE); // replace
        String before = editor(a);
        view(a, R.id.btn_format).performClick();
        idle();
        assertEquals(before, editor(a));
    }

    @Test
    public void replacingAnExistingConfigurationAsksFirst() throws Exception {
        Activity a = open(legacyEntry(true), PACKAGE, true).get();
        clip("{\"project_info\":{}}");
        view(a, R.id.btn_paste).performClick();
        AlertDialog d = dialog();
        assertNotNull(d);
        assertTrue(d.isShowing());
        assertTrue(allText(view(a, R.id.content)).contains("demo-app-12345"));
        press(d, AlertDialog.BUTTON_NEGATIVE);
        assertTrue(allText(view(a, R.id.content)).contains("demo-app-12345"));
    }

    // ---- leaving, disabling, deleting

    @Test
    public void leavingWithoutChangesHandsTheEntryBackUntouched() throws Exception {
        ProjectLibraryBean entry = legacyEntry(true);
        Activity a = open(entry, PACKAGE, true).get();
        ((androidx.activity.ComponentActivity) a).getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertTrue(a.isFinishing());
        ProjectLibraryBean back = resultOf(a).getParcelableExtra("firebase");
        // Migration only reads: the four fields are exactly what they were.
        assertEquals(HOST, back.data);
        assertEquals(APP_ID, back.reserved1);
        assertEquals(KEY, back.reserved2);
        assertEquals(BUCKET, back.reserved3);
        assertEquals("Y", back.useYn);
        assertNull(back.configurations.get(ManageFirebaseActivity.CONFIG_JSON));
    }

    @Test
    public void leavingWithUnsavedChangesAsksFirst() throws Exception {
        Activity a = open(legacyEntry(true), PACKAGE, true).get();
        clip(pro.sketchware.firebase.GoogleServicesConfigTestFixtures.file(PACKAGE));
        view(a, R.id.btn_paste).performClick();
        press(dialog(), AlertDialog.BUTTON_POSITIVE); // replace
        ((androidx.activity.ComponentActivity) a).getOnBackPressedDispatcher().onBackPressed();
        AlertDialog d = dialog();
        assertEquals(a.getString(R.string.fb_unsaved_message), ((TextView) d.findViewById(android.R.id.message)).getText().toString());
        assertFalse(a.isFinishing());
        press(d, AlertDialog.BUTTON_NEUTRAL); // cancel: stays
        assertFalse(a.isFinishing());
        ((androidx.activity.ComponentActivity) a).getOnBackPressedDispatcher().onBackPressed();
        press(dialog(), AlertDialog.BUTTON_NEGATIVE); // discard
        assertTrue(a.isFinishing());
        assertEquals(Activity.RESULT_CANCELED, resultCode(a));
    }

    @Test
    public void turningItOffAsksForConfirmation() throws Exception {
        Activity a = open(legacyEntry(true), PACKAGE, true).get();
        view(a, R.id.layout_switch).performClick();
        AlertDialog d = dialog();
        assertTrue(((com.google.android.material.materialswitch.MaterialSwitch) view(a, R.id.lib_switch)).isChecked());
        press(d, AlertDialog.BUTTON_POSITIVE);
        assertFalse(((com.google.android.material.materialswitch.MaterialSwitch) view(a, R.id.lib_switch)).isChecked());
    }

    @Test
    public void deletingAsksThenClearsEverythingAndTurnsFirebaseOff() throws Exception {
        ProjectLibraryBean entry = legacyEntry(true);
        entry.configurations.put(ManageFirebaseActivity.CONFIG_JSON, GoogleServicesConfig.fromStoredValues(HOST, APP_ID, KEY, BUCKET, PACKAGE));
        entry.configurations.put("material3", true); // belongs to someone else: must stay
        Activity a = open(entry, PACKAGE, true).get();
        a.onOptionsItemSelected(menuItem(a, 3));
        AlertDialog d = dialog();
        assertFalse(a.isFinishing());
        press(d, AlertDialog.BUTTON_POSITIVE);
        assertTrue(a.isFinishing());
        ProjectLibraryBean back = resultOf(a).getParcelableExtra("firebase");
        assertEquals("", back.data);
        assertEquals("", back.reserved1);
        assertEquals("", back.reserved2);
        assertEquals("", back.reserved3);
        assertEquals("N", back.useYn);
        assertNull(back.configurations.get(ManageFirebaseActivity.CONFIG_JSON));
        assertEquals(true, back.configurations.get("material3"));
    }

    private static android.view.MenuItem menuItem(Activity a, int id) {
        android.view.Menu menu = new androidx.appcompat.view.menu.MenuBuilder(a);
        a.onCreateOptionsMenu(menu);
        return menu.findItem(id);
    }

    // ---- a stored file that no longer agrees with the four fields is not trusted

    @Test
    public void aStoredFileThatDisagreesWithTheFieldsIsRebuiltFromTheFields() throws Exception {
        ProjectLibraryBean entry = legacyEntry(true);
        // Another screen (the step-by-step wizard) changed the API key afterwards.
        entry.configurations.put(ManageFirebaseActivity.CONFIG_JSON, GoogleServicesConfig.fromStoredValues(HOST, APP_ID, "AIza" + "Z".repeat(35), BUCKET, PACKAGE));
        Activity a = open(entry, PACKAGE, true).get();
        view(a, R.id.btn_reveal).performClick();
        idle();
        assertTrue(editor(a).contains(KEY));
        assertFalse(editor(a).contains("ZZZZ"));
    }

    @Test
    public void aStoredFileThatAgreesIsKeptWithItsOwnFormatting() throws Exception {
        String stored = pro.sketchware.firebase.GoogleServicesConfigTestFixtures.file(PACKAGE);
        ProjectLibraryBean entry = new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE);
        GoogleServicesConfig c = GoogleServicesConfig.parse(stored, PACKAGE);
        entry.data = c.libraryData();
        entry.reserved1 = c.appId();
        entry.reserved2 = c.apiKey();
        entry.reserved3 = c.storageBucket();
        entry.useYn = "Y";
        entry.configurations.put(ManageFirebaseActivity.CONFIG_JSON, stored);
        Activity a = open(entry, PACKAGE, true).get();
        view(a, R.id.btn_reveal).performClick();
        idle();
        assertEquals(stored, editor(a));
    }

    // ---- the tests of the connection

    @Test
    public void connectionTestsAreListedSeparatelyAndNeedAValidFile() throws Exception {
        Activity a = open(new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_FIREBASE), PACKAGE, true).get();
        ViewGroup tests = view(a, R.id.tests_list);
        assertEquals(4, tests.getChildCount());
        String all = allText(tests);
        assertTrue(all.contains("SDK initialization"));
        assertTrue(all.contains("Authentication"));
        assertTrue(all.contains("Database access"));
        assertTrue(all.contains("Storage access"));
        assertFalse(view(a, R.id.btn_run_tests).isEnabled());
    }

    @Test
    public void staleResultsAreClearedWhenTheFileChanges() throws Exception {
        Activity a = open(legacyEntry(true), PACKAGE, true).get();
        assertTrue(view(a, R.id.btn_run_tests).isEnabled());
        assertTrue(allText(view(a, R.id.tests_list)).contains("Not run yet"));
    }

    // ---- looks

    @Test
    public void rendersInDarkAndLight() throws Exception {
        for (boolean dark : new boolean[]{true, false}) {
            Activity a = open(legacyEntry(true), PACKAGE, dark).get();
            idle();
            save(a, "firebase-manager" + (dark ? "-dark" : "-light"));
        }
    }

    private static void save(Activity activity, String name) throws Exception {
        View root = activity.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(ThemeHarness.WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ThemeHarness.HEIGHT, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, ThemeHarness.WIDTH, ThemeHarness.HEIGHT);
        Bitmap bitmap = Bitmap.createBitmap(ThemeHarness.WIDTH, ThemeHarness.HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ThemeHarness.themeColor(activity, android.R.attr.colorBackground));
        root.draw(canvas);
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
