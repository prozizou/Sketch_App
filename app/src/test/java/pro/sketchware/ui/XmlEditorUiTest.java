package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.function.BooleanSupplier;

import io.github.rosemoe.sora.widget.CodeEditor;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.activities.editor.view.xml.XmlDiagnostics;
import pro.sketchware.activities.editor.view.xml.XmlEditorUi;
import pro.sketchware.activities.editor.view.xml.XmlFormatter;
import pro.sketchware.activities.editor.view.xml.XmlSymbols;
import pro.sketchware.databinding.ViewCodeEditorBinding;
import pro.sketchware.utility.EditorUtils;

/** The XML editor's chrome: status bar, quick-insert keys, find and replace, format, and how it all looks. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class XmlEditorUiTest {
    private static final String SAMPLE = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:padding="16dp">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Hello" />
                <Button
                    android:id="@+id/go"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Go" />
            </LinearLayout>
            """;

    private static final class Fixture {
        Activity activity;
        ViewCodeEditorBinding binding;
        CodeEditor editor;
        XmlEditorUi ui;
        int changes;
        int formatTaps;
    }

    private static Fixture fixture(boolean dark, String xml) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        Fixture f = new Fixture();
        f.activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        f.binding = ViewCodeEditorBinding.inflate(f.activity.getLayoutInflater());
        f.editor = f.binding.editor;
        f.editor.setText(xml);
        try {
            EditorUtils.loadXmlConfig(f.editor);
        } catch (RuntimeException | Error e) {
            // Syntax colors need the app's assets; the chrome is what is tested here.
        }
        f.binding.toolbar.inflateMenu(R.menu.xml_editor_menu);
        f.binding.toolbar.setTitle(R.string.xml_editor_title);
        f.binding.toolbar.setSubtitle("activity_main.xml");
        f.ui = new XmlEditorUi(f.binding);
        f.ui.attach(() -> f.changes++, () -> f.formatTaps++);
        return f;
    }

    private static void layout(View root, int widthDp, int heightDp) {
        float density = root.getResources().getDisplayMetrics().density;
        int w = Math.round(widthDp * density);
        int h = Math.round(heightDp * density);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
    }

    private static void save(View root, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static void waitUntil(String what, BooleanSupplier done) {
        long end = System.currentTimeMillis() + 5000;
        while (!done.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > end) throw new AssertionError("Timed out waiting for " + what);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static String text(TextView view) {
        return view.getText().toString();
    }

    @Test
    public void aValidLayoutSaysSoAndCountsItsElements() throws Exception {
        Fixture f = fixture(true, SAMPLE);
        assertTrue(f.ui.lastResult().valid());
        assertEquals("Valid XML · 3 elements", text(f.binding.statusText));
        assertFalse("nothing to go to", f.binding.statusValidity.isClickable());
        assertEquals("Ln 1, Col 1", text(f.binding.statusPosition));
    }

    @Test
    public void anErrorShowsItsLineAndTheStatusTakesYouThere() throws Exception {
        Fixture f = fixture(true, SAMPLE);
        // Break the closing tag of the Button (line 19 of the sample).
        String broken = SAMPLE.replace("</LinearLayout>", "</Linear>");
        f.editor.setText(broken);
        XmlDiagnostics.Result result = f.ui.checkNow();
        assertFalse(result.valid());
        assertTrue(text(f.binding.statusText), text(f.binding.statusText).startsWith("Line " + (result.line() + 1) + ":"));
        assertTrue(f.binding.statusValidity.isClickable());
        assertTrue("announced to screen readers with the way to the error",
                f.binding.statusValidity.getContentDescription().toString().contains("Go to the error"));
        f.ui.goToError();
        assertEquals(result.line(), f.editor.getCursor().getLeftLine());
        assertEquals("Ln " + (result.line() + 1), text(f.binding.statusPosition).substring(0, ("Ln " + (result.line() + 1)).length()));
        // Fixing it clears the status.
        f.editor.setText(SAMPLE);
        assertTrue(f.ui.checkNow().valid());
    }

    @Test
    public void theQuickKeysTypeAtTheCaretAndWrapASelection() throws Exception {
        Fixture f = fixture(true, "<a b=\"\" />");
        assertEquals(XmlSymbols.ALL.size(), f.binding.symbolBar.getChildCount());
        for (int i = 0; i < f.binding.symbolBar.getChildCount(); i++) {
            View key = f.binding.symbolBar.getChildAt(i);
            assertNotNull(key.getContentDescription());
            assertFalse("a key never takes the focus from the editor", key.isFocusable());
        }
        // Caret between the quotes of b="" (line 0, column 6), then the id key.
        f.editor.setSelection(0, 6);
        f.ui.insert(XmlSymbols.ALL.stream().filter(s -> s.label().equals("@+id/")).findFirst().orElseThrow());
        assertEquals("<a b=\"@+id/\" />", f.editor.getText().toString());
        assertEquals(11, f.editor.getCursor().getLeftColumn());
        // Select @+id/ and wrap it in quotes: the pair key wraps instead of replacing.
        f.editor.setSelectionRegion(0, 6, 0, 11);
        f.ui.insert(XmlSymbols.ALL.stream().filter(s -> s.label().equals("\"\"")).findFirst().orElseThrow());
        assertEquals("<a b=\"\"@+id/\"\" />", f.editor.getText().toString());
        assertEquals(13, f.editor.getCursor().getLeftColumn());
        // Tapping the key view itself does the same as calling insert.
        Fixture g = fixture(true, "<a/>");
        g.editor.setSelection(0, 2);
        g.binding.symbolBar.getChildAt(4).performClick();
        assertEquals("<a\"\"/>", g.editor.getText().toString());
        assertEquals(3, g.editor.getCursor().getLeftColumn());
    }

    @Test
    public void theStatusBarFollowsTheCaretAndTheSelection() throws Exception {
        Fixture f = fixture(true, SAMPLE);
        f.editor.setSelection(2, 4);
        assertEquals("Ln 3, Col 5", text(f.binding.statusPosition));
        f.editor.setSelectionRegion(2, 4, 2, 5);
        assertEquals("Ln 3, Col 5 \u00b7 1 character selected", text(f.binding.statusPosition));
        f.editor.setSelectionRegion(2, 4, 2, 9);
        assertEquals("Ln 3, Col 5 \u00b7 5 characters selected", text(f.binding.statusPosition));
    }

    @Test
    public void formatIsOneUndoableEditAndLeavesBrokenXmlAlone() throws Exception {
        String messy = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\"><TextView android:text=\"Hi\"/></LinearLayout>";
        Fixture f = fixture(true, messy);
        assertEquals(XmlEditorUi.FormatResult.DONE, f.ui.format());
        assertEquals(XmlFormatter.format(messy), f.editor.getText().toString());
        assertEquals("a second format has nothing to do", XmlEditorUi.FormatResult.UNCHANGED, f.ui.format());
        f.editor.undo();
        assertEquals("one undo restores the layout as it was", messy, f.editor.getText().toString());

        f.editor.setText("<a><b></a>");
        assertEquals(XmlEditorUi.FormatResult.INVALID, f.ui.format());
        assertEquals("<a><b></a>", f.editor.getText().toString());
    }

    @Test
    public void findAndReplaceWorkOnTheText() throws Exception {
        Fixture f = fixture(true, SAMPLE);
        assertFalse(f.ui.isFindVisible());
        assertFalse("nothing to close", f.ui.hideFind());
        f.ui.showFind();
        assertTrue(f.ui.isFindVisible());
        assertEquals(View.VISIBLE, f.binding.findPanel.getRoot().getVisibility());

        f.binding.findPanel.findQuery.setText("match_parent");
        waitUntil("search results", () -> text(f.binding.findPanel.findCount).length() > 0);
        assertEquals("3 matches", text(f.binding.findPanel.findCount));

        f.binding.findPanel.findNext.performClick();
        waitUntil("a selected match", () -> text(f.binding.findPanel.findCount).contains(" of "));
        assertEquals("1 of 3", text(f.binding.findPanel.findCount));
        assertEquals("match_parent", f.editor.getText().substring(f.editor.getCursor().getLeft(), f.editor.getCursor().getRight()));

        f.binding.findPanel.findQuery.setText("no such text");
        waitUntil("no results", () -> text(f.binding.findPanel.findCount).equals("No results"));

        f.binding.findPanel.findQuery.setText("match_parent");
        // The old match is still selected, so it may be reported as "1 of 3" rather than "3 matches".
        waitUntil("results again", () -> text(f.binding.findPanel.findCount).matches("(3 matches|1 of 3)"));
        f.binding.findPanel.replaceText.setText("wrap_content");
        f.binding.findPanel.replaceAll.performClick();
        waitUntil("replace all", () -> !f.editor.getText().toString().contains("match_parent"));
        assertEquals(SAMPLE.replace("match_parent", "wrap_content"), f.editor.getText().toString());

        assertTrue(f.ui.hideFind());
        assertEquals(View.GONE, f.binding.findPanel.getRoot().getVisibility());
    }

    @Test
    public void replacingOneAtATimeMovesOn() throws Exception {
        Fixture f = fixture(true, "<a x=\"1\" y=\"1\" z=\"1\" />");
        f.ui.showFind();
        f.binding.findPanel.findQuery.setText("\"1\"");
        waitUntil("results", () -> text(f.binding.findPanel.findCount).equals("3 matches"));
        f.binding.findPanel.replaceText.setText("\"2\"");
        f.binding.findPanel.replaceOne.performClick();   // selects the first match
        waitUntil("first match selected", () -> text(f.binding.findPanel.findCount).startsWith("1 of"));
        f.binding.findPanel.replaceOne.performClick();   // replaces it
        waitUntil("first replaced", () -> f.editor.getText().toString().startsWith("<a x=\"2\""));
        assertEquals("only one replaced", "<a x=\"2\" y=\"1\" z=\"1\" />", f.editor.getText().toString());
    }

    @Test
    public void aBadRegularExpressionSaysSoInsteadOfCrashing() throws Exception {
        Fixture f = fixture(true, SAMPLE);
        f.ui.showFind();
        f.binding.findPanel.findRegex.setChecked(true);
        f.binding.findPanel.findQuery.setText("(unclosed");
        assertEquals("Not a valid regular expression", text(f.binding.findPanel.findCount));
        f.binding.findPanel.findQuery.setText("android:id=\"@\\+id/\\w+\"");
        waitUntil("regex results", () -> text(f.binding.findPanel.findCount).equals("2 matches"));
    }

    @Test
    public void findAndFormatAreOneTapAwayAboveTheKeyboard() throws Exception {
        Fixture f = fixture(true, SAMPLE);
        assertFalse(f.ui.isFindVisible());
        f.binding.quickFind.performClick();
        assertTrue(f.ui.isFindVisible());
        f.ui.hideFind();
        f.binding.quickFormat.performClick();
        assertEquals(1, f.formatTaps);
        assertNotNull(f.binding.quickFind.getContentDescription());
        assertNotNull(f.binding.quickFormat.getContentDescription());
        assertTrue(f.binding.quickFind.getLayoutParams().width >= 44 * f.editor.getResources().getDisplayMetrics().density);
    }

    @Test
    public void everyActionOfTheMenuIsThere() throws Exception {
        Fixture f = fixture(true, SAMPLE);
        android.view.Menu menu = f.binding.toolbar.getMenu();
        for (int id : new int[]{R.id.xml_undo, R.id.xml_redo, R.id.xml_find, R.id.xml_save, R.id.xml_color, R.id.xml_format,
                R.id.xml_preview, R.id.xml_appcompat, R.id.xml_wrap, R.id.xml_reload_colors, R.id.xml_evolve}) {
            assertNotNull("menu item " + id, menu.findItem(id));
        }
        assertTrue(menu.findItem(R.id.xml_wrap).isCheckable());
    }

    @Test
    public void typingTellsTheScreenSoItCanRefreshUndoAndSave() throws Exception {
        Fixture f = fixture(true, SAMPLE);
        int before = f.changes;
        f.editor.getText().insert(0, 0, " ");
        assertTrue(f.changes > before);
        f.ui.detach();
        int after = f.changes;
        f.editor.getText().insert(0, 0, " ");
        assertEquals("nothing is delivered after detach", after, f.changes);
    }

    @Test
    public void theEditorScreenRenders() throws Exception {
        for (boolean dark : new boolean[]{true, false}) {
            Fixture f = fixture(dark, SAMPLE);
            layout(f.binding.getRoot(), 411, 760);
            assertTrue("the editor takes the room the bars leave", f.editor.getHeight() > f.binding.getRoot().getHeight() / 2);
            assertTrue("the bars sit under the editor", f.binding.symbolRow.getTop() >= f.editor.getBottom());
            assertTrue(f.binding.statusBar.getBottom() <= f.binding.getRoot().getHeight());
            save(f.binding.getRoot(), "xml-editor-" + (dark ? "dark" : "light"));
        }
    }

    @Test
    public void anErrorWithFindOpenRendersOnASmallScreen() throws Exception {
        for (boolean dark : new boolean[]{true, false}) {
            Fixture f = fixture(dark, SAMPLE.replace("</LinearLayout>", "</Linear>"));
            f.ui.checkNow();
            f.ui.showFind();
            f.binding.findPanel.findQuery.setText("android:text");
            f.binding.findPanel.replaceText.setText("android:contentDescription");
            waitUntil("results", () -> text(f.binding.findPanel.findCount).length() > 0);
            layout(f.binding.getRoot(), 320, 640);
            assertTrue("the editor keeps room next to the panel", f.editor.getHeight() >= 120 * f.editor.getResources().getDisplayMetrics().density);
            assertTrue(f.binding.findPanel.getRoot().getBottom() <= f.editor.getTop());
            assertTrue("every find control is inside the screen",
                    f.binding.findPanel.findClose.getRight() <= f.binding.getRoot().getWidth());
            assertTrue(f.binding.findPanel.replaceAll.getRight() <= f.binding.getRoot().getWidth());
            save(f.binding.getRoot(), "xml-editor-error-find-320-" + (dark ? "dark" : "light"));
        }
    }
}
