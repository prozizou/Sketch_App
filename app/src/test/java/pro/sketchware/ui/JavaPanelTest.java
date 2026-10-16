package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;

import com.besome.sketch.beans.BlockBean;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

import a.a.a.jq;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.databinding.LogicEditorBinding;
import pro.sketchware.logic.sync.BlockCatalog;
import pro.sketchware.logic.sync.BlockJson;
import pro.sketchware.logic.sync.BlockSpecs;
import pro.sketchware.logic.sync.JavaToBlocks;
import pro.sketchware.logic.sync.ProjectContext;
import pro.sketchware.logic.sync.ui.JavaPanel;

/** The editable Java panel: what it shows, what it says, what it keeps, and how it applies the Java to the blocks. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = Application.class)
public class JavaPanelTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** The editor around the panel, with the blocks of the event held in a list. */
    private final class FakeEditor implements JavaPanel.Host {
        final Activity activity;
        final File folder;
        ArrayList<BlockBean> blocks;
        final List<ArrayList<BlockBean>> replaced = new ArrayList<>();
        final View root;

        FakeEditor(Activity activity, View root, File folder, String java) {
            this.activity = activity;
            this.root = root;
            this.folder = folder;
            this.blocks = convert(java);
        }

        @Override
        public Activity activity() {
            return activity;
        }

        @Override
        public View messageAnchor() {
            return root;
        }

        @Override
        public String eventKey() {
            return "MainActivity_onCreate";
        }

        @Override
        public File syncFolder() {
            return folder;
        }

        @Override
        public ArrayList<BlockBean> blocks() {
            return BlockJson.copyOf(blocks);
        }

        @Override
        public String generate(ArrayList<BlockBean> list) {
            return pro.sketchware.logic.sync.SyncFixturesBridge.generate(list);
        }

        @Override
        public ProjectContext projectContext() {
            return pro.sketchware.logic.sync.SyncFixturesBridge.context();
        }

        @Override
        public jq buildConfig() {
            return new jq();
        }

        @Override
        public BlockSpecs.Strings strings() {
            return pro.sketchware.logic.sync.SyncFixturesBridge.strings();
        }

        @Override
        public void replaceBlocks(ArrayList<BlockBean> list) {
            replaced.add(BlockJson.copyOf(list));
            blocks = BlockJson.copyOf(list);
        }
    }

    private static ArrayList<BlockBean> convert(String java) {
        return pro.sketchware.logic.sync.SyncFixturesBridge.converter().convert(java, List.of()).blocks();
    }

    private Activity activity;
    private View root;
    private LogicEditorBinding binding;
    private File folder;

    @Before
    public void setUp() throws Exception {
        folder = temp.newFolder("sync");
    }

    private void inflate(boolean dark) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        root = LayoutInflater.from(activity).inflate(R.layout.logic_editor, null);
        binding = LogicEditorBinding.bind(root);
    }

    private JavaPanel open(FakeEditor editor) {
        JavaPanel panel = new JavaPanel(binding, editor);
        binding.codeEditor.setTypefaceText(android.graphics.Typeface.MONOSPACE);
        pro.sketchware.utility.EditorUtils.loadJavaConfig(binding.codeEditor);
        panel.attach();
        binding.codePanel.setVisibility(View.VISIBLE);
        panel.onShown();
        waitFor(() -> binding.btnCodeApply != null && statusIsNotPreparing());
        return panel;
    }

    private boolean statusIsNotPreparing() {
        return !binding.codeStatus.getText().toString().equals(activity.getString(R.string.logic_sync_preparing));
    }

    private static void idle() {
        try {
            Class.forName("org.robolectric.shadows.ShadowLooper").getMethod("idleMainLooper").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void advance(long millis) {
        try {
            Class.forName("org.robolectric.shadows.ShadowLooper").getMethod("idleMainLooper", long.class, java.util.concurrent.TimeUnit.class)
                    .invoke(null, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void waitFor(BooleanSupplier condition) {
        for (int i = 0; i < 500 && !condition.getAsBoolean(); i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            // the app's clock is Robolectric's: let some of its time pass so delayed work (checks, saves) is due
            advance(100);
        }
        assertTrue("timed out", condition.getAsBoolean());
    }

    private static AlertDialog latestDialog() {
        try {
            Object dialog = Class.forName("org.robolectric.shadows.ShadowDialog").getMethod("getLatestDialog").invoke(null);
            return dialog instanceof AlertDialog alert && alert.isShowing() ? alert : null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private String code() {
        return binding.codeEditor.getText().toString().replace("\r\n", "\n");
    }

    /** Types at the end of the code, as a person does. */
    private void type(String text) {
        binding.codeEditor.getText().insert(binding.codeEditor.getText().getLineCount() - 1,
                binding.codeEditor.getText().getColumnCount(binding.codeEditor.getText().getLineCount() - 1), text);
        idle();
    }

    private void waitForStatus(String text) {
        waitFor(() -> binding.codeStatus.getText().toString().startsWith(text));
    }

    private String status() {
        return binding.codeStatus.getText().toString();
    }

    private void layoutAndSave(String name) throws Exception {
        int width = ThemeHarness.WIDTH;
        int height = 1000;
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ThemeHarness.themeColor(activity, android.R.attr.colorBackground));
        binding.codePanel.draw(canvas);
        //noinspection ResultOfMethodCallIgnored
        new File("build/reports/sync-renders").mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File("build/reports/sync-renders", name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    // ---- showing and editing

    @Test
    public void theCodeOfTheBlocksIsShownAndSaidToMatch() throws Exception {
        inflate(true);
        open(new FakeEditor(activity, root, folder, "counter++;\nname = \"x\";"));
        assertEquals("counter++;\nname = \"x\";", code());
        assertEquals(activity.getString(R.string.logic_sync_status_synced), status());
        assertTrue(binding.codeEditor.isEditable());
        assertFalse("nothing to apply yet", binding.btnCodeApply.isEnabled());
        assertFalse(binding.btnCodeUndo.isEnabled());
        layoutAndSave("panel-synced-dark");
    }

    @Test
    public void typingMarksTheJavaAsEditedAndNotApplied() throws Exception {
        inflate(true);
        open(new FakeEditor(activity, root, folder, "counter++;"));
        type("\ncounter--;");
        waitForStatus(activity.getString(R.string.logic_sync_status_edited));
        assertTrue(binding.btnCodeApply.isEnabled());
        layoutAndSave("panel-edited-dark");
    }

    @Test
    public void aMistakeIsReportedWithItsLineAndNothingElseHappens() throws Exception {
        inflate(true);
        open(new FakeEditor(activity, root, folder, "counter++;"));
        type("\nfoo(");
        waitForStatus("Line 2");
        assertTrue(status(), status().startsWith("Line 2:"));
        layoutAndSave("panel-error-dark");
    }

    @Test
    public void typingBackWhatTheBlocksMakeIsInSyncAgain() throws Exception {
        inflate(true);
        open(new FakeEditor(activity, root, folder, "counter++;"));
        type("\n");
        waitForStatus(activity.getString(R.string.logic_sync_status_edited));
        binding.codeEditor.getText().delete(binding.codeEditor.getText().length() - 1, binding.codeEditor.getText().length());
        idle();
        assertEquals(activity.getString(R.string.logic_sync_status_synced), status());
        assertFalse(binding.btnCodeApply.isEnabled());
    }

    // ---- applying

    @Test
    public void applyingAnInvalidTextShowsTheMistakesAndChangesNothing() throws Exception {
        inflate(true);
        FakeEditor editor = new FakeEditor(activity, root, folder, "counter++;");
        open(editor);
        type("\nfoo(");
        waitForStatus("Line 2");
        binding.btnCodeApply.performClick();
        waitFor(() -> latestDialog() != null);
        AlertDialog dialog = latestDialog();
        TextView message = dialog.findViewById(android.R.id.message);
        assertTrue(message.getText().toString(), message.getText().toString().contains("Line 2"));
        assertTrue(editor.replaced.isEmpty());
        assertEquals("counter++;", BlockJson.hash(editor.blocks).isEmpty() ? "" : "counter++;");
    }

    @Test
    public void applyingShowsAReviewAndOnlyAConfirmationChangesTheBlocks() throws Exception {
        inflate(true);
        FakeEditor editor = new FakeEditor(activity, root, folder, "counter++;");
        open(editor);
        type("\nint local = 3;\nname = \"y\";");
        waitForStatus(activity.getString(R.string.logic_sync_status_edited));
        binding.btnCodeApply.performClick();
        waitFor(() -> latestDialog() != null);

        AlertDialog review = latestDialog();
        TextView summary = review.findViewById(R.id.review_summary);
        assertNotNull(summary);
        assertTrue(summary.getText().toString(), summary.getText().toString().startsWith("2 added"));
        TextView sources = review.findViewById(R.id.review_sources);
        assertEquals("the local variable is kept as Java and listed", View.VISIBLE, sources.getVisibility());
        assertTrue(sources.getText().toString().contains("int local = 3;"));
        TextView diff = review.findViewById(R.id.review_diff);
        assertTrue(diff.getText().toString().contains("+ name = \"y\";"));
        assertTrue("nothing changed before the confirmation", editor.replaced.isEmpty());

        // cancel: still nothing
        review.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
        assertTrue(editor.replaced.isEmpty());
        assertTrue("the typed Java is still there", code().contains("name = \"y\";"));
        assertTrue(binding.btnCodeApply.isEnabled());

        // confirm
        binding.btnCodeApply.performClick();
        waitFor(() -> latestDialog() != null);
        latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(1, editor.replaced.size());
        assertEquals("increaseInt", editor.replaced.get(0).get(0).opCode);
        assertTrue(editor.replaced.get(0).stream().anyMatch(b -> b.opCode.equals("setVarString")));
        assertTrue(editor.replaced.get(0).stream().anyMatch(b -> b.opCode.equals("addSourceDirectly")));
    }

    @Test
    public void javaThatTheBlocksAlreadyMakeOffersToReformatOnly() throws Exception {
        inflate(true);
        FakeEditor editor = new FakeEditor(activity, root, folder, "counter++;");
        open(editor);
        type("   ");
        waitForStatus(activity.getString(R.string.logic_sync_status_edited));
        binding.btnCodeApply.performClick();
        waitFor(() -> latestDialog() != null);
        AlertDialog dialog = latestDialog();
        TextView note = dialog.findViewById(R.id.review_note);
        assertEquals(View.VISIBLE, note.getVisibility());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertTrue("no block changed", editor.replaced.isEmpty());
        assertEquals("counter++;", code());
        assertEquals(activity.getString(R.string.logic_sync_status_synced), status());
    }

    @Test
    public void applyThenUndoAndRedoGoThroughTheBlocks() throws Exception {
        inflate(true);
        FakeEditor editor = new FakeEditor(activity, root, folder, "counter++;");
        ArrayList<BlockBean> original = BlockJson.copyOf(editor.blocks);
        JavaPanel panel = open(editor);
        type("\ncounter--;");
        waitForStatus(activity.getString(R.string.logic_sync_status_edited));
        binding.btnCodeApply.performClick();
        waitFor(() -> latestDialog() != null);
        latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(1, editor.replaced.size());
        ArrayList<BlockBean> applied = BlockJson.copyOf(editor.blocks);

        // the editor reloads: a new panel over the same event
        inflate(true);
        editor.blocks = applied;
        FakeEditor reopened = new FakeEditor(activity, root, folder, "counter++;");
        reopened.blocks = applied;
        open(reopened);
        assertEquals("counter++;\ncounter--;", code());
        assertTrue("the history survived the reload", binding.btnCodeUndo.isEnabled());
        assertFalse(binding.btnCodeRedo.isEnabled());

        binding.btnCodeUndo.performClick();
        idle();
        assertEquals(1, reopened.replaced.size());
        assertEquals(BlockJson.hash(original), BlockJson.hash(reopened.replaced.get(0)));
        assertNotNull(panel);

        inflate(true);
        FakeEditor third = new FakeEditor(activity, root, folder, "counter++;");
        third.blocks = BlockJson.copyOf(original);
        open(third);
        assertTrue(binding.btnCodeRedo.isEnabled());
        binding.btnCodeRedo.performClick();
        idle();
        assertEquals(BlockJson.hash(applied), BlockJson.hash(third.replaced.get(0)));
    }

    // ---- never losing what was typed

    @Test
    public void typedJavaComesBackWhenTheEditorIsOpenedAgain() throws Exception {
        inflate(true);
        FakeEditor editor = new FakeEditor(activity, root, folder, "counter++;");
        JavaPanel panel = open(editor);
        type("\nname = \"half done\";");
        waitForStatus(activity.getString(R.string.logic_sync_status_edited));
        panel.flush();
        // flushing writes in the background
        File[] files = new File[0];
        for (int i = 0; i < 200 && files.length == 0; i++) {
            Thread.sleep(20);
            files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        }
        assertEquals(1, files.length);
        panel.destroy();

        inflate(true);
        open(new FakeEditor(activity, root, folder, "counter++;"));
        assertEquals("counter++;\nname = \"half done\";", code());
        assertTrue(binding.btnCodeApply.isEnabled());
    }

    @Test
    public void whenTheBlocksChangedTheJavaIsKeptAndABannerOffersTheNewCode() throws Exception {
        inflate(true);
        FakeEditor editor = new FakeEditor(activity, root, folder, "counter++;");
        JavaPanel panel = open(editor);
        type("\nname = \"mine\";");
        waitForStatus(activity.getString(R.string.logic_sync_status_edited));

        editor.blocks = convert("counter--;");
        panel.onBlocksChanged();
        assertEquals("the typed Java is untouched", "counter++;\nname = \"mine\";", code());
        assertEquals(View.VISIBLE, binding.codeBanner.getVisibility());

        binding.codeBannerAction.performClick();
        AlertDialog dialog = latestDialog();
        assertNotNull("replacing asks first", dialog);
        assertEquals("still untouched while asking", "counter++;\nname = \"mine\";", code());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals("counter--;", code());
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals("the old text is on the clipboard", "counter++;\nname = \"mine\";", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        assertEquals(View.GONE, binding.codeBanner.getVisibility());
    }

    @Test
    public void whenNothingWasEditedTheCodeFollowsTheBlocks() throws Exception {
        inflate(true);
        FakeEditor editor = new FakeEditor(activity, root, folder, "counter++;");
        JavaPanel panel = open(editor);
        editor.blocks = convert("counter--;\nname = \"z\";");
        panel.onBlocksChanged();
        assertEquals("counter--;\nname = \"z\";", code());
        assertEquals(View.GONE, binding.codeBanner.getVisibility());
        assertNull(latestDialog());
    }

    @Test
    public void rendersInLightToo() throws Exception {
        inflate(false);
        open(new FakeEditor(activity, root, folder, "if (flag) {\n  counter++;\n} else {\n  name = \"x\";\n}"));
        type("\nint local = 1;");
        waitForStatus(activity.getString(R.string.logic_sync_status_edited));
        layoutAndSave("panel-edited-light");
    }
}
