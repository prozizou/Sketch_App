package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.test.core.app.ApplicationProvider;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.property.ExtraAttributeRow;
import com.besome.sketch.editor.property.PropertySubheader;
import com.besome.sketch.editor.property.ViewPropertyItems;

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

import mod.agus.jcoderz.beans.ViewBeans;
import mod.hey.studios.project.ProjectSettings;
import mod.hilal.saif.activities.tools.ConfigActivity;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class PropertiesScreenRenderTest {
    private ViewPropertyItems items(int type, String id) throws Exception {
        RuntimeEnvironment.setQualifiers("+night");
        ThemeHarness.prepareApplication();
        Context app = ApplicationProvider.getApplicationContext();
        Activity activity = Robolectric.buildActivity(ConfigActivity.class, new Intent(app, ConfigActivity.class)).setup().get();
        ViewPropertyItems items = new ViewPropertyItems(activity);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setProjectSettings(new ProjectSettings("999"));
        ProjectFileBean file = new ProjectFileBean(0, "main");
        items.setProjectFileBean(file);
        ViewBean bean = new ViewBean(id, type);
        items.a("999", bean);
        return items;
    }

    private static void layout(View root, int h) {
        root.measure(View.MeasureSpec.makeMeasureSpec(ThemeHarness.WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private static void save(View root, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF111318);
        root.draw(canvas);
        //noinspection ResultOfMethodCallIgnored
        ThemeHarness.OUTPUT_DIR.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(ThemeHarness.OUTPUT_DIR, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static ArrayList<ExtraAttributeRow> rows(ViewGroup group) {
        ArrayList<ExtraAttributeRow> rows = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof ExtraAttributeRow row) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static int shown(ViewGroup group) {
        int count = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getVisibility() == View.VISIBLE) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void textViewGetsTextAttributesInFoldedSections() throws Exception {
        ViewPropertyItems items = items(ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "textview1");

        ArrayList<ExtraAttributeRow> rows = rows(items);
        assertTrue("a TextView has many extra attributes, got " + rows.size(), rows.size() > 40);
        for (ExtraAttributeRow row : rows) {
            assertEquals("catalog sections start folded: " + row.getAttr().name(), View.GONE, row.getVisibility());
        }
        boolean hasTextRow = rows.stream().anyMatch(r -> r.getAttr().name().equals("android:letterSpacing"));
        assertTrue(hasTextRow);
    }

    @Test
    public void searchShowsMatchesEvenInFoldedSectionsAndHidesTheRest() throws Exception {
        ViewPropertyItems items = items(ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "textview1");
        layout(items, 6000);
        int before = shown(items);

        items.setFilter("letter");
        layout(items, 6000);

        ArrayList<ExtraAttributeRow> rows = rows(items);
        long visible = rows.stream().filter(r -> r.getVisibility() == View.VISIBLE).count();
        assertEquals(1, visible);
        assertTrue("other properties are hidden", shown(items) < before);

        items.setFilter("");
        layout(items, 6000);
        assertEquals("clearing the search restores the list", before, shown(items));
    }

    @Test
    public void foldingAHeaderHidesItsRows() throws Exception {
        ViewPropertyItems items = items(ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW, "textview1");
        PropertySubheader first = null;
        for (int i = 0; i < items.getChildCount(); i++) {
            if (items.getChildAt(i) instanceof PropertySubheader header && header.getHeaderName().equals("Text")) {
                first = header;
            }
        }
        assertNotNull("the Text section exists", first);
        assertTrue(first.isCollapsed());

        first.performClick();

        assertTrue(!first.isCollapsed());
        long visible = rows(items).stream().filter(r -> r.getVisibility() == View.VISIBLE).count();
        assertTrue("the Text rows are now shown, got " + visible, visible > 10);
    }

    @Test
    public void rendersThePropertiesScreen() throws Exception {
        ViewPropertyItems items = items(ViewBeans.VIEW_TYPE_WIDGET_GRIDVIEW, "gridview1");
        for (int i = 0; i < items.getChildCount(); i++) {
            if (items.getChildAt(i) instanceof PropertySubheader header && header.getHeaderName().startsWith("List")) {
                header.performClick();
            }
        }
        layout(items, 4000);
        save(items, "properties-gridview");
    }
}
