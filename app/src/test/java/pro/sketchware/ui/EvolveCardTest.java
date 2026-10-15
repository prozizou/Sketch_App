package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.databinding.EvolveItemBinding;
import pro.sketchware.evolve.EvolveCard;
import pro.sketchware.evolve.PreviewFrame;
import pro.sketchware.evolve.SharedBlock;
import pro.sketchware.evolve.SharedBlockCodec;
import pro.sketchware.evolve.SharedKind;
import pro.sketchware.evolve.TagRow;

/** Evolve cards: their texts (plurals, checks, tags), the scaled preview, the tag row, and the card itself. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class EvolveCardTest {

    private static Activity activity(boolean dark) {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        try {
            ThemeHarness.prepareApplication();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Context app = ApplicationProvider.getApplicationContext();
        return Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
    }

    private static SharedBlock entry(SharedKind kind, int count, long downloads, String description, List<String> opCodes,
                                     boolean raw, String json, String xml) {
        return new SharedBlock("id", "Show a toast when the list is empty, then go back to the previous screen", description,
                "uid", "Aziz", 1_760_000_000_000L, 1, count, opCodes, raw, downloads, json, kind, "", xml);
    }

    private static ArrayList<BlockBean> stack() {
        BlockBean toast = new BlockBean("10", "showToast %s", " ", "showToast");
        toast.parameters.add("@11");
        BlockBean variable = new BlockBean("11", "counter", "s", "getVar");
        return new ArrayList<>(List.of(toast, variable));
    }

    @Test
    public void countsUseTheRightPluralAndTheUsersNumberFormat() {
        Locale.setDefault(Locale.US);
        Context context = activity(true);
        SharedBlock one = entry(SharedKind.BLOCKS, 1, 1, "", List.of(), false, "", "");
        assertEquals("1 block · 1 download", EvolveCard.meta(context, one));
        SharedBlock many = entry(SharedKind.BLOCKS, 2, 1204, "", List.of(), false, "", "");
        assertEquals("2 blocks · 1,204 downloads", EvolveCard.meta(context, many));
        SharedBlock none = entry(SharedKind.MOREBLOCK, 0, 0, "", List.of(), false, "", "");
        assertEquals("0 blocks · 0 downloads", EvolveCard.meta(context, none));
        SharedBlock layout = entry(SharedKind.VIEW, 1, 3, "", List.of(), false, "", "<TextView/>");
        assertEquals("1 element · 3 downloads", EvolveCard.meta(context, layout));
    }

    @Test
    public void tagsAreTheOpcodesOrTheLayoutsElements() {
        SharedBlock blocks = entry(SharedKind.BLOCKS, 2, 0, "", List.of("showToast", "getVar"), false, "", "");
        assertEquals(List.of("showToast", "getVar"), EvolveCard.tags(blocks));
        SharedBlock layout = entry(SharedKind.VIEW, 3, 0, "", List.of(), false, "",
                "<?xml version=\"1.0\"?><LinearLayout><TextView/><androidx.cardview.widget.CardView/><TextView/></LinearLayout>");
        assertEquals(List.of("LinearLayout", "TextView", "CardView"), EvolveCard.tags(layout));
    }

    @Test
    public void theCardSaysWhatTheEntryNeedsAndWhenItWritesJava() {
        Context context = activity(true);
        SharedBlock block = entry(SharedKind.BLOCKS, 2, 0, "", List.of("showToast", "getVar"), false, "", "");
        assertEquals("Needs a variable or list: counter", EvolveCard.needs(context, block, stack()));
        BlockBean other = new BlockBean("12", "items", "l", "getVar");
        ArrayList<BlockBean> two = stack();
        two.add(other);
        assertEquals("Needs 2 variables or lists: counter, items", EvolveCard.needs(context, block, two));
        assertNull(EvolveCard.needs(context, block, new ArrayList<>(List.of(new BlockBean("1", "x", " ", "showToast")))));
        SharedBlock newer = new SharedBlock("id", "n", "", "u", "", 0, Integer.MAX_VALUE, 1, List.of(), false, 0, "");
        assertTrue(EvolveCard.needs(context, newer, stack()).contains(String.valueOf(Integer.MAX_VALUE)));

        assertFalse(EvolveCard.usesRawCode(block, stack()));
        ArrayList<BlockBean> raw = new ArrayList<>(List.of(new BlockBean("1", "add source directly %s", " ", "addSourceDirectly")));
        assertTrue(EvolveCard.usesRawCode(block, raw));
        assertTrue("flagged on the entry", EvolveCard.usesRawCode(entry(SharedKind.BLOCKS, 1, 0, "", List.of(), true, "", ""), null));
    }

    @Test
    public void thePreviewIsEnlargedButAlwaysFitsWithoutStretching() {
        assertEquals(PreviewFrame.MAX_SCALE, PreviewFrame.fit(100, 40, 1000, 1000), 0.001f);
        assertEquals("too wide: shrunk to the width", 0.5f, PreviewFrame.fit(400, 40, 200, 1000), 0.001f);
        assertEquals("too tall: shrunk to the height", 0.25f, PreviewFrame.fit(100, 800, 1000, 200), 0.001f);
        assertEquals("empty child", 1f, PreviewFrame.fit(0, 0, 200, 200), 0.001f);
    }

    @Test
    public void tagsThatDontFitBecomeACount() {
        int[] widths = {50, 50, 50, 50};
        assertEquals("all fit", 4, TagRow.fitCount(widths, 4, 500, 10, n -> 30));
        // 50 + 10 + 50 = 110, + 10 + "+2" (30) = 150 fits in 160; a third tag would need 170 + 40.
        assertEquals(2, TagRow.fitCount(widths, 4, 160, 10, n -> 30));
        assertEquals("only the count fits", 0, TagRow.fitCount(widths, 4, 60, 10, n -> 30));
        assertEquals("more tags than created", 4, TagRow.fitCount(widths, 4, 500, 10, n -> 30));
        assertEquals(3, TagRow.fitCount(widths, 12, 220, 10, n -> 30));
    }

    @Test
    public void cardsRenderDenseAndWithinTheirWidth() throws Exception {
        Locale.setDefault(Locale.US);
        for (boolean dark : new boolean[]{true, false}) {
            Activity activity = activity(dark);
            ArrayList<BlockBean> blocks = SharedBlockCodec.decode(SharedBlockCodec.encode(stack()));
            List<String> manyOpcodes = List.of("showToast", "getVar", "setText", "listGet", "addSourceDirectly", "intentSetScreen",
                    "startActivity", "finishActivity", "setVisible", "requestFocus");
            SharedBlock withBlocks = entry(SharedKind.MOREBLOCK, 1, 1,
                    "Checks the list and shows a toast when it is empty, so the screen never looks broken. Then it goes back "
                            + "to the previous screen and clears every field of the form.", manyOpcodes, true,
                    SharedBlockCodec.encode(stack()), "");
            SharedBlock layout = entry(SharedKind.VIEW, 3, 12, "", List.of(), false, "",
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n  <TextView android:text=\"Hi\"/>\n  <Button/>\n</LinearLayout>");
            SharedBlock damaged = entry(SharedKind.BLOCKS, 2, 0, "", List.of("if"), false, "not json", "");

            for (int widthDp : new int[]{320, 411}) {
                LinearLayout column = new LinearLayout(activity);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setBackgroundColor(activity.getColor(R.color.evolve_bg));
                int pad = dp(activity, 12);
                column.setPadding(pad, pad, pad, pad);
                EvolveItemBinding first = card(activity, column, withBlocks, blocks, R.string.evolve_moreblock_kind_badge);
                EvolveItemBinding xml = card(activity, column, layout, null, R.string.evolve_xml_kind_badge);
                EvolveItemBinding broken = card(activity, column, damaged, null, R.string.evolve_blocks_kind_badge);

                int width = dp(activity, widthDp);
                column.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                column.layout(0, 0, width, column.getMeasuredHeight());

                assertEquals(2, first.itemDescription.getMaxLines());
                assertTrue(first.itemDescription.getLineCount() >= 2);
                assertEquals(View.VISIBLE, first.itemRawBadge.getVisibility());
                assertEquals("Needs a variable or list: counter", first.itemNeeds.getText().toString());
                assertEquals("1 block · 1 download", first.itemMeta.getText().toString());
                assertTrue("long titles stop at two lines", first.itemName.getLineCount() <= 2);
                assertTrue("the preview fits the card", first.itemPreview.getRight() <= ((View) first.itemPreview.getParent()).getWidth());
                assertTrue("the tags never push Import out", first.itemPick.getRight() <= ((View) first.itemPick.getParent()).getWidth());
                assertTrue("some tags are folded into +N", first.itemOpcodes.getShownCount() < manyOpcodes.size());
                assertTrue(first.itemPick.isEnabled());
                assertTrue(first.itemPick.getHeight() >= dp(activity, 48));
                assertTrue(first.itemMenu.getWidth() >= dp(activity, 48));
                assertTrue(first.itemPick.getContentDescription().toString().startsWith("Import"));

                assertEquals("a layout shows its XML, not an empty preview", View.GONE, xml.itemPreview.getVisibility());
                assertEquals(View.VISIBLE, xml.itemXml.getVisibility());
                assertEquals(View.GONE, xml.itemPreviewMissing.getVisibility());

                assertFalse("a damaged entry can't be imported", broken.itemPick.isEnabled());
                assertEquals(View.VISIBLE, broken.itemPreviewMissing.getVisibility());
                assertEquals(activity.getString(R.string.evolve_damaged), broken.itemPreviewMissing.getText().toString());

                save(column, "evolve-cards-" + widthDp + "-" + (dark ? "dark" : "light"));
            }
        }
    }

    private static EvolveItemBinding card(Activity activity, LinearLayout column, SharedBlock block, ArrayList<BlockBean> blocks, int kind) {
        EvolveItemBinding item = EvolveItemBinding.inflate(LayoutInflater.from(activity), column, false);
        EvolveCard.bind(item, block, blocks, kind, () -> {
        }, anchor -> {
        });
        column.addView(item.getRoot());
        return item;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void save(View view, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
