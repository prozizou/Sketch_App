package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

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

import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.blocks.manager.BlockManagerAdapter;
import pro.sketchware.blocks.manager.PaletteListing;

/** The Block Manager screen: search, live counts, filters, palette rows with a 48 dp handle and menu. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class BlockManagerScreenTest {
    private Activity activity(boolean dark) throws Exception {
        RuntimeEnvironment.setQualifiers(dark ? "+night" : "+notnight");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        return Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
    }

    private static View layout(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        return root;
    }

    private static final BlockManagerAdapter.Listener NO_OP = new BlockManagerAdapter.Listener() {
        @Override
        public void onPaletteClick(PaletteListing.PaletteRow row) {
        }

        @Override
        public void onPaletteLongClick(PaletteListing.PaletteRow row) {
        }

        @Override
        public void onPaletteMenu(PaletteListing.PaletteRow row, View anchor) {
        }

        @Override
        public void onStartDrag(RecyclerView.ViewHolder holder) {
        }

        @Override
        public void onBlockClick(PaletteListing.BlockRow row) {
        }
    };

    private View screen(boolean dark, boolean reorderable) throws Exception {
        Activity activity = activity(dark);
        View root = LayoutInflater.from(activity).inflate(R.layout.activity_blocks_manager, null);
        ((TextView) root.findViewById(R.id.stat_palettes_value)).setText("3");
        ((TextView) root.findViewById(R.id.stat_blocks_value)).setText("12");
        ((TextView) root.findViewById(R.id.stat_bin_value)).setText("1");
        RecyclerView list = root.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(activity));
        BlockManagerAdapter adapter = new BlockManagerAdapter(NO_OP);
        list.setAdapter(adapter);
        List<BlockManagerAdapter.Row> rows = new ArrayList<>();
        rows.add(new BlockManagerAdapter.Palette(new PaletteListing.PaletteRow(0, "Toast helpers", "#FF5722", "UI", 2,
                System.currentTimeMillis() - 5 * 60_000, true), false, reorderable));
        rows.add(new BlockManagerAdapter.Palette(new PaletteListing.PaletteRow(1, "Files", "#FFEB3B", "", 10, 0, false), true, reorderable));
        adapter.submit(rows);
        return layout(root, ThemeHarness.WIDTH, ThemeHarness.HEIGHT);
    }

    @Test
    public void everyControlIsThereAndCountsAreShown() throws Exception {
        View root = screen(true, true);
        for (int id : new int[]{R.id.toolbar, R.id.search, R.id.stat_palettes, R.id.stat_blocks, R.id.stat_bin,
                R.id.filter_all, R.id.filter_favorites, R.id.filter_recent, R.id.filter_custom, R.id.list, R.id.fab}) {
            assertNotNull(root.getResources().getResourceEntryName(id), root.findViewById(id));
        }
        assertEquals("12", ((TextView) root.findViewById(R.id.stat_blocks_value)).getText().toString());
        // Only the recycle bin card does something when tapped.
        assertTrue(root.findViewById(R.id.stat_bin).isClickable());
        assertTrue(!root.findViewById(R.id.stat_palettes).isClickable());
        save(root, "block-manager-dark");
        save(screen(false, true), "block-manager-light");
    }

    @Test
    public void paletteRowsShowRealDataAndHaveLargeTouchTargets() throws Exception {
        View root = screen(false, true);
        RecyclerView list = root.findViewById(R.id.list);
        View first = list.getChildAt(0);
        assertNotNull(first);
        assertEquals("Toast helpers", ((TextView) first.findViewById(R.id.name)).getText().toString());
        String details = ((TextView) first.findViewById(R.id.details)).getText().toString();
        assertTrue(details, details.startsWith("2 blocks · Edited") && details.endsWith("· UI"));
        assertEquals(View.VISIBLE, first.findViewById(R.id.favorite).getVisibility());
        int min = Math.round(48 * root.getResources().getDisplayMetrics().density);
        assertTrue(first.findViewById(R.id.drag_handle).getWidth() >= min);
        assertTrue(first.findViewById(R.id.more).getHeight() >= min);
        View second = list.getChildAt(1);
        // No modification date known (older palette): nothing invented.
        assertEquals("10 blocks", ((TextView) second.findViewById(R.id.details)).getText().toString());
        assertTrue(((com.google.android.material.card.MaterialCardView) second.findViewById(R.id.card)).isChecked());
    }

    @Test
    public void dragHandleOnlyWhenReorderingIsPossible() throws Exception {
        View root = screen(false, false);
        View first = ((RecyclerView) root.findViewById(R.id.list)).getChildAt(0);
        assertEquals(View.GONE, first.findViewById(R.id.drag_handle).getVisibility());
    }

    private static void save(View root, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ThemeHarness.themeColor(root.getContext(), android.R.attr.colorBackground));
        root.draw(canvas);
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
