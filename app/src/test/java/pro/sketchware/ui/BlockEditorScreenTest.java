package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
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
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

import io.github.rosemoe.sora.widget.CodeEditor;
import mod.hilal.saif.activities.tools.BlocksManagerCreatorActivity;
import pro.sketchware.R;

/** The Edit Block screen: tabs, loading and saving the stored block, validation, drafts and the keyboard. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class BlockEditorScreenTest {
    private static final String BLOCKS = "[{\"name\":\"showToast\",\"type\":\" \",\"typeName\":\"\",\"spec\":\"show toast %s\","
            + "\"color\":\"#FF5722\",\"code\":\"android.widget.Toast.makeText(getApplicationContext(), %s, 0).show();\","
            + "\"palette\":\"9\",\"imports\":\"android.widget.Toast\",\"unknownKey\":\"kept\"},"
            + "{\"name\":\"other\",\"type\":\"c\",\"typeName\":\"\",\"spec\":\"when %b\",\"color\":\"#2196F3\","
            + "\"code\":\"if (%s) {\\n%s\\n}\",\"palette\":\"9\"}]";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private ActivityController<BlocksManagerCreatorActivity> open(String mode, String position, boolean dark) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        File blocks = new File(temp.getRoot(), "block.json");
        if (!blocks.exists()) Files.writeString(blocks.toPath(), BLOCKS);
        Context app = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(app, BlocksManagerCreatorActivity.class)
                .putExtra("mode", mode).putExtra("path", blocks.getAbsolutePath()).putExtra("color", "#8A55D7");
        if (mode.equals("add")) intent.putExtra("pallet", "9");
        else intent.putExtra("pos", position);
        return Robolectric.buildActivity(BlocksManagerCreatorActivity.class, intent).setup();
    }

    private static <T extends View> T view(Activity activity, int id) {
        return activity.findViewById(id);
    }

    private static void idle() {
        ShadowLooper.idleMainLooper(800, TimeUnit.MILLISECONDS);
    }

    private static void type(Activity activity, int id, String text) {
        ((TextView) activity.findViewById(id)).setText(text);
        idle();
    }

    private static void select(Activity activity, int tab) {
        TabLayout tabs = activity.findViewById(R.id.tabs);
        tabs.getTabAt(tab).select();
        idle();
    }

    @Test
    public void fourTabsShowOnePageEach() throws Exception {
        Activity activity = open("edit", "0", true).get();
        TabLayout tabs = view(activity, R.id.tabs);
        assertEquals(4, tabs.getTabCount());
        int[] pages = {R.id.page_general, R.id.page_parameters, R.id.page_spec, R.id.page_code};
        for (int tab = 0; tab < 4; tab++) {
            select(activity, tab);
            for (int page = 0; page < 4; page++) {
                assertEquals("tab " + tab + " page " + page, page == tab ? View.VISIBLE : View.GONE, view(activity, pages[page]).getVisibility());
            }
        }
        // Cancel and Save stay on screen whichever tab is open.
        View bar = view(activity, R.id.bottom_bar);
        assertTrue(bar.getHeight() > 0);
        assertEquals(View.VISIBLE, view(activity, R.id.save).getVisibility());
    }

    @Test
    public void anExistingBlockIsLoadedAsStored() throws Exception {
        Activity activity = open("edit", "0", true).get();
        assertEquals("showToast", ((TextView) view(activity, R.id.name)).getText().toString());
        assertEquals("show toast %s", ((TextView) view(activity, R.id.spec)).getText().toString());
        assertEquals("#FF5722", ((TextView) view(activity, R.id.colour)).getText().toString());
        assertEquals("android.widget.Toast.makeText(getApplicationContext(), %s, 0).show();", ((CodeEditor) view(activity, R.id.code)).getText().toString());
        // Regular block: no else part, no type name.
        assertEquals(View.GONE, view(activity, R.id.spec2_layout).getVisibility());
        assertEquals(View.GONE, view(activity, R.id.typename_layout).getVisibility());
        // The preview really draws the block.
        assertTrue(((ViewGroup) view(activity, R.id.block_area)).getChildCount() > 0);
        assertFalse(((CodeEditor) view(activity, R.id.code)).canUndo());
    }

    @Test
    public void ifElseBlocksShowTheElsePart() throws Exception {
        Activity activity = open("edit", "1", false).get();
        assertEquals(View.GONE, view(activity, R.id.spec2_layout).getVisibility());
        // Picking the If-else shape shows it.
        ViewGroup shapes = view(activity, R.id.type_shapes);
        shapes.getChildAt(2).performClick();
        idle();
        assertEquals(View.VISIBLE, view(activity, R.id.spec2_layout).getVisibility());
    }

    @Test
    public void twelveShapesAreOfferedAndTheCurrentOneIsChecked() throws Exception {
        Activity activity = open("edit", "1", false).get();
        ViewGroup shapes = view(activity, R.id.type_shapes);
        assertEquals(12, shapes.getChildCount());
        int checked = 0;
        for (int i = 0; i < shapes.getChildCount(); i++) {
            com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) shapes.getChildAt(i);
            assertTrue("shape " + i + " is drawn", ((ViewGroup) ((ViewGroup) card.getChildAt(0)).getChildAt(0)).getChildCount() > 0);
            assertTrue(card.getContentDescription().length() > 0);
            if (card.isChecked()) checked++;
        }
        assertEquals(1, checked);
        assertTrue(((com.google.android.material.card.MaterialCardView) shapes.getChildAt(1)).isChecked());
    }

    @Test
    public void parametersFollowTheSpecAndShowWhetherTheCodeUsesThem() throws Exception {
        Activity activity = open("edit", "0", false).get();
        RecyclerView list = view(activity, R.id.params_list);
        assertEquals(1, list.getAdapter().getItemCount());
        type(activity, R.id.spec, "show %s for %d ms %m.view");
        assertEquals(3, list.getAdapter().getItemCount());
        assertEquals(View.GONE, view(activity, R.id.params_empty).getVisibility());
        // The code only refers to the first input: the problem list stays empty (unused inputs are reported in Code).
        select(activity, 1);
        list.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        list.layout(0, 0, 1000, list.getMeasuredHeight());
        View first = list.getChildAt(0);
        assertNotNull(first);
        assertTrue(((TextView) first.findViewById(R.id.details)).getText().toString().contains("%s"));
        type(activity, R.id.spec, "no parameters here");
        assertEquals(View.VISIBLE, view(activity, R.id.params_empty).getVisibility());
    }

    @Test
    public void specBuilderAndTextStayInSync() throws Exception {
        Activity activity = open("edit", "0", false).get();
        ChipGroup chips = view(activity, R.id.spec_chips);
        assertEquals(3, chips.getChildCount());
        type(activity, R.id.spec, "a %s b %m.view");
        assertEquals(4, chips.getChildCount());
        // A spec the engine refuses is explained, not rewritten.
        type(activity, R.id.spec, "a %m b");
        assertEquals("a %m b", ((TextView) view(activity, R.id.spec)).getText().toString());
        assertEquals(View.VISIBLE, view(activity, R.id.spec_issues).getVisibility());
        assertTrue(((TextView) view(activity, R.id.spec_issues)).getText().toString().contains("%m"));
        type(activity, R.id.spec, "a %s");
        assertEquals(View.GONE, view(activity, R.id.spec_issues).getVisibility());
    }

    @Test
    public void diagnosticsNameTheLine() throws Exception {
        Activity activity = open("edit", "0", false).get();
        ((CodeEditor) view(activity, R.id.code)).setText("a(%s);\nb(%d);");
        idle();
        ViewGroup rows = view(activity, R.id.diagnostics_rows);
        boolean found = false;
        for (int i = 0; i < rows.getChildCount(); i++) {
            String text = ((TextView) ((ViewGroup) rows.getChildAt(i)).getChildAt(0)).getText().toString();
            if (text.contains("2") && text.contains("%d")) found = true;
        }
        assertTrue("an error on line 2", found);
    }

    @Test
    public void quickTemplatesFillAnEmptyBlock() throws Exception {
        Activity activity = open("add", "0", false).get();
        ChipGroup chips = view(activity, R.id.template_chips);
        assertTrue(chips.getChildCount() >= 6);
        chips.getChildAt(0).performClick();
        idle();
        assertEquals("show toast %s", ((TextView) view(activity, R.id.spec)).getText().toString());
        assertTrue(((CodeEditor) view(activity, R.id.code)).getText().toString().contains("Toast.makeText"));
        // Now the block has content: only templates of its own type (and the empty one) remain.
        assertTrue(chips.getChildCount() < 9);
    }

    @Test
    public void savingKeepsEveryStoredKeyAndPaletteId() throws Exception {
        ActivityController<BlocksManagerCreatorActivity> controller = open("edit", "0", false);
        Activity activity = controller.get();
        ((CodeEditor) view(activity, R.id.code)).setText("android.widget.Toast.makeText(getApplicationContext(), %s, 1).show();");
        idle();
        view(activity, R.id.save).performClick();
        idle();
        assertTrue(activity.isFinishing());
        List<Map<String, Object>> saved = new Gson().fromJson(Files.readString(new File(temp.getRoot(), "block.json").toPath()),
                new TypeToken<List<Map<String, Object>>>() {
                }.getType());
        assertEquals(2, saved.size());
        Map<String, Object> block = saved.get(0);
        assertEquals("kept", block.get("unknownKey"));
        assertEquals("9", block.get("palette"));
        assertEquals(" ", block.get("type"));
        assertEquals("show toast %s", block.get("spec"));
        assertTrue(block.get("code").toString().endsWith(", 1).show();"));
        assertEquals("android.widget.Toast", block.get("imports"));
        // The second block is untouched.
        assertEquals("other", saved.get(1).get("name"));
        assertTrue(new File(temp.getRoot(), "block.json.bak").isFile());
    }

    @Test
    public void errorsStopTheSaveAndKeepTheFile() throws Exception {
        Activity activity = open("edit", "0", false).get();
        String before = Files.readString(new File(temp.getRoot(), "block.json").toPath());
        type(activity, R.id.name, "other"); // already used by the second block
        view(activity, R.id.save).performClick();
        idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        assertFalse(activity.isFinishing());
        assertEquals(before, Files.readString(new File(temp.getRoot(), "block.json").toPath()));
        assertTrue(((com.google.android.material.textfield.TextInputLayout) view(activity, R.id.name_layout)).getError() != null);
    }

    @Test
    public void leavingWithChangesAsksFirst() throws Exception {
        Activity activity = open("edit", "0", false).get();
        activity.onBackPressed();
        assertTrue("nothing changed: leave at once", activity.isFinishing());

        Activity second = open("edit", "0", false).get();
        type(second, R.id.name, "renamed");
        second.onBackPressed();
        assertFalse(second.isFinishing());
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertTrue(dialog.isShowing());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle(); // dialog buttons answer through a handler
        assertTrue(second.isFinishing());
    }

    @Test
    public void recreatingTheScreenKeepsTheEditsAndTheTab() throws Exception {
        ActivityController<BlocksManagerCreatorActivity> controller = open("edit", "0", false);
        Activity activity = controller.get();
        type(activity, R.id.name, "renamedBlock");
        type(activity, R.id.spec, "renamed %s");
        select(activity, 2);
        controller.recreate();
        Activity again = controller.get();
        idle();
        assertEquals("renamedBlock", ((TextView) view(again, R.id.name)).getText().toString());
        assertEquals("renamed %s", ((TextView) view(again, R.id.spec)).getText().toString());
        assertEquals(View.VISIBLE, view(again, R.id.page_spec).getVisibility());
        assertEquals(2, ((TabLayout) view(again, R.id.tabs)).getSelectedTabPosition());
    }

    @Test
    public void theBottomBarRidesAboveTheKeyboard() throws Exception {
        Activity activity = open("edit", "0", false).get();
        View content = view(activity, R.id.content);
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 720))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .setVisible(WindowInsetsCompat.Type.ime(), true).build();
        content.dispatchApplyWindowInsets(insets.toWindowInsets());
        assertTrue("padding " + content.getPaddingBottom(), content.getPaddingBottom() >= 720);
        WindowInsetsCompat closed = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48)).build();
        content.dispatchApplyWindowInsets(closed.toWindowInsets());
        assertTrue(content.getPaddingBottom() >= 48 && content.getPaddingBottom() < 720);
    }

    @Test
    public void rendersEveryTabInDarkAndLight() throws Exception {
        for (boolean dark : new boolean[]{true, false}) {
            Activity activity = open("edit", "1", dark).get();
            String[] names = {"general", "parameters", "spec", "code"};
            for (int tab = 0; tab < 4; tab++) {
                select(activity, tab);
                save(activity, "block-editor-" + names[tab] + (dark ? "-dark" : "-light"));
            }
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
